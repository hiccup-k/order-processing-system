package com.example.order.kafka;

import com.example.events.InventoryFailedEvent;
import com.example.events.InventoryReservedEvent;
import com.example.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer group "order-service" — both instances of order-service you scale out will
 * share this group, so Kafka distributes inventory-reserved/inventory-failed partitions
 * across them (each partition consumed by exactly one instance at a time). Offsets are
 * committed automatically after successful listener execution; OrderService.confirmOrder/
 * failOrder are idempotent so re-processing a partition after a rebalance is safe.
 */
@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderService orderService;

    public OrderEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = Topics.INVENTORY_RESERVED, groupId = "order-service")
    public void onInventoryReserved(InventoryReservedEvent event) {
        log.info("Received inventory-reserved for order {} (reservation {})",
            event.getOrderId(), event.getReservationId());
        orderService.confirmOrder(event.getOrderId());
    }

    @KafkaListener(topics = Topics.INVENTORY_FAILED, groupId = "order-service")
    public void onInventoryFailed(InventoryFailedEvent event) {
        log.info("Received inventory-failed for order {} reason: {}", event.getOrderId(), event.getReason());
        orderService.failOrder(event.getOrderId(), event.getReason());
    }
}
