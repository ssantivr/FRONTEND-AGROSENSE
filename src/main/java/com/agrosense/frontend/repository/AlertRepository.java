package com.agrosense.frontend.repository;

import com.agrosense.frontend.entity.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Integer> {

    @EntityGraph(attributePaths = {"crop", "crop.estate"})
    Page<Alert> findByCropEstateUserEmail(String email, Pageable pageable);

    @EntityGraph(attributePaths = {"crop", "crop.estate"})
    Page<Alert> findByCropEstateUserEmailAndAcknowledgedFalse(String email, Pageable pageable);

    long countByCropEstateUserEmailAndAcknowledgedFalse(String email);

    Optional<Alert> findByIdAlertAndCropEstateUserEmail(Integer idAlert, String email);
}
