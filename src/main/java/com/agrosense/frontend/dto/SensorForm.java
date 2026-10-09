package com.agrosense.frontend.dto;

import com.agrosense.frontend.entity.enums.SensorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SensorForm {

    @NotNull(message = "Selecciona un cultivo.")
    private Integer cropId;

    @NotBlank(message = "Ingresa el código del sensor.")
    @Size(max = 40, message = "El código no puede superar los 40 caracteres.")
    @Pattern(regexp = "[A-Za-z0-9_-]*", message = "El código solo admite letras, números, guiones y guion bajo.")
    private String sensorCode;

    @NotNull(message = "Selecciona el tipo de sensor.")
    private SensorType sensorType;

    @Size(max = 160, message = "La ubicación no puede superar los 160 caracteres.")
    private String location;
}
