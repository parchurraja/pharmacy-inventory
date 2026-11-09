package com.pharmacy.inventory;

import com.pharmacy.inventory.repository.MedicineBatchRepository;
import com.pharmacy.inventory.repository.MedicineRepository;
import com.pharmacy.inventory.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class FlywayMigrationCleanDbTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private MedicineBatchRepository batchRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Flyway Test: Verify all migrations execute cleanly and seed baseline data")
    void testFlywayMigrationsOnCleanDatabase() {
        // Verify Flyway info has applied versions V1, V2 and V3
        var appliedMigrations = flyway.info().applied();
        assertTrue(appliedMigrations.length >= 3, "At least 3 Flyway migrations (V1, V2, V3) should be applied");

        assertEquals("1", appliedMigrations[0].getVersion().getVersion());
        assertEquals("2", appliedMigrations[1].getVersion().getVersion());
        assertEquals("3", appliedMigrations[2].getVersion().getVersion());

        // Verify baseline seeded medicines exist from V2
        assertTrue(medicineRepository.existsByNameIgnoreCase("Paracetamol"),
                "Paracetamol should be seeded by Flyway V2");
        assertTrue(medicineRepository.existsByNameIgnoreCase("Amoxicillin"),
                "Amoxicillin should be seeded by Flyway V2");
        assertTrue(medicineRepository.existsByNameIgnoreCase("Ibuprofen"),
                "Ibuprofen should be seeded by Flyway V2");

        // Verify batches seeded from V2
        assertTrue(batchRepository.count() >= 4, "Initial batches should be seeded by Flyway V2");

        // Verify default accounts seeded
        assertTrue(userRepository.existsByUsername("admin"), "Admin user should be initialized");
        assertTrue(userRepository.existsByUsername("pharmacist"), "Pharmacist user should be initialized");
    }
}
