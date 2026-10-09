package com.agrosense.frontend.service;

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
import java.util.List;
import java.util.Optional;

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

    private final SensorRepository sensorRepository;
    private final SensorReadingRepository readingRepository;

    public List<SensorView> findSensors(String email) {
        return sensorRepository.findByCropEstateUserEmailOrderBySensorCode(email).stream()
                .map(this::toView)
                .toList();
    }

    public List<SensorSummary> summarize(List<SensorView> sensors) {
        return DASHBOARD_TYPES.stream().map(type -> summarize(type, sensors)).toList();
    }

    public ReadingSeries findLast24Hours(String email, Integer sensorId) {
        Sensor sensor = sensorRepository.findByIdSensorAndCropEstateUserEmail(sensorId, email)
                .orElseThrow(() -> new NotFoundException("Sensor not found"));
        List<SensorReading> readings = readingRepository
                .findBySensorIdSensorAndRecordedAtGreaterThanEqualOrderByRecordedAt(
                        sensorId, LocalDateTime.now().minusHours(CHART_WINDOW_HOURS));

        BigDecimal[] range = thresholds(sensor.getSensorType(), sensor.getCrop());
        return new ReadingSeries(
                sensor.getSensorCode(),
                readings.isEmpty() ? null : readings.get(readings.size() - 1).getUnit(),
                range[0],
                range[1],
                readings.stream()
                        .map(reading -> new ReadingPoint(reading.getRecordedAt(), reading.getValue()))
                        .toList());
    }

    private SensorView toView(Sensor sensor) {
        Optional<SensorReading> latest =
                readingRepository.findFirstBySensorIdSensorOrderByRecordedAtDesc(sensor.getIdSensor());
        return new SensorView(
                sensor.getIdSensor(),
                sensor.getSensorCode(),
                sensor.getSensorType(),
                sensor.getCrop().getName(),
                sensor.getCrop().getEstate().getName(),
                sensor.getLocation(),
                Boolean.TRUE.equals(sensor.getActive()),
                latest.map(SensorReading::getValue).orElse(null),
                latest.map(SensorReading::getUnit).orElse(null),
                latest.map(SensorReading::getRecordedAt).orElse(sensor.getLastReadingAt()));
    }

    private static SensorSummary summarize(SensorType type, List<SensorView> sensors) {
        List<SensorView> withData = sensors.stream()
                .filter(sensor -> sensor.type() == type && sensor.active() && sensor.lastValue() != null)
                .toList();
        if (withData.isEmpty()) {
            return new SensorSummary(type, null, null, 0);
        }
        BigDecimal total = withData.stream().map(SensorView::lastValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = total.divide(BigDecimal.valueOf(withData.size()), 1, RoundingMode.HALF_UP);
        return new SensorSummary(type, average, withData.get(0).unit(), withData.size());
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
