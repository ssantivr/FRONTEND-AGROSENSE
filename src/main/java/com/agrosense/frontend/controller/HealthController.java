package com.agrosense.frontend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private static final Map<String, String> UP = Map.of("status", "UP");

    @GetMapping("/health")
    public Map<String, String> health() {
        return UP;
    }
}
