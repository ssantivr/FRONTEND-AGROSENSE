package com.agrosense.frontend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

/**
 * A field device as the frontend needs it: only which crop it measures. The backend owns the table and its keys, so
 * this mapping is read-only and leaves out every other column.
 */
@Entity
@Immutable
@Table(name = "devices")
@Getter
@NoArgsConstructor
public class Device {

    @Id
    @Column(name = "id_device")
    private Integer idDevice;

    @Column(name = "id_crop", nullable = false)
    private Integer idCrop;

    @Column(name = "device_code", nullable = false)
    private String deviceCode;
}
