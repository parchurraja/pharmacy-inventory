package com.pharmacy.inventory.repository;

import com.pharmacy.inventory.entity.MedicineBatch;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MedicineBatchRepository extends JpaRepository<MedicineBatch, Long> {

    Optional<MedicineBatch> findByBatchNumber(String batchNumber);

    boolean existsByBatchNumber(String batchNumber);

    // Get all batches for a medicine ordered by expiry date ASC (FEFO order)
    List<MedicineBatch> findByMedicineIdAndQuantityGreaterThanOrderByExpiryDateAsc(Long medicineId, int quantity);

    // Concurrency-safe: Acquire pessimistic write lock during sale deduction
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM MedicineBatch b WHERE b.medicine.id = :medicineId AND b.quantity > 0 AND b.active = true ORDER BY b.expiryDate ASC")
    List<MedicineBatch> findAvailableBatchesForUpdate(@Param("medicineId") Long medicineId);

    // Concurrency-safe: Acquire pessimistic write lock for single batch adjustment/return
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM MedicineBatch b WHERE b.id = :id")
    Optional<MedicineBatch> findByIdForUpdate(@Param("id") Long id);

    // Find batches expiring within the next X days (excluding already expired)
    @Query("SELECT b FROM MedicineBatch b " +
           "WHERE b.expiryDate BETWEEN :today AND :targetDate " +
           "AND b.quantity > 0 AND b.active = true " +
           "ORDER BY b.expiryDate ASC")
    List<MedicineBatch> findBatchesExpiringSoon(
            @Param("today") LocalDate today,
            @Param("targetDate") LocalDate targetDate
    );

    // Find expired batches that still have quantity in stock
    @Query("SELECT b FROM MedicineBatch b " +
           "WHERE b.expiryDate <= :today " +
           "AND b.quantity > 0 AND b.active = true " +
           "ORDER BY b.expiryDate ASC")
    List<MedicineBatch> findExpiredBatches(@Param("today") LocalDate today);

    // Paginated batches for a specific medicine
    Page<MedicineBatch> findByMedicineId(Long medicineId, Pageable pageable);

    // Paginated batches with active filter
    Page<MedicineBatch> findByActive(boolean active, Pageable pageable);
}
