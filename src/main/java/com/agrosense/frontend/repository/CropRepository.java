package com.agrosense.frontend.repository;

import com.agrosense.frontend.entity.Crop;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CropRepository extends JpaRepository<Crop, Integer> {

    @EntityGraph(attributePaths = "estate")
    List<Crop> findByEstateUserEmailAndActiveTrueOrderByName(String email);

    Optional<Crop> findByIdCropAndEstateUserEmail(Integer idCrop, String email);

    /** The crop a field device measures, with its estate, only when it belongs to this user: one statement. */
    @Query("select c from Crop c join fetch c.estate e where e.user.email = :email and c.idCrop in "
            + "(select d.idCrop from Device d where upper(d.deviceCode) = upper(:code))")
    List<Crop> findMeasuredByDevice(@Param("code") String deviceCode, @Param("email") String email);
}
