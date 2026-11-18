package com.agrosense.frontend.service;

import com.agrosense.frontend.dto.Views.ReadingPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HumidityBucketTests {

    private static ReadingPoint at(int hour, int minute, String value) {
        return new ReadingPoint(LocalDateTime.of(2026, 11, 15, hour, minute, 30), value == null ? null : new BigDecimal(value));
    }

    @Test
    void readingsAreAveragedInClockAlignedBucketsOldestFirst() {
        List<ReadingPoint> buckets = SensorService.bucket(List.of(
                at(14, 31, "30"), at(14, 2, "40"), at(14, 14, "41"), at(14, 15, "50"), at(14, 44, "31")), 15);

        assertThat(buckets).extracting(ReadingPoint::recordedAt).containsExactly(
                LocalDateTime.of(2026, 11, 15, 14, 0), LocalDateTime.of(2026, 11, 15, 14, 15),
                LocalDateTime.of(2026, 11, 15, 14, 30));
        assertThat(buckets).extracting(ReadingPoint::value).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("40.5"), new BigDecimal("50"), new BigDecimal("30.5"));
    }

    @Test
    void anHourWithoutReadingsStaysAGapAndEmptyValuesAreSkipped() {
        List<ReadingPoint> buckets = SensorService.bucket(List.of(at(10, 5, "20"), at(10, 6, null), at(12, 5, "22")), 15);

        assertThat(buckets).extracting(ReadingPoint::recordedAt).containsExactly(
                LocalDateTime.of(2026, 11, 15, 10, 0), LocalDateTime.of(2026, 11, 15, 12, 0));
        assertThat(SensorService.bucket(List.of(), 15)).isEmpty();
    }

    @Test
    void aBucketThatDoesNotDivideAnHourIsRefused() {
        assertThatThrownBy(() -> SensorService.bucket(List.of(), 7)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SensorService.bucket(List.of(), 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
