package com.agrosense.frontend.repository;

import com.agrosense.frontend.entity.SensorReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    Optional<SensorReading> findFirstBySensorIdSensorOrderByRecordedAtDesc(Integer idSensor);

    List<SensorReading> findBySensorIdSensorAndRecordedAtGreaterThanEqualOrderByRecordedAt(
            Integer idSensor, LocalDateTime since);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SensorReading r where r.sensor.idSensor in "
            + "(select s.idSensor from Sensor s where s.crop.estate.user.email = :email)")
    void deleteByOwner(@Param("email") String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SensorReading r where r.sensor.idSensor in "
            + "(select s.idSensor from Sensor s where s.crop.estate.idEstate = :idEstate)")
    void deleteByEstate(@Param("idEstate") Integer idEstate);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SensorReading r where r.sensor.idSensor in "
            + "(select s.idSensor from Sensor s where s.crop.idCrop = :idCrop)")
    void deleteByCrop(@Param("idCrop") Integer idCrop);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SensorReading r where r.sensor.idSensor = :idSensor")
    void deleteBySensor(@Param("idSensor") Integer idSensor);
}
