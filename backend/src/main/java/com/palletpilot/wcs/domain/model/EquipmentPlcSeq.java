package com.palletpilot.wcs.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks the last PLC SeqId per equipment.
 * Guarantees monotonically increasing SeqIds across JVM restarts
 * and multi-pod deployments (via SELECT FOR UPDATE row lock).
 */
@Entity
@Table(name = "equipment_plc_seq")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentPlcSeq {

    @Id
    @Column(name = "equipment_id", nullable = false)
    private UUID equipmentId;

    @Column(name = "last_seq_id", nullable = false)
    private long lastSeqId;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
