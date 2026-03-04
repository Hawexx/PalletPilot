package com.palletpilot.wcs.infrastructure.wms;

import com.palletpilot.wcs.application.service.ConfigurationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WmsClient — WMS REST integration.
 * Tests: constructor injection, getPalletLocation error handling.
 */
@ExtendWith(MockitoExtension.class)
class WmsClientTest {

    @Mock
    private RestClient.Builder restClientBuilder;
    @Mock
    private ConfigurationService configService;

    @Test
    @DisplayName("getPalletLocation: RestClient error → wraps in RuntimeException")
    void getPalletLocation_error_wrapsException() {
        when(configService.getString("app.wms.url", "http://localhost:8081/api"))
                .thenReturn("http://wms:8081/api");
        when(restClientBuilder.baseUrl("http://wms:8081/api")).thenThrow(
                new RuntimeException("Connection refused"));

        WmsClient client = new WmsClient(restClientBuilder, configService);

        assertThatThrownBy(() -> client.getPalletLocation("SKU-001"))
                .isInstanceOf(RuntimeException.class);
    }
}
