package com.palletpilot.wcs.infrastructure.persistence;

import com.palletpilot.wcs.domain.model.User;
import com.palletpilot.wcs.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserSeeder — admin user creation and update.
 */
@ExtendWith(MockitoExtension.class)
class UserSeederTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private UserSeeder seeder;

    @Test
    @DisplayName("run: no admin user → creates new admin")
    void run_noAdmin_createsNew() {
        when(passwordEncoder.encode("12345678")).thenReturn("$2a$10$encodedHash");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        seeder.run();

        verify(userRepository).save(argThat(user -> "admin".equals(user.getUsername()) &&
                "ADMIN".equals(user.getRole()) &&
                "$2a$10$encodedHash".equals(user.getPasswordHash())));
    }

    @Test
    @DisplayName("run: admin exists → updates password hash")
    void run_adminExists_updatesPassword() {
        User existing = User.builder()
                .id(UUID.randomUUID()).username("admin")
                .passwordHash("$2a$10$oldHash").role("ADMIN")
                .build();
        when(passwordEncoder.encode("12345678")).thenReturn("$2a$10$newHash");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existing));

        seeder.run();

        verify(userRepository).save(argThat(user -> "$2a$10$newHash".equals(user.getPasswordHash())));
    }

    @Test
    @DisplayName("run: always encodes password with BCrypt")
    void run_alwaysEncodesPassword() {
        when(passwordEncoder.encode("12345678")).thenReturn("$2a$10$encoded");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());

        seeder.run();

        verify(passwordEncoder).encode("12345678");
    }
}
