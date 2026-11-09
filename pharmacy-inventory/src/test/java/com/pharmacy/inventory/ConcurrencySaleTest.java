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
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class ConcurrencySaleTest {

    @Autowired
    private SaleService saleService;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private MedicineBatchRepository batchRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Test
    @DisplayName("Concurrency Test: Two simultaneous sales for the last 5 units must never sell 10 units")
    void testConcurrentSaleOfLastStock() throws Exception {
        // 1. Arrange: Create medicine with exactly 5 units in a single batch
        String medicineName = "Azithromycin-" + System.currentTimeMillis();
        Medicine medicine = medicineRepository.save(Medicine.builder()
                .name(medicineName)
                .category("Antibiotics")
                .lowStockThreshold(10)
                .active(true)
                .build());

        MedicineBatch batch = batchRepository.save(MedicineBatch.builder()
                .medicine(medicine)
                .batchNumber("AZI-CONCUR-001")
                .quantity(5) // Exactly 5 units in stock
                .expiryDate(LocalDate.now().plusMonths(12))
                .costPrice(BigDecimal.valueOf(5.00))
                .sellingPrice(BigDecimal.valueOf(12.00))
                .active(true)
                .build());

        // 2. Act: Prepare two concurrent threads trying to sell 5 units simultaneously
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String pharmacistName = "pharmacist-" + (i + 1);
            executor.submit(() -> {
                try {
                    // Block until all threads are ready, ensuring true simultaneous execution
                    startSignal.await();

                    MedicineDTO.SaleCreateRequest request = MedicineDTO.SaleCreateRequest.builder()
                            .customerName("Patient " + pharmacistName)
                            .items(Collections.singletonList(
                                    MedicineDTO.SaleItemRequest.builder()
                                            .medicineId(medicine.getId())
                                            .quantity(5) // Each requests 5 units
                                            .build()
                            ))
                            .build();

                    saleService.processSale(request, pharmacistName);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException ex) {
                    failureCount.incrementAndGet();
                } catch (Exception ex) {
                    if (ex.getCause() instanceof InsufficientStockException) {
                        failureCount.incrementAndGet();
                    } else {
                        ex.printStackTrace();
                    }
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startSignal.countDown();
        boolean completed = doneSignal.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // 3. Assert:
        assertTrue(completed, "Concurrent threads should complete within 10 seconds");

        // Exactly one sale succeeded, and exactly one sale failed
        assertEquals(1, successCount.get(), "Exactly one sale should succeed");
        assertEquals(1, failureCount.get(), "Exactly one sale should fail due to insufficient stock");

        // Verify remaining stock in database is exactly 0 (never negative, and never 10 sold)
        MedicineBatch updatedBatch = batchRepository.findById(batch.getId()).orElseThrow();
        assertEquals(0, updatedBatch.getQuantity(), "Batch remaining quantity must be exactly 0");
    }
}
