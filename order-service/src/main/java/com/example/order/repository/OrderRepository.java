package com.example.order.repository;

import com.example.order.domain.Order;
import com.example.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Page<Order> findByCustomerId(String customerId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    Optional<Order> findByIdAndCustomerId(Long id, String customerId);

    @Query("""
        SELECT DISTINCT o
        FROM Order o
        LEFT JOIN FETCH o.items
        WHERE o.id = :id
    """)
    Optional<Order> findByIdWithItems(@Param("id") Long id);
}
