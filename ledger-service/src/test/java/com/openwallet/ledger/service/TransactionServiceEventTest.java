package com.openwallet.ledger.service;

import com.openwallet.ledger.dto.DepositRequest;
import com.openwallet.ledger.dto.TransactionResponse;
import com.openwallet.ledger.events.TransactionEventProducer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest
@Import({com.openwallet.ledger.config.JpaConfig.class, TransactionService.class})
@ActiveProfiles("test")
@SuppressWarnings({ "DataFlowIssue", "ConstantConditions", "null" })
class TransactionServiceEventTest {

    @Autowired
    private TransactionService transactionService;

    @MockBean
    private TransactionEventProducer transactionEventProducer;

    @Test
    void publishEventsOnDeposit() {
        DepositRequest request = DepositRequest.builder()
                .toWalletId(100L)
                .amount(new BigDecimal("5.00"))
                .currency("KES")
                .idempotencyKey("dep-event-1")
                .build();

        TransactionResponse response = transactionService.createDeposit(request);

        assertThat(response.getId()).isNotNull();
        verify(transactionEventProducer, times(1)).publish(Mockito.argThat(e -> "TRANSACTION_INITIATED".equals(e.getEventType())));
        verify(transactionEventProducer, times(1)).publish(Mockito.argThat(e -> "TRANSACTION_COMPLETED".equals(e.getEventType())));
    }
}


