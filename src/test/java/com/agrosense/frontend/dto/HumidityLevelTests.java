package com.agrosense.frontend.dto;

import com.agrosense.frontend.dto.Views.HumidityLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class HumidityLevelTests {

    private static final BigDecimal MIN = new BigDecimal("40");
    private static final BigDecimal MAX = new BigDecimal("80");

    @Test
    void aReadingIsLowNormalOrHighAgainstTheCropRangeWithBothEdgesNormal() {
        assertThat(HumidityLevel.of(new BigDecimal("39.9"), MIN, MAX)).isEqualTo(HumidityLevel.LOW);
        assertThat(HumidityLevel.of(new BigDecimal("40.0"), MIN, MAX)).isEqualTo(HumidityLevel.NORMAL);
        assertThat(HumidityLevel.of(new BigDecimal("80.0"), MIN, MAX)).isEqualTo(HumidityLevel.NORMAL);
        assertThat(HumidityLevel.of(new BigDecimal("80.1"), MIN, MAX)).isEqualTo(HumidityLevel.HIGH);
    }

    @Test
    void withoutAValueOrARangeTheReadingIsNotJudged() {
        assertThat(HumidityLevel.of(null, MIN, MAX)).isEqualTo(HumidityLevel.NO_RANGE);
        assertThat(HumidityLevel.of(new BigDecimal("83.3"), null, null)).isEqualTo(HumidityLevel.NO_RANGE);
    }
}
