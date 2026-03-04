package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.MovuMission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface MovuMissionRepository extends JpaRepository<MovuMission, String> {

    /** Count missions NOT in given statuses (e.g. active = not COMPLETED/FAILED) */
    long countByStatusNotIn(Collection<String> statuses);

    /** Count missions with a specific status (e.g. FAILED for health check) */
    long countByStatus(String status);

    /** Find stale missions — active status AND not updated since threshold */
    @Query("SELECT m FROM MovuMission m WHERE m.status IN :statuses AND m.updatedAt < :threshold")
    List<MovuMission> findByStatusInAndUpdatedAtBefore(
            @Param("statuses") Collection<String> statuses,
            @Param("threshold") Instant threshold);
}
