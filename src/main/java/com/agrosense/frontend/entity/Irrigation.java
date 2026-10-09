package com.agrosense.frontend.entity;

import com.agrosense.frontend.entity.enums.IrrigationType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "irrigations", indexes = {
        @Index(name = "idx_irrigations_crop_started_at", columnList = "id_crop, started_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Irrigation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_irrigation")
    private Integer idIrrigation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_crop", nullable = false)
    private Crop crop;

    // "start" and "end" are reserved words in SQL, so the columns use explicit names.
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "duration_min")
    private Integer durationMin;

    @Column(name = "water_liters", precision = 10, scale = 2)
    private BigDecimal waterLiters;

    @Enumerated(EnumType.STRING)
    @Column(name = "irrigation_type", nullable = false)
    @Builder.Default
    private IrrigationType type = IrrigationType.AUTOMATIC;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activated_by")
    private User activatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
