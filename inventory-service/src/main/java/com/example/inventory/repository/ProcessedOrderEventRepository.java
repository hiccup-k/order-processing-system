package com.example.inventory.repository;

import com.example.inventory.domain.ProcessedOrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedOrderEventRepository extends JpaRepository<ProcessedOrderEvent, Long> {
}
