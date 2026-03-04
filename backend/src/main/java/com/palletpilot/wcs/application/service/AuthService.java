package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public boolean authenticate(String username, String password) {
        log.info("Attempting authentication for user: {}", username);

        return userRepository.findByUsername(username)
                .map(user -> {
                    boolean matches = passwordEncoder.matches(password, user.getPasswordHash());
                    if (matches) {
                        log.info("Authentication successful for {}", username);
                    } else {
                        log.warn("Authentication failed for {}", username);
                    }
                    return matches;
                })
                .orElseGet(() -> {
                    log.warn("User not found: {}", username);
                    return false;
                });
    }
}
