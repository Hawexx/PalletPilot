package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.ChargingStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChargingStationRepository extends JpaRepository<ChargingStation, UUID> {
    List<ChargingStation> findByIsOccupiedFalse();
}
