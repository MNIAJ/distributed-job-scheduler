package com.placements.job_scheduler.config;

import com.placements.job_scheduler.entity.User;
import com.placements.job_scheduler.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log =
            LoggerFactory.getLogger(DataInitializer.class);

    private static final String ADMIN_EMAIL = "admin@jobscheduler.com";
    private static final String ADMIN_PASSWORD = "Admin@123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {

        // Check for the SAME email we are about to create
        if (userRepository.findByEmail(ADMIN_EMAIL).isEmpty()) {

            User admin = User.builder()
                    .name("Admin")
                    .email(ADMIN_EMAIL)
                    .password(passwordEncoder.encode(ADMIN_PASSWORD))
                    .build();

            userRepository.save(admin);

            log.info("Default admin user created: {}", ADMIN_EMAIL);
        }
    }
}