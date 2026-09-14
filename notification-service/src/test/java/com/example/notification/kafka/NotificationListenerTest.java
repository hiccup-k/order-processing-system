package com.example.notification.kafka;

import com.example.events.InventoryFailedEvent;
import com.example.events.OrderConfirmedEvent;
import com.example.events.OrderStatusAvro;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * The listener's only side effect right now is a log line (send provider is stubbed),
 * so these tests assert it handles every OrderStatusAvro branch and both topics without
 * throwing. Once a real notification provider client is wired in, replace this with a
 * Mockito verify(provider).send(...) per branch.
 */
class NotificationListenerTest {

    private final NotificationListener listener = new NotificationListener();

    private OrderConfirmedEvent statusEvent(OrderStatusAvro status) {
        return OrderConfirmedEvent.newBuilder()
            .setEventId("evt-1")
            .setOrderId(1L)
            .setCustomerId("cust-1")
            .setStatus(status)
            .setConfirmedAt(Instant.now())
            .build();
    }

    @Test
    void handlesConfirmed() {
        assertDoesNotThrow(() -> listener.onOrderStatusChanged(statusEvent(OrderStatusAvro.CONFIRMED)));
    }

    @Test
    void handlesCancelled() {
        assertDoesNotThrow(() -> listener.onOrderStatusChanged(statusEvent(OrderStatusAvro.CANCELLED)));
    }

    @Test
    void handlesFailed() {
        assertDoesNotThrow(() -> listener.onOrderStatusChanged(statusEvent(OrderStatusAvro.FAILED)));
    }

    @Test
    void handlesInventoryFailedEvent() {
        InventoryFailedEvent event = InventoryFailedEvent.newBuilder()
            .setEventId("evt-2")
            .setOrderId(1L)
            .setReason("Insufficient stock")
            .setFailedAt(Instant.now())
            .build();

        assertDoesNotThrow(() -> listener.onInventoryFailed(event));
    }
}
