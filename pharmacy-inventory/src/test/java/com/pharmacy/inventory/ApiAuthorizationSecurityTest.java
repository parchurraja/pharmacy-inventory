package com.pharmacy.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmacy.inventory.dto.AuthDTO;
import com.pharmacy.inventory.dto.MedicineDTO;
import com.pharmacy.inventory.entity.Medicine;
import com.pharmacy.inventory.entity.MedicineBatch;
import com.pharmacy.inventory.repository.MedicineBatchRepository;
import com.pharmacy.inventory.repository.MedicineRepository;
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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ApiAuthorizationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private MedicineBatchRepository batchRepository;

    private String adminToken;
    private String pharmacistToken;
    private Medicine testMedicine;
    private MedicineBatch expiredBatch;

    @BeforeEach
    void setUp() throws Exception {
        // Obtain real JWT tokens via login endpoint
        adminToken = loginAndGetToken("admin", "Admin@123");
        pharmacistToken = loginAndGetToken("pharmacist", "Pharm@123");

        // Seed test medicine
        testMedicine = medicineRepository.save(Medicine.builder()
                .name("Auth-Test-Med-" + System.currentTimeMillis())
                .category("Antibiotics")
                .lowStockThreshold(10)
                .active(true)
                .build());

        // Seed expired batch for disposal test
        expiredBatch = batchRepository.save(MedicineBatch.builder()
                .medicine(testMedicine)
                .batchNumber("EXP-AUTH-001")
                .quantity(10)
                .expiryDate(LocalDate.now().minusDays(10))
                .costPrice(BigDecimal.valueOf(5.00))
                .sellingPrice(BigDecimal.valueOf(15.00))
                .active(true)
                .build());
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
    @DisplayName("Case 1: No token on protected API must return 401 Unauthorized")
    void testNoTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/medicines"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Case 2: Invalid or expired token must return 401 Unauthorized")
    void testInvalidTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/medicines")
                        .header("Authorization", "Bearer invalid.fake.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Case 3: Pharmacist calling deactivation must return 403 Forbidden")
    void testPharmacistCannotDeactivateMedicine() throws Exception {
        mockMvc.perform(patch("/api/medicines/" + testMedicine.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + pharmacistToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Case 4: Pharmacist calling expired batch disposal must return 403 Forbidden")
    void testPharmacistCannotDisposeExpiredBatch() throws Exception {
        mockMvc.perform(post("/api/inventory/expired/" + expiredBatch.getId() + "/dispose")
                        .header("Authorization", "Bearer " + pharmacistToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Case 5: Admin calling deactivation must succeed (200)")
    void testAdminCanDeactivateMedicine() throws Exception {
        mockMvc.perform(patch("/api/medicines/" + testMedicine.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Case 6: Admin calling expired batch disposal must succeed (200)")
    void testAdminCanDisposeExpiredBatch() throws Exception {
        mockMvc.perform(post("/api/inventory/expired/" + expiredBatch.getId() + "/dispose")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Case 7: Pharmacist creating a sale must succeed (200)")
    void testPharmacistCanCreateSale() throws Exception {
        // Create an active non-expired batch for sales test
        MedicineBatch validBatch = batchRepository.save(MedicineBatch.builder()
                .medicine(testMedicine)
                .batchNumber("SALE-AUTH-001")
                .quantity(20)
                .expiryDate(LocalDate.now().plusMonths(6))
                .costPrice(BigDecimal.valueOf(4.00))
                .sellingPrice(BigDecimal.valueOf(10.00))
                .active(true)
                .build());

        MedicineDTO.SaleItemRequest itemReq = MedicineDTO.SaleItemRequest.builder()
                .medicineId(testMedicine.getId())
                .medicineName(testMedicine.getName())
                .quantity(2)
                .build();

        MedicineDTO.SaleCreateRequest saleReq = MedicineDTO.SaleCreateRequest.builder()
                .customerName("Auth Test Customer")
                .items(Collections.singletonList(itemReq))
                .build();

        mockMvc.perform(post("/api/sales")
                        .header("Authorization", "Bearer " + pharmacistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saleReq)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Case 8: Pharmacist processing a sale return must succeed (200)")
    void testPharmacistCanReturnSale() throws Exception {
        // 1. Setup sale
        MedicineBatch validBatch = batchRepository.save(MedicineBatch.builder()
                .medicine(testMedicine)
                .batchNumber("RET-AUTH-001")
                .quantity(20)
                .expiryDate(LocalDate.now().plusMonths(6))
                .costPrice(BigDecimal.valueOf(4.00))
                .sellingPrice(BigDecimal.valueOf(10.00))
                .active(true)
                .build());

        MedicineDTO.SaleItemRequest itemReq = MedicineDTO.SaleItemRequest.builder()
                .medicineId(testMedicine.getId())
                .quantity(5)
                .build();

        MedicineDTO.SaleCreateRequest saleReq = MedicineDTO.SaleCreateRequest.builder()
                .customerName("Return Auth Customer")
                .items(Collections.singletonList(itemReq))
                .build();

        MvcResult saleResult = mockMvc.perform(post("/api/sales")
                        .header("Authorization", "Bearer " + pharmacistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(saleReq)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode dataNode = objectMapper.readTree(saleResult.getResponse().getContentAsString()).get("data");
        Long saleId = dataNode.get("id").asLong();
        Long saleItemId = dataNode.get("items").get(0).get("id").asLong();

        // 2. Perform return as pharmacist
        MedicineDTO.ReturnItemRequest retItem = MedicineDTO.ReturnItemRequest.builder()
                .saleItemId(saleItemId)
                .quantity(2)
                .build();

        MedicineDTO.SaleReturnRequest returnReq = MedicineDTO.SaleReturnRequest.builder()
                .items(Collections.singletonList(retItem))
                .reason("Defective packaging")
                .build();

        mockMvc.perform(post("/api/sales/" + saleId + "/return")
                        .header("Authorization", "Bearer " + pharmacistToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(returnReq)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Case 9: Pharmacist viewing reports must succeed (200)")
    void testPharmacistCanViewReports() throws Exception {
        mockMvc.perform(get("/api/reports/summary")
                        .header("Authorization", "Bearer " + pharmacistToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Case 10: Admin viewing reports must succeed (200)")
    void testAdminCanViewReports() throws Exception {
        mockMvc.perform(get("/api/reports/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
