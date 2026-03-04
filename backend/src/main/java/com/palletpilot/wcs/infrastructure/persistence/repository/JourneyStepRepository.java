package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.JourneyStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JourneyStepRepository extends JpaRepository<JourneyStep, UUID> {

    List<JourneyStep> findByJourneyIdOrderByStepIndex(UUID journeyId);

    List<JourneyStep> findByJourneyIdAndStatus(UUID journeyId, String status);

    List<JourneyStep> findByCommandId(String commandId);
}
