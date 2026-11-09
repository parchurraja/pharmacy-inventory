package com.pharmacy.inventory.repository;

import com.pharmacy.inventory.entity.Sale;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

    Optional<Sale> findByInvoiceNumber(String invoiceNumber);

    boolean existsByInvoiceNumber(String invoiceNumber);

    Page<Sale> findBySaleDateBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sale s WHERE s.id = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT DISTINCT s FROM Sale s LEFT JOIN FETCH s.items i LEFT JOIN FETCH i.medicine LEFT JOIN FETCH i.batch WHERE (:start IS NULL OR s.saleDate >= :start) AND (:end IS NULL OR s.saleDate <= :end) ORDER BY s.saleDate ASC")
    java.util.List<Sale> findSalesForReporting(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
