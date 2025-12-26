package com.openwallet.wallet.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class BalanceCacheService {

    private static final Logger log = LoggerFactory.getLogger(BalanceCacheService.class);
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Gets balance from cache. Returns empty if cache miss or Redis unavailable.
     * Fails gracefully - caching is optional and should not break the application.
     */
    public Optional<BalanceSnapshot> getBalance(Long walletId) {
        try {
            String key = key(walletId);
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(objectMapper.readValue(raw, BalanceSnapshot.class));
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize balance cache for wallet {}", walletId, e);
                return Optional.empty();
            }
        } catch (RedisConnectionFailureException e) {
            // Redis unavailable - fail gracefully, return empty to fall back to database
            log.debug("Redis unavailable, falling back to database for wallet {}", walletId);
            return Optional.empty();
        } catch (Exception e) {
            // Any other Redis error - fail gracefully
            log.warn("Failed to get balance from cache for wallet {}: {}", walletId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Puts balance in cache. Fails gracefully if Redis unavailable.
     * Caching is optional and should not break the application.
     */
    public void putBalance(Long walletId, BalanceSnapshot snapshot) {
        try {
            redisTemplate.opsForValue().set(key(walletId), objectMapper.writeValueAsString(snapshot), TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize balance cache for wallet {}", walletId, e);
        } catch (RedisConnectionFailureException e) {
            // Redis unavailable - fail gracefully, log at debug level
            log.debug("Redis unavailable, skipping cache update for wallet {}", walletId);
        } catch (Exception e) {
            // Any other Redis error - fail gracefully
            log.warn("Failed to cache balance for wallet {}: {}", walletId, e.getMessage());
        }
    }

    /**
     * Invalidates balance cache. Fails gracefully if Redis unavailable.
     */
    public void invalidate(Long walletId) {
        try {
            redisTemplate.delete(key(walletId));
            log.debug("Invalidated balance cache for wallet {}", walletId);
        } catch (RedisConnectionFailureException e) {
            // Redis unavailable - fail gracefully
            log.debug("Redis unavailable, skipping cache invalidation for wallet {}", walletId);
        } catch (Exception e) {
            // Any other Redis error - fail gracefully
            log.warn("Failed to invalidate cache for wallet {}: {}", walletId, e.getMessage());
        }
    }

    private String key(Long walletId) {
        return "wallet:balance:" + walletId;
    }

    public static class BalanceSnapshot {
        private String balance;
        private String currency;
        private String updatedAt;

        public BalanceSnapshot() {
        }

        public BalanceSnapshot(String balance, String currency, String updatedAt) {
            this.balance = balance;
            this.currency = currency;
            this.updatedAt = updatedAt;
        }

        public String getBalance() {
            return balance;
        }

        public String getCurrency() {
            return currency;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }
    }
}


