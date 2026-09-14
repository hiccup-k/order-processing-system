package com.example.order.service;

import com.example.events.OrderStatusAvro;
import com.example.order.domain.Order;
import com.example.order.domain.OrderStatus;
import com.example.order.dto.CreateOrderRequest;
import com.example.order.exception.InvalidOrderStateException;
import com.example.order.exception.ResourceNotFoundException;
import com.example.order.kafka.OrderEventProducer;
import com.example.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventProducer eventProducer;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, eventProducer);
    }

    private CreateOrderRequest sampleRequest() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerId("cust-1");
        CreateOrderRequest.Item item = new CreateOrderRequest.Item();
        item.setSku("SKU-1");
        item.setQuantity(2);
        item.setPrice(BigDecimal.valueOf(10));
        request.setItems(List.of(item));
        return request;
    }

    @Test
    void createOrder_savesOrderAndPublishesEvent() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.createOrder(sampleRequest());

        assertThat(result.getCustomerId()).isEqualTo("cust-1");
        assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING_INVENTORY);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(eventProducer).publishOrderCreated(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isEqualTo("cust-1");
    }

    @Test
    void getOrder_throwsWhenMissing() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancelOrder_rejectsWhenAlreadyConfirmed() {
        Order order = new Order("cust-1", BigDecimal.TEN);
        order.markPendingInventory();
        order.confirm(); // now CONFIRMED, not cancellable
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
            .isInstanceOf(InvalidOrderStateException.class);

        verify(eventProducer, never()).publishOrderStatusChanged(any(), any());
    }

    @Test
    void confirmOrder_isIdempotent_ignoresDuplicateWhenAlreadyConfirmed() {
        Order order = new Order("cust-1", BigDecimal.TEN);
        order.markPendingInventory();
        order.confirm(); // simulate: already processed once
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.confirmOrder(1L);

        verify(orderRepository, never()).save(any());
        verify(eventProducer, never()).publishOrderStatusChanged(any(), any());
    }

    @Test
    void confirmOrder_confirmsWhenPending() {
        Order order = new Order("cust-1", BigDecimal.TEN);
        order.markPendingInventory();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.confirmOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(eventProducer).publishOrderStatusChanged(order, OrderStatusAvro.CONFIRMED);
    }
}
