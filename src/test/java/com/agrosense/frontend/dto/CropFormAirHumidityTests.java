package com.agrosense.frontend.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CropFormAirHumidityTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private CropForm form(String min, String max) {
        CropForm form = new CropForm();
        form.setName("Café");
        form.setAirHumidityMin(min == null ? null : new BigDecimal(min));
        form.setAirHumidityMax(max == null ? null : new BigDecimal(max));
        return form;
    }

    private boolean airRangeRejected(CropForm form) {
        return validator.validate(form).stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().startsWith("airHumidity"));
    }

    @Test
    void theAirRangeIsOptionalButNeverHalfFilledOrUpsideDown() {
        assertThat(airRangeRejected(form(null, null))).as("both empty").isFalse();
        assertThat(airRangeRejected(form("60", "85"))).as("a real range").isFalse();
        assertThat(airRangeRejected(form("60", null))).as("only the minimum").isTrue();
        assertThat(airRangeRejected(form(null, "85"))).as("only the maximum").isTrue();
        assertThat(airRangeRejected(form("85", "60"))).as("upside down").isTrue();
        assertThat(airRangeRejected(form("60", "120"))).as("over 100 %").isTrue();
    }
}
