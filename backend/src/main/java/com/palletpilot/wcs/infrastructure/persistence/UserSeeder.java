package com.palletpilot.wcs.infrastructure.persistence;

import com.palletpilot.wcs.domain.model.User;
import com.palletpilot.wcs.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        log.info("Ensuring admin user exists and has correct password...");

        String encodedPassword = passwordEncoder.encode("12345678");

        User admin = userRepository.findByUsername("admin")
                .map(existing -> {
                    existing.setPasswordHash(encodedPassword);
                    return existing;
                })
                .orElse(User.builder()
                        .id(UUID.randomUUID())
                        .username("admin")
                        .passwordHash(encodedPassword)
                        .role("ADMIN")
                        .build());

        userRepository.save(admin);
        log.info("Admin user updated/created with valid BCrypt hash.");
    }
}
