package com.example.inventory.domain;

import jakarta.persistence.*;

@Entity
@Table(
    name = "inventory_items",
    indexes = { @Index(name = "idx_inventory_items_sku", columnList = "sku", unique = true) }
)
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity;

    /** Optimistic locking: concurrent reservations for the same SKU must not oversell it. */
    @Version
    private Long version;

    protected InventoryItem() {
        // JPA
    }

    public InventoryItem(String sku, Integer availableQuantity) {
        this.sku = sku;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = 0;
    }

    public boolean reserve(int quantity) {
        if (availableQuantity < quantity) {
            return false;
        }
        availableQuantity -= quantity;
        reservedQuantity += quantity;
        return true;
    }

    public void release(int quantity) {
        int toRelease = Math.min(quantity, reservedQuantity);
        reservedQuantity -= toRelease;
        availableQuantity += toRelease;
    }

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public Integer getAvailableQuantity() { return availableQuantity; }
    public Integer getReservedQuantity() { return reservedQuantity; }
    public Long getVersion() { return version; }
}
