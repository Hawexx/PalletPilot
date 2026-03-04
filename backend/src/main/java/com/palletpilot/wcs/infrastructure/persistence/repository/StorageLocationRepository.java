package com.palletpilot.wcs.infrastructure.persistence.repository;

import com.palletpilot.wcs.domain.model.StorageLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StorageLocationRepository extends JpaRepository<StorageLocation, UUID> {
    List<StorageLocation> findByZone(String zone);

    List<StorageLocation> findByIsOccupiedFalse();

    List<StorageLocation> findByZoneClass(String zoneClass);

    List<StorageLocation> findByIsOccupiedTrue();

    long countByZoneClassAndIsOccupied(String zoneClass, boolean occupied);

    long countByZoneClass(String zoneClass);

    /**
     * Concurrent-safe slot selection using row-level locking.
     * FOR UPDATE SKIP LOCKED ensures two parallel putaway calls never select the
     * same slot.
     * Ordering: lowest Z (fastest access) → front bay → shallow depth → nearest
     * aisle.
     */
    @Query(value = """
            SELECT * FROM storage_locations
            WHERE is_occupied = false
              AND zone_class = :zoneClass
              AND (:palletHeight IS NULL OR max_height_mm >= :palletHeight)
              AND (:palletWeight IS NULL OR max_weight_kg >= :palletWeight)
            ORDER BY z ASC, x ASC, y ASC, aisle ASC
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    StorageLocation lockBestEmptySlot(
            @Param("zoneClass") String zoneClass,
            @Param("palletHeight") Integer palletHeight,
            @Param("palletWeight") Double palletWeight);
}
