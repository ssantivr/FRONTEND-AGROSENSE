package com.agrosense.frontend.repository;

import com.agrosense.frontend.entity.Alert;
import com.agrosense.frontend.entity.enums.Severity;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Integer> {

    @EntityGraph(attributePaths = {"crop", "crop.estate"})
    Page<Alert> findByCropEstateUserEmail(String email, Pageable pageable);

    @EntityGraph(attributePaths = {"crop", "crop.estate"})
    Page<Alert> findByCropEstateUserEmailAndAcknowledgedFalse(String email, Pageable pageable);

    @EntityGraph(attributePaths = {"crop", "crop.estate"})
    List<Alert> findByCropEstateUserEmailAndAcknowledgedFalseOrderByCreatedAtDesc(String email, Limit limit);

    @EntityGraph(attributePaths = {"crop", "crop.estate"})
    Page<Alert> findByCropEstateUserEmailAndSeverity(String email, Severity severity, Pageable pageable);

    @EntityGraph(attributePaths = {"crop", "crop.estate"})
    Page<Alert> findByCropEstateUserEmailAndAcknowledgedFalseAndSeverity(String email, Severity severity,
            Pageable pageable);

    @EntityGraph(attributePaths = {"crop", "crop.estate"})
    List<Alert> findByCropIdCropAndCropEstateUserEmailOrderByCreatedAtDesc(Integer idCrop, String email, Limit limit);

    @Query("select a.crop.idCrop, count(a) from Alert a "
            + "where a.crop.estate.user.email = :email and a.acknowledged = false group by a.crop.idCrop")
    List<Object[]> countOpenByCrop(@Param("email") String email);

    @EntityGraph(attributePaths = {"crop", "crop.estate"})
    List<Alert> findByCropEstateIdEstateAndCropEstateUserEmailAndAcknowledgedFalseOrderByCreatedAtDesc(
            Integer idEstate, String email, Limit limit);

    long countByCropEstateUserEmailAndAcknowledgedFalse(String email);

    @Query("select a.alertType, a.severity, a.acknowledged, count(a) from Alert a "
            + "where a.crop.estate.user.email = :email and a.createdAt >= :since "
            + "group by a.alertType, a.severity, a.acknowledged")
    List<Object[]> countByKindSince(@Param("email") String email, @Param("since") java.time.LocalDateTime since);

    @Query("select a.crop.estate.idEstate, count(a) from Alert a "
            + "where a.crop.estate.user.email = :email and a.acknowledged = false group by a.crop.estate.idEstate")
    List<Object[]> countOpenByEstate(@Param("email") String email);

    Optional<Alert> findByIdAlertAndCropEstateUserEmail(Integer idAlert, String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Alert a where a.crop.idCrop in "
            + "(select c.idCrop from Crop c where c.estate.user.email = :email)")
    void deleteByOwner(@Param("email") String email);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Alert a where a.crop.idCrop in "
            + "(select c.idCrop from Crop c where c.estate.idEstate = :idEstate)")
    void deleteByEstate(@Param("idEstate") Integer idEstate);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Alert a where a.crop.idCrop = :idCrop")
    void deleteByCrop(@Param("idCrop") Integer idCrop);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Alert a set a.sensor = null where a.sensor.idSensor = :idSensor")
    void clearSensor(@Param("idSensor") Integer idSensor);
}
