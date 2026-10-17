package com.agrosense.frontend.dto;

import com.agrosense.frontend.dto.Views.SensorStatus;
import com.agrosense.frontend.dto.Views.SensorView;
import com.agrosense.frontend.entity.enums.SensorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SensorStatusTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);

    private static SensorView sensor(boolean active, String value, LocalDateTime readAt, String quality) {
        return new SensorView(1, "AS-001", SensorType.SOIL_MOISTURE, 1, "Café", 1, "Finca", null, active,
                value == null ? null : new BigDecimal(value), "%", readAt, quality, null, null, null);
    }

    @Test
    void aRecentGoodReadingMeansTheSensorIsOnline() {
        assertThat(sensor(true, "55.1", NOW.minusMinutes(5), "OK").statusAt(NOW)).isEqualTo(SensorStatus.ONLINE);
        assertThat(sensor(true, "55.1", NOW.minusMinutes(5), null).statusAt(NOW)).isEqualTo(SensorStatus.ONLINE);
    }

    @Test
    void oldReadingsAreStaleAndThenOffline() {
        assertThat(sensor(true, "55.1", NOW.minusHours(4), "OK").statusAt(NOW)).isEqualTo(SensorStatus.STALE);
        assertThat(sensor(true, "55.1", NOW.minusHours(30), "OK").statusAt(NOW)).isEqualTo(SensorStatus.OFFLINE);
    }

    @Test
    void aFlaggedReadingIsAnErrorEvenWhenRecent() {
        assertThat(sensor(true, "140", NOW.minusMinutes(1), "OUT_OF_RANGE").statusAt(NOW))
                .isEqualTo(SensorStatus.ERROR);
    }

    @Test
    void inactiveEmptyAndUndatedSensorsAreToldApart() {
        assertThat(sensor(false, "55.1", NOW.minusMinutes(1), "OK").statusAt(NOW)).isEqualTo(SensorStatus.INACTIVE);
        assertThat(sensor(true, null, null, null).statusAt(NOW)).isEqualTo(SensorStatus.NO_DATA);
        assertThat(sensor(true, "55.1", null, null).statusAt(NOW)).isEqualTo(SensorStatus.UNKNOWN);
    }
}
