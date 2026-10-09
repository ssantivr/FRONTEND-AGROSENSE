package com.agrosense.frontend.repository;

import com.agrosense.frontend.entity.Crop;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CropRepository extends JpaRepository<Crop, Integer> {

    @EntityGraph(attributePaths = "estate")
    List<Crop> findByEstateUserEmailAndActiveTrueOrderByName(String email);

    Optional<Crop> findByIdCropAndEstateUserEmail(Integer idCrop, String email);
}
