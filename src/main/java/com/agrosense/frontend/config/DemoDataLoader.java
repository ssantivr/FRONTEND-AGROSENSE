package com.agrosense.frontend.config;

import com.agrosense.frontend.entity.Alert;
import com.agrosense.frontend.entity.Crop;
import com.agrosense.frontend.entity.Estate;
import com.agrosense.frontend.entity.Irrigation;
import com.agrosense.frontend.entity.Sensor;
import com.agrosense.frontend.entity.SensorReading;
import com.agrosense.frontend.entity.User;
import com.agrosense.frontend.entity.enums.AlertType;
import com.agrosense.frontend.entity.enums.CropStage;
import com.agrosense.frontend.entity.enums.IrrigationType;
import com.agrosense.frontend.entity.enums.SensorType;
import com.agrosense.frontend.entity.enums.Severity;
import com.agrosense.frontend.repository.AlertRepository;
import com.agrosense.frontend.repository.CropRepository;
import com.agrosense.frontend.repository.EstateRepository;
import com.agrosense.frontend.repository.IrrigationRepository;
import com.agrosense.frontend.repository.SensorReadingRepository;
import com.agrosense.frontend.repository.SensorRepository;
import com.agrosense.frontend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Fills the in-memory database with sample data. Only runs with the "demo" profile. */
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataLoader implements ApplicationRunner {

    private static final int READING_HOURS = 24;

    private final UserRepository userRepository;
    private final EstateRepository estateRepository;
    private final CropRepository cropRepository;
    private final SensorRepository sensorRepository;
    private final SensorReadingRepository readingRepository;
    private final AlertRepository alertRepository;
    private final IrrigationRepository irrigationRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${agrosense.demo.email}")
    private String demoEmail;

    @Value("${agrosense.demo.password}")
    private String demoPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.findByEmail(demoEmail).isPresent()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        User user = userRepository.save(User.builder()
                .name("Usuario")
                .lastName("Demo")
                .email(demoEmail)
                .passwordHash(passwordEncoder.encode(demoPassword))
                .role("farmer")
                .build());

        Estate esperanza = estateRepository.save(Estate.builder()
                .user(user)
                .name("Finca La Esperanza")
                .location("Pasto, Nariño")
                .latitude(new BigDecimal("1.2136000"))
                .longitude(new BigDecimal("-77.2811000"))
                .areaHa(new BigDecimal("12.50"))
                .build());
        Estate mirador = estateRepository.save(Estate.builder()
                .user(user)
                .name("Finca El Mirador")
                .location("La Unión, Nariño")
                .latitude(new BigDecimal("1.6044000"))
                .longitude(new BigDecimal("-77.1317000"))
                .areaHa(new BigDecimal("6.00"))
                .build());

        Crop coffee = saveCrop(esperanza, "Café", "Castillo", LocalDate.now().minusMonths(18), CropStage.FLOWERING);
        Crop plantain = saveCrop(esperanza, "Plátano", "Hartón", LocalDate.now().minusMonths(10), CropStage.GROWTH);
        Crop potato = saveCrop(mirador, "Papa", "Pastusa", LocalDate.now().minusMonths(2), CropStage.GERMINATION);

        saveSensorWithReadings(coffee, "AS-001", SensorType.SOIL_MOISTURE, "Lote norte", "%", 52, 14, now);
        saveSensorWithReadings(coffee, "AS-002", SensorType.AIR_TEMPERATURE, "Lote norte", "°C", 21, 6, now);
        saveSensorWithReadings(coffee, "AS-003", SensorType.PH, "Lote sur", "pH", 6.2, 0.3, now);
        saveSensorWithReadings(plantain, "AS-004", SensorType.SOIL_MOISTURE, "Platanera", "%", 66, 9, now);
        saveSensorWithReadings(plantain, "AS-005", SensorType.CONDUCTIVITY, "Platanera", "dS/m", 1.4, 0.2, now);
        sensorRepository.save(Sensor.builder()
                .crop(potato)
                .sensorCode("AS-006")
                .sensorType(SensorType.LIGHT)
                .location("Parcela 1")
                .active(false)
                .lastReadingAt(now.minusDays(3))
                .build());

        saveAlert(coffee, AlertType.LOW_HUMIDITY, Severity.HIGH,
                "La humedad del suelo está por debajo del mínimo configurado.", "37.4", now.minusHours(2), false);
        saveAlert(plantain, AlertType.RECOMMENDED_WATERING, Severity.MEDIUM,
                "Se recomienda regar en las próximas horas.", "58.1", now.minusHours(5), false);
        saveAlert(potato, AlertType.PEST_DETECTED, Severity.VERY_HIGH,
                "Posible presencia de plaga reportada en la parcela.", null, now.minusHours(9), false);
        saveAlert(coffee, AlertType.HIGH_TEMPERATURE, Severity.LOW,
                "La temperatura superó brevemente el máximo.", "35.6", now.minusHours(27), true);

        saveIrrigation(coffee, user, now.minusDays(1).minusHours(3), 30, "450", IrrigationType.AUTOMATIC);
        saveIrrigation(plantain, user, now.minusDays(2).minusHours(1), 45, "680", IrrigationType.MANUAL);
        saveIrrigation(coffee, user, now.minusDays(4), 25, "380", IrrigationType.AUTOMATIC);
        saveIrrigation(potato, user, now.minusDays(5).minusHours(6), 20, "240", IrrigationType.MANUAL);
    }

    private Crop saveCrop(Estate estate, String name, String variety, LocalDate sowingDate, CropStage stage) {
        return cropRepository.save(Crop.builder()
                .estate(estate)
                .name(name)
                .variety(variety)
                .sowingDate(sowingDate)
                .stage(stage)
                .build());
    }

    /** Stores a sensor with one reading per hour that follows a daily wave around {@code base}. */
    private void saveSensorWithReadings(Crop crop, String code, SensorType type, String location, String unit,
            double base, double amplitude, LocalDateTime now) {
        Sensor sensor = sensorRepository.save(Sensor.builder()
                .crop(crop)
                .sensorCode(code)
                .sensorType(type)
                .location(location)
                .lastReadingAt(now)
                .build());
        List<SensorReading> readings = new ArrayList<>();
        for (int hour = READING_HOURS - 1; hour >= 0; hour--) {
            double wave = Math.sin((READING_HOURS - 1 - hour) / (double) READING_HOURS * Math.PI * 2);
            double ripple = Math.sin(hour * 12.9898 + code.hashCode()) * amplitude * 0.12;
            readings.add(SensorReading.builder()
                    .sensor(sensor)
                    .value(BigDecimal.valueOf(base + wave * amplitude + ripple).setScale(2, RoundingMode.HALF_UP))
                    .unit(unit)
                    .recordedAt(now.minusHours(hour))
                    .build());
        }
        readingRepository.saveAll(readings);
    }

    private void saveAlert(Crop crop, AlertType type, Severity severity, String message, String detectedValue,
            LocalDateTime createdAt, boolean acknowledged) {
        alertRepository.save(Alert.builder()
                .crop(crop)
                .alertType(type)
                .severity(severity)
                .message(message)
                .detectedValue(detectedValue == null ? null : new BigDecimal(detectedValue))
                .acknowledged(acknowledged)
                .createdAt(createdAt)
                .build());
    }

    private void saveIrrigation(Crop crop, User user, LocalDateTime startedAt, int durationMin, String liters,
            IrrigationType type) {
        irrigationRepository.save(Irrigation.builder()
                .crop(crop)
                .startedAt(startedAt)
                .endedAt(startedAt.plusMinutes(durationMin))
                .durationMin(durationMin)
                .waterLiters(new BigDecimal(liters))
                .type(type)
                .activatedBy(type == IrrigationType.MANUAL ? user : null)
                .build());
    }
}
