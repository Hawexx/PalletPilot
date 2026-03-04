package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.SystemConfig;
import com.palletpilot.wcs.infrastructure.persistence.repository.SystemConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConfigurationService — in-memory cache + DB config.
 */
@ExtendWith(MockitoExtension.class)
class ConfigurationServiceTest {

    @Mock
    private SystemConfigRepository repository;

    @InjectMocks
    private ConfigurationService service;

    @Test
    @DisplayName("getString: cached value → returns from cache")
    void getString_cached_returnsFromCache() {
        // Simulate loading cache
        SystemConfig cfg = SystemConfig.builder().key("app.mode").value("PRODUCTION").build();
        when(repository.findAll()).thenReturn(List.of(cfg));
        service.loadCache();

        String result = service.getString("app.mode", "DEV");

        assertThat(result).isEqualTo("PRODUCTION");
        verify(repository, never()).findById(any());
    }

    @Test
    @DisplayName("getString: not cached → falls back to DB")
    void getString_notCached_fallsToDb() {
        when(repository.findAll()).thenReturn(List.of());
        service.loadCache();

        SystemConfig cfg = SystemConfig.builder().key("db.key").value("DB_VALUE").build();
        when(repository.findById("db.key")).thenReturn(Optional.of(cfg));

        String result = service.getString("db.key", "DEFAULT");

        assertThat(result).isEqualTo("DB_VALUE");
    }

    @Test
    @DisplayName("getString: not in cache or DB → returns default")
    void getString_notFound_returnsDefault() {
        when(repository.findAll()).thenReturn(List.of());
        service.loadCache();
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThat(service.getString("missing", "FALLBACK")).isEqualTo("FALLBACK");
    }

    @Test
    @DisplayName("getBoolean: parses 'true' correctly")
    void getBoolean_parsesTrue() {
        SystemConfig cfg = SystemConfig.builder().key("flag").value("true").build();
        when(repository.findAll()).thenReturn(List.of(cfg));
        service.loadCache();

        assertThat(service.getBoolean("flag", false)).isTrue();
    }

    @Test
    @DisplayName("getInt: parses integer correctly")
    void getInt_parsesInt() {
        SystemConfig cfg = SystemConfig.builder().key("count").value("42").build();
        when(repository.findAll()).thenReturn(List.of(cfg));
        service.loadCache();

        assertThat(service.getInt("count", 0)).isEqualTo(42);
    }

    @Test
    @DisplayName("getInt: invalid value → returns default")
    void getInt_invalidValue_returnsDefault() {
        SystemConfig cfg = SystemConfig.builder().key("bad").value("not-a-number").build();
        when(repository.findAll()).thenReturn(List.of(cfg));
        service.loadCache();

        assertThat(service.getInt("bad", 99)).isEqualTo(99);
    }

    @Test
    @DisplayName("set: existing key → updates value and cache")
    void set_existingKey_updatesValueAndCache() {
        SystemConfig existing = SystemConfig.builder().key("k").value("old").build();
        when(repository.findById("k")).thenReturn(Optional.of(existing));

        service.set("k", "new");

        verify(repository).save(argThat(c -> "new".equals(((SystemConfig) c).getValue())));
        assertThat(service.getString("k", "?")).isEqualTo("new");
    }

    @Test
    @DisplayName("set: new key → creates with CUSTOM category")
    void set_newKey_createsCustom() {
        when(repository.findById("new.key")).thenReturn(Optional.empty());

        service.set("new.key", "val");

        verify(repository).save(argThat(c -> "CUSTOM".equals(((SystemConfig) c).getCategory())
                && "val".equals(((SystemConfig) c).getValue())));
    }
}
