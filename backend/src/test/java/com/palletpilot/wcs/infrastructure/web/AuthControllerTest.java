package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @InjectMocks
    private AuthController controller;

    @Test
    @DisplayName("login: valid credentials → 200 + success")
    void login_validCredentials_returns200() {
        when(authService.authenticate("admin", "pass")).thenReturn(true);

        var resp = controller.login(new AuthController.LoginRequest("admin", "pass"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().success()).isTrue();
    }

    @Test
    @DisplayName("login: invalid credentials → 401")
    void login_invalidCredentials_returns401() {
        when(authService.authenticate("admin", "wrong")).thenReturn(false);

        var resp = controller.login(new AuthController.LoginRequest("admin", "wrong"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().success()).isFalse();
    }
}
