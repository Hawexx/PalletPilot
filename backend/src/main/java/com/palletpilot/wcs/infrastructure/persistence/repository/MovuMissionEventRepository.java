package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.MovuMissionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MovuMissionEventRepository extends JpaRepository<MovuMissionEvent, UUID> {

    /** Idempotency check: does an event with this notificationId already exist? */
    boolean existsByNotificationId(String notificationId);

    /**
     * Mission timeline: all events for a given mission, ordered chronologically.
     */
    List<MovuMissionEvent> findByMissionIdOrderByEventTimestampAsc(String missionId);

    /** Find events by type for a mission (e.g. find all MissionError events). */
    List<MovuMissionEvent> findByMissionIdAndNotificationType(String missionId, String notificationType);
}
