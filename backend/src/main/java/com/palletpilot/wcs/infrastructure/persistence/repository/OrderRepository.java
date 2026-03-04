package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.Order;
import com.palletpilot.wcs.domain.model.enums.ProcessState;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Override
    @EntityGraph(attributePaths = "items")
    List<Order> findAll();

    @EntityGraph(attributePaths = "items")
    Optional<Order> findById(UUID id);

    Optional<Order> findByWmsOrderId(String wmsOrderId);

    boolean existsByWmsOrderId(String wmsOrderId);

    /**
     * Count orders NOT in given statuses (e.g. active = not
     * COMPLETED/FAILED/CANCELLED)
     */
    long countByStatusNotIn(Collection<ProcessState> statuses);
}
