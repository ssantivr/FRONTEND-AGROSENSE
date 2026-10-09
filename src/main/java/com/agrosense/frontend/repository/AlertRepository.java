package com.agrosense.frontend.repository;

import com.agrosense.frontend.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Integer> {

    @EntityGraph(attributePaths = {"crop", "crop.estate"})
    Page<Alert> findByCropEstateUserEmail(String email, Pageable pageable);

    @EntityGraph(attributePaths = {"crop", "crop.estate"})
    Page<Alert> findByCropEstateUserEmailAndAcknowledgedFalse(String email, Pageable pageable);

    long countByCropEstateUserEmailAndAcknowledgedFalse(String email);

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

    /** Keeps the alerts of a sensor that is being removed, without the reference to it. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Alert a set a.sensor = null where a.sensor.idSensor = :idSensor")
    void clearSensor(@Param("idSensor") Integer idSensor);
}
