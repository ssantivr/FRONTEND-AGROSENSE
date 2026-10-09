package com.agrosense.frontend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IrrigationForm {

    public static final int MAX_DURATION_MIN = 240;

    @NotNull(message = "Selecciona un cultivo.")
    private Integer cropId;

    @NotNull(message = "Indica la duración del riego.")
    @Min(value = 1, message = "La duración mínima es de 1 minuto.")
    @Max(value = MAX_DURATION_MIN, message = "La duración máxima es de 240 minutos.")
    private Integer durationMin = 30;
}
