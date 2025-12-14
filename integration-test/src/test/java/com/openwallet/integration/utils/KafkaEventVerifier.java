package com.openwallet.integration.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;

/**
 * Utility for verifying Kafka events in integration tests.
 * Consumes events from Kafka topics and provides methods to verify expected events.
 */
@Slf4j
public class KafkaEventVerifier implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;
    private final String topic;
    private final String bootstrapServers;

    public KafkaEventVerifier(String bootstrapServers, String topic) {
        this.bootstrapServers = bootstrapServers;
        this.topic = topic;
        
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "integration-test-consumer-" + System.currentTimeMillis());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        
        this.consumer = new KafkaConsumer<>(props);
        this.consumer.subscribe(Collections.singletonList(topic));
        
        log.info("KafkaEventVerifier subscribed to topic: {}", topic);
    }

    /**
     * Waits for and verifies an event matching the predicate.
     * 
     * @param predicate Function to test if an event matches
     * @param timeoutSeconds Maximum time to wait in seconds
     * @return The matching event record, or null if timeout
     */
    public ConsumerRecordWrapper<String, String> waitForEvent(Predicate<ConsumerRecordWrapper<String, String>> predicate, int timeoutSeconds) {
        log.info("Waiting for event on topic {} (timeout: {}s)", topic, timeoutSeconds);
        
        LocalDateTime deadline = LocalDateTime.now().plusSeconds(timeoutSeconds);
        
        while (LocalDateTime.now().isBefore(deadline)) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            
            for (ConsumerRecord<String, String> kafkaRecord : records) {
                log.debug("Received event: key={}, value={}", kafkaRecord.key(), kafkaRecord.value());
                
                ConsumerRecordWrapper<String, String> wrapper = new ConsumerRecordWrapper<>(
                        kafkaRecord.key(), kafkaRecord.value());
                
                if (predicate.test(wrapper)) {
                    log.info("Found matching event: key={}, value={}", kafkaRecord.key(), kafkaRecord.value());
                    consumer.commitSync();
                    return wrapper;
                }
            }
        }
        
        log.warn("Timeout waiting for event on topic {}", topic);
        return null;
    }

    /**
     * Verifies that an event with the specified event type exists.
     * 
     * @param eventType The event type to look for (e.g., "USER_REGISTERED")
     * @param timeoutSeconds Maximum time to wait
     * @return true if event found, false otherwise
     */
    public boolean verifyEventType(String eventType, int timeoutSeconds) {
        ConsumerRecordWrapper<String, String> record = waitForEvent(
                r -> r.value() != null && r.value().contains("\"eventType\":\"" + eventType + "\""),
                timeoutSeconds
        );
        return record != null;
    }

    /**
     * Verifies that an event contains the specified field-value pair.
     * Handles both string values (with quotes) and numeric values (without quotes).
     * 
     * @param field The JSON field name
     * @param value The expected value
     * @param timeoutSeconds Maximum time to wait
     * @return The matching record, or null if not found
     */
    public ConsumerRecordWrapper<String, String> verifyEventContains(String field, String value, int timeoutSeconds) {
        return waitForEvent(
                r -> {
                    if (r.value() == null) {
                        return false;
                    }
                    // Check for string value: "field":"value"
                    boolean stringMatch = r.value().contains("\"" + field + "\":\"" + value + "\"");
                    // Check for numeric value: "field":value
                    boolean numericMatch = r.value().contains("\"" + field + "\":" + value);
                    return stringMatch || numericMatch;
                },
                timeoutSeconds
        );
    }

    /**
     * Gets all events received so far (for debugging).
     */
    public Map<String, String> getAllEvents(int timeoutSeconds) {
        Map<String, String> events = new HashMap<>();
        LocalDateTime deadline = LocalDateTime.now().plusSeconds(timeoutSeconds);
        
        while (LocalDateTime.now().isBefore(deadline)) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                events.put(record.key() != null ? record.key() : "null", record.value());
            }
        }
        
        consumer.commitSync();
        return events;
    }
    
    /**
     * Wrapper class for consumer record to avoid naming conflicts with Kafka's ConsumerRecord.
     */
    public static class ConsumerRecordWrapper<K, V> {
        private final K key;
        private final V value;
        
        public ConsumerRecordWrapper(K key, V value) {
            this.key = key;
            this.value = value;
        }
        
        public K key() {
            return key;
        }
        
        public V value() {
            return value;
        }
    }

    @Override
    public void close() {
        if (consumer != null) {
            consumer.close();
            log.info("KafkaEventVerifier closed for topic: {}", topic);
        }
    }
}

