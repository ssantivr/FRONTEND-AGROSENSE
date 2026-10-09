package com.agrosense.frontend.repository;

import com.agrosense.frontend.entity.Irrigation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IrrigationRepository extends JpaRepository<Irrigation, Integer> {

    @EntityGraph(attributePaths = {"crop", "crop.estate"})
    List<Irrigation> findByCropEstateUserEmailOrderByStartedAtDesc(String email);

    List<Irrigation> findByCropIdCropOrderByStartedAtDesc(Integer idCrop);
}
