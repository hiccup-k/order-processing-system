package com.example.order.kafka;

import com.example.events.OrderConfirmedEvent;
import com.example.events.OrderCreatedEvent;
import com.example.events.OrderItemAvro;
import com.example.events.OrderStatusAvro;
import com.example.order.domain.Order;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Publishes domain events to Kafka using Avro payloads validated against Schema Registry.
 * Keyed by orderId so all events for a given order land on the same partition and are
 * therefore strictly ordered for that order (a core guarantee we rely on downstream).
 */
@Component
public class OrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Retry(name = "kafkaPublisher")
    @CircuitBreaker(name = "kafkaPublisher", fallbackMethod = "publishOrderCreatedFallback")
    public void publishOrderCreated(Order order) {
        List<OrderItemAvro> items = order.getItems().stream()
            .map(i -> OrderItemAvro.newBuilder()
                .setSku(i.getSku())
                .setQuantity(i.getQuantity())
                .build())
            .toList();

        OrderCreatedEvent event = OrderCreatedEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setOrderId(order.getId())
            .setCustomerId(order.getCustomerId())
            .setItems(items)
            .setTotalAmount(order.getTotalAmount().doubleValue())
            .setCreatedAt(Instant.now())
            .build();

        send(Topics.ORDER_CREATED, String.valueOf(order.getId()), event);
    }

    @Retry(name = "kafkaPublisher")
    @CircuitBreaker(name = "kafkaPublisher", fallbackMethod = "publishOrderStatusChangedFallback")
    public void publishOrderStatusChanged(Order order, OrderStatusAvro status) {
        OrderConfirmedEvent event = OrderConfirmedEvent.newBuilder()
            .setEventId(UUID.randomUUID().toString())
            .setOrderId(order.getId())
            .setCustomerId(order.getCustomerId())
            .setStatus(status)
            .setConfirmedAt(Instant.now())
            .build();

        send(Topics.ORDER_CONFIRMED, String.valueOf(order.getId()), event);
    }

    private void send(String topic, String key, Object event) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event to topic {} key {}: {}", topic, key, ex.getMessage());
            } else {
                log.info("Published event to {}-{} offset {}", topic,
                    result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            }
        });
    }

    // Resilience4j fallback methods must match signature + a trailing Throwable param.

    @SuppressWarnings("unused")
    private void publishOrderCreatedFallback(Order order, Throwable t) {
        log.error("Circuit open / retries exhausted publishing order-created for order {}: {}",
            order.getId(), t.getMessage());
        // In production this would write to an outbox table for later replay.
    }

    @SuppressWarnings("unused")
    private void publishOrderStatusChangedFallback(Order order, OrderStatusAvro status, Throwable t) {
        log.error("Circuit open / retries exhausted publishing order-confirmed for order {}: {}",
            order.getId(), t.getMessage());
    }
}
