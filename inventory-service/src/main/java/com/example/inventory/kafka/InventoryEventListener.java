package com.example.inventory.kafka;

import com.example.events.OrderCreatedEvent;
import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer group "inventory-service" — scale this service out and Kafka spreads the
 * order-created partitions across the running instances (one partition owned by exactly
 * one instance at a time within the group). Because events for a given order are always
 * keyed by orderId (see order-service's producer), every event for one order still goes
 * to the same partition and is handled by the same instance, in order.
 *
 * Offsets commit only after this listener returns normally, so a crash mid-processing
 * causes Kafka to redeliver the record on restart — InventoryService.reserveForOrder
 * is written to be safe against that redelivery.
 */
@Component
public class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private final InventoryService inventoryService;
    private final InventoryEventProducer eventProducer;

    public InventoryEventListener(InventoryService inventoryService, InventoryEventProducer eventProducer) {
        this.inventoryService = inventoryService;
        this.eventProducer = eventProducer;
    }

    @KafkaListener(topics = Topics.ORDER_CREATED, groupId = "inventory-service")
    public void onOrderCreated(OrderCreatedEvent event) {
        Long orderId = event.getOrderId();
        log.info("Received order-created for order {} with {} line item(s)", orderId, event.getItems().size());
        try {
            inventoryService.reserveForOrder(orderId, event.getItems());
            eventProducer.publishInventoryReserved(orderId);
        } catch (InsufficientStockException ex) {
            log.warn("Reservation failed for order {}: {}", orderId, ex.getMessage());
            eventProducer.publishInventoryFailed(orderId, ex.getMessage());
        }
    }
}
