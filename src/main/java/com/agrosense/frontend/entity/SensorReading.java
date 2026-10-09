package com.agrosense.frontend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sensor_readings", indexes = {
        @Index(name = "idx_sensor_readings_sensor_recorded_at", columnList = "id_sensor, recorded_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reading")
    private Long idReading;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_sensor", nullable = false)
    private Sensor sensor;

    @Column(name = "reading_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal value;

    private String unit;

    @Builder.Default
    private String quality = "OK";

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @PrePersist
    public void prePersist() {
        if (this.recordedAt == null) {
            this.recordedAt = LocalDateTime.now();
        }
    }
}
