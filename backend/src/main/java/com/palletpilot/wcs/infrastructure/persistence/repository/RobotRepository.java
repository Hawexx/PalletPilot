package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.Robot;
import com.palletpilot.wcs.domain.model.enums.RobotState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RobotRepository extends JpaRepository<Robot, UUID> {
    List<Robot> findByState(RobotState state);
}
