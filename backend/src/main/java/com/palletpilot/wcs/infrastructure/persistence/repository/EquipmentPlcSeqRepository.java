package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.EquipmentPlcSeq;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EquipmentPlcSeqRepository extends JpaRepository<EquipmentPlcSeq, UUID> {

    /**
     * Find and lock the SeqId row for a specific equipment.
     * Uses PESSIMISTIC_WRITE (SELECT ... FOR UPDATE) to prevent
     * concurrent SeqId generation for the same equipment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM EquipmentPlcSeq s WHERE s.equipmentId = :equipmentId")
    Optional<EquipmentPlcSeq> findByIdForUpdate(UUID equipmentId);
}
