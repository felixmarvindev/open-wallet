package com.openwallet.ledger.service;

import com.openwallet.ledger.dto.WalletLimits;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Service for querying wallet limits from wallets table using native SQL.
 * Uses EntityManager with native queries to avoid creating a JPA entity
 * that could cause schema conflicts in integration tests.
 * 
 * This is a cross-service read access for transaction limit validation.
 * The wallet service owns the wallets table and is the source of truth.
 * 
 * Note: This service should NEVER write to wallets table - it's read-only.
 */
@Service
@Slf4j
@SuppressWarnings("null")
public class WalletLimitsService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Gets wallet limits by wallet ID using native SQL query.
     * This avoids creating a JPA entity that could interfere with schema creation.
     * 
     * @param walletId Wallet ID
     * @return Wallet limits (daily and monthly) or empty if wallet not found
     */
    @Transactional(readOnly = true)
    public Optional<WalletLimits> getWalletLimits(Long walletId) {
        if (walletId == null) {
            throw new IllegalArgumentException("Wallet ID is required");
        }

        try {
            Query query = entityManager.createNativeQuery(
                "SELECT id, daily_limit, monthly_limit " +
                "FROM wallets " +
                "WHERE id = :walletId"
            );
            query.setParameter("walletId", walletId);

            Object result = query.getSingleResult();
            
            if (result == null) {
                log.warn("Wallet not found for walletId={}", walletId);
                return Optional.empty();
            }

            Object[] row = (Object[]) result;
            if (row.length < 3) {
                log.warn("Incomplete wallet data for walletId={}", walletId);
                return Optional.empty();
            }

            Long id = ((Number) row[0]).longValue();
            BigDecimal dailyLimit = (BigDecimal) row[1];
            BigDecimal monthlyLimit = (BigDecimal) row[2];

            WalletLimits limits = WalletLimits.builder()
                    .walletId(id)
                    .dailyLimit(dailyLimit)
                    .monthlyLimit(monthlyLimit)
                    .build();

            log.debug("Retrieved wallet limits for walletId={}: daily={}, monthly={}", 
                    walletId, dailyLimit, monthlyLimit);
            
            return Optional.of(limits);

        } catch (jakarta.persistence.NoResultException e) {
            log.debug("Wallet not found: walletId={}", walletId);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error retrieving wallet limits for walletId={}: {}", walletId, e.getMessage(), e);
            throw new IllegalStateException("Failed to retrieve wallet limits: " + e.getMessage(), e);
        }
    }
}

