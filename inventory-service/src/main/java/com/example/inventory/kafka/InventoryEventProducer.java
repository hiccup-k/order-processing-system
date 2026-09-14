package com.example.inventory.kafka;

import com.example.events.InventoryFailedEvent;
import com.example.events.InventoryReservedEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Keyed by orderId, same as order-service's producer, so every event for a given order
 * (order-created, inventory-reserved/-failed, order-confirmed) lands on the same
 * partition number in each topic and is processed in order relative to itself.
 */
@Component
public class InventoryEventProducer {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InventoryEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Retry(name = "kafkaPublisher")
    @CircuitBreaker(name = "kafkaPublisher", fallbackMethod = "publishReservedFallback")
    public void publishInventoryReserved(Long orderId) {
        InventoryReservedEvent event = InventoryReservedEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setOrderId(orderId)
            .setReservationId(UUID.randomUUID().toString())
            .setReservedAt(Instant.now())
            .build();
        send(Topics.INVENTORY_RESERVED, String.valueOf(orderId), event);
    }

    @Retry(name = "kafkaPublisher")
    @CircuitBreaker(name = "kafkaPublisher", fallbackMethod = "publishFailedFallback")
    public void publishInventoryFailed(Long orderId, String reason) {
        InventoryFailedEvent event = InventoryFailedEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setOrderId(orderId)
            .setReason(reason)
            .setFailedAt(Instant.now())
            .build();
        send(Topics.INVENTORY_FAILED, String.valueOf(orderId), event);
    }

    private void send(String topic, String key, Object event) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish to {} key {}: {}", topic, key, ex.getMessage());
            } else {
                log.info("Published to {}-{} offset {}", topic,
                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }

    @SuppressWarnings("unused")
    private void publishReservedFallback(Long orderId, Throwable t) {
        log.error("Circuit open / retries exhausted publishing inventory-reserved for order {}: {}",
            orderId, t.getMessage());
    }

    @SuppressWarnings("unused")
    private void publishFailedFallback(Long orderId, String reason, Throwable t) {
        log.error("Circuit open / retries exhausted publishing inventory-failed for order {}: {}",
            orderId, t.getMessage());
    }
}
