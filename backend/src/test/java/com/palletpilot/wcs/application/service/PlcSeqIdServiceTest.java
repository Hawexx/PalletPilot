package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.EquipmentPlcSeq;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentPlcSeqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for PlcSeqIdService.
 * Verifies:
 * - Monotonically increasing SeqIds
 * - First-time creation (no existing row)
 * - Incremental updates for existing equipment
 * - Simulated restart safety (new IDs > old IDs)
 */
@ExtendWith(MockitoExtension.class)
class PlcSeqIdServiceTest {

    @Mock
    private EquipmentPlcSeqRepository seqRepository;

    @InjectMocks
    private PlcSeqIdService plcSeqIdService;

    private final UUID equipmentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Default: save returns the argument
        when(seqRepository.save(any(EquipmentPlcSeq.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void firstCallCreatesNewRowWithEpochMillis() {
        // No existing row
        when(seqRepository.findByIdForUpdate(equipmentId)).thenReturn(Optional.empty());

        long seqId = plcSeqIdService.nextSeqId(equipmentId);

        assertThat(seqId).isGreaterThan(0);
        // Should be at least epochMillis (approximately current time)
        assertThat(seqId).isGreaterThanOrEqualTo(System.currentTimeMillis() - 1000);

        // Verify a new entity was saved
        ArgumentCaptor<EquipmentPlcSeq> captor = ArgumentCaptor.forClass(EquipmentPlcSeq.class);
        verify(seqRepository).save(captor.capture());
        EquipmentPlcSeq saved = captor.getValue();
        assertThat(saved.getEquipmentId()).isEqualTo(equipmentId);
        assertThat(saved.getLastSeqId()).isEqualTo(seqId);
    }

    @Test
    void subsequentCallsIncrement() {
        long existingSeq = System.currentTimeMillis();
        EquipmentPlcSeq existing = EquipmentPlcSeq.builder()
                .equipmentId(equipmentId)
                .lastSeqId(existingSeq)
                .build();

        when(seqRepository.findByIdForUpdate(equipmentId)).thenReturn(Optional.of(existing));

        long seqId = plcSeqIdService.nextSeqId(equipmentId);

        // Must be strictly greater than existing
        assertThat(seqId).isGreaterThan(existingSeq);

        // Verify the existing entity was updated
        assertThat(existing.getLastSeqId()).isEqualTo(seqId);
    }

    @Test
    void multipleCallsAreMonotonic() {
        // Simulate multiple calls by tracking the "current" last_seq_id
        long[] currentSeq = { System.currentTimeMillis() };
        EquipmentPlcSeq entity = EquipmentPlcSeq.builder()
                .equipmentId(equipmentId)
                .lastSeqId(currentSeq[0])
                .build();

        when(seqRepository.findByIdForUpdate(equipmentId)).thenReturn(Optional.of(entity));

        long prev = 0;
        for (int i = 0; i < 10; i++) {
            long next = plcSeqIdService.nextSeqId(equipmentId);
            assertThat(next).isGreaterThan(prev);
            prev = next;
        }
    }

    @Test
    void afterRestartSeqIdNeverReusedOldValues() {
        // Simulate: previous run left lastSeqId = 100 (very old value)
        EquipmentPlcSeq old = EquipmentPlcSeq.builder()
                .equipmentId(equipmentId)
                .lastSeqId(100L)
                .build();

        when(seqRepository.findByIdForUpdate(equipmentId)).thenReturn(Optional.of(old));

        long seqId = plcSeqIdService.nextSeqId(equipmentId);

        // After restart, seqId should jump to epochMillis (much greater than 100)
        assertThat(seqId).isGreaterThanOrEqualTo(System.currentTimeMillis() - 1000);
        assertThat(seqId).isGreaterThan(100L);
    }

    @Test
    void differentEquipmentIdsAreIndependent() {
        UUID eq1 = UUID.randomUUID();
        UUID eq2 = UUID.randomUUID();

        when(seqRepository.findByIdForUpdate(eq1)).thenReturn(Optional.empty());
        when(seqRepository.findByIdForUpdate(eq2)).thenReturn(Optional.empty());

        long seq1 = plcSeqIdService.nextSeqId(eq1);
        long seq2 = plcSeqIdService.nextSeqId(eq2);

        // Both should be valid (>0) and independent
        assertThat(seq1).isGreaterThan(0);
        assertThat(seq2).isGreaterThan(0);

        // Saved with correct equipment IDs
        verify(seqRepository, times(2)).save(any(EquipmentPlcSeq.class));
    }
}
