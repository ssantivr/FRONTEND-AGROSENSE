package com.agrosense.frontend.dto;

import com.agrosense.frontend.entity.enums.AlertType;
import com.agrosense.frontend.entity.enums.CropStage;
import com.agrosense.frontend.entity.enums.IrrigationType;
import com.agrosense.frontend.entity.enums.SensorType;
import com.agrosense.frontend.entity.enums.Severity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Read-only projections handed to the templates and to the /api/ui JSON endpoints. */
public final class Views {

    private Views() {
    }

    public record NavItem(String path, String label, String icon, boolean active) {
    }

    public record CropView(
            Integer id,
            String name,
            String variety,
            LocalDate sowingDate,
            CropStage stage,
            BigDecimal humidityMin,
            BigDecimal humidityMax,
            BigDecimal tempMin,
            BigDecimal tempMax,
            BigDecimal phMin,
            BigDecimal phMax,
            boolean active) {
    }

    public record EstateView(
            Integer id,
            String name,
            String location,
            BigDecimal areaHa,
            BigDecimal latitude,
            BigDecimal longitude,
            List<CropView> crops) {
    }

    /** An estate with coordinates, as plotted on the dashboard map. */
    public record EstateMarker(
            Integer id,
            String name,
            String location,
            BigDecimal areaHa,
            BigDecimal latitude,
            BigDecimal longitude,
            List<String> crops) {
    }

    public record CropOption(Integer id, String label) {
    }

    public record SensorView(
            Integer id,
            String code,
            SensorType type,
            String cropName,
            String estateName,
            String location,
            boolean active,
            BigDecimal lastValue,
            String unit,
            LocalDateTime lastReadingAt) {
    }

    /** Average of the latest reading of every active sensor of one type; value is null without data. */
    public record SensorSummary(SensorType type, BigDecimal value, String unit, int sensorCount) {
    }

    public record ReadingPoint(LocalDateTime recordedAt, BigDecimal value) {
    }

    /** Readings of one sensor plus the crop's acceptable range for that magnitude, when it has one. */
    public record ReadingSeries(
            String sensorCode,
            String unit,
            BigDecimal thresholdMin,
            BigDecimal thresholdMax,
            List<ReadingPoint> points) {
    }

    public record AlertView(
            Integer id,
            AlertType type,
            Severity severity,
            String message,
            String cropName,
            String estateName,
            BigDecimal detectedValue,
            boolean acknowledged,
            LocalDateTime createdAt) {
    }

    public record IrrigationView(
            Integer id,
            String cropName,
            String estateName,
            LocalDateTime startedAt,
            LocalDateTime endsAt,
            Integer durationMin,
            BigDecimal waterLiters,
            IrrigationType type,
            boolean running) {
    }

    public record DailyWater(LocalDate date, BigDecimal liters) {
    }

    public record CropWaterReport(
            String cropName,
            String estateName,
            long irrigationCount,
            long totalMinutes,
            BigDecimal totalLiters) {
    }

    public record DashboardView(
            List<EstateView> estates,
            int activeCropCount,
            BigDecimal waterLitersLast7Days,
            List<SensorSummary> sensorSummaries,
            List<SensorView> chartSensors,
            List<AlertView> recentAlerts,
            List<CropOption> cropOptions,
            IrrigationView runningIrrigation,
            boolean hasMapData) {

        public int estateCount() {
            return estates.size();
        }

        /** Summary for a sensor type name, or null when the user has no sensor of that type. */
        public SensorSummary summary(String type) {
            return sensorSummaries.stream()
                    .filter(summary -> summary.type().name().equals(type))
                    .findFirst()
                    .orElse(null);
        }
    }

    public record ProfileView(String name, String lastName, String email, LocalDateTime createdAt) {
    }

    public record ApiMessage(String message) {
    }
}
