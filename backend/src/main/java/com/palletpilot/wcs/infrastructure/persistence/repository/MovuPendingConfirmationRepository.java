package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.MovuPendingConfirmation;
import com.palletpilot.wcs.domain.model.MovuPendingConfirmation.ConfirmationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MovuPendingConfirmationRepository extends JpaRepository<MovuPendingConfirmation, UUID> {

        Optional<MovuPendingConfirmation> findByMissionIdAndConfirmationType(
                        String missionId, MovuPendingConfirmation.ConfirmationType type);

        /**
         * Find pending/failed confirmations ready for retry with DB-level locking.
         * FOR UPDATE SKIP LOCKED ensures only one worker processes each row.
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM MovuPendingConfirmation p " +
                        "WHERE p.status IN :statuses " +
                        "AND (p.nextRetryAt IS NULL OR p.nextRetryAt <= :now) " +
                        "ORDER BY p.requestedAt ASC")
        List<MovuPendingConfirmation> findReadyForProcessing(List<ConfirmationStatus> statuses, Instant now);

        List<MovuPendingConfirmation> findByMissionId(String missionId);

        /** Find confirmations by status list (PENDING, FAILED, IN_PROGRESS) */
        List<MovuPendingConfirmation> findByStatusIn(Collection<ConfirmationStatus> statuses);
}
