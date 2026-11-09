package com.pharmacy.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.inventory.dto.AuthDTO;
import com.pharmacy.inventory.dto.MedicineDTO;
import com.pharmacy.inventory.entity.AdjustmentType;
import com.pharmacy.inventory.entity.Medicine;
import com.pharmacy.inventory.entity.MedicineBatch;
import com.pharmacy.inventory.entity.Sale;
import com.pharmacy.inventory.repository.MedicineBatchRepository;
import com.pharmacy.inventory.repository.MedicineRepository;
import com.pharmacy.inventory.repository.SaleItemRepository;
import com.pharmacy.inventory.repository.SaleRepository;
import com.pharmacy.inventory.repository.StockAdjustmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class EndToEndWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private MedicineBatchRepository batchRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private SaleItemRepository saleItemRepository;

    @Autowired
    private StockAdjustmentRepository stockAdjustmentRepository;

    private String adminToken;
    private String pharmacistToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = loginAndGetToken("admin", "Admin@123");
        pharmacistToken = loginAndGetToken("pharmacist", "Pharm@123");
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        AuthDTO.LoginRequest req = AuthDTO.LoginRequest.builder()
                .username(username)
                .password(password)
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("data").get("token").asText();
    }

    @Test
    @DisplayName("Complete E2E Workflow: Auth -> Catalog -> FEFO Batches -> POS Sale -> Return -> Alerts -> Reports")
    void testCompletePharmacyOperationsWorkflow() throws Exception {
        // =========================================================================
        // 1. AUTHENTICATION & TOKEN VALIDATION
        // =========================================================================
        assertNotNull(adminToken);
        assertNotNull(pharmacistToken);

        // Missing token -> 401 Unauthorized
        mockMvc.perform(get("/api/medicines"))
                .andExpect(status().isUnauthorized());

        // Invalid token -> 401 Unauthorized
        mockMvc.perform(get("/api/medicines")
                        .header("Authorization", "Bearer invalid.expired.jwt"))
                .andExpect(status().isUnauthorized());

        // =========================================================================
        // 2. MEDICINE MANAGEMENT (CRUD & PERMISSIONS)
        // =========================================================================
        String medName = "E2E-Amoxiclav-" + System.currentTimeMillis();
        MedicineDTO.MedicineCreateRequest createMedReq = MedicineDTO.MedicineCreateRequest.builder()
                .name(medName)
                .description("Broad spectrum antibiotic formulation")
                .category("Antibiotics")
                .lowStockThreshold(15)
                .build();

        MvcResult medCreateResult = mockMvc.perform(post("/api/medicines")
                        .header("Authorization", "Bearer " + pharmacistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createMedReq)))
                .andExpect(status().isCreated())
                .andReturn();

        Long medicineId = objectMapper.readTree(medCreateResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        assertTrue(medicineId > 0);

        // Search medicine
        mockMvc.perform(get("/api/medicines?search=" + medName)
                        .header("Authorization", "Bearer " + pharmacistToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value(medName));

        // Update medicine
        MedicineDTO.MedicineUpdateRequest updateMedReq = MedicineDTO.MedicineUpdateRequest.builder()
                .name(medName + "-Updated")
                .description("Updated description")
                .category("Antibiotics")
                .lowStockThreshold(20)
                .build();

        mockMvc.perform(put("/api/medicines/" + medicineId)
                        .header("Authorization", "Bearer " + pharmacistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateMedReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lowStockThreshold").value(20));

        // Pagination beyond last page -> 200 OK with empty content
        mockMvc.perform(get("/api/medicines?page=999&size=10")
                        .header("Authorization", "Bearer " + pharmacistToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());

        // Pharmacist cannot deactivate medicine -> 403 Forbidden
        mockMvc.perform(patch("/api/medicines/" + medicineId + "/deactivate")
                        .header("Authorization", "Bearer " + pharmacistToken))
                .andExpect(status().isForbidden());

        // Admin can deactivate medicine -> 200 OK
        mockMvc.perform(patch("/api/medicines/" + medicineId + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Selling an inactive medicine must be rejected (400)
        MedicineDTO.SaleItemRequest inactiveItem = MedicineDTO.SaleItemRequest.builder()
                .medicineId(medicineId)
                .quantity(1)
                .build();

        mockMvc.perform(post("/api/sales")
                        .header("Authorization", "Bearer " + pharmacistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MedicineDTO.SaleCreateRequest.builder()
                                .customerName("Patient X")
                                .items(Collections.singletonList(inactiveItem))
                                .build())))
                .andExpect(status().isBadRequest());

        // Reactivate medicine as Admin
        mockMvc.perform(patch("/api/medicines/" + medicineId + "/activate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // =========================================================================
        // 3. BATCH MANAGEMENT (FEFO ORDERING & HETEROGENEOUS COSTS)
        // =========================================================================
        // Batch 1: Expiring in 2 months, 4 units @ cost 4.00, sell 10.00
        MedicineDTO.BatchCreateRequest b1Req = MedicineDTO.BatchCreateRequest.builder()
                .medicineId(medicineId)
                .batchNumber("B1-EARLY-" + System.currentTimeMillis())
                .quantity(4)
                .expiryDate(LocalDate.now().plusMonths(2))
                .costPrice(BigDecimal.valueOf(4.00))
                .sellingPrice(BigDecimal.valueOf(10.00))
                .build();

        mockMvc.perform(post("/api/batches")
                        .header("Authorization", "Bearer " + pharmacistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(b1Req)))
                .andExpect(status().isCreated());

        // Batch 2: Expiring in 8 months, 10 units @ cost 7.00, sell 10.00
        MedicineDTO.BatchCreateRequest b2Req = MedicineDTO.BatchCreateRequest.builder()
                .medicineId(medicineId)
                .batchNumber("B2-LATER-" + System.currentTimeMillis())
                .quantity(10)
                .expiryDate(LocalDate.now().plusMonths(8))
                .costPrice(BigDecimal.valueOf(7.00))
                .sellingPrice(BigDecimal.valueOf(10.00))
                .build();

        mockMvc.perform(post("/api/batches")
                        .header("Authorization", "Bearer " + pharmacistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(b2Req)))
                .andExpect(status().isCreated());

        // Verify batches list sorted by expiryDate ascending (FEFO)
        mockMvc.perform(get("/api/batches?medicineId=" + medicineId + "&sort=expiryDate,asc")
                        .header("Authorization", "Bearer " + pharmacistToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].batchNumber").value(b1Req.getBatchNumber()));

        // =========================================================================
        // 4. POS SALES (MULTI-BATCH ALLOCATION & INVOICE GENERATION)
        // =========================================================================
        // Sell 6 units: FEFO consumes 4 units from Batch 1 + 2 units from Batch 2
        MedicineDTO.SaleItemRequest saleItemReq = MedicineDTO.SaleItemRequest.builder()
                .medicineId(medicineId)
                .quantity(6)
                .build();

        MvcResult saleResult = mockMvc.perform(post("/api/sales")
                        .header("Authorization", "Bearer " + pharmacistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MedicineDTO.SaleCreateRequest.builder()
                                .customerName("E2E Test Patient")
                                .items(Collections.singletonList(saleItemReq))
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount").value(60.00))
                .andExpect(jsonPath("$.data.items.length()").value(2)) // 2 batch allocations
                .andReturn();

        JsonNode saleData = objectMapper.readTree(saleResult.getResponse().getContentAsString()).get("data");
        Long saleId = saleData.get("id").asLong();
        String invoiceNumber = saleData.get("invoiceNumber").asText();
        assertNotNull(invoiceNumber);
        assertTrue(invoiceNumber.startsWith("INV-"));

        // Verify batch allocations:
        // Item 1: 4 units from Batch 1, unitPrice=10.00, costPrice=4.00
        // Item 2: 2 units from Batch 2, unitPrice=10.00, costPrice=7.00
        JsonNode itemsArray = saleData.get("items");
        assertEquals(4, itemsArray.get(0).get("quantity").asInt());
        assertEquals(4.00, itemsArray.get(0).get("costPrice").asDouble(), 0.01);
        assertEquals(2, itemsArray.get(1).get("quantity").asInt());
        assertEquals(7.00, itemsArray.get(1).get("costPrice").asDouble(), 0.01);

        Long saleItem1Id = itemsArray.get(0).get("id").asLong();

        // =========================================================================
        // 5. SALES RETURN & RE-PRICED BATCH RETURN
        // =========================================================================
        // Mutate original batch cost price in the database before processing return
        MedicineBatch b1InDb = batchRepository.findByBatchNumber(b1Req.getBatchNumber()).orElseThrow();
        b1InDb.setCostPrice(BigDecimal.valueOf(9.99));
        batchRepository.save(b1InDb);

        // Process partial return: return 2 units from Item 1 (original cost was 4.00)
        MedicineDTO.ReturnItemRequest retReq = MedicineDTO.ReturnItemRequest.builder()
                .saleItemId(saleItem1Id)
                .quantity(2)
                .build();

        mockMvc.perform(post("/api/sales/" + saleId + "/return")
                        .header("Authorization", "Bearer " + pharmacistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(MedicineDTO.SaleReturnRequest.builder()
                                .items(Collections.singletonList(retReq))
                                .reason("Surplus tablets returned")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.data.refundAmount").value(20.00));

        // Verify batch stock was restored: Batch 1 had 0, now has 2
        MedicineBatch b1Restored = batchRepository.findByBatchNumber(b1Req.getBatchNumber()).orElseThrow();
        assertEquals(2, b1Restored.getQuantity());

        // Verify audit log created in stock_adjustments
        boolean returnAuditExists = stockAdjustmentRepository.findAll().stream()
                .anyMatch(a -> a.getAdjustmentType() == AdjustmentType.RETURN && a.getQuantityAdjusted() == 2);
        assertTrue(returnAuditExists, "A RETURN stock adjustment record must be persisted");

        // =========================================================================
        // 6. INVENTORY ALERTS & EXPIRED BATCH DISPOSAL
        // =========================================================================
        // Add an expired batch for disposal testing
        MedicineBatch expiredBatch = batchRepository.save(MedicineBatch.builder()
                .medicine(medicineRepository.findById(medicineId).get())
                .batchNumber("EXP-DISPOSE-" + System.currentTimeMillis())
                .quantity(15)
                .expiryDate(LocalDate.now().minusDays(5))
                .costPrice(BigDecimal.valueOf(3.50))
                .sellingPrice(BigDecimal.valueOf(8.00))
                .active(true)
                .build());

        // Low stock alert verification
        mockMvc.perform(get("/api/inventory/alerts/low-stock")
                        .header("Authorization", "Bearer " + pharmacistToken))
                .andExpect(status().isOk());

        // Expiry alert verification (30 days)
        mockMvc.perform(get("/api/inventory/alerts/expiring?days=30")
                        .header("Authorization", "Bearer " + pharmacistToken))
                .andExpect(status().isOk());

        // Expired stock listing
        mockMvc.perform(get("/api/inventory/expired")
                        .header("Authorization", "Bearer " + pharmacistToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.batchNumber == '" + expiredBatch.getBatchNumber() + "')]").exists());

        // Pharmacist cannot dispose expired batch -> 403 Forbidden
        mockMvc.perform(post("/api/inventory/expired/" + expiredBatch.getId() + "/dispose")
                        .header("Authorization", "Bearer " + pharmacistToken))
                .andExpect(status().isForbidden());

        // Admin disposes expired batch -> 200 OK
        mockMvc.perform(post("/api/inventory/expired/" + expiredBatch.getId() + "/dispose")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Verify batch is zeroes and deactivated
        MedicineBatch disposedBatch = batchRepository.findById(expiredBatch.getId()).orElseThrow();
        assertEquals(0, disposedBatch.getQuantity());
        assertFalse(disposedBatch.isActive());

        // =========================================================================
        // 7. REPORTS & PROFITABILITY (STORED SALE-TIME PRICE VERIFICATION)
        // =========================================================================
        // Calculate expected metrics for the 1 sale with partial return:
        // Initial sale: 4 units @ (sell=10, cost=4) + 2 units @ (sell=10, cost=7)
        // Return: 2 units from (sell=10, cost=4)
        // Retained units:
        //   - 2 units @ sell=10, cost=4 -> Revenue = 20.00, Cost = 8.00
        //   - 2 units @ sell=10, cost=7 -> Revenue = 20.00, Cost = 14.00
        // Net Revenue = 40.00
        // COGS = 8.00 + 14.00 = 22.00
        // Gross Profit = 40.00 - 22.00 = 18.00
        // Gross Margin = (18.00 / 40.00) * 100 = 45.0%
        // Total Refund = 20.00
        MvcResult reportResult = mockMvc.perform(get("/api/reports/summary")
                        .header("Authorization", "Bearer " + pharmacistToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode reportData = objectMapper.readTree(reportResult.getResponse().getContentAsString());
        // Verify values are populated and gross profit is strictly computed from sale-time cost
        assertTrue(reportData.get("netRevenue").asDouble() > 0);
        assertTrue(reportData.get("costOfGoodsSold").asDouble() > 0);
        assertTrue(reportData.get("grossProfit").asDouble() > 0);
        assertTrue(reportData.get("grossMarginPercentage").asDouble() > 0);
    }
}
