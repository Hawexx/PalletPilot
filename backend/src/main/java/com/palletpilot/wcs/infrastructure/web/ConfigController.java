package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.ConfigurationService;
import com.palletpilot.wcs.domain.model.SystemConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Tag(name = "System Configuration", description = "Manage system-wide dynamic settings")
@Slf4j
public class ConfigController {

    private final ConfigurationService configService;

    @GetMapping
    @Operation(summary = "Get All Configs")
    public List<SystemConfig> getAllConfigs() {
        return configService.getAll();
    }

    @PostMapping("/{key}")
    @Operation(summary = "Update Config")
    public ResponseEntity<Void> updateConfig(@PathVariable String key, @RequestBody Map<String, String> payload) {
        String value = payload.get("value");
        if (value == null) {
            return ResponseEntity.badRequest().build();
        }
        configService.set(key, value);
        return ResponseEntity.ok().build();
    }
}
