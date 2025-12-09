package com.openwallet.wallet.events;

import com.openwallet.wallet.cache.BalanceCacheService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TransactionEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionEventListener.class);

    private final BalanceCacheService balanceCacheService;

    @KafkaListener(topics = "${app.topics.transaction-events:transaction-events}", groupId = "${spring.kafka.group-id:wallet-service}", containerFactory = "transactionEventKafkaListenerContainerFactory")
    public void handle(TransactionEvent event) {
        if ("TRANSACTION_COMPLETED".equals(event.getEventType())) {
            if (event.getFromWalletId() != null) {
                balanceCacheService.invalidate(event.getFromWalletId());
            }
            if (event.getToWalletId() != null) {
                balanceCacheService.invalidate(event.getToWalletId());
            }
        }
        log.info("Consumed transaction event type={} txId={}", event.getEventType(), event.getTransactionId());
    }
}


