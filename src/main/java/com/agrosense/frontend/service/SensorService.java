package com.agrosense.frontend.service;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.backend.BackendClient.ReadingResponse;
import com.agrosense.frontend.dto.Views.ReadingPoint;
import com.agrosense.frontend.dto.Views.ReadingSeries;
import com.agrosense.frontend.dto.Views.SensorSummary;
import com.agrosense.frontend.dto.Views.SensorView;
import com.agrosense.frontend.entity.Crop;
import com.agrosense.frontend.entity.Sensor;
import com.agrosense.frontend.entity.SensorReading;
import com.agrosense.frontend.entity.enums.SensorType;
import com.agrosense.frontend.exception.NotFoundException;
import com.agrosense.frontend.repository.SensorReadingRepository;
import com.agrosense.frontend.repository.SensorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SensorService {

    /** Sensor types summarised on the dashboard, in display order. */
    public static final List<SensorType> DASHBOARD_TYPES = List.of(
            SensorType.SOIL_MOISTURE,
            SensorType.AIR_TEMPERATURE,
            SensorType.PH,
            SensorType.LIGHT,
            SensorType.CONDUCTIVITY);

    private static final int CHART_WINDOW_HOURS = 24;
    /** The most readings the backend returns in one call. */
    private static final int BACKEND_READING_LIMIT = 500;

    private final SensorRepository sensorRepository;
    private final SensorReadingRepository readingRepository;
    private final BackendClient backendClient;

    public List<SensorView> findSensors(String email) {
        List<Sensor> sensors = sensorRepository.findByCropEstateUserEmailOrderBySensorCode(email);
        Map<Integer, SensorReading> latest = latestReadings(sensors);
        return sensors.stream()
                .map(sensor -> toView(sensor, Optional.ofNullable(latest.get(sensor.getIdSensor()))))
                .toList();
    }

    /** The newest reading of every sensor, by sensor id, read in one query. */
    private Map<Integer, SensorReading> latestReadings(List<Sensor> sensors) {
        if (sensors.isEmpty()) {
            return Map.of();
        }
        // Two readings with the same timestamp are possible; either one describes "the latest".
        return readingRepository.findLatestOf(sensors.stream().map(Sensor::getIdSensor).toList()).stream()
                .collect(Collectors.toMap(reading -> reading.getSensor().getIdSensor(), Function.identity(),
                        (first, second) -> first));
    }

    /** Summaries without a trend line. */
    public List<SensorSummary> summarize(List<SensorView> sensors) {
        return summarize(sensors, Map.of());
    }

    public List<SensorSummary> summarize(List<SensorView> sensors, Map<SensorType, List<BigDecimal>> trends) {
        return DASHBOARD_TYPES.stream()
                .map(type -> summarize(type, sensors, Sparkline.points(trends.get(type))))
                .toList();
    }

    /** Hourly averages of the last 24 hours for each sensor type, oldest first, read in one query. */
    public Map<SensorType, List<BigDecimal>> findHourlyTrends(String email) {
        Map<SensorType, List<BigDecimal>> trends = new EnumMap<>(SensorType.class);
        readingRepository.findHourlyAverages(email, LocalDateTime.now().minusHours(CHART_WINDOW_HOURS))
                .forEach(row -> trends.computeIfAbsent((SensorType) row[0], type -> new ArrayList<>())
                        .add(BigDecimal.valueOf(((Number) row[2]).doubleValue())));
        return trends;
    }

    public ReadingSeries findLast24Hours(String email, Integer sensorId) {
        Sensor sensor = sensorRepository.findByIdSensorAndCropEstateUserEmail(sensorId, email)
                .orElseThrow(() -> new NotFoundException("Sensor not found"));
        LocalDateTime since = LocalDateTime.now().minusHours(CHART_WINDOW_HOURS);
        // The series comes from the backend API when it answers; the sensor and its range are read here.
        List<ReadingResponse> readings = backendClient
                .attempt(() -> backendClient.latestReadings(email, sensorId, BACKEND_READING_LIMIT))
                .map(latest -> latest.stream()
                        .filter(reading -> !reading.recordedAt().isBefore(since))
                        .sorted(Comparator.comparing(ReadingResponse::recordedAt))
                        .toList())
                .orElseGet(() -> readingRepository
                        .findBySensorIdSensorAndRecordedAtGreaterThanEqualOrderByRecordedAt(sensorId, since).stream()
                        .map(reading -> new ReadingResponse(
                                reading.getValue(), reading.getUnit(), reading.getRecordedAt()))
                        .toList());

        BigDecimal[] range = thresholds(sensor.getSensorType(), sensor.getCrop());
        return new ReadingSeries(
                sensor.getSensorCode(),
                readings.isEmpty() ? null : readings.get(readings.size() - 1).unit(),
                range[0],
                range[1],
                readings.stream()
                        .map(reading -> new ReadingPoint(reading.recordedAt(), reading.value()))
                        .toList());
    }

    private static SensorView toView(Sensor sensor, Optional<SensorReading> latest) {
        return new SensorView(
                sensor.getIdSensor(),
                sensor.getSensorCode(),
                sensor.getSensorType(),
                sensor.getCrop().getName(),
                sensor.getCrop().getEstate().getIdEstate(),
                sensor.getCrop().getEstate().getName(),
                sensor.getLocation(),
                Boolean.TRUE.equals(sensor.getActive()),
                latest.map(SensorReading::getValue).orElse(null),
                latest.map(SensorReading::getUnit).orElse(null),
                latest.map(SensorReading::getRecordedAt).orElse(sensor.getLastReadingAt()),
                sensor.getLatitude(),
                sensor.getLongitude(),
                sensor.getCoverageM());
    }

    private static SensorSummary summarize(SensorType type, List<SensorView> sensors, String sparkline) {
        List<SensorView> withData = sensors.stream()
                .filter(sensor -> sensor.type() == type && sensor.active() && sensor.lastValue() != null)
                .toList();
        if (withData.isEmpty()) {
            return new SensorSummary(type, null, null, 0, null);
        }
        BigDecimal total = withData.stream().map(SensorView::lastValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = total.divide(BigDecimal.valueOf(withData.size()), 1, RoundingMode.HALF_UP);
        return new SensorSummary(type, average, withData.get(0).unit(), withData.size(), sparkline);
    }

    /** The crop's acceptable {min, max} for what the sensor measures; both null when it has no range. */
    private static BigDecimal[] thresholds(SensorType type, Crop crop) {
        return switch (type) {
            case SOIL_MOISTURE, RELATIVE_HUMIDITY ->
                    new BigDecimal[] {crop.getHumidityMin(), crop.getHumidityMax()};
            case AIR_TEMPERATURE, SOIL_TEMPERATURE -> new BigDecimal[] {crop.getTempMin(), crop.getTempMax()};
            case PH -> new BigDecimal[] {crop.getPhMin(), crop.getPhMax()};
            default -> new BigDecimal[] {null, null};
        };
    }
}
