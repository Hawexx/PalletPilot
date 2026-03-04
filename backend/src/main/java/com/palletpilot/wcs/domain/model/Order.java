package com.palletpilot.wcs.domain.model;

import com.palletpilot.wcs.domain.model.enums.ProcessState;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wms_order_id", nullable = false, unique = true)
    private String wmsOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessState status;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 5;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @com.fasterxml.jackson.annotation.JsonManagedReference
    private List<OrderItem> items;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
