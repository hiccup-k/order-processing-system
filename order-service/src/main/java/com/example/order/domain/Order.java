package com.example.order.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "orders",
    indexes = {
        @Index(name = "idx_orders_customer_id", columnList = "customer_id"),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_created_at", columnList = "created_at")
    }
)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, length = 64)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic locking guards against concurrent writes from the REST path
     * (e.g. cancel) racing with the Kafka consumer path (e.g. inventory-reserved)
     * updating the same order at the same time.
     */
    @Version
    @Column(name = "version")
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
        // JPA
    }

    public Order(String customerId, BigDecimal totalAmount) {
        this.customerId = customerId;
        this.totalAmount = totalAmount;
        this.status = OrderStatus.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    public void markPendingInventory() {
        this.status = OrderStatus.PENDING_INVENTORY;
        this.updatedAt = Instant.now();
    }

    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    public void fail(String reason) {
        this.status = OrderStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public boolean isCancellable() {
        return status == OrderStatus.CREATED || status == OrderStatus.PENDING_INVENTORY;
    }

    // Getters / setters

    public Long getId() { return id; }
    public String getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderItem> getItems() { return items; }
    public Long getVersion() { return version; }
}
