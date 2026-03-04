package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.ErpIntegrationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ErpIntegrationLogRepository extends JpaRepository<ErpIntegrationLog, UUID> {

    List<ErpIntegrationLog> findByErpOrderIdOrderByCreatedAtDesc(String erpOrderId);

    Page<ErpIntegrationLog> findByDirectionOrderByCreatedAtDesc(String direction, Pageable pageable);

    Page<ErpIntegrationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByDirectionAndStatus(String direction, String status);
}
