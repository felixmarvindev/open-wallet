package com.openwallet.ledger.service;

import com.openwallet.ledger.exception.WalletNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for querying wallet status from wallets table using native SQL.
 * Uses EntityManager with native queries to avoid creating a JPA entity
 * that could cause schema conflicts in integration tests.
 * 
 * This is a cross-service read access for transaction validation.
 * The wallet service owns the wallets table and is the source of truth.
 * 
 * Note: This service should NEVER write to wallets table - it's read-only.
 */
@Service
@Slf4j
@SuppressWarnings("null")
public class WalletStatusService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Gets wallet status by wallet ID using native SQL query.
     * This avoids creating a JPA entity that could interfere with schema creation.
     * 
     * @param walletId Wallet ID
     * @return Wallet status (ACTIVE, SUSPENDED, CLOSED) or empty if wallet not found
     */
    @Transactional(readOnly = true)
    public Optional<String> getWalletStatus(Long walletId) {
        if (walletId == null) {
            throw new IllegalArgumentException("Wallet ID is required");
        }

        try {
            Query query = entityManager.createNativeQuery(
                "SELECT status " +
                "FROM wallets " +
                "WHERE id = :walletId"
            );
            query.setParameter("walletId", walletId);

            Object result = query.getSingleResult();
            
            if (result == null) {
                log.warn("Wallet not found for walletId={}", walletId);
                return Optional.empty();
            }

            String status = (String) result;

            log.debug("Retrieved wallet status for walletId={}: {}", walletId, status);
            
            return Optional.of(status);

        } catch (jakarta.persistence.NoResultException e) {
            log.debug("Wallet not found: walletId={}", walletId);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error retrieving wallet status for walletId={}: {}", walletId, e.getMessage(), e);
            throw new IllegalStateException("Failed to retrieve wallet status: " + e.getMessage(), e);
        }
    }

    /**
     * Validates that a wallet is active and can process transactions.
     * 
     * @param walletId Wallet ID to validate
     * @throws IllegalStateException if wallet is not found or not active
     */
    @Transactional(readOnly = true)
    public void validateWalletActive(Long walletId) {
        if (walletId == null) {
            // Cash account transactions (deposits/withdrawals) don't have wallet limits
            return;
        }

        Optional<String> statusOpt = getWalletStatus(walletId);
        
        if (statusOpt.isEmpty()) {
            throw new WalletNotFoundException("Wallet not found: " + walletId);
        }

        String status = statusOpt.get();
        
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException(
                String.format("Wallet %d is %s and cannot process transactions. Only ACTIVE wallets can process transactions.", 
                    walletId, status));
        }
    }
}

