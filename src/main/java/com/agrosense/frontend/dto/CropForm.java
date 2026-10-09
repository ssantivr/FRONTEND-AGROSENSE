package com.agrosense.frontend.dto;

import com.agrosense.frontend.entity.enums.CropStage;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class CropForm {

    @NotBlank(message = "Ingresa el nombre del cultivo.")
    @Size(max = 120, message = "El nombre no puede superar los 120 caracteres.")
    private String name;

    @Size(max = 120, message = "La variedad no puede superar los 120 caracteres.")
    private String variety;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @PastOrPresent(message = "La fecha de siembra no puede ser futura.")
    private LocalDate sowingDate;

    @NotNull(message = "Selecciona la etapa del cultivo.")
    private CropStage stage = CropStage.GERMINATION;

    @NotNull(message = "Indica la humedad mínima.")
    @DecimalMin(value = "0", message = "La humedad debe estar entre 0 y 100.")
    @DecimalMax(value = "100", message = "La humedad debe estar entre 0 y 100.")
    private BigDecimal humidityMin = new BigDecimal("40");

    @NotNull(message = "Indica la humedad máxima.")
    @DecimalMin(value = "0", message = "La humedad debe estar entre 0 y 100.")
    @DecimalMax(value = "100", message = "La humedad debe estar entre 0 y 100.")
    private BigDecimal humidityMax = new BigDecimal("80");

    @NotNull(message = "Indica la temperatura mínima.")
    @DecimalMin(value = "-50", message = "La temperatura debe estar entre -50 y 70 °C.")
    @DecimalMax(value = "70", message = "La temperatura debe estar entre -50 y 70 °C.")
    private BigDecimal tempMin = new BigDecimal("15");

    @NotNull(message = "Indica la temperatura máxima.")
    @DecimalMin(value = "-50", message = "La temperatura debe estar entre -50 y 70 °C.")
    @DecimalMax(value = "70", message = "La temperatura debe estar entre -50 y 70 °C.")
    private BigDecimal tempMax = new BigDecimal("35");

    @NotNull(message = "Indica el pH mínimo.")
    @DecimalMin(value = "0", message = "El pH debe estar entre 0 y 14.")
    @DecimalMax(value = "14", message = "El pH debe estar entre 0 y 14.")
    private BigDecimal phMin = new BigDecimal("5.5");

    @NotNull(message = "Indica el pH máximo.")
    @DecimalMin(value = "0", message = "El pH debe estar entre 0 y 14.")
    @DecimalMax(value = "14", message = "El pH debe estar entre 0 y 14.")
    private BigDecimal phMax = new BigDecimal("7.0");

    private boolean active = true;

    @AssertTrue(message = "La humedad mínima debe ser menor que la máxima.")
    public boolean isHumidityRangeValid() {
        return isAscending(humidityMin, humidityMax);
    }

    @AssertTrue(message = "La temperatura mínima debe ser menor que la máxima.")
    public boolean isTempRangeValid() {
        return isAscending(tempMin, tempMax);
    }

    @AssertTrue(message = "El pH mínimo debe ser menor que el máximo.")
    public boolean isPhRangeValid() {
        return isAscending(phMin, phMax);
    }

    /** Missing values are reported by @NotNull, so they do not also fail the range check. */
    private static boolean isAscending(BigDecimal min, BigDecimal max) {
        return min == null || max == null || min.compareTo(max) < 0;
    }
}
