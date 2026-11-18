package com.agrosense.frontend.service;

import com.agrosense.frontend.dto.Views.HumidityDaySummary;
import com.agrosense.frontend.dto.Views.HumidityLevel;
import com.agrosense.frontend.dto.Views.HumidityPoint;
import com.agrosense.frontend.dto.Views.HumiditySeries;
import com.agrosense.frontend.entity.enums.SensorType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HumidityDaySummaryTests {

    private static final BigDecimal MIN = new BigDecimal("40");
    private static final BigDecimal MAX = new BigDecimal("80");

    /** One 15-minute bucket per value, oldest first, starting at midnight. */
    private static HumiditySeries soil(BigDecimal min, BigDecimal max, String... values) {
        List<HumidityPoint> points = new ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            points.add(new HumidityPoint(String.format("2026-11-15T%02d:%02d", index / 4, index % 4 * 15),
                    new BigDecimal(values[index])));
        }
        return new HumiditySeries("AS-001", "Humedad del suelo", min, max, points);
    }

    @Test
    void theDayIsSummedUpFromItsBucketsWithTheEdgesInsideTheRange() {
        HumidityDaySummary day = SensorService.summarize(SensorType.SOIL_MOISTURE,
                soil(MIN, MAX, "30", "35", "40", "80", "90", "45"), 15);

        assertThat(day.now()).isEqualByComparingTo("45");
        assertThat(day.lowest()).isEqualByComparingTo("30");
        assertThat(day.highest()).isEqualByComparingTo("90");
        assertThat(day.average()).isEqualByComparingTo("53.3");
        assertThat(day.minutesMeasured()).isEqualTo(90);
        assertThat(day.minutesLow()).as("30 and 35; 40 is the edge").isEqualTo(30);
        assertThat(day.minutesHigh()).as("90; 80 is the edge").isEqualTo(15);
        assertThat(day.level()).isEqualTo(HumidityLevel.NORMAL);
        assertThat(day.timeOutside())
                .isEqualTo("Estuvo por debajo de lo ideal 30 min y por encima 15 min de 1 h 30 min medidas.");
    }

    @Test
    void aDryDayIsSaidInTheFarmersWords() {
        String[] dry = new String[96];
        java.util.Arrays.fill(dry, "32");
        dry[95] = "41";
        HumidityDaySummary day = SensorService.summarize(SensorType.SOIL_MOISTURE, soil(MIN, MAX, dry), 15);

        assertThat(day.timeOutside()).isEqualTo("La tierra estuvo seca 23 h 45 min de 24 h medidas.");
        assertThat(day.advice()).isEqualTo("La tierra tiene la humedad que el cultivo necesita.");

        HumidityDaySummary air = SensorService.summarize(SensorType.RELATIVE_HUMIDITY,
                new HumiditySeries("AS-007", "Humedad relativa", new BigDecimal("60"), new BigDecimal("85"),
                        soil(MIN, MAX, "45", "46").points()), 15);
        assertThat(air.timeOutside()).isEqualTo("El aire estuvo seco todo el tiempo medido (30 min).");
        assertThat(air.level()).isEqualTo(HumidityLevel.LOW);
    }

    @Test
    void aDayInsideTheRangeWithoutARangeOrWithoutReadingsSaysSo() {
        assertThat(SensorService.summarize(SensorType.SOIL_MOISTURE, soil(MIN, MAX, "50", "60", "70", "75"), 15)
                .timeOutside()).isEqualTo("Estuvo dentro de lo ideal todo el tiempo medido (1 h).");
        HumidityDaySummary noRange = SensorService.summarize(SensorType.RELATIVE_HUMIDITY, soil(null, null, "50"), 15);
        assertThat(noRange.hasRange()).isFalse();
        assertThat(noRange.timeOutside()).isEqualTo("Se midió durante 15 min.");
        HumidityDaySummary empty = SensorService.summarize(SensorType.SOIL_MOISTURE, soil(MIN, MAX), 15);
        assertThat(empty.now()).isNull();
        assertThat(empty.timeOutside()).isEqualTo("Sin lecturas en las últimas 24 horas.");
    }

    @Test
    void durationsReadLikeAClock() {
        assertThat(HumidityDaySummary.duration(45)).isEqualTo("45 min");
        assertThat(HumidityDaySummary.duration(180)).isEqualTo("3 h");
        assertThat(HumidityDaySummary.duration(195)).isEqualTo("3 h 15 min");
    }
}
