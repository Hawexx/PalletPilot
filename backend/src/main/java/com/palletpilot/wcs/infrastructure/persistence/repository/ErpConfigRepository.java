package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.ErpConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ErpConfigRepository extends JpaRepository<ErpConfig, UUID> {

    Optional<ErpConfig> findByEnabledTrueAndIntegrationMode(String integrationMode);

    Optional<ErpConfig> findByWarehouseCodeAndEnabledTrue(String warehouseCode);

    Optional<ErpConfig> findFirstByEnabledTrue();
}
