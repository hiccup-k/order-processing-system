package com.example.order.service;

import com.example.events.OrderStatusAvro;
import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import com.example.order.domain.OrderStatus;
import com.example.order.dto.CreateOrderRequest;
import com.example.order.exception.InvalidOrderStateException;
import com.example.order.exception.ResourceNotFoundException;
import com.example.order.kafka.OrderEventProducer;
import com.example.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderEventProducer eventProducer;

    public OrderService(OrderRepository orderRepository, OrderEventProducer eventProducer) {
        this.orderRepository = orderRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        BigDecimal total = request.getItems().stream()
            .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order(request.getCustomerId(), total);
        request.getItems().forEach(i -> order.addItem(new OrderItem(i.getSku(), i.getQuantity(), i.getPrice())));
        order.markPendingInventory();

        Order saved = orderRepository.save(order);
        // Publish after the transaction has flushed the id; for a stronger guarantee than
        // "publish after commit", swap this for an outbox pattern (write event row in the
        // same transaction, relay it via a separate poller/Debezium).
        eventProducer.publishOrderCreated(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long id) {
        return orderRepository.findByIdWithItems(id)
            .orElseThrow(() -> new ResourceNotFoundException("Order " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public Page<Order> listOrders(String customerId, Pageable pageable) {
        Page<Order> page;

        if (customerId != null && !customerId.isBlank()) {
         page = orderRepository.findByCustomerId(customerId, pageable);
        } else {
         page = orderRepository.findAll(pageable);
        }

        // Initialize the lazy items collection while the Hibernate session is active
        page.forEach(order -> order.getItems().size());

        return page;
    }

    @Transactional
    public Order cancelOrder(Long id) {
        Order order = getOrder(id);
        if (!order.isCancellable()) {
            throw new InvalidOrderStateException(
                "Order " + id + " cannot be cancelled from status " + order.getStatus());
        }
        order.cancel();
        Order saved = orderRepository.save(order);
        eventProducer.publishOrderStatusChanged(saved, OrderStatusAvro.CANCELLED);
        return saved;
    }

    /**
     * Idempotent: if the order is already CONFIRMED/FAILED/CANCELLED (e.g. this is a
     * redelivered Kafka message after a consumer restart), we no-op instead of re-publishing.
     */
    @Transactional
    public void confirmOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("inventory-reserved received for unknown order {}", orderId);
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING_INVENTORY) {
            log.info("Ignoring duplicate/late confirm for order {} already in status {}",
                orderId, order.getStatus());
            return;
        }
        order.confirm();
        Order saved = orderRepository.save(order);
        eventProducer.publishOrderStatusChanged(saved, OrderStatusAvro.CONFIRMED);
    }

    @Transactional
    public void failOrder(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("inventory-failed received for unknown order {}", orderId);
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING_INVENTORY) {
            log.info("Ignoring duplicate/late failure for order {} already in status {}",
                orderId, order.getStatus());
            return;
        }
        order.fail(reason);
        Order saved = orderRepository.save(order);
        eventProducer.publishOrderStatusChanged(saved, OrderStatusAvro.FAILED);
    }
}
