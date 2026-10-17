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

public final class Views {

    private Views() {
    }

    public static int toneOf(String name) {
        int[] letters = name.strip().toLowerCase(java.util.Locale.ROOT).chars().toArray();
        long sum = 0;
        for (int index = 0; index < letters.length; index++) {
            sum += (long) letters[index] * (index + 1);
        }
        return (int) (sum % 6) + 1;
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

        public int tone() {
            return toneOf(name);
        }

        public String initial() {
            return name.isBlank() ? "?" : name.strip().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        }
    }

    public record EstateView(
            Integer id,
            String name,
            String location,
            BigDecimal areaHa,
            BigDecimal latitude,
            BigDecimal longitude,
            List<CropView> crops,
            List<double[]> boundary) {

        public MapThumb thumb() {
            return MapThumb.of(latitude, longitude, boundary);
        }

        public long activeCropCount() {
            return crops.stream().filter(CropView::active).count();
        }
    }

    public record MapThumb(String tileUrl, double x, double y, String outline) {

        private static final int ZOOM = 11;
        private static final int TILE_SIZE = 256;

        static MapThumb of(BigDecimal latitude, BigDecimal longitude, List<double[]> boundary) {
            if (latitude == null || longitude == null) {
                return null;
            }
            double tiles = Math.pow(2, ZOOM);
            double latitudeRad = Math.toRadians(latitude.doubleValue());
            double tileX = (longitude.doubleValue() + 180) / 360 * tiles;
            double tileY = (1 - Math.log(Math.tan(latitudeRad) + 1 / Math.cos(latitudeRad)) / Math.PI) / 2 * tiles;
            if (!Double.isFinite(tileY) || tileY < 0 || tileY >= tiles) {
                return null;
            }
            long column = (long) Math.floor(tileX);
            long row = (long) Math.floor(tileY);
            String outline = boundary == null ? null : boundary.stream()
                    .map(corner -> {
                        double cornerRad = Math.toRadians(corner[0]);
                        double x = ((corner[1] + 180) / 360 * tiles - column) * TILE_SIZE;
                        double y = ((1 - Math.log(Math.tan(cornerRad) + 1 / Math.cos(cornerRad)) / Math.PI) / 2 * tiles
                                - row) * TILE_SIZE;
                        return String.format(java.util.Locale.ROOT, "%.1f,%.1f", x, y);
                    })
                    .collect(java.util.stream.Collectors.joining(" "));
            return new MapThumb("https://tile.openstreetmap.org/" + ZOOM + "/" + column + "/" + row + ".png",
                    (tileX - column) * TILE_SIZE, (tileY - row) * TILE_SIZE, outline);
        }
    }

    public record EstateMarker(
            Integer id,
            String name,
            String location,
            BigDecimal areaHa,
            BigDecimal latitude,
            BigDecimal longitude,
            List<String> crops,
            int sensorCount,
            int openAlerts,
            List<SensorSpot> sensors,
            List<double[]> boundary) {

        public boolean hasCoordinates() {
            return latitude != null && longitude != null;
        }
    }

    public record SensorSpot(
            Integer id,
            String code,
            String typeLabel,
            BigDecimal latitude,
            BigDecimal longitude,
            int coverageM,
            boolean active,
            BigDecimal lastValue,
            String unit) {
    }

    public record CropOption(Integer id, String label) {
    }

    public record SensorView(
            Integer id,
            String code,
            SensorType type,
            String cropName,
            Integer estateId,
            String estateName,
            String location,
            boolean active,
            BigDecimal lastValue,
            String unit,
            LocalDateTime lastReadingAt,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer coverageM) {

        public boolean placed() {
            return latitude != null && longitude != null && coverageM != null;
        }

        public String lastReadingAgo() {
            if (lastReadingAt == null) {
                return null;
            }
            long minutes = Math.max(0, java.time.Duration.between(lastReadingAt, LocalDateTime.now()).toMinutes());
            if (minutes < 1) {
                return "hace un momento";
            }
            if (minutes < 60) {
                return "hace " + minutes + " min";
            }
            long hours = minutes / 60;
            if (hours < 48) {
                return "hace " + hours + " h";
            }
            return "hace " + hours / 24 + " días";
        }

        public String icon() {
            return switch (type) {
                case SOIL_MOISTURE -> "bi-moisture";
                case AIR_TEMPERATURE -> "bi-thermometer-half";
                case SOIL_TEMPERATURE -> "bi-thermometer-low";
                case PH -> "bi-eyedropper";
                case CONDUCTIVITY -> "bi-lightning-charge";
                case LIGHT -> "bi-brightness-high";
                case RAIN_GAUGE -> "bi-cloud-rain";
                case RELATIVE_HUMIDITY -> "bi-droplet-half";
            };
        }
    }

    public record SensorSummary(SensorType type, BigDecimal value, String unit, int sensorCount, String sparkline) {
    }

    public record RangeBar(double min, double max, double value, String minLabel, String maxLabel, boolean within) {
    }

    public record WaterTotals(BigDecimal liters, long irrigations, long minutes, BigDecimal averageLiters,
            Integer changePercent) {
    }

    public record EstateWater(String estateName, BigDecimal liters, BigDecimal litersPerHa) {
    }

    public record TypeWater(IrrigationType type, BigDecimal liters, long irrigations) {
    }

    public record SensorStat(String code, SensorType type, String cropName, String estateName, String unit,
            BigDecimal min, BigDecimal max, BigDecimal average, long readings,
            BigDecimal rangeMin, BigDecimal rangeMax, Boolean within) {
    }

    public record AlertStat(AlertType type, Severity severity, long total, long open) {
    }

    public record ReportView(
            int days,
            WaterTotals totals,
            List<CropWaterReport> byCrop,
            List<EstateWater> byEstate,
            List<TypeWater> byType,
            List<DailyWater> daily,
            List<SensorStat> sensors,
            List<AlertStat> alerts) {

        public long alertTotal() {
            return alerts.stream().mapToLong(AlertStat::total).sum();
        }

        public long alertOpen() {
            return alerts.stream().mapToLong(AlertStat::open).sum();
        }
    }

    public record IrrigationDay(LocalDate date, List<IrrigationView> irrigations, BigDecimal liters) {
    }

    public record AlertDay(LocalDate date, List<AlertView> alerts) {
    }

    public record ReadingPoint(LocalDateTime recordedAt, BigDecimal value) {
    }

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

        public long remainingSeconds() {
            return running && endsAt != null
                    ? Math.max(0, java.time.Duration.between(LocalDateTime.now(), endsAt).toSeconds())
                    : 0;
        }

        public long totalSeconds() {
            return startedAt != null && endsAt != null
                    ? Math.max(0, java.time.Duration.between(startedAt, endsAt).toSeconds())
                    : 0;
        }
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
            List<EstateMarker> estateCards,
            List<EstateMarker> markers,
            List<DailyWater> waterUsage,
            RangeBar humidityRange,
            String waterSparkline) {

        public boolean hasMapData() {
            return !markers.isEmpty();
        }

        public int estateCount() {
            return estates.size();
        }

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
