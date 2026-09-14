package com.example.notification.kafka;

import com.example.events.InventoryFailedEvent;
import com.example.events.OrderConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer group "notification-service" is independent of "order-service" and
 * "inventory-service" — each group maintains its own offsets, so this service gets its
 * own full copy of every message on these topics regardless of what the other consumer
 * groups have already read. That's the mechanism that lets order-created fan out to both
 * inventory-service (to act on it) and, transitively via order-confirmed, to
 * notification-service (to tell the customer) without them stepping on each other.
 *
 * Sending the actual email/SMS/push is stubbed as a log line — swap sendNotification for
 * a real provider client (SES, Twilio, FCM) without touching the listener wiring.
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    @KafkaListener(topics = "order-confirmed", groupId = "notification-service")
    public void onOrderStatusChanged(OrderConfirmedEvent event) {
        switch (event.getStatus()) {
            case CONFIRMED -> sendNotification(event.getCustomerId(),
                "Your order #" + event.getOrderId() + " is confirmed!");
            case CANCELLED -> sendNotification(event.getCustomerId(),
                "Your order #" + event.getOrderId() + " was cancelled.");
            case FAILED -> sendNotification(event.getCustomerId(),
                "Sorry, we couldn't fulfil order #" + event.getOrderId() + ".");
        }
    }

    @KafkaListener(topics = "inventory-failed", groupId = "notification-service")
    public void onInventoryFailed(InventoryFailedEvent event) {
        log.info("Inventory reservation failed for order {}: {} (order-service will notify the customer " +
            "via order-confirmed once it marks the order FAILED)", event.getOrderId(), event.getReason());
    }

    private void sendNotification(String customerId, String message) {
        // Stub: a real implementation would call an email/SMS/push provider here.
        log.info("[NOTIFY -> {}] {}", customerId, message);
    }
}
