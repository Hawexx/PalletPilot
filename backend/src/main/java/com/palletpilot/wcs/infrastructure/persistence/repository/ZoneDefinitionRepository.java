package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.ZoneDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ZoneDefinitionRepository extends JpaRepository<ZoneDefinition, UUID> {

    /** All active rules ordered by priority (lowest first = highest priority) */
    List<ZoneDefinition> findByActiveTrueOrderByPriorityAsc();

    /** All rules for a specific zone class */
    List<ZoneDefinition> findByZoneClassOrderByPriorityAsc(String zoneClass);
}
