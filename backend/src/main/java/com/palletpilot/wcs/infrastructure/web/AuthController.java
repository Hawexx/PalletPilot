package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Authentication")
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate with username and password")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        boolean success = authService.authenticate(request.username(), request.password());

        if (success) {
            return ResponseEntity.ok(new LoginResponse(true, "Login successful"));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(false, "Invalid credentials"));
        }
    }

    // DTOs
    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(boolean success, String message) {
    }
}
