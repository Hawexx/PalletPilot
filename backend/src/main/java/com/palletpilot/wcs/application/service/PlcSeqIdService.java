package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.EquipmentPlcSeq;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentPlcSeqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * DB-backed monotonic SeqId generator per equipment.
 * Guarantees SeqIds never repeat across JVM restarts.
 * Uses SELECT FOR UPDATE row locking for multi-pod safety.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlcSeqIdService {

    private final EquipmentPlcSeqRepository seqRepository;

    /**
     * Generate the next monotonically increasing SeqId for a given equipment.
     * <p>
     * Algorithm:
     * 1. Acquire row lock (SELECT FOR UPDATE)
     * 2. If no row → insert with initial seq = max(currentEpochMillis, 1)
     * 3. Else seq = max(last_seq_id + 1, currentEpochMillis)
     * (Using epochMillis as floor prevents collisions after long downtime)
     * 4. Persist updated value
     * </p>
     *
     * @param equipmentId the target equipment UUID
     * @return next SeqId guaranteed to be > all previous values for this equipment
     */
    @Transactional
    public long nextSeqId(UUID equipmentId) {
        var existing = seqRepository.findByIdForUpdate(equipmentId);

        long epochMillis = System.currentTimeMillis();
        long nextSeq;

        if (existing.isPresent()) {
            EquipmentPlcSeq seq = existing.get();
            nextSeq = Math.max(seq.getLastSeqId() + 1, epochMillis);
            seq.setLastSeqId(nextSeq);
            seqRepository.save(seq);
        } else {
            nextSeq = Math.max(epochMillis, 1L);
            EquipmentPlcSeq seq = EquipmentPlcSeq.builder()
                    .equipmentId(equipmentId)
                    .lastSeqId(nextSeq)
                    .build();
            seqRepository.save(seq);
        }

        log.debug("[PLC-SEQ] equipmentId={} nextSeqId={}", equipmentId, nextSeq);
        return nextSeq;
    }
}
