package com.openwallet.ledger.service;

import com.openwallet.ledger.domain.EntryType;
import com.openwallet.ledger.domain.LedgerEntry;
import com.openwallet.ledger.domain.Transaction;
import com.openwallet.ledger.domain.TransactionStatus;
import com.openwallet.ledger.domain.TransactionType;
import com.openwallet.ledger.dto.DepositRequest;
import com.openwallet.ledger.dto.TransactionResponse;
import com.openwallet.ledger.dto.TransferRequest;
import com.openwallet.ledger.dto.WithdrawalRequest;
import com.openwallet.ledger.exception.TransactionNotFoundException;
import com.openwallet.ledger.repository.LedgerEntryRepository;
import com.openwallet.ledger.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@SuppressWarnings({ "DataFlowIssue", "ConstantConditions" })
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public TransactionResponse createDeposit(DepositRequest request) {
        validateAmount(request.getAmount());
        String currency = normalizeCurrency(request.getCurrency());

        Transaction existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey()).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        Transaction tx = Transaction.builder()
                .transactionType(TransactionType.DEPOSIT)
                .amount(request.getAmount())
                .currency(currency)
                .toWalletId(request.getToWalletId())
                .status(TransactionStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .initiatedAt(LocalDateTime.now())
                .build();

        Transaction saved = Objects.requireNonNull(transactionRepository.save(tx));
        createDoubleEntry(saved,
                null,
                request.getToWalletId(),
                request.getAmount());
        saved.setStatus(TransactionStatus.COMPLETED);
        saved.setCompletedAt(LocalDateTime.now());
        Transaction persisted = Objects.requireNonNull(transactionRepository.save(saved));
        return toResponse(persisted);
    }

    @Transactional
    public TransactionResponse createWithdrawal(WithdrawalRequest request) {
        validateAmount(request.getAmount());
        String currency = normalizeCurrency(request.getCurrency());

        Transaction existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey()).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        Transaction tx = Transaction.builder()
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(request.getAmount())
                .currency(currency)
                .fromWalletId(request.getFromWalletId())
                .status(TransactionStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .initiatedAt(LocalDateTime.now())
                .build();

        Transaction saved = Objects.requireNonNull(transactionRepository.save(tx));
        createDoubleEntry(saved,
                request.getFromWalletId(),
                null,
                request.getAmount());
        saved.setStatus(TransactionStatus.COMPLETED);
        saved.setCompletedAt(LocalDateTime.now());
        Transaction persisted = Objects.requireNonNull(transactionRepository.save(saved));
        return toResponse(persisted);
    }

    @Transactional
    public TransactionResponse createTransfer(TransferRequest request) {
        validateAmount(request.getAmount());
        String currency = normalizeCurrency(request.getCurrency());
        if (request.getFromWalletId().equals(request.getToWalletId())) {
            throw new IllegalArgumentException("fromWalletId and toWalletId must differ");
        }

        Transaction existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey()).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        Transaction tx = Transaction.builder()
                .transactionType(TransactionType.TRANSFER)
                .amount(request.getAmount())
                .currency(currency)
                .fromWalletId(request.getFromWalletId())
                .toWalletId(request.getToWalletId())
                .status(TransactionStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .initiatedAt(LocalDateTime.now())
                .build();

        Transaction saved = Objects.requireNonNull(transactionRepository.save(tx));
        createDoubleEntry(saved,
                request.getFromWalletId(),
                request.getToWalletId(),
                request.getAmount());
        saved.setStatus(TransactionStatus.COMPLETED);
        saved.setCompletedAt(LocalDateTime.now());
        Transaction persisted = Objects.requireNonNull(transactionRepository.save(saved));
        return toResponse(persisted);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Long id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found"));
        return toResponse(tx);
    }

    private void createDoubleEntry(Transaction tx, Long fromWalletId, Long toWalletId, BigDecimal amount) {
        // Note: balanceAfter is set to the current amount as a placeholder; real
        // balance computation/integration to wallet is future work.
        BigDecimal debitBalance = amount;
        BigDecimal creditBalance = amount;

        if (fromWalletId != null) {
            LedgerEntry debit = LedgerEntry.builder()
                    .transaction(tx)
                    .walletId(fromWalletId)
                    .accountType("WALLET_" + fromWalletId)
                    .entryType(EntryType.DEBIT)
                    .amount(amount)
                    .balanceAfter(debitBalance)
                    .build();
            ledgerEntryRepository.save(debit);
        } else {
            LedgerEntry debit = LedgerEntry.builder()
                    .transaction(tx)
                    .accountType("CASH_ACCOUNT")
                    .entryType(EntryType.DEBIT)
                    .amount(amount)
                    .balanceAfter(debitBalance)
                    .build();
            ledgerEntryRepository.save(debit);
        }

        if (toWalletId != null) {
            LedgerEntry credit = LedgerEntry.builder()
                    .transaction(tx)
                    .walletId(toWalletId)
                    .accountType("WALLET_" + toWalletId)
                    .entryType(EntryType.CREDIT)
                    .amount(amount)
                    .balanceAfter(creditBalance)
                    .build();
            ledgerEntryRepository.save(credit);
        } else {
            LedgerEntry credit = LedgerEntry.builder()
                    .transaction(tx)
                    .accountType("CASH_ACCOUNT")
                    .entryType(EntryType.CREDIT)
                    .amount(amount)
                    .balanceAfter(creditBalance)
                    .build();
            ledgerEntryRepository.save(credit);
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(new BigDecimal("0.01")) < 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
    }

    private String normalizeCurrency(String currency) {
        if (!StringUtils.hasText(currency)) {
            throw new IllegalArgumentException("Currency is required");
        }
        return currency.toUpperCase(Locale.ROOT);
    }

    private TransactionResponse toResponse(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .transactionType(tx.getTransactionType().name())
                .status(tx.getStatus().name())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .fromWalletId(tx.getFromWalletId())
                .toWalletId(tx.getToWalletId())
                .initiatedAt(tx.getInitiatedAt() != null ? tx.getInitiatedAt().toString() : null)
                .completedAt(tx.getCompletedAt() != null ? tx.getCompletedAt().toString() : null)
                .failureReason(tx.getFailureReason())
                .build();
    }
}
