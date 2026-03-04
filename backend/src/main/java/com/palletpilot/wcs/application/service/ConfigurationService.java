package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.SystemConfig;
import com.palletpilot.wcs.infrastructure.persistence.repository.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigurationService {

    private final SystemConfigRepository repository;

    // Simple in-memory cache to reduce DB hits for high-frequency reads
    private final Map<String, String> cache = new HashMap<>();

    @PostConstruct
    public void init() {
        seedDefaults();
        loadCache();
    }

    private void seedDefaults() {
        seed("app.simulation.enabled", "false", "SYSTEM", "Enable simulation mode");

        seed("hardware.beckhoff.local-ams-net-id", "192.168.1.10.1.1", "HARDWARE", "Local AMS Net ID for ADS");

        seed("app.wms.url", "http://localhost:8081/api", "WMS", "WMS Base URL");
        seed("app.wms.enabled", "false", "WMS", "Enable WMS Integration");

        // Add other defaults here as we migrate them
    }

    private void seed(String key, String defaultValue, String category, String description) {
        if (!repository.existsById(key)) {
            repository.save(SystemConfig.builder()
                    .key(key)
                    .value(defaultValue)
                    .category(category)
                    .description(description)
                    .build());
        }
    }

    public void loadCache() {
        repository.findAll().forEach(cfg -> cache.put(cfg.getKey(), cfg.getValue()));
        log.info("Loaded {} configuration keys", cache.size());
    }

    public String getString(String key, String defaultValue) {
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        return repository.findById(key).map(SystemConfig::getValue).orElse(defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String val = getString(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(val);
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(getString(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Transactional
    public void set(String key, String value) {
        Optional<SystemConfig> existing = repository.findById(key);
        if (existing.isPresent()) {
            SystemConfig cfg = existing.get();
            cfg.setValue(value);
            repository.save(cfg);
        } else {
            repository.save(SystemConfig.builder()
                    .key(key)
                    .value(value)
                    .category("CUSTOM")
                    .build());
        }
        cache.put(key, value);
    }

    public List<SystemConfig> getAll() {
        return repository.findAll();
    }
}
