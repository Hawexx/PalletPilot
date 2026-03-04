package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.Equipment;
import com.palletpilot.wcs.domain.model.enums.EquipmentState;
import com.palletpilot.wcs.domain.model.enums.EquipmentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {
    Optional<Equipment> findByName(String name);

    boolean existsByName(String name);

    /** Count equipment NOT in given statuses (e.g. online = not OFFLINE/ERROR) */
    long countByStatusNotIn(Collection<EquipmentState> statuses);

    /** Count equipment IN given statuses (e.g. error count) */
    long countByStatusIn(Collection<EquipmentState> statuses);

    /** Find first equipment by type — avoids loading all then filtering */
    Optional<Equipment> findFirstByType(EquipmentType type);
}
