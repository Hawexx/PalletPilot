package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.OrderService;
import com.palletpilot.wcs.domain.model.Pallet;
import com.palletpilot.wcs.domain.model.enums.ProcessState;
import com.palletpilot.wcs.domain.service.PalletBuildingService;
import com.palletpilot.wcs.infrastructure.persistence.repository.PalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PalletPlanningControllerTest {

    @Mock
    private OrderService orderService;
    @Mock
    private PalletBuildingService palletBuildingService;
    @Mock
    private PalletRepository palletRepository;
    @InjectMocks
    private PalletPlanningController controller;

    @Test
    @DisplayName("getPallet: found → 200")
    void getPallet_found_200() {
        UUID id = UUID.randomUUID();
        Pallet p = Pallet.builder().id(id).build();
        when(palletRepository.findById(id)).thenReturn(Optional.of(p));

        var resp = controller.getPallet(id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getPallet: not found → 404")
    void getPallet_notFound_404() {
        UUID id = UUID.randomUUID();
        when(palletRepository.findById(id)).thenReturn(Optional.empty());

        var resp = controller.getPallet(id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("deletePallet: exists → 204")
    void deletePallet_exists_204() {
        UUID id = UUID.randomUUID();
        when(palletRepository.existsById(id)).thenReturn(true);

        var resp = controller.deletePallet(id);

        verify(palletRepository).deleteById(id);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("deletePallet: not exists → 404")
    void deletePallet_notExists_404() {
        UUID id = UUID.randomUUID();
        when(palletRepository.existsById(id)).thenReturn(false);

        var resp = controller.deletePallet(id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("updatePalletState: found → 200")
    void updatePalletState_found_200() {
        UUID id = UUID.randomUUID();
        Pallet p = Pallet.builder().id(id).processState(ProcessState.CREATED).build();
        when(palletRepository.findById(id)).thenReturn(Optional.of(p));
        when(palletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resp = controller.updatePalletState(id,
                new PalletPlanningController.StateUpdateRequest(ProcessState.PALLETIZED));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("updatePalletState: not found → 404")
    void updatePalletState_notFound_404() {
        UUID id = UUID.randomUUID();
        when(palletRepository.findById(id)).thenReturn(Optional.empty());

        var resp = controller.updatePalletState(id,
                new PalletPlanningController.StateUpdateRequest(ProcessState.PALLETIZED));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
