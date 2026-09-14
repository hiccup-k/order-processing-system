package com.example.inventory.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Records every order-created event we've already acted on. Kafka's at-least-once
 * delivery means the same message can be redelivered after a consumer restart or
 * rebalance; checking (and inserting) this table inside the same transaction as the
 * reservation makes the whole "check stock, reserve, mark processed" unit idempotent.
 */
@Entity
@Table(name = "processed_order_events")
public class ProcessedOrderEvent {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedOrderEvent() {
        // JPA
    }

    public ProcessedOrderEvent(Long orderId) {
        this.orderId = orderId;
        this.processedAt = Instant.now();
    }

    public Long getOrderId() { return orderId; }
    public Instant getProcessedAt() { return processedAt; }
}
