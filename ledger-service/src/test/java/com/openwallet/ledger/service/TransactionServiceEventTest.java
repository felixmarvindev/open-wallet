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
@Import({com.openwallet.ledger.config.JpaConfig.class, TransactionService.class, LedgerEntryService.class, 
         WalletLimitsService.class, TransactionLimitService.class})
@ActiveProfiles("test")
@org.springframework.test.context.jdbc.Sql(scripts = "/sql/create_wallets_table.sql", executionPhase = org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@SuppressWarnings({ "DataFlowIssue", "ConstantConditions", "null" })
class TransactionServiceEventTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private TransactionEventProducer transactionEventProducer;

    @Test
    void publishEventsOnDeposit() {
        // Setup: Create test wallet with high limits
        jdbcTemplate.update(
            "INSERT INTO wallets (id, customer_id, currency, balance, daily_limit, monthly_limit, status) " +
            "VALUES (?, 1, 'KES', 0.00, ?, ?, 'ACTIVE')",
            100L, new BigDecimal("100000.00"), new BigDecimal("1000000.00")
        );

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


