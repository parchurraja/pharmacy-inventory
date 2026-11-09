package com.pharmacy.inventory;

import com.pharmacy.inventory.config.DataInitializer;
import com.pharmacy.inventory.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProductionSecurityFailFastTest {

    @Test
    @DisplayName("Production Security: Startup must fail if demo user seeding is enabled in production")
    void testFailStartupIfSeedDemoUsersEnabledInProd() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(Profiles.of("prod", "production"))).thenReturn(true);

        DataInitializer initializer = new DataInitializer(
                userRepository,
                passwordEncoder,
                environment,
                "admin",
                "StrongCustomProdPassword#999",
                "pharmacist",
                "StrongCustomPharmPassword#999",
                true, // seedDemoUsers = true in prod -> MUST FAIL!
                false
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, initializer::run);
        assertTrue(ex.getMessage().contains("app.seed-demo-users"));
    }

    @Test
    @DisplayName("Production Security: Startup must fail if default demo passwords are used in production")
    void testFailStartupIfDefaultPasswordsUsedInProd() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(Profiles.of("prod", "production"))).thenReturn(true);

        DataInitializer initializer = new DataInitializer(
                userRepository,
                passwordEncoder,
                environment,
                "admin",
                "Admin@123", // Default password in prod -> MUST FAIL!
                "pharmacist",
                "Pharm@123", // Default password in prod -> MUST FAIL!
                false,
                false
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, initializer::run);
        assertTrue(ex.getMessage().contains("Default demo passwords"));
    }

    @Test
    @DisplayName("Production Security: Startup must fail if allowDefaultCredentials is true in production")
    void testFailStartupIfAllowDefaultCredentialsInProd() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        Environment environment = mock(Environment.class);
        when(environment.acceptsProfiles(Profiles.of("prod", "production"))).thenReturn(true);

        DataInitializer initializer = new DataInitializer(
                userRepository,
                passwordEncoder,
                environment,
                "admin",
                "CustomProdPassword!123",
                "pharmacist",
                "CustomPharmPassword!456",
                false,
                true // allowDefaultCredentials = true in prod -> MUST FAIL!
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, initializer::run);
        assertTrue(ex.getMessage().contains("app.allow-default-credentials"));
    }
}
