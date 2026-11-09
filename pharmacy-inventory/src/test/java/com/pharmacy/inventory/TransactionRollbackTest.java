package com.pharmacy.inventory;

import com.pharmacy.inventory.dto.MedicineDTO;
import com.pharmacy.inventory.entity.Medicine;
import com.pharmacy.inventory.entity.MedicineBatch;
import com.pharmacy.inventory.exception.InsufficientStockException;
import com.pharmacy.inventory.repository.MedicineBatchRepository;
import com.pharmacy.inventory.repository.MedicineRepository;
import com.pharmacy.inventory.repository.SaleRepository;
import com.pharmacy.inventory.service.SaleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class TransactionRollbackTest {

    @Autowired
    private SaleService saleService;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private MedicineBatchRepository batchRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Test
    @DisplayName("Rollback Test: Failed sale rolls back every prior stock deduction and does not persist sale")
    void testSaleTransactionRollbackOnPartialFailure() {
        // 1. Arrange: Create two medicines
        // Medicine 1: Amoxicillin with 20 units
        Medicine med1 = medicineRepository.save(Medicine.builder()
                .name("MedA-" + System.currentTimeMillis())
                .category("Antibiotics")
                .lowStockThreshold(5)
                .active(true)
                .build());

        MedicineBatch batch1 = batchRepository.save(MedicineBatch.builder()
                .medicine(med1)
                .batchNumber("MEDA-BATCH-001")
                .quantity(20)
                .expiryDate(LocalDate.now().plusMonths(6))
                .sellingPrice(BigDecimal.valueOf(10.00))
                .active(true)
                .build());

        // Medicine 2: Ciprofloxacin with only 5 units
        Medicine med2 = medicineRepository.save(Medicine.builder()
                .name("MedB-" + System.currentTimeMillis())
                .category("Antibiotics")
                .lowStockThreshold(5)
                .active(true)
                .build());

        MedicineBatch batch2 = batchRepository.save(MedicineBatch.builder()
                .medicine(med2)
                .batchNumber("MEDB-BATCH-001")
                .quantity(5)
                .expiryDate(LocalDate.now().plusMonths(6))
                .sellingPrice(BigDecimal.valueOf(15.00))
                .active(true)
                .build());

        long initialSaleCount = saleRepository.count();

        // 2. Act: Attempt multi-item sale where:
        // Item 1: requests 10 units of Med1 (which succeeds individually)
        // Item 2: requests 50 units of Med2 (which exceeds available stock of 5 units and fails)
        MedicineDTO.SaleCreateRequest request = MedicineDTO.SaleCreateRequest.builder()
                .customerName("Rollback Patient")
                .items(Arrays.asList(
                        MedicineDTO.SaleItemRequest.builder()
                                .medicineId(med1.getId())
                                .quantity(10)
                                .build(),
                        MedicineDTO.SaleItemRequest.builder()
                                .medicineId(med2.getId())
                                .quantity(50) // Exceeds available stock of 5
                                .build()
                ))
                .build();

        assertThrows(InsufficientStockException.class, () -> {
            saleService.processSale(request, "pharmacist");
        }, "Should throw InsufficientStockException when item 2 stock is exceeded");

        // 3. Assert:
        // Verify Medicine 1's batch was ROLLED BACK and still has full 20 units (NOT 10 units)
        MedicineBatch updatedBatch1 = batchRepository.findById(batch1.getId()).orElseThrow();
        assertEquals(20, updatedBatch1.getQuantity(),
                "Medicine 1 stock deduction must be completely rolled back to original 20 units");

        // Verify Medicine 2's batch still has 5 units
        MedicineBatch updatedBatch2 = batchRepository.findById(batch2.getId()).orElseThrow();
        assertEquals(5, updatedBatch2.getQuantity(),
                "Medicine 2 stock must remain unchanged at 5 units");

        // Verify no sale record was persisted
        long finalSaleCount = saleRepository.count();
        assertEquals(initialSaleCount, finalSaleCount,
                "No sale record should be saved in database when transaction rolls back");
    }
}
