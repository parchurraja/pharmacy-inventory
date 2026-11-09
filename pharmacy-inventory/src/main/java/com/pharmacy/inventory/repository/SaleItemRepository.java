package com.pharmacy.inventory.repository;

import com.pharmacy.inventory.entity.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    List<SaleItem> findBySaleId(Long saleId);

    List<SaleItem> findByBatchId(Long batchId);

    List<SaleItem> findByMedicineId(Long medicineId);
}
