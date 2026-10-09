package com.agrosense.frontend.repository;

import com.agrosense.frontend.entity.Estate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstateRepository extends JpaRepository<Estate, Integer> {

    @EntityGraph(attributePaths = "crops")
    List<Estate> findByUserEmailOrderByName(String email);
}
