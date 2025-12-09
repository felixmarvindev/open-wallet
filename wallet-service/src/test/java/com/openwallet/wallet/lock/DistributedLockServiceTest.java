package com.openwallet.wallet.lock;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class DistributedLockServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private DistributedLockService lockService;

    @Test
    void tryLockReturnsTokenWhenAcquired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(String.class), any(String.class), any(Duration.class))).thenReturn(true);

        String token = lockService.tryLock(1L);

        assertThat(token).isNotNull();
    }

    @Test
    void tryLockReturnsNullWhenNotAcquired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(String.class), any(String.class), any(Duration.class))).thenReturn(false);

        String token = lockService.tryLock(1L);

        assertThat(token).isNull();
    }

    @Test
    void releaseNoOpWhenTokenNull() {
        boolean released = lockService.release(1L, null);

        assertThat(released).isFalse();
        verifyNoInteractions(redisTemplate);
    }
}


