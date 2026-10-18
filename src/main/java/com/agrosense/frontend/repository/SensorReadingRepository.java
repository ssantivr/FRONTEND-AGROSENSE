package com.agrosense.frontend.repository;

import com.agrosense.frontend.entity.SensorReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    @Query("select r.sensor.sensorType, trunc(r.recordedAt, hour), avg(r.value) from SensorReading r "
            + "where r.sensor.crop.estate.user.email = :email and r.sensor.active = true and r.recordedAt >= :since "
            + "group by r.sensor.sensorType, trunc(r.recordedAt, hour) order by trunc(r.recordedAt, hour)")
    List<Object[]> findHourlyAverages(@Param("email") String email, @Param("since") LocalDateTime since);

    @Query("select r.sensor.idSensor, min(r.value), max(r.value), avg(r.value), count(r) from SensorReading r "
            + "where r.sensor.crop.estate.user.email = :email and r.recordedAt >= :since group by r.sensor.idSensor")
    List<Object[]> findStatistics(@Param("email") String email, @Param("since") LocalDateTime since);

    @Query("select r from SensorReading r where r.sensor.idSensor in :sensorIds and r.recordedAt = "
            + "(select max(latest.recordedAt) from SensorReading latest where latest.sensor = r.sensor)")
    List<SensorReading> findLatestOf(@Param("sensorIds") Collection<Integer> sensorIds);

    List<SensorReading> findBySensorIdSensorAndRecordedAtGreaterThanEqualOrderByRecordedAt(
            Integer idSensor, LocalDateTime since);

    List<SensorReading> findTop10BySensorIdSensorOrderByRecordedAtDesc(Integer idSensor);

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
