package com.openwallet.wallet.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public Optional<BalanceSnapshot> getBalance(Long walletId) {
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
    }

    public void putBalance(Long walletId, BalanceSnapshot snapshot) {
        try {
            redisTemplate.opsForValue().set(key(walletId), objectMapper.writeValueAsString(snapshot), TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize balance cache for wallet {}", walletId, e);
        }
    }

    public void invalidate(Long walletId) {
        redisTemplate.delete(key(walletId));
        log.info("Invalidated balance cache for wallet {}", walletId);
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


