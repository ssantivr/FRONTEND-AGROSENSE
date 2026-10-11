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

    /**
     * The colour slot, 1 to 6, a crop keeps everywhere it appears (badges, map, charts). It only depends on
     * the name, so "Café" has the same colour in every estate; map-shapes.ts computes the same number.
     */
    public static int toneOf(String name) {
        // Each letter weighs by its position, so names with the same letters still land on different colours.
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

        /** Colour slot of the crop, 1 to 6; see {@link Views#toneOf}. */
        public int tone() {
            return toneOf(name);
        }

        /** The letter shown on the crop's badge. */
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

        /** The map tile around the estate, for its card; null without coordinates. */
        public MapThumb thumb() {
            return MapThumb.of(latitude, longitude, boundary);
        }

        public long activeCropCount() {
            return crops.stream().filter(CropView::active).count();
        }
    }

    /**
     * One OpenStreetMap tile and where the estate falls inside it, in the tile's 256 px square.
     * A whole tile is used as it is served, so the estate is rarely at its centre.
     */
    public record MapThumb(String tileUrl, double x, double y, String outline) {

        private static final int ZOOM = 11;
        private static final int TILE_SIZE = 256;

        /** {@code outline} becomes the points of an SVG polygon in the tile's square; null without a boundary. */
        static MapThumb of(BigDecimal latitude, BigDecimal longitude, List<double[]> boundary) {
            if (latitude == null || longitude == null) {
                return null;
            }
            // Web Mercator, as in the OpenStreetMap tile naming scheme.
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

    /**
     * An estate as the dashboard map shows it: on the map when it has coordinates, and always in the list
     * beside it. {@code crops} are the names of its active crops.
     */
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

    /**
     * A sensor that has been placed on the map: where it stands and the radius, in metres, of the ground
     * its readings stand for. {@code lastValue} and {@code unit} are null while it has no readings.
     */
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

        /** Whether it has a position and a coverage radius, and so can be drawn on the map. */
        public boolean placed() {
            return latitude != null && longitude != null && coverageM != null;
        }

        /** How long ago the last reading arrived, in words; null when there is none. */
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

        /** Bootstrap icon for what the sensor measures. */
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

    /** Average of the latest reading of every active sensor of one type; value is null without data. */
    /**
     * {@code sparkline} holds the points of the last 24 hours (hourly averages) for an SVG polyline, or
     * null when there are fewer than two.
     */
    public record SensorSummary(SensorType type, BigDecimal value, String unit, int sensorCount, String sparkline) {
    }

    /**
     * Where a value falls against an acceptable range, as positions from 0 to 100 along a bar.
     * {@code within} tells whether the value is inside the range.
     */
    public record RangeBar(double min, double max, double value, String minLabel, String maxLabel, boolean within) {
    }

    /** Water figures of a period; {@code changePercent} compares its litres with the period before, when that had any. */
    public record WaterTotals(BigDecimal liters, long irrigations, long minutes, BigDecimal averageLiters,
            Integer changePercent) {
    }

    /** Litres applied in one estate, and per hectare when its area is known. */
    public record EstateWater(String estateName, BigDecimal liters, BigDecimal litersPerHa) {
    }

    /** Litres and number of irrigations started one way: by hand or automatically. */
    public record TypeWater(IrrigationType type, BigDecimal liters, long irrigations) {
    }

    /**
     * What one sensor read over a period. {@code within} tells whether its average is inside the crop's
     * range; null when the crop has no range for that magnitude.
     */
    public record SensorStat(String code, SensorType type, String cropName, String estateName, String unit,
            BigDecimal min, BigDecimal max, BigDecimal average, long readings,
            BigDecimal rangeMin, BigDecimal rangeMax, Boolean within) {
    }

    /** Alerts of one type and severity in a period, and how many of them are still open. */
    public record AlertStat(AlertType type, Severity severity, long total, long open) {
    }

    /** Everything the reports page shows for one period. {@code days} is 0 for "since the beginning". */
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

    /** The irrigations started on one day and the litres they add up to. */
    public record IrrigationDay(LocalDate date, List<IrrigationView> irrigations, BigDecimal liters) {
    }

    /** The alerts raised on one day, for the timeline. */
    public record AlertDay(LocalDate date, List<AlertView> alerts) {
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

        /** Seconds left of a running irrigation; 0 when it is not running. */
        public long remainingSeconds() {
            return running && endsAt != null
                    ? Math.max(0, java.time.Duration.between(LocalDateTime.now(), endsAt).toSeconds())
                    : 0;
        }

        /** Planned length in seconds; 0 when it is unknown. */
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

        /** Whether any estate has coordinates to draw on the map. */
        public boolean hasMapData() {
            return !markers.isEmpty();
        }

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
