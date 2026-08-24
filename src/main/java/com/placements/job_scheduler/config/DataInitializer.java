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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {

        if (userRepository.findByEmail("admin@example.com").isEmpty()) {

            User admin = User.builder()
                    .name("Admin")
                    .email("admin@jobscheduler.com")
                    .password(passwordEncoder.encode("admin123"))
                    .build();

            userRepository.save(admin);

            log.info("Default Admin User Created");
            log.info("Email    : admin@jobscheduler.com");
            log.info("Password : Admin@123");
        }
    }
}