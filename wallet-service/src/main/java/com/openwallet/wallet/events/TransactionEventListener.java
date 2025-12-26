package com.openwallet.wallet.events;

import com.openwallet.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listens to transaction-events topic and updates wallet balances when transactions complete.
 * 
 * This listener processes TRANSACTION_COMPLETED events and updates the affected wallet balances
 * in the database, then refreshes the cache. This ensures balance consistency across the system.
 */
@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TransactionEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    private final WalletService walletService;

    @KafkaListener(
            topics = "${app.topics.transaction-events:transaction-events}",
            groupId = "${spring.kafka.group-id:wallet-service}",
            containerFactory = "transactionEventKafkaListenerContainerFactory"
    )
    @Transactional
    public void handle(TransactionEvent event) {
        if (event == null) {
            log.warn("Received null transaction event, ignoring");
            return;
        }

        log.info("Received transaction event: type={}, txId={}, transactionType={}", 
                event.getEventType(), event.getTransactionId(), event.getTransactionType());

        if ("TRANSACTION_COMPLETED".equals(event.getEventType())) {
            handleTransactionCompleted(event);
        } else {
            log.debug("Ignoring event type: {}", event.getEventType());
        }
    }

    /**
     * Handles TRANSACTION_COMPLETED events by updating wallet balances.
     * 
     * For each transaction type:
     * - DEPOSIT: Updates toWalletId (credit)
     * - WITHDRAWAL: Updates fromWalletId (debit)
     * - TRANSFER: Updates both fromWalletId (debit) and toWalletId (credit)
     */
    private void handleTransactionCompleted(TransactionEvent event) {
        String transactionType = event.getTransactionType();
        if (transactionType == null) {
            log.warn("Transaction event missing transactionType, txId={}", event.getTransactionId());
            return;
        }

        try {
            switch (transactionType.toUpperCase()) {
                case "DEPOSIT":
                    handleDeposit(event);
                    break;
                case "WITHDRAWAL":
                    handleWithdrawal(event);
                    break;
                case "TRANSFER":
                    handleTransfer(event);
                    break;
                default:
                    log.warn("Unknown transaction type: {}, txId={}", transactionType, event.getTransactionId());
            }
        } catch (com.openwallet.wallet.exception.InsufficientBalanceException e) {
            // Insufficient balance - this is a business logic error, not a transient failure
            // Log as error but don't retry - the transaction should not have been completed
            log.error("Insufficient balance for transaction txId={}, type={}: {}", 
                    event.getTransactionId(), transactionType, e.getMessage());
            // Re-throw to allow Kafka to handle (may need to send to DLQ or alert)
            throw new RuntimeException("Insufficient balance for transaction", e);
        } catch (com.openwallet.wallet.exception.WalletNotFoundException e) {
            // Wallet not found - this is a data integrity issue
            log.error("Wallet not found for transaction txId={}, type={}: {}", 
                    event.getTransactionId(), transactionType, e.getMessage());
            // Re-throw to allow Kafka to handle
            throw new RuntimeException("Wallet not found for transaction", e);
        } catch (IllegalArgumentException e) {
            // Invalid input - this is a data integrity issue
            log.error("Invalid transaction data for txId={}, type={}: {}", 
                    event.getTransactionId(), transactionType, e.getMessage());
            // Re-throw to allow Kafka to handle
            throw new RuntimeException("Invalid transaction data", e);
        } catch (Exception e) {
            // Transient or unexpected error - retry may help
            log.error("Failed to update balance for transaction txId={}, type={}: {}", 
                    event.getTransactionId(), transactionType, e.getMessage(), e);
            // Re-throw to allow Kafka to retry the event
            throw new RuntimeException("Failed to process transaction event", e);
        }
    }

    /**
     * Handles DEPOSIT transaction: increases balance of toWalletId.
     */
    private void handleDeposit(TransactionEvent event) {
        if (event.getToWalletId() == null) {
            log.warn("DEPOSIT event missing toWalletId, txId={}", event.getTransactionId());
            return;
        }

        log.info("Processing DEPOSIT: txId={}, toWalletId={}, amount={}", 
                event.getTransactionId(), event.getToWalletId(), event.getAmount());

        walletService.updateBalanceFromTransaction(
                event.getToWalletId(),
                event.getAmount(),
                "DEPOSIT",
                true // Credit: increase balance
        );
    }

    /**
     * Handles WITHDRAWAL transaction: decreases balance of fromWalletId.
     */
    private void handleWithdrawal(TransactionEvent event) {
        if (event.getFromWalletId() == null) {
            log.warn("WITHDRAWAL event missing fromWalletId, txId={}", event.getTransactionId());
            return;
        }

        log.info("Processing WITHDRAWAL: txId={}, fromWalletId={}, amount={}", 
                event.getTransactionId(), event.getFromWalletId(), event.getAmount());

        walletService.updateBalanceFromTransaction(
                event.getFromWalletId(),
                event.getAmount(),
                "WITHDRAWAL",
                false // Debit: decrease balance
        );
    }

    /**
     * Handles TRANSFER transaction: decreases balance of fromWalletId and increases balance of toWalletId.
     */
    private void handleTransfer(TransactionEvent event) {
        if (event.getFromWalletId() == null || event.getToWalletId() == null) {
            log.warn("TRANSFER event missing fromWalletId or toWalletId, txId={}", event.getTransactionId());
            return;
        }

        if (event.getFromWalletId().equals(event.getToWalletId())) {
            log.warn("TRANSFER event has same fromWalletId and toWalletId, txId={}", event.getTransactionId());
            return;
        }

        log.info("Processing TRANSFER: txId={}, fromWalletId={}, toWalletId={}, amount={}", 
                event.getTransactionId(), event.getFromWalletId(), event.getToWalletId(), event.getAmount());

        // Update source wallet (debit)
        walletService.updateBalanceFromTransaction(
                event.getFromWalletId(),
                event.getAmount(),
                "TRANSFER",
                false // Debit: decrease balance
        );

        // Update destination wallet (credit)
        walletService.updateBalanceFromTransaction(
                event.getToWalletId(),
                event.getAmount(),
                "TRANSFER",
                true // Credit: increase balance
        );
    }
}


