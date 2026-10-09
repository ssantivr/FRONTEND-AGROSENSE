package com.agrosense.frontend.entity;

import com.agrosense.frontend.entity.enums.CropStage;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "crops")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Crop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_crop")
    private Integer idCrop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_estate", nullable = false)
    private Estate estate;

    @Column(nullable = false)
    private String name;

    private String variety;

    @Column(name = "sowing_date")
    private LocalDate sowingDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CropStage stage = CropStage.GERMINATION;

    @Column(name = "humidity_min", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal humidityMin = new BigDecimal("40.0");

    @Column(name = "humidity_max", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal humidityMax = new BigDecimal("80.0");

    @Column(name = "temp_min", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal tempMin = new BigDecimal("15.0");

    @Column(name = "temp_max", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal tempMax = new BigDecimal("35.0");

    @Column(name = "ph_min", precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal phMin = new BigDecimal("5.5");

    @Column(name = "ph_max", precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal phMax = new BigDecimal("7.0");

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @OneToMany(mappedBy = "crop", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<Sensor> sensors = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
