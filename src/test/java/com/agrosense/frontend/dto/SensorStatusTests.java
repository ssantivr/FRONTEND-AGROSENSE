package com.agrosense.frontend.dto;

import com.agrosense.frontend.dto.Views.SensorStatus;
import com.agrosense.frontend.service.SensorStatusPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SensorStatusTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);
    private static final SensorStatusPolicy POLICY = new SensorStatusPolicy(60, 1440);

    private static SensorStatus status(boolean active, String value, LocalDateTime readAt, String quality) {
        return POLICY.statusOf(active, value == null ? null : new BigDecimal(value), readAt, quality, NOW);
    }

    @Test
    void aReadingFromTheLastHourMeansTheSensorIsOnline() {
        assertThat(status(true, "55.1", NOW.minusMinutes(5), "OK")).isEqualTo(SensorStatus.ONLINE);
        assertThat(status(true, "55.1", NOW.minusMinutes(5), null)).isEqualTo(SensorStatus.ONLINE);
        assertThat(status(true, "55.1", NOW.minusMinutes(60), "OK")).isEqualTo(SensorStatus.ONLINE);
    }

    @Test
    void readingsOlderThanSixtyMinutesAreStaleAndOlderThanADayOffline() {
        assertThat(status(true, "55.1", NOW.minusMinutes(61), "OK")).isEqualTo(SensorStatus.STALE);
        assertThat(status(true, "55.1", NOW.minusMinutes(1440), "OK")).isEqualTo(SensorStatus.STALE);
        assertThat(status(true, "55.1", NOW.minusMinutes(1441), "OK")).isEqualTo(SensorStatus.OFFLINE);
    }

    @Test
    void theThresholdsComeFromTheConfiguration() {
        SensorStatusPolicy strict = new SensorStatusPolicy(10, 30);

        assertThat(strict.statusOf(true, BigDecimal.ONE, NOW.minusMinutes(11), "OK", NOW)).isEqualTo(SensorStatus.STALE);
        assertThat(strict.statusOf(true, BigDecimal.ONE, NOW.minusMinutes(31), "OK", NOW)).isEqualTo(SensorStatus.OFFLINE);
        assertThat(strict.staleAfterText()).isEqualTo("10 minutos");
        assertThat(POLICY.staleAfterText()).isEqualTo("1 hora");
        assertThat(POLICY.offlineAfterText()).isEqualTo("24 horas");
        assertThatIllegalArgumentException().isThrownBy(() -> new SensorStatusPolicy(60, 60));
    }

    @Test
    void aFlaggedReadingIsAnErrorEvenWhenRecent() {
        assertThat(status(true, "140", NOW.minusMinutes(1), "OUT_OF_RANGE")).isEqualTo(SensorStatus.ERROR);
    }

    @Test
    void inactiveEmptyAndUndatedSensorsAreToldApart() {
        assertThat(status(false, "55.1", NOW.minusMinutes(1), "OK")).isEqualTo(SensorStatus.INACTIVE);
        assertThat(status(true, null, null, null)).isEqualTo(SensorStatus.NO_DATA);
        assertThat(status(true, "55.1", null, null)).isEqualTo(SensorStatus.UNKNOWN);
    }
}
