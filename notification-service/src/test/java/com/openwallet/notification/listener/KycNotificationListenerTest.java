package com.openwallet.notification.listener;

import com.openwallet.notification.events.KycEvent;
import com.openwallet.notification.service.NotificationSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KycNotificationListenerTest {

    @Mock
    private NotificationSender notificationSender;

    @InjectMocks
    private KycNotificationListener listener;

    @Test
    void sendsNotificationOnVerified() {
        KycEvent event = KycEvent.builder()
                .kycCheckId(1L)
                .customerId(2L)
                .userId("user-1")
                .eventType("KYC_VERIFIED")
                .build();

        listener.handle(event);

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationSender, times(1)).send(typeCaptor.capture(), org.mockito.Mockito.anyString(),
                org.mockito.Mockito.any(), org.mockito.Mockito.anyString());
        assertThat(typeCaptor.getValue()).isEqualTo("KYC_VERIFIED");

        // duplicate should be ignored
        listener.handle(event);
        verify(notificationSender, times(1)).send(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyString(),
                org.mockito.Mockito.any(), org.mockito.Mockito.anyString());
    }

    @Test
    void sendsNotificationOnRejected() {
        KycEvent event = KycEvent.builder()
                .kycCheckId(3L)
                .customerId(4L)
                .userId("user-2")
                .eventType("KYC_REJECTED")
                .rejectionReason("Document blur")
                .build();

        listener.handle(event);

        ArgumentCaptor<String> typeCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationSender, times(1)).send(typeCaptor.capture(), org.mockito.Mockito.anyString(),
                org.mockito.Mockito.any(), org.mockito.Mockito.anyString());
        assertThat(typeCaptor.getValue()).isEqualTo("KYC_REJECTED");
    }
}


