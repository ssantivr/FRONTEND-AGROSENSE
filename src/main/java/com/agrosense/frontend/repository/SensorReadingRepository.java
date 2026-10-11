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

    /**
     * Average value per sensor type and hour since {@code since}, oldest first, over the user's active
     * sensors. Each row is {SensorType, hour, average}.
     */
    @Query("select r.sensor.sensorType, trunc(r.recordedAt, hour), avg(r.value) from SensorReading r "
            + "where r.sensor.crop.estate.user.email = :email and r.sensor.active = true and r.recordedAt >= :since "
            + "group by r.sensor.sensorType, trunc(r.recordedAt, hour) order by trunc(r.recordedAt, hour)")
    List<Object[]> findHourlyAverages(@Param("email") String email, @Param("since") LocalDateTime since);

    /**
     * Lowest, highest and average value and number of readings of each of the user's sensors since
     * {@code since}. Each row is {sensor id, min, max, average, count}; sensors without readings are absent.
     */
    @Query("select r.sensor.idSensor, min(r.value), max(r.value), avg(r.value), count(r) from SensorReading r "
            + "where r.sensor.crop.estate.user.email = :email and r.recordedAt >= :since group by r.sensor.idSensor")
    List<Object[]> findStatistics(@Param("email") String email, @Param("since") LocalDateTime since);

    /** The newest reading of each of these sensors, in one query instead of one per sensor. */
    @Query("select r from SensorReading r where r.sensor.idSensor in :sensorIds and r.recordedAt = "
            + "(select max(latest.recordedAt) from SensorReading latest where latest.sensor = r.sensor)")
    List<SensorReading> findLatestOf(@Param("sensorIds") Collection<Integer> sensorIds);

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
