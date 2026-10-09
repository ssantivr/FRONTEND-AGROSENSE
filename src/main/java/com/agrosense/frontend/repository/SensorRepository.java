package com.agrosense.frontend.repository;

import com.agrosense.frontend.entity.Sensor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SensorRepository extends JpaRepository<Sensor, Integer> {

    @EntityGraph(attributePaths = {"crop", "crop.estate"})
    List<Sensor> findByCropEstateUserEmailOrderBySensorCode(String email);

    @EntityGraph(attributePaths = "crop")
    Optional<Sensor> findByIdSensorAndCropEstateUserEmail(Integer idSensor, String email);
}
