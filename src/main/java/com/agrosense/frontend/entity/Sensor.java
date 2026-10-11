package com.agrosense.frontend.entity;

import com.agrosense.frontend.entity.enums.SensorType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sensors")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sensor")
    private Integer idSensor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_crop", nullable = false)
    private Crop crop;

    @Column(name = "sensor_code", nullable = false, unique = true)
    private String sensorCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensor_type", nullable = false)
    private SensorType sensorType;

    private String location;

    /** Where the sensor stands; null until it is placed on the map. */
    private BigDecimal latitude;

    private BigDecimal longitude;

    /** Radius, in metres, of the ground the readings are taken to describe. */
    @Column(name = "coverage_m")
    private Integer coverageM;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "last_reading_at")
    private LocalDateTime lastReadingAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
