package com.palletpilot.wcs.infrastructure.movu.security;

import com.palletpilot.wcs.domain.model.Equipment;
import com.palletpilot.wcs.domain.model.enums.EquipmentType;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MovuWebhookTokenService — token-to-equipment resolution cache.
 * Tests: rebuild with MOVU equipment, skip non-MOVU, null/blank/invalid
 * configJson,
 * resolveEquipmentId (valid/invalid/null), hasConfiguredTokens.
 */
@ExtendWith(MockitoExtension.class)
class MovuWebhookTokenServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;
    private MovuWebhookTokenService service;

    @BeforeEach
    void setup() {
        service = new MovuWebhookTokenService(equipmentRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("rebuildTokenCache: MOVU equipment with token → resolves correctly")
    void rebuild_movuWithToken_resolves() {
        UUID eqId = UUID.randomUUID();
        Equipment eq = Equipment.builder()
                .id(eqId).name("aSTC-1").type(EquipmentType.MOVU)
                .configJson("{\"movuWebhookToken\":\"secret-123\"}")
                .build();
        when(equipmentRepository.findAll()).thenReturn(List.of(eq));

        service.rebuildTokenCache();

        assertThat(service.resolveEquipmentId("secret-123")).isPresent().contains(eqId);
        assertThat(service.hasConfiguredTokens()).isTrue();
    }

    @Test
    @DisplayName("rebuildTokenCache: non-MOVU equipment → skipped")
    void rebuild_nonMovu_skipped() {
        Equipment eq = Equipment.builder()
                .id(UUID.randomUUID()).name("KUKA-1").type(EquipmentType.KUKA)
                .configJson("{\"movuWebhookToken\":\"should-ignore\"}")
                .build();
        when(equipmentRepository.findAll()).thenReturn(List.of(eq));

        service.rebuildTokenCache();

        assertThat(service.hasConfiguredTokens()).isFalse();
    }

    @Test
    @DisplayName("rebuildTokenCache: null configJson → skipped gracefully")
    void rebuild_nullConfig_skipped() {
        Equipment eq = Equipment.builder()
                .id(UUID.randomUUID()).name("aSTC-2").type(EquipmentType.MOVU)
                .configJson(null)
                .build();
        when(equipmentRepository.findAll()).thenReturn(List.of(eq));

        service.rebuildTokenCache();

        assertThat(service.hasConfiguredTokens()).isFalse();
    }

    @Test
    @DisplayName("rebuildTokenCache: blank configJson → skipped gracefully")
    void rebuild_blankConfig_skipped() {
        Equipment eq = Equipment.builder()
                .id(UUID.randomUUID()).name("aSTC-3").type(EquipmentType.MOVU)
                .configJson("   ")
                .build();
        when(equipmentRepository.findAll()).thenReturn(List.of(eq));

        service.rebuildTokenCache();

        assertThat(service.hasConfiguredTokens()).isFalse();
    }

    @Test
    @DisplayName("rebuildTokenCache: invalid JSON → skipped with warning (no exception)")
    void rebuild_invalidJson_skipped() {
        Equipment eq = Equipment.builder()
                .id(UUID.randomUUID()).name("aSTC-4").type(EquipmentType.MOVU)
                .configJson("NOT_VALID_JSON{{{")
                .build();
        when(equipmentRepository.findAll()).thenReturn(List.of(eq));

        // Should NOT throw
        assertThatCode(() -> service.rebuildTokenCache()).doesNotThrowAnyException();
        assertThat(service.hasConfiguredTokens()).isFalse();
    }

    @Test
    @DisplayName("resolveEquipmentId: null token → empty")
    void resolve_nullToken_empty() {
        assertThat(service.resolveEquipmentId(null)).isEmpty();
    }

    @Test
    @DisplayName("resolveEquipmentId: blank token → empty")
    void resolve_blankToken_empty() {
        assertThat(service.resolveEquipmentId("")).isEmpty();
        assertThat(service.resolveEquipmentId("   ")).isEmpty();
    }

    @Test
    @DisplayName("resolveEquipmentId: unknown token → empty")
    void resolve_unknownToken_empty() {
        assertThat(service.resolveEquipmentId("no-such-token")).isEmpty();
    }

    @Test
    @DisplayName("hasConfiguredTokens: no equipment → false")
    void hasTokens_noEquipment_false() {
        when(equipmentRepository.findAll()).thenReturn(List.of());
        service.rebuildTokenCache();

        assertThat(service.hasConfiguredTokens()).isFalse();
    }
}
