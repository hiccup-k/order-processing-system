package com.example.order.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(
    name = "order_items",
    indexes = {
        @Index(name = "idx_order_items_order_id", columnList = "order_id"),
        @Index(name = "idx_order_items_sku", columnList = "sku")
    }
)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    protected OrderItem() {
        // JPA
    }

    public OrderItem(String sku, Integer quantity, BigDecimal price) {
        this.sku = sku;
        this.quantity = quantity;
        this.price = price;
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public String getSku() { return sku; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
}
