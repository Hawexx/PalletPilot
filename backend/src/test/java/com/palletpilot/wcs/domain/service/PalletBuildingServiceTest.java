package com.palletpilot.wcs.domain.service;

import com.palletpilot.wcs.domain.model.Pallet;
import com.palletpilot.wcs.domain.model.valueobjects.BoxDimension;
import com.palletpilot.wcs.domain.model.valueobjects.PackingItem;
import com.palletpilot.wcs.infrastructure.license.LicenseGuard;
import com.palletpilot.wcs.infrastructure.license.LicenseVault;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit tests for {@link PalletBuildingService}.
 * Tests the palletization engine with WCS constraints (EPAL3, 4 layers, 10
 * boxes/layer).
 *
 * Uses mockStatic for LicenseVault.verify() since it's in an external JAR.
 */
class PalletBuildingServiceTest {

    private final PalletBuildingService service = new PalletBuildingService();

    @BeforeAll
    static void initLicense() {
        LicenseGuard.earlyInstall();
    }

    @Test
    @DisplayName("planPallet — should create pallet with single box")
    void testPlanPallet_singleBox() {
        try (MockedStatic<LicenseVault> vault = mockStatic(LicenseVault.class)) {
            vault.when(LicenseVault::verify).thenReturn(true);

            var items = List.of(
                    new PackingItem("SKU-001", new BoxDimension(200, 300, 150), 5.0, 1));

            Pallet result = service.planPallet(UUID.randomUUID(), items);

            assertThat(result).isNotNull();
            assertThat(result.getTotalBoxCount()).isEqualTo(1);
            assertThat(result.getLayerCount()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("planPallet — should handle multiple boxes from same SKU")
    void testPlanPallet_multipleBoxesSameSku() {
        try (MockedStatic<LicenseVault> vault = mockStatic(LicenseVault.class)) {
            vault.when(LicenseVault::verify).thenReturn(true);

            var items = List.of(
                    new PackingItem("SKU-001", new BoxDimension(200, 300, 150), 5.0, 4));

            Pallet result = service.planPallet(UUID.randomUUID(), items);

            assertThat(result).isNotNull();
            assertThat(result.getTotalBoxCount()).isEqualTo(4);
        }
    }

    @Test
    @DisplayName("planPallet — should handle multiple SKUs")
    void testPlanPallet_multipleSKUs() {
        try (MockedStatic<LicenseVault> vault = mockStatic(LicenseVault.class)) {
            vault.when(LicenseVault::verify).thenReturn(true);

            var items = List.of(
                    new PackingItem("SKU-001", new BoxDimension(200, 300, 150), 5.0, 3),
                    new PackingItem("SKU-002", new BoxDimension(400, 300, 200), 8.0, 2));

            Pallet result = service.planPallet(UUID.randomUUID(), items);

            assertThat(result).isNotNull();
            assertThat(result.getTotalBoxCount()).isEqualTo(5);
        }
    }

    @Test
    @DisplayName("planPallet — should reject empty box list")
    void testPlanPallet_emptyList() {
        try (MockedStatic<LicenseVault> vault = mockStatic(LicenseVault.class)) {
            vault.when(LicenseVault::verify).thenReturn(true);

            assertThatThrownBy(() -> service.planPallet(UUID.randomUUID(), Collections.emptyList()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No boxes");
        }
    }

    @Test
    @DisplayName("planPallet — pallet type should be EPAL3")
    void testPlanPallet_palletType() {
        try (MockedStatic<LicenseVault> vault = mockStatic(LicenseVault.class)) {
            vault.when(LicenseVault::verify).thenReturn(true);

            var items = List.of(
                    new PackingItem("SKU-001", new BoxDimension(200, 300, 150), 5.0, 1));

            Pallet result = service.planPallet(UUID.randomUUID(), items);

            assertThat(result.getType()).isEqualTo("EPAL3");
        }
    }
}
