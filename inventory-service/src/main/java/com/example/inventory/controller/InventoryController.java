package com.example.inventory.controller;

import com.example.inventory.domain.InventoryItem;
import com.example.inventory.dto.InventoryDtos.ReserveRequest;
import com.example.inventory.dto.InventoryDtos.StockResponse;
import com.example.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{sku}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<StockResponse> getStock(@PathVariable("sku") String sku) {
        InventoryItem item = inventoryService.getStock(sku);
        return ResponseEntity.ok(StockResponse.from(item));
    }

    /**
     * Manual reserve/release endpoints exist for ops tooling and testing; the primary
     * reservation path for orders is the order-created Kafka listener, not this endpoint.
     */
    @PostMapping("/release")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockResponse> release(@Valid @RequestBody ReserveRequest request) {
        InventoryItem item = inventoryService.release(request.getSku(), request.getQuantity());
        return ResponseEntity.ok(StockResponse.from(item));
    }
}
