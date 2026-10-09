package com.agrosense.frontend.repository;

import com.agrosense.frontend.entity.Irrigation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IrrigationRepository extends JpaRepository<Irrigation, Integer> {

    @EntityGraph(attributePaths = {"crop", "crop.estate"})
    List<Irrigation> findByCropEstateUserEmailOrderByStartedAtDesc(String email);

    List<Irrigation> findByCropIdCropOrderByStartedAtDesc(Integer idCrop);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Irrigation i where i.crop.idCrop in "
            + "(select c.idCrop from Crop c where c.estate.user.email = :email)")
    void deleteByOwner(@Param("email") String email);

    /** Keeps irrigations a user started on someone else's crop, without the reference to that user. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Irrigation i set i.activatedBy = null where i.activatedBy.idUser = :idUser")
    void clearActivatedBy(@Param("idUser") Integer idUser);
}
