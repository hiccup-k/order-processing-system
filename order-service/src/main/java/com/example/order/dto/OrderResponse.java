package com.example.order.dto;

import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
    Long id,
    String customerId,
    String status,
    BigDecimal totalAmount,
    String failureReason,
    Instant createdAt,
    Instant updatedAt,
    List<ItemResponse> items
) {
    public static OrderResponse from(Order order) {
        List<ItemResponse> items = order.getItems().stream()
            .map(ItemResponse::from)
            .toList();
        return new OrderResponse(
            order.getId(),
            order.getCustomerId(),
            order.getStatus().name(),
            order.getTotalAmount(),
            order.getFailureReason(),
            order.getCreatedAt(),
            order.getUpdatedAt(),
            items
        );
    }

    public record ItemResponse(String sku, Integer quantity, BigDecimal price) {
        public static ItemResponse from(OrderItem item) {
            return new ItemResponse(item.getSku(), item.getQuantity(), item.getPrice());
        }
    }
}
