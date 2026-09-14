package com.example.inventory.repository;

import com.example.inventory.domain.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<InventoryItem, Long> {

    Optional<InventoryItem> findBySku(String sku);

    /** Pessimistic lock so two concurrent reservations for the same SKU serialize cleanly. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.sku = :sku")
    Optional<InventoryItem> findBySkuForUpdate(@Param("sku")String sku);
}
