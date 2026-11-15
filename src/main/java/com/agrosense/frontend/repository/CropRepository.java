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

    @EntityGraph(attributePaths = "estate")
    Optional<Crop> findWithEstateByIdCropAndEstateUserEmail(Integer idCrop, String email);

    /** The crop of a field device; the frontend maps no Device entity, it only needs where the device measures. */
    @Query(value = "select d.id_crop from devices d where upper(d.device_code) = upper(:code)", nativeQuery = true)
    Optional<Integer> findCropIdOfDevice(@Param("code") String deviceCode);
}
