package com.openwallet.ledger.service;

import com.openwallet.ledger.domain.EntryType;
import com.openwallet.ledger.domain.LedgerEntry;
import com.openwallet.ledger.domain.Transaction;
import com.openwallet.ledger.domain.TransactionStatus;
import com.openwallet.ledger.domain.TransactionType;
import com.openwallet.ledger.dto.DepositRequest;
import com.openwallet.ledger.dto.TransactionListResponse;
import com.openwallet.ledger.dto.TransactionResponse;
import com.openwallet.ledger.dto.TransferRequest;
import com.openwallet.ledger.dto.WithdrawalRequest;
import com.openwallet.ledger.events.TransactionEvent;
import com.openwallet.ledger.events.TransactionEventProducer;
import com.openwallet.ledger.exception.TransactionNotFoundException;
import com.openwallet.ledger.repository.LedgerEntryRepository;
import com.openwallet.ledger.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings({"ConstantConditions", "null" })
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionEventProducer transactionEventProducer;
    private final LedgerEntryService ledgerEntryService;
    private final TransactionLimitService transactionLimitService;

    @Transactional
    public TransactionResponse createDeposit(DepositRequest request) {
        validateAmount(request.getAmount());
        validateDepositRequest(request);
        String currency = normalizeCurrency(request.getCurrency());

        Transaction existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey()).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        // Validate transaction limits before creating transaction
        transactionLimitService.validateTransactionLimits(
                request.getToWalletId(), 
                request.getAmount(), 
                TransactionType.DEPOSIT
        );

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
        
        try {
            publishInitiated(saved);
            createDoubleEntry(saved, null, request.getToWalletId(), request.getAmount());
            saved.setStatus(TransactionStatus.COMPLETED);
            saved.setCompletedAt(LocalDateTime.now());
            Transaction persisted = Objects.requireNonNull(transactionRepository.save(saved));
            publishCompleted(persisted);
            return toResponse(persisted);
        } catch (Exception e) {
            log.error("Failed to complete deposit transaction txId={}, error={}", saved.getId(), e.getMessage(), e);
            saved.setStatus(TransactionStatus.FAILED);
            saved.setFailureReason("Failed to create ledger entries: " + e.getMessage());
            Transaction failed = Objects.requireNonNull(transactionRepository.save(saved));
            publishFailed(failed, e.getMessage());
            throw new IllegalStateException("Failed to complete deposit transaction: " + e.getMessage(), e);
        }
    }

    @Transactional
    public TransactionResponse createWithdrawal(WithdrawalRequest request) {
        validateAmount(request.getAmount());
        validateWithdrawalRequest(request);
        String currency = normalizeCurrency(request.getCurrency());

        Transaction existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey()).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        // Validate transaction limits before creating transaction
        transactionLimitService.validateTransactionLimits(
                request.getFromWalletId(), 
                request.getAmount(), 
                TransactionType.WITHDRAWAL
        );

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
        
        try {
            publishInitiated(saved);
            createDoubleEntry(saved, request.getFromWalletId(), null, request.getAmount());
            saved.setStatus(TransactionStatus.COMPLETED);
            saved.setCompletedAt(LocalDateTime.now());
            Transaction persisted = Objects.requireNonNull(transactionRepository.save(saved));
            publishCompleted(persisted);
            return toResponse(persisted);
        } catch (Exception e) {
            log.error("Failed to complete withdrawal transaction txId={}, error={}", saved.getId(), e.getMessage(), e);
            saved.setStatus(TransactionStatus.FAILED);
            saved.setFailureReason("Failed to create ledger entries: " + e.getMessage());
            Transaction failed = Objects.requireNonNull(transactionRepository.save(saved));
            publishFailed(failed, e.getMessage());
            throw new IllegalStateException("Failed to complete withdrawal transaction: " + e.getMessage(), e);
        }
    }

    @Transactional
    public TransactionResponse createTransfer(TransferRequest request) {
        validateAmount(request.getAmount());
        validateTransferRequest(request);
        String currency = normalizeCurrency(request.getCurrency());

        Transaction existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey()).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        // Validate transaction limits for both wallets (from and to)
        transactionLimitService.validateTransactionLimits(
                request.getFromWalletId(), 
                request.getAmount(), 
                TransactionType.TRANSFER
        );
        transactionLimitService.validateTransactionLimits(
                request.getToWalletId(), 
                request.getAmount(), 
                TransactionType.TRANSFER
        );

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
        
        try {
            publishInitiated(saved);
            createDoubleEntry(saved, request.getFromWalletId(), request.getToWalletId(), request.getAmount());
            saved.setStatus(TransactionStatus.COMPLETED);
            saved.setCompletedAt(LocalDateTime.now());
            Transaction persisted = Objects.requireNonNull(transactionRepository.save(saved));
            publishCompleted(persisted);
            return toResponse(persisted);
        } catch (Exception e) {
            log.error("Failed to complete transfer transaction txId={}, error={}", saved.getId(), e.getMessage(), e);
            saved.setStatus(TransactionStatus.FAILED);
            saved.setFailureReason("Failed to create ledger entries: " + e.getMessage());
            Transaction failed = Objects.requireNonNull(transactionRepository.save(saved));
            publishFailed(failed, e.getMessage());
            throw new IllegalStateException("Failed to complete transfer transaction: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Long id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found"));
        return toResponse(tx);
    }

    @Transactional(readOnly = true)
    public TransactionListResponse getTransactions(
            Long walletId,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            TransactionStatus status,
            TransactionType transactionType,
            Integer page,
            Integer size,
            String sortBy,
            String sortDirection
    ) {
        // Default pagination values
        int pageNumber = (page != null && page >= 0) ? page : 0;
        // Default to 20 if null or <= 0, cap at 100 if > 100
        int pageSize = (size == null || size <= 0) ? 20 : Math.min(size, 100);

        // Default sorting: by initiatedAt descending (newest first)
        Sort sort = Sort.by(Sort.Direction.DESC, "initiatedAt");
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) 
                    ? Sort.Direction.ASC 
                    : Sort.Direction.DESC;
            
            // Validate sort field
            String validSortBy = validateSortField(sortBy);
            sort = Sort.by(direction, validSortBy);
        }

        Pageable pageable = PageRequest.of(pageNumber, pageSize, sort);

        // Query transactions with filters
        Page<Transaction> transactionPage = transactionRepository.findTransactionsWithFilters(
                walletId,
                fromDate,
                toDate,
                status,
                transactionType,
                pageable
        );

        // Convert to response
        List<TransactionResponse> transactionResponses = transactionPage.getContent().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        // Build pagination metadata
        TransactionListResponse.PaginationMetadata pagination = TransactionListResponse.PaginationMetadata.builder()
                .page(transactionPage.getNumber())
                .size(transactionPage.getSize())
                .totalElements(transactionPage.getTotalElements())
                .totalPages(transactionPage.getTotalPages())
                .hasNext(transactionPage.hasNext())
                .hasPrevious(transactionPage.hasPrevious())
                .build();

        return TransactionListResponse.builder()
                .transactions(transactionResponses)
                .pagination(pagination)
                .build();
    }

    /**
     * Validates and normalizes sort field name.
     * Only allows sorting by safe fields to prevent SQL injection.
     */
    private String validateSortField(String sortBy) {
        String normalized = sortBy.trim().toLowerCase();
        // Allowed sort fields
        switch (normalized) {
            case "id":
            case "initiatedat":
            case "completedat":
            case "amount":
            case "status":
            case "transactiontype":
                return normalized.equals("initiatedat") ? "initiatedAt" :
                       normalized.equals("completedat") ? "completedAt" :
                       normalized.equals("transactiontype") ? "transactionType" :
                       normalized;
            default:
                log.warn("Invalid sort field '{}', defaulting to 'initiatedAt'", sortBy);
                return "initiatedAt";
        }
    }

    private void createDoubleEntry(Transaction tx, Long fromWalletId, Long toWalletId, BigDecimal amount) {
        log.info("Creating double-entry for transaction txId={}, fromWalletId={}, toWalletId={}, amount={}",
                tx.getId(), fromWalletId, toWalletId, amount);

        try {
            // Calculate current balances from previous ledger entries BEFORE creating new entries
            // This ensures balanceAfter reflects the actual wallet balance after this transaction
            BigDecimal fromWalletBalanceBefore = BigDecimal.ZERO;
            BigDecimal toWalletBalanceBefore = BigDecimal.ZERO;

            if (fromWalletId != null) {
                fromWalletBalanceBefore = ledgerEntryService.calculateBalanceFromLedger(fromWalletId);
                log.debug("Current balance for fromWalletId={} (before transaction): {}", fromWalletId, fromWalletBalanceBefore);
            }

            if (toWalletId != null) {
                toWalletBalanceBefore = ledgerEntryService.calculateBalanceFromLedger(toWalletId);
                log.debug("Current balance for toWalletId={} (before transaction): {}", toWalletId, toWalletBalanceBefore);
            }

            // Calculate balanceAfter: DEBIT decreases balance, CREDIT increases balance
            BigDecimal debitBalanceAfter = fromWalletId != null 
                    ? fromWalletBalanceBefore.subtract(amount)  // DEBIT: decrease balance
                    : amount;  // Cash account: just use amount as placeholder
            BigDecimal creditBalanceAfter = toWalletId != null 
                    ? toWalletBalanceBefore.add(amount)  // CREDIT: increase balance
                    : amount;  // Cash account: just use amount as placeholder

            // Create and save DEBIT entry
            LedgerEntry debit;
            if (fromWalletId != null) {
                debit = LedgerEntry.builder()
                        .transaction(tx)
                        .walletId(fromWalletId)
                        .accountType("WALLET_" + fromWalletId)
                        .entryType(EntryType.DEBIT)
                        .amount(amount)
                        .balanceAfter(debitBalanceAfter)
                        .build();
                log.debug("Prepared DEBIT LedgerEntry for walletId={}, txId={}, amount={}, balanceBefore={}, balanceAfter={}", 
                        fromWalletId, tx.getId(), amount, fromWalletBalanceBefore, debitBalanceAfter);
            } else {
                debit = LedgerEntry.builder()
                        .transaction(tx)
                        .accountType("CASH_ACCOUNT")
                        .entryType(EntryType.DEBIT)
                        .amount(amount)
                        .balanceAfter(debitBalanceAfter)
                        .build();
                log.debug("Prepared DEBIT LedgerEntry for cash account, txId={}, amount={}, balanceAfter={}", 
                        tx.getId(), amount, debitBalanceAfter);
            }

            LedgerEntry savedDebit = ledgerEntryRepository.save(debit);
            log.info("Saved DEBIT LedgerEntry: id={}, walletId={}, accountType={}, amount={}, balanceAfter={}", 
                    savedDebit.getId(), savedDebit.getWalletId(), savedDebit.getAccountType(), 
                    savedDebit.getAmount(), savedDebit.getBalanceAfter());

            // Create and save CREDIT entry
            LedgerEntry credit;
            if (toWalletId != null) {
                credit = LedgerEntry.builder()
                        .transaction(tx)
                        .walletId(toWalletId)
                        .accountType("WALLET_" + toWalletId)
                        .entryType(EntryType.CREDIT)
                        .amount(amount)
                        .balanceAfter(creditBalanceAfter)
                        .build();
                log.debug("Prepared CREDIT LedgerEntry for walletId={}, txId={}, amount={}, balanceBefore={}, balanceAfter={}", 
                        toWalletId, tx.getId(), amount, toWalletBalanceBefore, creditBalanceAfter);
            } else {
                credit = LedgerEntry.builder()
                        .transaction(tx)
                        .accountType("CASH_ACCOUNT")
                        .entryType(EntryType.CREDIT)
                        .amount(amount)
                        .balanceAfter(creditBalanceAfter)
                        .build();
                log.debug("Prepared CREDIT LedgerEntry for cash account, txId={}, amount={}, balanceAfter={}", 
                        tx.getId(), amount, creditBalanceAfter);
            }

            LedgerEntry savedCredit = ledgerEntryRepository.save(credit);
            log.info("Saved CREDIT LedgerEntry: id={}, walletId={}, accountType={}, amount={}, balanceAfter={}", 
                    savedCredit.getId(), savedCredit.getWalletId(), savedCredit.getAccountType(), 
                    savedCredit.getAmount(), savedCredit.getBalanceAfter());
        } catch (Exception e) {
            log.error("Failed to create double-entry for transaction txId={}, fromWalletId={}, toWalletId={}, amount={}: {}",
                    tx.getId(), fromWalletId, toWalletId, amount, e.getMessage(), e);
            throw new IllegalStateException("Failed to create ledger entries: " + e.getMessage(), e);
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(new BigDecimal("0.01")) < 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
    }

    private void validateDepositRequest(DepositRequest request) {
        if (request.getToWalletId() == null) {
            throw new IllegalArgumentException("toWalletId is required for deposit");
        }
        // Database constraint: DEPOSIT requires to_wallet_id IS NOT NULL AND from_wallet_id IS NULL
    }

    private void validateWithdrawalRequest(WithdrawalRequest request) {
        if (request.getFromWalletId() == null) {
            throw new IllegalArgumentException("fromWalletId is required for withdrawal");
        }
        // Database constraint: WITHDRAWAL requires from_wallet_id IS NOT NULL AND to_wallet_id IS NULL
    }

    private void validateTransferRequest(TransferRequest request) {
        if (request.getFromWalletId() == null) {
            throw new IllegalArgumentException("fromWalletId is required for transfer");
        }
        if (request.getToWalletId() == null) {
            throw new IllegalArgumentException("toWalletId is required for transfer");
        }
        if (request.getFromWalletId().equals(request.getToWalletId())) {
            throw new IllegalArgumentException("fromWalletId and toWalletId must differ");
        }
        // Database constraint: TRANSFER requires from_wallet_id IS NOT NULL AND to_wallet_id IS NOT NULL
        // Note: Currency validation between wallets would require wallet service integration
    }

    private String normalizeCurrency(String currency) {
        if (!StringUtils.hasText(currency)) {
            // Default to KES for MVP
            return "KES";
        }
        String normalized = currency.toUpperCase(Locale.ROOT);
        
        // For MVP: Only KES is supported
        if (!"KES".equals(normalized)) {
            throw new IllegalArgumentException("Only KES currency is supported in MVP. Provided: " + currency);
        }
        
        return normalized;
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

    private void publishInitiated(Transaction tx) {
        try {
            TransactionEvent event = TransactionEvent.builder()
                    .transactionId(tx.getId())
                    .eventType("TRANSACTION_INITIATED")
                    .transactionType(tx.getTransactionType().name())
                    .status(tx.getStatus().name())
                    .amount(tx.getAmount())
                    .currency(tx.getCurrency())
                    .fromWalletId(tx.getFromWalletId())
                    .toWalletId(tx.getToWalletId())
                    .build();
            transactionEventProducer.publish(event);
        } catch (Exception e) {
            log.warn("Failed to publish TRANSACTION_INITIATED event for txId={}: {}", tx.getId(), e.getMessage());
            // Don't fail the transaction if event publishing fails
        }
    }

    private void publishCompleted(Transaction tx) {
        try {
            TransactionEvent event = TransactionEvent.builder()
                    .transactionId(tx.getId())
                    .eventType("TRANSACTION_COMPLETED")
                    .transactionType(tx.getTransactionType().name())
                    .status(tx.getStatus().name())
                    .amount(tx.getAmount())
                    .currency(tx.getCurrency())
                    .fromWalletId(tx.getFromWalletId())
                    .toWalletId(tx.getToWalletId())
                    .completedAt(tx.getCompletedAt())
                    .failureReason(tx.getFailureReason())
                    .build();
            transactionEventProducer.publish(event);
        } catch (Exception e) {
            log.warn("Failed to publish TRANSACTION_COMPLETED event for txId={}: {}", tx.getId(), e.getMessage());
            // Don't fail the transaction if event publishing fails
        }
    }

    private void publishFailed(Transaction tx, String errorMessage) {
        try {
            TransactionEvent event = TransactionEvent.builder()
                    .transactionId(tx.getId())
                    .eventType("TRANSACTION_FAILED")
                    .transactionType(tx.getTransactionType().name())
                    .status(tx.getStatus().name())
                    .amount(tx.getAmount())
                    .currency(tx.getCurrency())
                    .fromWalletId(tx.getFromWalletId())
                    .toWalletId(tx.getToWalletId())
                    .failureReason(errorMessage)
                    .build();
            transactionEventProducer.publish(event);
        } catch (Exception e) {
            log.warn("Failed to publish TRANSACTION_FAILED event for txId={}: {}", tx.getId(), e.getMessage());
            // Don't fail if event publishing fails
        }
    }
}
