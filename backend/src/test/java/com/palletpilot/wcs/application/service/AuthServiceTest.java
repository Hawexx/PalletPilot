package com.palletpilot.wcs.application.service;

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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService — username/password authentication.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService service;

    @Test
    @DisplayName("authenticate: valid credentials → true")
    void authenticate_validCredentials_returnsTrue() {
        User user = User.builder().username("admin").passwordHash("$2a$10$hashed").build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "$2a$10$hashed")).thenReturn(true);

        assertThat(service.authenticate("admin", "secret")).isTrue();
    }

    @Test
    @DisplayName("authenticate: wrong password → false")
    void authenticate_wrongPassword_returnsFalse() {
        User user = User.builder().username("admin").passwordHash("$2a$10$hashed").build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "$2a$10$hashed")).thenReturn(false);

        assertThat(service.authenticate("admin", "wrong")).isFalse();
    }

    @Test
    @DisplayName("authenticate: user not found → false")
    void authenticate_userNotFound_returnsFalse() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThat(service.authenticate("ghost", "any")).isFalse();
    }
}
