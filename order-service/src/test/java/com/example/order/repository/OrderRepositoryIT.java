package com.example.order.repository;

import com.example.order.domain.Order;
import com.example.order.domain.OrderItem;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Postgres via Testcontainers so the Flyway migrations, indexes, and pagination
 * queries are exercised against the actual database engine, not just H2 compatibility mode.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @org.springframework.beans.factory.annotation.Autowired
    private OrderRepository orderRepository;

    @Test
    void savesOrderWithItemsAndFindsByCustomerId() {
        Order order = new Order("cust-42", BigDecimal.valueOf(30));
        order.addItem(new OrderItem("SKU-1", 3, BigDecimal.TEN));
        orderRepository.save(order);

        var page = orderRepository.findByCustomerId("cust-42", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getItems()).hasSize(1);
        assertThat(page.getContent().get(0).getItems().get(0).getSku()).isEqualTo("SKU-1");
    }
}
