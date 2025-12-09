package com.openwallet.wallet.events;

import com.openwallet.wallet.cache.BalanceCacheService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class TransactionEventListenerTest {

    @Mock
    private BalanceCacheService balanceCacheService;

    @InjectMocks
    private TransactionEventListener listener;

    @Test
    void invalidateBothWalletsOnCompletedTransfer() {
        TransactionEvent event = TransactionEvent.builder()
                .eventType("TRANSACTION_COMPLETED")
                .fromWalletId(1L)
                .toWalletId(2L)
                .build();

        listener.handle(event);

        verify(balanceCacheService).invalidate(1L);
        verify(balanceCacheService).invalidate(2L);
    }

    @Test
    void noInvalidateOnInitiated() {
        TransactionEvent event = TransactionEvent.builder()
                .eventType("TRANSACTION_INITIATED")
                .fromWalletId(1L)
                .toWalletId(2L)
                .build();

        listener.handle(event);
    }
}


