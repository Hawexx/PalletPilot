package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.ConfigurationService;
import com.palletpilot.wcs.domain.model.SystemConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigControllerTest {

    @Mock
    private ConfigurationService configService;
    @InjectMocks
    private ConfigController controller;

    @Test
    @DisplayName("getAllConfigs → returns list")
    void getAllConfigs_returnsList() {
        SystemConfig cfg = SystemConfig.builder().key("k").value("v").build();
        when(configService.getAll()).thenReturn(List.of(cfg));

        var result = controller.getAllConfigs();

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("updateConfig: valid value → 200 + calls set")
    void updateConfig_validValue_200() {
        var resp = controller.updateConfig("app.mode", Map.of("value", "PROD"));

        verify(configService).set("app.mode", "PROD");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("updateConfig: missing value → 400")
    void updateConfig_missingValue_400() {
        var resp = controller.updateConfig("app.mode", Map.of("other", "x"));

        verify(configService, never()).set(any(), any());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
