package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.MovuLoadPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovuLoadPositionRepository extends JpaRepository<MovuLoadPosition, String> {

    /** Find all load positions in a specific lane (for slot calculation). */
    List<MovuLoadPosition> findByLevelAndLaneAndSection(int level, int lane, int section);
}
