package com.example.inventory.service;

import com.example.events.OrderItemAvro;
import com.example.inventory.domain.InventoryItem;
import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.repository.InventoryRepository;
import com.example.inventory.repository.ProcessedOrderEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProcessedOrderEventRepository processedOrderEventRepository;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(inventoryRepository, processedOrderEventRepository);
    }

    private OrderItemAvro item(String sku, int qty) {
        return OrderItemAvro.newBuilder().setSku(sku).setQuantity(qty).build();
    }

    @Test
    void reserveForOrder_succeedsWhenStockAvailable() {
        when(processedOrderEventRepository.existsById(1L)).thenReturn(false);
        InventoryItem stock = new InventoryItem("SKU-1", 10);
        when(inventoryRepository.findBySkuForUpdate("SKU-1")).thenReturn(Optional.of(stock));

        var result = inventoryService.reserveForOrder(1L, List.of(item("SKU-1", 3)));

        assertThat(result.success()).isTrue();
        assertThat(stock.getAvailableQuantity()).isEqualTo(7);
        assertThat(stock.getReservedQuantity()).isEqualTo(3);
        verify(processedOrderEventRepository).save(any());
    }

    @Test
    void reserveForOrder_throwsWhenInsufficientStock() {
        when(processedOrderEventRepository.existsById(2L)).thenReturn(false);
        InventoryItem stock = new InventoryItem("SKU-3", 2);
        when(inventoryRepository.findBySkuForUpdate("SKU-3")).thenReturn(Optional.of(stock));

        assertThatThrownBy(() -> inventoryService.reserveForOrder(2L, List.of(item("SKU-3", 5))))
            .isInstanceOf(InsufficientStockException.class);

        // stock must be untouched since the reservation failed
        assertThat(stock.getAvailableQuantity()).isEqualTo(2);
        verify(processedOrderEventRepository, never()).save(any());
    }

    @Test
    void reserveForOrder_throwsWhenSkuUnknown() {
        when(processedOrderEventRepository.existsById(3L)).thenReturn(false);
        when(inventoryRepository.findBySkuForUpdate("SKU-UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.reserveForOrder(3L, List.of(item("SKU-UNKNOWN", 1))))
            .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void reserveForOrder_isIdempotent_skipsAlreadyProcessedOrder() {
        when(processedOrderEventRepository.existsById(4L)).thenReturn(true);

        var result = inventoryService.reserveForOrder(4L, List.of(item("SKU-1", 3)));

        assertThat(result.success()).isTrue();
        verifyNoInteractions(inventoryRepository);
    }
}
