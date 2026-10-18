package com.agrosense.frontend.service;

import com.agrosense.frontend.dto.Views.SensorStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class SensorStatusPolicy {

    private final Duration staleAfter;
    private final Duration offlineAfter;

    public SensorStatusPolicy(@Value("${app.sensors.stale-after-minutes:60}") long staleAfterMinutes,
            @Value("${app.sensors.offline-after-minutes:1440}") long offlineAfterMinutes) {
        if (staleAfterMinutes <= 0 || offlineAfterMinutes <= staleAfterMinutes) {
            throw new IllegalArgumentException(
                    "app.sensors.offline-after-minutes must be greater than app.sensors.stale-after-minutes, and both positive");
        }
        this.staleAfter = Duration.ofMinutes(staleAfterMinutes);
        this.offlineAfter = Duration.ofMinutes(offlineAfterMinutes);
    }

    public SensorStatus statusOf(boolean active, BigDecimal lastValue, LocalDateTime lastReadingAt, String lastQuality,
            LocalDateTime now) {
        if (!active) {
            return SensorStatus.INACTIVE;
        }
        if (lastValue == null && lastReadingAt == null) {
            return SensorStatus.NO_DATA;
        }
        if (lastReadingAt == null) {
            return SensorStatus.UNKNOWN;
        }
        if (lastQuality != null && !"OK".equalsIgnoreCase(lastQuality)) {
            return SensorStatus.ERROR;
        }
        Duration age = Duration.between(lastReadingAt, now);
        if (age.compareTo(offlineAfter) > 0) {
            return SensorStatus.OFFLINE;
        }
        if (age.compareTo(staleAfter) > 0) {
            return SensorStatus.STALE;
        }
        return SensorStatus.ONLINE;
    }

    public String staleAfterText() {
        return describe(staleAfter);
    }

    public String offlineAfterText() {
        return describe(offlineAfter);
    }

    private static String describe(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes % 60 != 0) {
            return minutes == 1 ? "1 minuto" : minutes + " minutos";
        }
        long hours = minutes / 60;
        return hours == 1 ? "1 hora" : hours + " horas";
    }
}
