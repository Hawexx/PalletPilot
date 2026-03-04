package com.palletpilot.wcs.infrastructure.wms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.palletpilot.wcs.application.service.ConfigurationService;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
@Slf4j
public class WmsClient {

    private final RestClient.Builder restClientBuilder;
    private final ConfigurationService configService;

    /**
     * Queries the WMS for the location of a pallet containing the specified SKU.
     * 
     * @param sku The SKU to search for.
     * @return The location string (e.g., "A-1-2") or null if not found.
     */
    public String getPalletLocation(String sku) {
        String wmsUrl = configService.getString("app.wms.url", "http://localhost:8081/api");
        try {
            log.info("Querying WMS (url={}) for location of SKU: {}", wmsUrl, sku);
            return restClientBuilder.baseUrl(wmsUrl)
                    .build()
                    .get()
                    .uri("/inventory/" + sku + "/location")
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("Failed to query WMS for SKU {}: {}", sku, e.getMessage());
            throw new RuntimeException("WMS Lookup Failed", e);
        }
    }
}
