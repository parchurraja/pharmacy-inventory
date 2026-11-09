package com.pharmacy.inventory;

import com.pharmacy.inventory.dto.MedicineDTO;
import com.pharmacy.inventory.dto.ReportSummaryDTO;
import com.pharmacy.inventory.entity.Medicine;
import com.pharmacy.inventory.entity.MedicineBatch;
import com.pharmacy.inventory.repository.MedicineBatchRepository;
import com.pharmacy.inventory.repository.MedicineRepository;
import com.pharmacy.inventory.repository.SaleItemRepository;
import com.pharmacy.inventory.repository.SaleRepository;
import com.pharmacy.inventory.service.ReportService;
import com.pharmacy.inventory.service.SaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class HistoricalProfitCalculationTest {

    @Autowired
    private SaleService saleService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private MedicineBatchRepository batchRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private SaleItemRepository saleItemRepository;

    @BeforeEach
    void cleanUp() {
        saleItemRepository.deleteAll();
        saleRepository.deleteAll();
    }

    @Test
    @DisplayName("Historical Cost Isolation: Modifying batch cost price later must NOT alter historical sale profit")
    void testHistoricalCostPriceImmunity() {
        // 1. Arrange: Create medicine and batch with selling price = 10.00, cost price = 4.00
        Medicine medicine = medicineRepository.save(Medicine.builder()
                .name("Paracetamol-ProfitTest-" + System.currentTimeMillis())
                .category("Pain Relief")
                .lowStockThreshold(10)
                .active(true)
                .build());

        MedicineBatch batch = batchRepository.save(MedicineBatch.builder()
                .medicine(medicine)
                .batchNumber("PARA-P-001")
                .quantity(20)
                .expiryDate(LocalDate.now().plusMonths(12))
                .costPrice(BigDecimal.valueOf(4.00))
                .sellingPrice(BigDecimal.valueOf(10.00))
                .active(true)
                .build());

        // 2. Act: Sell 5 units
        MedicineDTO.SaleItemRequest itemReq = MedicineDTO.SaleItemRequest.builder()
                .medicineId(medicine.getId())
                .quantity(5)
                .build();

        MedicineDTO.SaleResponseDTO saleResponse = saleService.processSale(
                MedicineDTO.SaleCreateRequest.builder()
                        .customerName("Patient A")
                        .items(Collections.singletonList(itemReq))
                        .build(),
                "pharmacist");

        // 3. Verify initial report: Net revenue = 50.00, COGS = 20.00, Gross Profit = 30.00, Gross Margin = 60.0%
        ReportSummaryDTO initialReport = reportService.getSummary(null, null);
        assertEquals(0, BigDecimal.valueOf(50.00).compareTo(initialReport.getNetRevenue()));
        assertEquals(0, BigDecimal.valueOf(20.00).compareTo(initialReport.getCostOfGoodsSold()));
        assertEquals(0, BigDecimal.valueOf(30.00).compareTo(initialReport.getGrossProfit()));
        assertEquals(60.0, initialReport.getGrossMarginPercentage(), 0.01);

        // 4. Mutate batch cost price in database from 4.00 -> 9.00
        MedicineBatch freshBatch = batchRepository.findById(batch.getId()).orElseThrow();
        freshBatch.setCostPrice(BigDecimal.valueOf(9.00));
        batchRepository.save(freshBatch);

        // 5. Recalculate report: Profit MUST still use sale-time cost price (4.00), NOT current batch cost (9.00)!
        ReportSummaryDTO mutatedReport = reportService.getSummary(null, null);
        assertEquals(0, BigDecimal.valueOf(50.00).compareTo(mutatedReport.getNetRevenue()), "Net revenue should remain 50.00");
        assertEquals(0, BigDecimal.valueOf(20.00).compareTo(mutatedReport.getCostOfGoodsSold()), "COGS must remain 20.00 based on sale-time cost");
        assertEquals(0, BigDecimal.valueOf(30.00).compareTo(mutatedReport.getGrossProfit()), "Gross profit must remain 30.00, not 5.00");
        assertEquals(60.0, mutatedReport.getGrossMarginPercentage(), 0.01);
    }

    @Test
    @DisplayName("Partial Return: Returning units decrements net revenue and COGS using sale-time cost")
    void testPartialReturnHistoricalProfitAdjustment() {
        // Arrange
        Medicine medicine = medicineRepository.save(Medicine.builder()
                .name("Amoxicillin-ReturnTest-" + System.currentTimeMillis())
                .category("Antibiotics")
                .lowStockThreshold(5)
                .active(true)
                .build());

        MedicineBatch batch = batchRepository.save(MedicineBatch.builder()
                .medicine(medicine)
                .batchNumber("AMOX-RET-001")
                .quantity(10)
                .expiryDate(LocalDate.now().plusMonths(12))
                .costPrice(BigDecimal.valueOf(4.00))
                .sellingPrice(BigDecimal.valueOf(10.00))
                .active(true)
                .build());

        // Sell 5 units
        MedicineDTO.SaleResponseDTO saleResponse = saleService.processSale(
                MedicineDTO.SaleCreateRequest.builder()
                        .customerName("Patient B")
                        .items(Collections.singletonList(
                                MedicineDTO.SaleItemRequest.builder()
                                        .medicineId(medicine.getId())
                                        .quantity(5)
                                        .build()))
                        .build(),
                "pharmacist");

        Long saleId = saleResponse.getId();
        Long saleItemId = saleResponse.getItems().get(0).getId();

        // Mutate batch cost price after sale
        MedicineBatch freshBatch = batchRepository.findById(batch.getId()).orElseThrow();
        freshBatch.setCostPrice(BigDecimal.valueOf(8.50));
        batchRepository.save(freshBatch);

        // Return 2 units
        MedicineDTO.SaleReturnRequest returnReq = MedicineDTO.SaleReturnRequest.builder()
                .items(Collections.singletonList(
                        MedicineDTO.ReturnItemRequest.builder()
                                .saleItemId(saleItemId)
                                .quantity(2)
                                .build()))
                .reason("Doctor revised prescription")
                .build();

        saleService.processReturn(saleId, returnReq, "pharmacist");

        // Verify report metrics:
        // Net revenue = (5 - 2) * 10.00 = 30.00
        // COGS = (5 - 2) * 4.00 = 12.00
        // Gross Profit = 30.00 - 12.00 = 18.00
        // Gross Margin = (18 / 30) * 100 = 60.0%
        // Refund Amount = 2 * 10.00 = 20.00
        ReportSummaryDTO report = reportService.getSummary(null, null);
        assertEquals(0, BigDecimal.valueOf(30.00).compareTo(report.getNetRevenue()));
        assertEquals(0, BigDecimal.valueOf(12.00).compareTo(report.getCostOfGoodsSold()));
        assertEquals(0, BigDecimal.valueOf(18.00).compareTo(report.getGrossProfit()));
        assertEquals(60.0, report.getGrossMarginPercentage(), 0.01);
        assertEquals(0, BigDecimal.valueOf(20.00).compareTo(report.getTotalRefundAmount()));
        assertEquals(3, report.getTotalUnitsSold());
        assertEquals(1, report.getTotalReturnsCount());
    }

    @Test
    @DisplayName("Multi-Batch Sale: FEFO sale spanning batches with different cost prices computes exact weighted COGS")
    void testMultiBatchDifferentCostPrices() {
        Medicine medicine = medicineRepository.save(Medicine.builder()
                .name("Cetirizine-MultiBatch-" + System.currentTimeMillis())
                .category("Allergy")
                .lowStockThreshold(5)
                .active(true)
                .build());

        // Batch 1: Expiring in 2 months, 3 units @ cost 5.00, sell 15.00
        MedicineBatch batch1 = batchRepository.save(MedicineBatch.builder()
                .medicine(medicine)
                .batchNumber("CET-BATCH-1")
                .quantity(3)
                .expiryDate(LocalDate.now().plusMonths(2))
                .costPrice(BigDecimal.valueOf(5.00))
                .sellingPrice(BigDecimal.valueOf(15.00))
                .active(true)
                .build());

        // Batch 2: Expiring in 6 months, 10 units @ cost 8.00, sell 15.00
        MedicineBatch batch2 = batchRepository.save(MedicineBatch.builder()
                .medicine(medicine)
                .batchNumber("CET-BATCH-2")
                .quantity(10)
                .expiryDate(LocalDate.now().plusMonths(6))
                .costPrice(BigDecimal.valueOf(8.00))
                .sellingPrice(BigDecimal.valueOf(15.00))
                .active(true)
                .build());

        // Sell 5 units: FEFO consumes 3 from Batch 1 + 2 from Batch 2
        saleService.processSale(
                MedicineDTO.SaleCreateRequest.builder()
                        .customerName("Allergy Patient")
                        .items(Collections.singletonList(
                                MedicineDTO.SaleItemRequest.builder()
                                        .medicineId(medicine.getId())
                                        .quantity(5)
                                        .build()))
                        .build(),
                "pharmacist");

        // Expected:
        // Net Revenue = 5 * 15.00 = 75.00
        // COGS = (3 * 5.00) + (2 * 8.00) = 15.00 + 16.00 = 31.00
        // Gross Profit = 75.00 - 31.00 = 44.00
        // Gross Margin = (44.00 / 75.00) * 100 = 58.67%
        ReportSummaryDTO report = reportService.getSummary(null, null);
        assertEquals(0, BigDecimal.valueOf(75.00).compareTo(report.getNetRevenue()));
        assertEquals(0, BigDecimal.valueOf(31.00).compareTo(report.getCostOfGoodsSold()));
        assertEquals(0, BigDecimal.valueOf(44.00).compareTo(report.getGrossProfit()));
        assertEquals(58.67, report.getGrossMarginPercentage(), 0.01);
    }

    @Test
    @DisplayName("Fully Refunded Sale: Margin and profit reset to 0 with zero-division safety")
    void testFullyRefundedSaleZeroDivisionSafety() {
        Medicine medicine = medicineRepository.save(Medicine.builder()
                .name("Ibuprofen-FullRefund-" + System.currentTimeMillis())
                .category("Pain Relief")
                .lowStockThreshold(5)
                .active(true)
                .build());

        batchRepository.save(MedicineBatch.builder()
                .medicine(medicine)
                .batchNumber("IBU-FULL-001")
                .quantity(10)
                .expiryDate(LocalDate.now().plusMonths(12))
                .costPrice(BigDecimal.valueOf(6.00))
                .sellingPrice(BigDecimal.valueOf(20.00))
                .active(true)
                .build());

        // Sell 4 units
        MedicineDTO.SaleResponseDTO saleResponse = saleService.processSale(
                MedicineDTO.SaleCreateRequest.builder()
                        .customerName("Full Refund Customer")
                        .items(Collections.singletonList(
                                MedicineDTO.SaleItemRequest.builder()
                                        .medicineId(medicine.getId())
                                        .quantity(4)
                                        .build()))
                        .build(),
                "pharmacist");

        // Return all 4 units
        saleService.processReturn(
                saleResponse.getId(),
                MedicineDTO.SaleReturnRequest.builder()
                        .items(Collections.singletonList(
                                MedicineDTO.ReturnItemRequest.builder()
                                        .saleItemId(saleResponse.getItems().get(0).getId())
                                        .quantity(4)
                                        .build()))
                        .reason("Incorrect dosage prescribed")
                        .build(),
                "pharmacist");

        ReportSummaryDTO report = reportService.getSummary(null, null);
        assertEquals(0, BigDecimal.valueOf(0.00).compareTo(report.getNetRevenue()));
        assertEquals(0, BigDecimal.valueOf(0.00).compareTo(report.getCostOfGoodsSold()));
        assertEquals(0, BigDecimal.valueOf(0.00).compareTo(report.getGrossProfit()));
        assertEquals(0.0, report.getGrossMarginPercentage(), 0.001); // No division by zero!
        assertEquals(0, BigDecimal.valueOf(80.00).compareTo(report.getTotalRefundAmount()));
        assertEquals(0, report.getTotalUnitsSold());
    }

    @Test
    @DisplayName("Zero Revenue: No sales recorded returns 0 margin without exception")
    void testEmptyDatabaseSafeDivision() {
        ReportSummaryDTO report = reportService.getSummary(null, null);
        assertNotNull(report);
        assertEquals(0, BigDecimal.ZERO.compareTo(report.getNetRevenue()));
        assertEquals(0.0, report.getGrossMarginPercentage());
    }

    @Test
    @DisplayName("Date Range Filtering: Report correctly isolates transactions within boundary")
    void testDateRangeFiltering() {
        Medicine medicine = medicineRepository.save(Medicine.builder()
                .name("Filter-Med-" + System.currentTimeMillis())
                .category("General")
                .lowStockThreshold(5)
                .active(true)
                .build());

        batchRepository.save(MedicineBatch.builder()
                .medicine(medicine)
                .batchNumber("FIL-BATCH-1")
                .quantity(10)
                .expiryDate(LocalDate.now().plusMonths(12))
                .costPrice(BigDecimal.valueOf(2.00))
                .sellingPrice(BigDecimal.valueOf(8.00))
                .active(true)
                .build());

        saleService.processSale(
                MedicineDTO.SaleCreateRequest.builder()
                        .customerName("Date Filter Patient")
                        .items(Collections.singletonList(
                                MedicineDTO.SaleItemRequest.builder()
                                        .medicineId(medicine.getId())
                                        .quantity(3)
                                        .build()))
                        .build(),
                "pharmacist");

        // Query future range -> should find 0 sales
        ReportSummaryDTO futureReport = reportService.getSummary(
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().plusDays(5));
        assertEquals(0, BigDecimal.ZERO.compareTo(futureReport.getNetRevenue()));
        assertEquals(0, futureReport.getTotalSalesCount());

        // Query today range -> should find 1 sale
        ReportSummaryDTO currentReport = reportService.getSummary(
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1));
        assertEquals(1, currentReport.getTotalSalesCount());
        assertEquals(0, BigDecimal.valueOf(24.00).compareTo(currentReport.getNetRevenue()));
    }
}
