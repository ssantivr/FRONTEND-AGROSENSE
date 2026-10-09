package com.agrosense.frontend.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class EstateForm {

    @NotBlank(message = "Ingresa el nombre de la parcela.")
    @Size(max = 120, message = "El nombre no puede superar los 120 caracteres.")
    private String name;

    @Size(max = 160, message = "La ubicación no puede superar los 160 caracteres.")
    private String location;

    @DecimalMin(value = "0.01", message = "El área debe ser mayor que cero.")
    @Digits(integer = 8, fraction = 2, message = "El área admite hasta 2 decimales.")
    private BigDecimal areaHa;

    @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90.")
    @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90.")
    @Digits(integer = 3, fraction = 7, message = "La latitud admite hasta 7 decimales.")
    private BigDecimal latitude;

    @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180.")
    @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180.")
    @Digits(integer = 3, fraction = 7, message = "La longitud admite hasta 7 decimales.")
    private BigDecimal longitude;

    @AssertTrue(message = "Indica latitud y longitud, o deja ambas vacías.")
    public boolean isCoordinatesComplete() {
        return (latitude == null) == (longitude == null);
    }
}
