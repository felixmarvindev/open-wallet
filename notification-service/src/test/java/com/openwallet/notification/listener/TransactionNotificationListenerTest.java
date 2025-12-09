package com.openwallet.notification.listener;

import com.openwallet.notification.events.TransactionEvent;
import com.openwallet.notification.service.NotificationSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionNotificationListenerTest {

    @Mock
    private NotificationSender notificationSender;

    @InjectMocks
    private TransactionNotificationListener listener;

    @Test
    void sendsNotificationOnCompleted() {
        TransactionEvent event = TransactionEvent.builder()
                .transactionId(10L)
                .eventType("TRANSACTION_COMPLETED")
                .amount(new BigDecimal("100.00"))
                .currency("KES")
                .toWalletId(20L)
                .build();

        listener.handle(event);

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationSender, times(1)).send(typeCaptor.capture(), org.mockito.Mockito.anyString(),
                org.mockito.Mockito.any(), org.mockito.Mockito.anyString());
        assertThat(typeCaptor.getValue()).isEqualTo("TRANSACTION_COMPLETED");

        // duplicate should be ignored
        listener.handle(event);
        verify(notificationSender, times(1)).send(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(),
                org.mockito.Mockito.any(), org.mockito.Mockito.anyString());
    }

    @Test
    void sendsNotificationOnFailed() {
        TransactionEvent event = TransactionEvent.builder()
                .transactionId(11L)
                .eventType("TRANSACTION_FAILED")
                .failureReason("Insufficient funds")
                .fromWalletId(30L)
                .build();

        listener.handle(event);

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationSender, times(1)).send(typeCaptor.capture(), org.mockito.Mockito.anyString(),
                org.mockito.Mockito.any(), org.mockito.Mockito.anyString());
        assertThat(typeCaptor.getValue()).isEqualTo("TRANSACTION_FAILED");
    }
}


