package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.Pallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PalletRepository extends JpaRepository<Pallet, UUID> {
    void deleteByOrderId(UUID orderId);

    List<Pallet> findByOrderId(UUID orderId);
}
