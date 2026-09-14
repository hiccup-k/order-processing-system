package com.example.inventory.kafka;

import com.example.events.OrderCreatedEvent;
import com.example.events.OrderItemAvro;
import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.service.InventoryService;
import com.example.inventory.service.InventoryService.ReservationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryEventListenerTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private InventoryEventProducer eventProducer;

    private InventoryEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new InventoryEventListener(inventoryService, eventProducer);
    }

    private OrderCreatedEvent event(long orderId) {
        return OrderCreatedEvent.newBuilder()
            .setEventId("evt-1")
            .setOrderId(orderId)
            .setCustomerId("cust-1")
            .setItems(List.of(OrderItemAvro.newBuilder().setSku("SKU-1").setQuantity(1).build()))
            .setTotalAmount(10.0)
            .setCreatedAt(Instant.now())
            .build();
    }

    @Test
    void onOrderCreated_publishesReservedOnSuccess() {
        when(inventoryService.reserveForOrder(eq(1L), anyList())).thenReturn(ReservationResult.ok());

        listener.onOrderCreated(event(1L));

        verify(eventProducer).publishInventoryReserved(1L);
        verify(eventProducer, never()).publishInventoryFailed(anyLong(), anyString());
    }

    @Test
    void onOrderCreated_publishesFailedWhenStockInsufficient() {
        when(inventoryService.reserveForOrder(eq(2L), anyList()))
            .thenThrow(new InsufficientStockException("Insufficient stock for SKU SKU-1"));

        listener.onOrderCreated(event(2L));

        verify(eventProducer).publishInventoryFailed(eq(2L), contains("Insufficient stock"));
        verify(eventProducer, never()).publishInventoryReserved(anyLong());
    }
}
