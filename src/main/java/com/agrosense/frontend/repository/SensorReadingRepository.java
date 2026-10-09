package com.agrosense.frontend.repository;

import com.agrosense.frontend.entity.SensorReading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    Optional<SensorReading> findFirstBySensorIdSensorOrderByRecordedAtDesc(Integer idSensor);

    List<SensorReading> findBySensorIdSensorAndRecordedAtGreaterThanEqualOrderByRecordedAt(
            Integer idSensor, LocalDateTime since);
}
