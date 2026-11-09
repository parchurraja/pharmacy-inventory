package com.pharmacy.inventory;

import com.pharmacy.inventory.dto.MedicineDTO;
import com.pharmacy.inventory.entity.Medicine;
import com.pharmacy.inventory.entity.MedicineBatch;
import com.pharmacy.inventory.entity.SaleStatus;
import com.pharmacy.inventory.exception.InvalidSaleReturnException;
import com.pharmacy.inventory.repository.MedicineBatchRepository;
import com.pharmacy.inventory.repository.MedicineRepository;
import com.pharmacy.inventory.service.SaleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class SalesReturnTest {

    @Autowired
    private SaleService saleService;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private MedicineBatchRepository batchRepository;

    @Test
    @DisplayName("Sales Return Test: Prevent returning the same quantity twice and enforce available quantity check")
    void testPreventDuplicateAndExcessiveSalesReturn() {
        // 1. Arrange: Create medicine and batch with 10 units
        Medicine medicine = medicineRepository.save(Medicine.builder()
                .name("Ibuprofen-Return-" + System.currentTimeMillis())
                .category("Pain Relief")
                .lowStockThreshold(5)
                .active(true)
                .build());

        MedicineBatch batch = batchRepository.save(MedicineBatch.builder()
                .medicine(medicine)
                .batchNumber("IBU-RET-001")
                .quantity(10)
                .expiryDate(LocalDate.now().plusMonths(12))
                .sellingPrice(BigDecimal.valueOf(8.00))
                .active(true)
                .build());

        // 2. Act: Sell all 10 units
        MedicineDTO.SaleResponseDTO sale = saleService.processSale(MedicineDTO.SaleCreateRequest.builder()
                .customerName("Return Customer")
                .items(Collections.singletonList(
                        MedicineDTO.SaleItemRequest.builder()
                                .medicineId(medicine.getId())
                                .quantity(10)
                                .build()
                ))
                .build(), "pharmacist");

        Long saleItemId = sale.getItems().get(0).getId();

        // Verify stock is now 0
        assertEquals(0, batchRepository.findById(batch.getId()).orElseThrow().getQuantity());

        // 3. First Return: Return 6 units (valid)
        MedicineDTO.SaleReturnResponse return1 = saleService.processReturn(sale.getId(),
                MedicineDTO.SaleReturnRequest.builder()
                        .items(Collections.singletonList(
                                MedicineDTO.ReturnItemRequest.builder()
                                        .saleItemId(saleItemId)
                                        .quantity(6)
                                        .build()
                        ))
                        .reason("Customer only needed 4 units")
                        .build(), "pharmacist");

        assertEquals(SaleStatus.PARTIALLY_REFUNDED, return1.getStatus());
        assertEquals(6, return1.getTotalQuantityReturned());
        assertEquals(new BigDecimal("48.00"), return1.getRefundAmount());
        assertEquals(6, batchRepository.findById(batch.getId()).orElseThrow().getQuantity());

        // 4. Duplicate Return Attempt: Try to return 6 units again (only 4 remain available for return)
        assertThrows(InvalidSaleReturnException.class, () -> {
            saleService.processReturn(sale.getId(),
                    MedicineDTO.SaleReturnRequest.builder()
                            .items(Collections.singletonList(
                                    MedicineDTO.ReturnItemRequest.builder()
                                            .saleItemId(saleItemId)
                                            .quantity(6) // Cannot return 6 when only 4 remain
                                            .build()
                            ))
                            .reason("Trying to return duplicate quantity")
                            .build(), "pharmacist");
        }, "Should throw InvalidSaleReturnException when return exceeds remaining quantity");

        // 5. Valid Remaining Return: Return the remaining 4 units
        MedicineDTO.SaleReturnResponse return2 = saleService.processReturn(sale.getId(),
                MedicineDTO.SaleReturnRequest.builder()
                        .items(Collections.singletonList(
                                MedicineDTO.ReturnItemRequest.builder()
                                        .saleItemId(saleItemId)
                                        .quantity(4)
                                        .build()
                        ))
                        .reason("Returning last 4 units")
                        .build(), "pharmacist");

        assertEquals(SaleStatus.REFUNDED, return2.getStatus(), "Sale should now be fully REFUNDED");
        assertEquals(4, return2.getTotalQuantityReturned());
        assertEquals(10, batchRepository.findById(batch.getId()).orElseThrow().getQuantity());

        // 6. Over-Return on Fully Refunded Sale: Attempt to return 1 more unit
        assertThrows(InvalidSaleReturnException.class, () -> {
            saleService.processReturn(sale.getId(),
                    MedicineDTO.SaleReturnRequest.builder()
                            .items(Collections.singletonList(
                                    MedicineDTO.ReturnItemRequest.builder()
                                            .saleItemId(saleItemId)
                                            .quantity(1)
                                            .build()
                            ))
                            .reason("Attempting return on already fully refunded sale")
                            .build(), "pharmacist");
        }, "Should throw InvalidSaleReturnException when sale is already fully refunded");
    }
}
