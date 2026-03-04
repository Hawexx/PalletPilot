package com.palletpilot.wcs.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger configuration for PalletPilot WCS.
 * 
 * Access the UI at: /swagger-ui.html
 * 
 * Features:
 * - API key header authentication (X-API-Key)
 * - Server environments (dev / prod)
 * - Contact and license info
 */
@Configuration
public class OpenApiConfig {

        @Bean
        public OpenAPI palletPilotOpenAPI() {
                final String apiKeyScheme = "X-API-Key";

                return new OpenAPI()
                                .info(new Info()
                                                .title("PalletPilot WCS API")
                                                .version("1.0.0")
                                                .description("""
                                                                Industrial-grade Warehouse Control System API.

                                                                **Modules:**
                                                                - 📦 Order Management — WMS ingestion, lifecycle tracking
                                                                - 🏗 Pallet Planning — Deterministic pallet building algorithm
                                                                - ⚙️ Equipment Control — KUKA, Beckhoff PLC, Movu aSTC
                                                                - 📊 ABC Slotting — Demand-based SKU zone optimization
                                                                - 🔗 ERP Integration — Webhook-based bidirectional messaging
                                                                - 📈 Monitoring — Prometheus metrics, audit logging
                                                                """)
                                                .contact(new Contact()
                                                                .name("FonTek Engineering")
                                                                .email("engineering@fontek.dev"))
                                                .license(new License()
                                                                .name("Proprietary")
                                                                .url("https://fontek.dev")))
                                .externalDocs(new ExternalDocumentation()
                                                .description("PalletPilot Documentation")
                                                .url("https://docs.palletpilot.com"))
                                .servers(List.of(
                                                new Server().url("http://localhost:8080")
                                                                .description("Local Development"),
                                                new Server().url("https://api.palletpilot.com")
                                                                .description("Production")))
                                .components(new Components()
                                                .addSecuritySchemes(apiKeyScheme, new SecurityScheme()
                                                                .type(SecurityScheme.Type.APIKEY)
                                                                .in(SecurityScheme.In.HEADER)
                                                                .name(apiKeyScheme)
                                                                .description("API key for service-to-service authentication")))
                                .addSecurityItem(new SecurityRequirement().addList(apiKeyScheme));
        }
}
