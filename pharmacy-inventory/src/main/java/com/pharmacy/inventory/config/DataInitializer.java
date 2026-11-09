package com.pharmacy.inventory.config;

import com.pharmacy.inventory.entity.Role;
import com.pharmacy.inventory.entity.User;
import com.pharmacy.inventory.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final String adminUsername;
    private final String adminPassword;
    private final String pharmacistUsername;
    private final String pharmacistPassword;
    private final boolean seedDemoUsers;
    private final boolean allowDefaultCredentials;

    public DataInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Environment environment,
            @Value("${app.admin.username:admin}") String adminUsername,
            @Value("${app.admin.password:Admin@123}") String adminPassword,
            @Value("${app.pharmacist.username:pharmacist}") String pharmacistUsername,
            @Value("${app.pharmacist.password:Pharm@123}") String pharmacistPassword,
            @Value("${app.seed-demo-users:true}") boolean seedDemoUsers,
            @Value("${app.allow-default-credentials:true}") boolean allowDefaultCredentials) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.pharmacistUsername = pharmacistUsername;
        this.pharmacistPassword = pharmacistPassword;
        this.seedDemoUsers = seedDemoUsers;
        this.allowDefaultCredentials = allowDefaultCredentials;
    }

    @Override
    public void run(String... args) {
        validateSecurityPolicies();
        if (seedDemoUsers) {
            initDefaultUsers();
        } else {
            log.info("Demo user seeding is disabled (app.seed-demo-users=false).");
        }
    }

    private void validateSecurityPolicies() {
        boolean isProduction = environment.acceptsProfiles(Profiles.of("prod", "production"));

        if (isProduction) {
            log.info("Production profile detected. Enforcing strict security compliance checks...");

            if (seedDemoUsers) {
                throw new IllegalStateException(
                        "FATAL SECURITY CONFIGURATION: 'app.seed-demo-users' must be disabled in production profile!");
            }

            if (allowDefaultCredentials) {
                throw new IllegalStateException(
                        "FATAL SECURITY CONFIGURATION: 'app.allow-default-credentials' must be false in production profile!");
            }

            if ("Admin@123".equals(adminPassword) || "Pharm@123".equals(pharmacistPassword)) {
                throw new IllegalStateException(
                        "FATAL SECURITY CONFIGURATION: Default demo passwords ('Admin@123', 'Pharm@123') are strictly prohibited in production!");
            }

            // Ensure an already-seeded demo account cannot remain usable in production with default credentials
            userRepository.findByUsername(adminUsername).ifPresent(user -> {
                if (passwordEncoder.matches("Admin@123", user.getPassword())) {
                    user.setActive(false);
                    userRepository.save(user);
                    log.error("SECURITY ALERT: Deactivated demo admin account with default password in production!");
                }
            });

            userRepository.findByUsername(pharmacistUsername).ifPresent(user -> {
                if (passwordEncoder.matches("Pharm@123", user.getPassword())) {
                    user.setActive(false);
                    userRepository.save(user);
                    log.error("SECURITY ALERT: Deactivated demo pharmacist account with default password in production!");
                }
            });
        } else {
            if ("Admin@123".equals(adminPassword)) {
                log.warn("⚠️  SECURITY WARNING: Using default admin password! Set APP_ADMIN_PASSWORD in environment before deploying to production.");
            }
            if ("Pharm@123".equals(pharmacistPassword)) {
                log.warn("⚠️  SECURITY WARNING: Using default pharmacist password! Set APP_PHARMACIST_PASSWORD in environment before deploying to production.");
            }
        }
    }

    private void initDefaultUsers() {
        if (!userRepository.existsByUsername(adminUsername)) {
            log.info("Creating initial ADMIN user: '{}'", adminUsername);
            userRepository.save(User.builder()
                    .username(adminUsername)
                    .password(passwordEncoder.encode(adminPassword))
                    .fullName("System Administrator")
                    .role(Role.ROLE_ADMIN)
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        if (!userRepository.existsByUsername(pharmacistUsername)) {
            log.info("Creating initial PHARMACIST user: '{}'", pharmacistUsername);
            userRepository.save(User.builder()
                    .username(pharmacistUsername)
                    .password(passwordEncoder.encode(pharmacistPassword))
                    .fullName("Lead Pharmacist")
                    .role(Role.ROLE_PHARMACIST)
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }
}
