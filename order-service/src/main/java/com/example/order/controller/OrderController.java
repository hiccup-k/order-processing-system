package com.example.order.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.order.domain.Order;
import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderResponse;
import com.example.order.service.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable("id") Long id) {
        return ResponseEntity.ok(OrderResponse.from(orderService.getOrder(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Page<OrderResponse>> listOrders(
            @RequestParam(value = "customerId",required = false) String customerId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<OrderResponse> page = orderService.listOrders(customerId, pageable).map(OrderResponse::from);
        return ResponseEntity.ok(page);
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable("id") Long id) {
        return ResponseEntity.ok(OrderResponse.from(orderService.cancelOrder(id)));
    }
}
