package com.example.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public class CreateOrderRequest {

    @NotBlank
    private String customerId;

    @NotEmpty
    @Valid
    private List<Item> items;

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    public static class Item {
        @NotBlank
        private String sku;

        @NotNull
        @Min(1)
        private Integer quantity;

        @NotNull
        @Positive
        private BigDecimal price;

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
    }
}
