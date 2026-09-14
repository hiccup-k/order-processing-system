package com.example.inventory.dto;

import com.example.inventory.domain.InventoryItem;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class InventoryDtos {

    public record StockResponse(String sku, Integer available, Integer reserved) {
        public static StockResponse from(InventoryItem item) {
            return new StockResponse(item.getSku(), item.getAvailableQuantity(), item.getReservedQuantity());
        }
    }

    public static class ReserveRequest {
        @NotBlank
        private String sku;
        @NotNull @Min(1)
        private Integer quantity;

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
