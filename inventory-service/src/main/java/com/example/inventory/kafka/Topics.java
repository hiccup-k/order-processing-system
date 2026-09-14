package com.example.inventory.kafka;

public final class Topics {
    public static final String ORDER_CREATED = "order-created";
    public static final String INVENTORY_RESERVED = "inventory-reserved";
    public static final String INVENTORY_FAILED = "inventory-failed";

    private Topics() {}
}
