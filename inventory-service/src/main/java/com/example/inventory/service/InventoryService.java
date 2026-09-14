package com.example.inventory.service;

import com.example.events.OrderItemAvro;
import com.example.inventory.domain.InventoryItem;
import com.example.inventory.domain.ProcessedOrderEvent;
import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.exception.ResourceNotFoundException;
import com.example.inventory.repository.InventoryRepository;
import com.example.inventory.repository.ProcessedOrderEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final ProcessedOrderEventRepository processedOrderEventRepository;

    public InventoryService(InventoryRepository inventoryRepository,
                             ProcessedOrderEventRepository processedOrderEventRepository) {
        this.inventoryRepository = inventoryRepository;
        this.processedOrderEventRepository = processedOrderEventRepository;
    }

    @Transactional(readOnly = true)
    public InventoryItem getStock(String sku) {
        return inventoryRepository.findBySku(sku)
            .orElseThrow(() -> new ResourceNotFoundException("SKU " + sku + " not found"));
    }

    public record ReservationResult(boolean success, String reason) {
        public static ReservationResult ok() { return new ReservationResult(true, null); }
        public static ReservationResult failed(String reason) { return new ReservationResult(false, reason); }
    }

    /**
     * Attempts to reserve every line item for an order in a single transaction: either
     * all items are reserved or none are. Failures are signalled by throwing
     * InsufficientStockException, which triggers Spring's transaction rollback — so any
     * SKU already decremented earlier in the loop is undone along with everything else,
     * instead of us needing to hand-unwind partial reservations.
     *
     * Idempotency: if this orderId was already processed (row exists in
     * processed_order_events), we short-circuit and report success without touching
     * stock again — this is what makes redelivered order-created messages safe.
     */
    @Transactional
    public ReservationResult reserveForOrder(Long orderId, List<OrderItemAvro> items) {
        if (processedOrderEventRepository.existsById(orderId)) {
            log.info("order {} already processed by inventory-service, skipping duplicate", orderId);
            return ReservationResult.ok();
        }

        for (OrderItemAvro item : items) {
            InventoryItem stock = inventoryRepository.findBySkuForUpdate(item.getSku().toString())
                .orElseThrow(() -> new InsufficientStockException("Unknown SKU: " + item.getSku()));
            if (!stock.reserve(item.getQuantity())) {
                throw new InsufficientStockException(
                    "Insufficient stock for SKU " + item.getSku() + " (requested " + item.getQuantity()
                        + ", available " + stock.getAvailableQuantity() + ")");
            }
            inventoryRepository.save(stock);
        }

        processedOrderEventRepository.save(new ProcessedOrderEvent(orderId));
        return ReservationResult.ok();
    }

    @Transactional
    public InventoryItem release(String sku, int quantity) {
        InventoryItem stock = inventoryRepository.findBySkuForUpdate(sku)
            .orElseThrow(() -> new ResourceNotFoundException("SKU " + sku + " not found"));
        stock.release(quantity);
        return inventoryRepository.save(stock);
    }
}
