package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.MovuLaneReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface MovuLaneReservationRepository extends JpaRepository<MovuLaneReservation, UUID> {

    /** Find all active reservations for a lane (not expired). */
    List<MovuLaneReservation> findByEquipmentIdAndLevelAndLaneAndSectionAndExpiresAtAfter(
            UUID equipmentId, int level, int lane, int section, Instant now);

    /** Find reservations for a specific mission (for cleanup). */
    List<MovuLaneReservation> findByMissionId(String missionId);

    /** Delete all reservations for a mission (on completion/abort). */
    void deleteByMissionId(String missionId);

    /** Clean up expired reservations (safety net). */
    void deleteByExpiresAtBefore(Instant now);
}
