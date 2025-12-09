package com.openwallet.wallet.lock;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * Simple Redis-based distributed lock using SETNX with TTL and safe release via
 * LUA compare-and-delete.
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);
    private static final String LOCK_KEY_PREFIX = "wallet:lock:";
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    /**
     * Try to acquire a lock for the given wallet. Returns a lock token if acquired,
     * or null otherwise.
     */
    public String tryLock(Long walletId) {
        return tryLock(walletId, DEFAULT_TTL);
    }

    public String tryLock(Long walletId, Duration ttl) {
        String key = key(walletId);
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        if (Boolean.TRUE.equals(acquired)) {
            return token;
        }
        return null;
    }

    /**
     * Release lock only if the token matches the holder.
     */
    public boolean release(Long walletId, String token) {
        if (token == null) {
            return false;
        }
        String key = key(walletId);
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "return redis.call('del', KEYS[1]) " +
                "else " +
                "return 0 " +
                "end";
        Long result = redisTemplate.execute(new DefaultRedisScript<>(script, Long.class),
                Collections.singletonList(key),
                token);
        boolean released = result > 0;
        if (released) {
            log.debug("Released lock for wallet {} with token {}", walletId, token);
        }
        return released;
    }

    private String key(Long walletId) {
        return LOCK_KEY_PREFIX + walletId;
    }
}
