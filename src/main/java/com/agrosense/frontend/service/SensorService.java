package com.agrosense.frontend.service;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.backend.BackendClient.ReadingResponse;
import com.agrosense.frontend.dto.Views.ConnectedSensor;
import com.agrosense.frontend.dto.Views.HumidityCard;
import com.agrosense.frontend.dto.Views.HumidityDayReport;
import com.agrosense.frontend.dto.Views.HumidityDaySummary;
import com.agrosense.frontend.dto.Views.HumidityPoint;
import com.agrosense.frontend.dto.Views.HumiditySeries;
import com.agrosense.frontend.dto.Views.HumidityLevel;
import com.agrosense.frontend.dto.Views.HumidityReading;
import com.agrosense.frontend.dto.Views.ReadingPoint;
import com.agrosense.frontend.dto.Views.ReadingSeries;
import com.agrosense.frontend.dto.Views.SensorDetail;
import com.agrosense.frontend.dto.Views.SensorSummary;
import com.agrosense.frontend.dto.Views.SensorView;
import com.agrosense.frontend.entity.Crop;
import com.agrosense.frontend.entity.Sensor;
import com.agrosense.frontend.entity.SensorReading;
import com.agrosense.frontend.entity.enums.SensorType;
import com.agrosense.frontend.exception.NotFoundException;
import com.agrosense.frontend.repository.CropRepository;
import com.agrosense.frontend.repository.SensorReadingRepository;
import com.agrosense.frontend.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SensorService {

    /** The dashboard's cards: one per value the ESP32 measures. */
    public static final List<SensorType> DASHBOARD_TYPES = SensorType.ESP32;

    private static final int CHART_WINDOW_HOURS = 24;
    private static final Set<Integer> CHART_WINDOWS_HOURS = Set.of(6, 24, 72, 168);
    private static final int BACKEND_READING_LIMIT = 500;

    /** What the ESP32 measures about water: the soil's moisture and the air's relative humidity. */
    public static final List<SensorType> HUMIDITY_TYPES = List.of(SensorType.SOIL_MOISTURE, SensorType.RELATIVE_HUMIDITY);

    private final SensorRepository sensorRepository;
    private final SensorReadingRepository readingRepository;
    private final CropRepository cropRepository;
    private final BackendClient backendClient;
    private final SensorStatusPolicy statusPolicy;

    /**
     * The configured ESP32, the plant it measures and only its humidity readings. Empty when the device does not exist or
     * its crop belongs to someone else, so nobody sees another user's plant by guessing a device code.
     */
    public Optional<ConnectedSensor> findConnected(String email, String deviceCode, List<SensorView> userSensors) {
        return cropRepository.findMeasuredByDevice(deviceCode, email).stream().findFirst()
                .map(crop -> connected(deviceCode, crop, userSensors));
    }

    /** Reuses the sensors the page already loaded, so the section costs two statements: the crop and its readings. */
    private ConnectedSensor connected(String deviceCode, Crop crop, List<SensorView> userSensors) {
        List<SensorView> sensors = userSensors.stream()
                .filter(sensor -> crop.getIdCrop().equals(sensor.cropId()) && HUMIDITY_TYPES.contains(sensor.type()))
                .toList();
        List<HumidityReading> readings = sensors.isEmpty() ? List.of() : readingRepository
                .findTop20BySensorIdSensorInOrderByRecordedAtDesc(sensors.stream().map(SensorView::id).toList())
                .stream()
                .map(reading -> {
                    SensorType type = reading.getSensor().getSensorType();
                    BigDecimal[] range = thresholds(type, crop);
                    return new HumidityReading(reading.getRecordedAt(), reading.getSensor().getSensorCode(), type,
                            reading.getValue(), reading.getUnit(), HumidityLevel.of(reading.getValue(), range[0], range[1]));
                })
                .toList();
        return new ConnectedSensor(deviceCode, crop.getIdCrop(), crop.getName(), crop.getVariety(), crop.getStage(),
                crop.getSowingDate(), crop.getEstate().getIdEstate(), crop.getEstate().getName(),
                crop.getEstate().getLocation(), crop.getEstate().getAreaHa(), crop.getHumidityMin(),
                crop.getHumidityMax(), crop.getAirHumidityMin(), crop.getAirHumidityMax(), crop.getTempMin(),
                crop.getTempMax(), sensors, sensors.stream().map(sensor -> {
                    BigDecimal[] range = thresholds(sensor.type(), crop);
                    return new HumidityCard(sensor, HumidityLevel.of(sensor.lastValue(), range[0], range[1]), range[0],
                            range[1]);
                }).toList(), readings);
    }

    /** The user's sensors of the types the ESP32 measures; old sensors of other types stay stored but are not shown. */
    /** The ESP32's day chart averages its readings in buckets this long; it must divide an hour. */
    public static final int DAY_BUCKET_MINUTES = 15;

    /**
     * The last 24 hours of each humidity sensor of the configured ESP32's crop, averaged every
     * {@link #DAY_BUCKET_MINUTES} minutes. Empty when the device does not exist or its crop belongs to someone else.
     */
    public List<HumiditySeries> findHumidityDay(String email, String deviceCode, Function<SensorType, String> measureOf) {
        return findHumidityDayReport(email, deviceCode, measureOf).map(HumidityDayReport::series).orElse(List.of());
    }

    /** The same 24 hours with the crop they belong to, for the irrigation page. Empty without the user's ESP32. */
    public Optional<HumidityDayReport> findHumidityDayReport(String email, String deviceCode,
            Function<SensorType, String> measureOf) {
        Optional<Crop> found = cropRepository.findMeasuredByDevice(deviceCode, email).stream().findFirst();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Crop crop = found.get();
        List<Sensor> sensors = sensorRepository.findByCropEstateUserEmailAndSensorTypeInOrderBySensorCode(email,
                        HUMIDITY_TYPES).stream()
                .filter(sensor -> crop.getIdCrop().equals(sensor.getCrop().getIdCrop()))
                .toList();
        if (sensors.isEmpty()) {
            return Optional.of(new HumidityDayReport(crop.getIdCrop(), crop.getName(), crop.getEstate().getName(),
                    List.of(), List.of()));
        }
        Map<Integer, List<ReadingPoint>> bySensor = readingRepository
                .findBySensorIdSensorInAndRecordedAtGreaterThanEqualOrderByRecordedAt(
                        sensors.stream().map(Sensor::getIdSensor).toList(), LocalDateTime.now().minusHours(24))
                .stream()
                .collect(Collectors.groupingBy(reading -> reading.getSensor().getIdSensor(),
                        Collectors.mapping(reading -> new ReadingPoint(reading.getRecordedAt(), reading.getValue()),
                                Collectors.toList())));
        List<HumiditySeries> series = sensors.stream()
                .map(sensor -> {
                    BigDecimal[] range = thresholds(sensor.getSensorType(), crop);
                    List<HumidityPoint> points = bucket(bySensor.getOrDefault(sensor.getIdSensor(), List.of()),
                            DAY_BUCKET_MINUTES).stream()
                            .map(point -> new HumidityPoint(point.recordedAt().toString(), point.value()))
                            .toList();
                    return new HumiditySeries(sensor.getSensorCode(), measureOf.apply(sensor.getSensorType()),
                            range[0], range[1], points);
                })
                .toList();
        return Optional.of(new HumidityDayReport(crop.getIdCrop(), crop.getName(), crop.getEstate().getName(), series,
                sensors.stream().map(Sensor::getSensorType).toList()));
    }

    /**
     * Sums up one measure's day from its averaged buckets (oldest first, each {@code bucketMinutes} long): the newest
     * value, the lowest, the highest, the average, and the minutes spent below and above the ideal range. A bucket on
     * the range's edge counts as inside it.
     */
    public static HumidityDaySummary summarize(SensorType type, HumiditySeries series, int bucketMinutes) {
        List<BigDecimal> values = series.points().stream().map(HumidityPoint::value).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return new HumidityDaySummary(type, series.measure(), null, null, null, null, series.min(), series.max(), 0,
                    0, 0);
        }
        BigDecimal average = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 1, RoundingMode.HALF_UP);
        long low = series.min() == null ? 0 : values.stream().filter(value -> value.compareTo(series.min()) < 0).count();
        long high = series.max() == null ? 0 : values.stream().filter(value -> value.compareTo(series.max()) > 0).count();
        return new HumidityDaySummary(type, series.measure(), values.get(values.size() - 1),
                values.stream().min(BigDecimal::compareTo).orElseThrow(),
                values.stream().max(BigDecimal::compareTo).orElseThrow(), average, series.min(), series.max(),
                values.size() * bucketMinutes, (int) low * bucketMinutes, (int) high * bucketMinutes);
    }

    /**
     * Averages the points in clock-aligned buckets of {@code minutes} (which must divide an hour), oldest first, rounded
     * to one decimal. Points without a value are skipped and empty buckets are left out, so a gap stays a gap.
     */
    public static List<ReadingPoint> bucket(List<ReadingPoint> points, int minutes) {
        if (minutes <= 0 || 60 % minutes != 0) {
            throw new IllegalArgumentException("The bucket must divide an hour: " + minutes);
        }
        Map<LocalDateTime, List<BigDecimal>> buckets = new TreeMap<>();
        for (ReadingPoint point : points) {
            if (point.value() == null || point.recordedAt() == null) {
                continue;
            }
            LocalDateTime start = point.recordedAt().truncatedTo(ChronoUnit.HOURS)
                    .plusMinutes((long) (point.recordedAt().getMinute() / minutes) * minutes);
            buckets.computeIfAbsent(start, key -> new ArrayList<>()).add(point.value());
        }
        return buckets.entrySet().stream()
                .map(entry -> new ReadingPoint(entry.getKey(), entry.getValue().stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(entry.getValue().size()), 1, RoundingMode.HALF_UP)))
                .toList();
    }

    public List<SensorView> findSensors(String email) {
        List<Sensor> sensors = sensorRepository.findByCropEstateUserEmailAndSensorTypeInOrderBySensorCode(email,
                SensorType.ESP32);
        Map<Integer, SensorReading> latest = latestReadings(sensors);
        LocalDateTime now = LocalDateTime.now();
        return sensors.stream()
                .map(sensor -> toView(sensor, Optional.ofNullable(latest.get(sensor.getIdSensor())), now))
                .toList();
    }

    private Map<Integer, SensorReading> latestReadings(List<Sensor> sensors) {
        if (sensors.isEmpty()) {
            return Map.of();
        }
        return readingRepository.findLatestOf(sensors.stream().map(Sensor::getIdSensor).toList()).stream()
                .collect(Collectors.toMap(reading -> reading.getSensor().getIdSensor(), Function.identity(),
                        (first, second) -> first));
    }

    public List<SensorSummary> summarize(List<SensorView> sensors) {
        return summarize(sensors, Map.of());
    }

    public List<SensorSummary> summarize(List<SensorView> sensors, Map<SensorType, List<BigDecimal>> trends) {
        return DASHBOARD_TYPES.stream()
                .map(type -> summarize(type, sensors, Sparkline.points(trends.get(type))))
                .toList();
    }

    public Map<SensorType, List<BigDecimal>> findHourlyTrends(String email) {
        Map<SensorType, List<BigDecimal>> trends = new EnumMap<>(SensorType.class);
        readingRepository.findHourlyAverages(email, LocalDateTime.now().minusHours(CHART_WINDOW_HOURS))
                .forEach(row -> trends.computeIfAbsent((SensorType) row[0], type -> new ArrayList<>())
                        .add(BigDecimal.valueOf(((Number) row[2]).doubleValue())));
        return trends;
    }

    public SensorDetail findDetail(String email, Integer sensorId) {
        Sensor sensor = sensorRepository.findByIdSensorAndCropEstateUserEmail(sensorId, email)
                .orElseThrow(() -> new NotFoundException("Sensor not found"));
        List<SensorReading> recent = readingRepository.findTop10BySensorIdSensorOrderByRecordedAtDesc(sensorId);
        BigDecimal[] range = thresholds(sensor.getSensorType(), sensor.getCrop());
        return new SensorDetail(
                toView(sensor, recent.stream().findFirst(), LocalDateTime.now()),
                range[0],
                range[1],
                recent.stream().map(reading -> new ReadingPoint(reading.getRecordedAt(), reading.getValue())).toList());
    }

    public ReadingSeries findLast24Hours(String email, Integer sensorId) {
        return findReadings(email, sensorId, CHART_WINDOW_HOURS);
    }

    public ReadingSeries findReadings(String email, Integer sensorId, int hours) {
        Sensor sensor = sensorRepository.findByIdSensorAndCropEstateUserEmail(sensorId, email)
                .orElseThrow(() -> new NotFoundException("Sensor not found"));
        int window = CHART_WINDOWS_HOURS.contains(hours) ? hours : CHART_WINDOW_HOURS;
        LocalDateTime since = LocalDateTime.now().minusHours(window);
        // The API returns the latest BACKEND_READING_LIMIT readings: when they do not reach back to the start of
        // the window, the database (the same one the backend writes to) answers instead.
        Optional<List<ReadingResponse>> fromApi = backendClient
                .attempt(() -> backendClient.latestReadings(email, sensorId, BACKEND_READING_LIMIT))
                .filter(latest -> latest.size() < BACKEND_READING_LIMIT
                        || latest.stream().anyMatch(reading -> !reading.recordedAt().isAfter(since)))
                .map(latest -> latest.stream()
                        .filter(reading -> !reading.recordedAt().isBefore(since))
                        .sorted(Comparator.comparing(ReadingResponse::recordedAt))
                        .toList());
        List<ReadingResponse> readings = fromApi.orElseGet(() -> readingRepository
                .findBySensorIdSensorAndRecordedAtGreaterThanEqualOrderByRecordedAt(sensorId, since).stream()
                .map(reading -> new ReadingResponse(reading.getValue(), reading.getUnit(), reading.getRecordedAt()))
                .toList());

        BigDecimal[] range = thresholds(sensor.getSensorType(), sensor.getCrop());
        return new ReadingSeries(
                sensor.getSensorCode(),
                readings.isEmpty() ? null : readings.get(readings.size() - 1).unit(),
                range[0],
                range[1],
                readings.stream()
                        .map(reading -> new ReadingPoint(reading.recordedAt(), reading.value()))
                        .toList(),
                window,
                fromApi.isPresent() ? ReadingSeries.SOURCE_API : ReadingSeries.SOURCE_DATABASE,
                LocalDateTime.now());
    }

    private SensorView toView(Sensor sensor, Optional<SensorReading> latest, LocalDateTime now) {
        boolean active = Boolean.TRUE.equals(sensor.getActive());
        BigDecimal lastValue = latest.map(SensorReading::getValue).orElse(null);
        LocalDateTime lastReadingAt = latest.map(SensorReading::getRecordedAt).orElse(sensor.getLastReadingAt());
        String lastQuality = latest.map(SensorReading::getQuality).orElse(null);
        return new SensorView(
                sensor.getIdSensor(),
                sensor.getSensorCode(),
                sensor.getSensorType(),
                sensor.getCrop().getIdCrop(),
                sensor.getCrop().getName(),
                sensor.getCrop().getEstate().getIdEstate(),
                sensor.getCrop().getEstate().getName(),
                sensor.getLocation(),
                active,
                lastValue,
                latest.map(SensorReading::getUnit).orElse(null),
                lastReadingAt,
                lastQuality,
                sensor.getLatitude(),
                sensor.getLongitude(),
                sensor.getCoverageM(),
                statusPolicy.statusOf(active, lastValue, lastReadingAt, lastQuality, now));
    }

    private static SensorSummary summarize(SensorType type, List<SensorView> sensors, String sparkline) {
        List<SensorView> withData = sensors.stream()
                .filter(sensor -> sensor.type() == type && sensor.active() && sensor.lastValue() != null)
                .toList();
        if (withData.isEmpty()) {
            return new SensorSummary(type, null, null, 0, null, null);
        }
        BigDecimal total = withData.stream().map(SensorView::lastValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = total.divide(BigDecimal.valueOf(withData.size()), 1, RoundingMode.HALF_UP);
        LocalDateTime latest = withData.stream().map(SensorView::lastReadingAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        return new SensorSummary(type, average, withData.get(0).unit(), withData.size(), sparkline, latest);
    }

    private static BigDecimal[] thresholds(SensorType type, Crop crop) {
        return switch (type) {
            // The crop's humidity range is the soil's; the air's has its own, optional one.
            case SOIL_MOISTURE -> new BigDecimal[] {crop.getHumidityMin(), crop.getHumidityMax()};
            case RELATIVE_HUMIDITY -> new BigDecimal[] {crop.getAirHumidityMin(), crop.getAirHumidityMax()};
            case AIR_TEMPERATURE, SOIL_TEMPERATURE -> new BigDecimal[] {crop.getTempMin(), crop.getTempMax()};
            case PH -> new BigDecimal[] {crop.getPhMin(), crop.getPhMax()};
            default -> new BigDecimal[] {null, null};
        };
    }
}
