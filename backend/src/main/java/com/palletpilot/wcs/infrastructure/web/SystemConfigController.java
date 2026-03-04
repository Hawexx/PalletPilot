package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.domain.model.HardwareSettings;
import com.palletpilot.wcs.infrastructure.persistence.repository.HardwareSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@Tag(name = "System Config (Legacy)")
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Slf4j
public class SystemConfigController {

        private final Environment environment;
        private final HardwareSettingsRepository hardwareSettingsRepository;
        private final com.palletpilot.wcs.application.service.OrderService orderService;

        @GetMapping("/all")
        public ResponseEntity<Map<String, Object>> getAllConfig() {
                // Return composite, but use DB for hardware
                Map<String, Object> config = new HashMap<>();
                config.put("hardware", getHardwareConfig().getBody());
                config.put("simulationEnabled",
                                Boolean.parseBoolean(environment.getProperty("app.simulation.enabled", "true")));
                config.put("autoPlanning", orderService.isAutoPlanningEnabled());
                return ResponseEntity.ok(config);
        }

        @GetMapping("/auto-planning")
        public ResponseEntity<Boolean> getAutoPlanning() {
                return ResponseEntity.ok(orderService.isAutoPlanningEnabled());
        }

        @PutMapping("/auto-planning")
        public ResponseEntity<Void> setAutoPlanning(@RequestBody Map<String, Boolean> payload) {
                if (payload.containsKey("enabled")) {
                        orderService.setAutoPlanningEnabled(payload.get("enabled"));
                }
                return ResponseEntity.ok().build();
        }

        @GetMapping("/hardware")
        public ResponseEntity<Map<String, HardwareConfig>> getHardwareConfig() {
                Map<String, HardwareConfig> configMap = new HashMap<>();

                // Helper to get or default
                configMap.put("movu", toDto(getOrInit("movu",
                                "http://localhost:9000", null, null, "key", "5000")));
                configMap.put("kuka", toDto(getOrInit("kuka",
                                null, "192.168.1.10", 30002, null, "3000")));
                configMap.put("beckhoff", toDto(getOrInit("beckhoff",
                                null, "192.168.1.20", 851, null, "2000")));

                return ResponseEntity.ok(configMap);
        }

        private HardwareSettings getOrInit(String key, String url, String ip, Integer port, String apiKey,
                        String timeout) {
                return hardwareSettingsRepository.findById(key).orElseGet(() -> {
                        HardwareSettings s = HardwareSettings.builder()
                                        .configKey(key)
                                        .baseUrl(url)
                                        .ipAddress(ip)
                                        .port(port)
                                        .apiKey(apiKey)
                                        .timeoutMs(timeout)
                                        .build();
                        return hardwareSettingsRepository.save(s);
                });
        }

        private HardwareConfig toDto(HardwareSettings s) {
                return new HardwareConfig(s.getConfigKey(), s.getBaseUrl(), s.getIpAddress(), s.getPort(),
                                s.getApiKey(), s.getTimeoutMs());
        }

        @PutMapping("/hardware/{type}")
        public ResponseEntity<String> updateHardwareConfig(
                        @PathVariable String type,
                        @RequestBody HardwareConfigUpdate update) {
                log.info("Updating hardware config for: {}", type);

                HardwareSettings s = hardwareSettingsRepository.findById(type)
                                .orElse(HardwareSettings.builder().configKey(type).build());

                if (update.baseUrl() != null)
                        s.setBaseUrl(update.baseUrl());
                if (update.ipAddress() != null)
                        s.setIpAddress(update.ipAddress());
                if (update.port() != null)
                        s.setPort(update.port());
                if (update.apiKey() != null)
                        s.setApiKey(update.apiKey());
                if (update.timeout() != null)
                        s.setTimeoutMs(update.timeout());

                hardwareSettingsRepository.save(s);
                return ResponseEntity.ok("Configuration saved.");
        }

        // ... (Keep other methods or allow them to be environment based for now)
        @GetMapping("/simulation")
        public ResponseEntity<SimulationConfig> getSimulationMode() {
                // Check DB for override, else fallback to env
                Optional<HardwareSettings> simSetting = hardwareSettingsRepository.findById("SIMULATION");
                boolean enabled;
                if (simSetting.isPresent() && simSetting.get().getApiKey() != null) {
                        enabled = Boolean.parseBoolean(simSetting.get().getApiKey());
                } else {
                        enabled = Boolean.parseBoolean(environment.getProperty("app.simulation.enabled", "true"));
                }
                return ResponseEntity.ok(new SimulationConfig(enabled));
        }

        @PutMapping("/simulation")
        public ResponseEntity<Void> setSimulationMode(@RequestBody SimulationConfig config) {
                HardwareSettings s = hardwareSettingsRepository.findById("SIMULATION")
                                .orElse(HardwareSettings.builder().configKey("SIMULATION").build());

                // Store in apiKey field as it is a String
                s.setApiKey(String.valueOf(config.enabled()));
                hardwareSettingsRepository.save(s);
                return ResponseEntity.ok().build();
        }

        // DTOs
        public record HardwareConfig(String type, String baseUrl, String ipAddress, Integer port, String apiKey,
                        String timeout) {
        }

        public record HardwareConfigUpdate(String baseUrl, String ipAddress, Integer port, String apiKey,
                        String timeout) {
        }

        public record SimulationConfig(boolean enabled) {
        }
}
