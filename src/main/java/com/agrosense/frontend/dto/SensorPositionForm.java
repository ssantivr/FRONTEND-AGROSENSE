package com.agrosense.frontend.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Where a sensor stands and how much ground its readings stand for. Everything is optional, but a point
 * needs both coordinates and a radius: without the three the sensor is not drawn on the map.
 */
@Getter
@Setter
public class SensorPositionForm {

    public static final int MIN_COVERAGE_M = 1;
    public static final int MAX_COVERAGE_M = 2000;

    @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90.")
    @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90.")
    @Digits(integer = 3, fraction = 7, message = "La latitud admite hasta 7 decimales.")
    private BigDecimal latitude;

    @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180.")
    @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180.")
    @Digits(integer = 3, fraction = 7, message = "La longitud admite hasta 7 decimales.")
    private BigDecimal longitude;

    @Min(value = MIN_COVERAGE_M, message = "El radio de cobertura debe ser de al menos 1 metro.")
    @Max(value = MAX_COVERAGE_M, message = "El radio de cobertura no puede superar los 2000 metros.")
    private Integer coverageM;

    @AssertTrue(message = "Indica latitud, longitud y radio de cobertura, o deja los tres vacíos.")
    public boolean isComplete() {
        return (latitude == null) == (longitude == null) && (latitude == null) == (coverageM == null);
    }

    public boolean isEmpty() {
        return latitude == null && longitude == null && coverageM == null;
    }
}
