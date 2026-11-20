package com.agrosense.frontend.controller;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.dto.MonitoringView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.time.ZoneId;

/** «Monitoreo»: what the backend does with the ESP32 while nobody looks, read from GET /api/devices/monitoring. */
@Controller
@RequiredArgsConstructor
public class MonitoringController {

    private final BackendClient backendClient;

    @GetMapping("/monitoreo")
    public String monitoring(Principal principal, Model model) {
        model.addAttribute("monitoring", backendClient.monitoring(principal.getName())
                .map(response -> MonitoringView.of(response, ZoneId.systemDefault()))
                .orElse(null));
        return "monitoring";
    }
}
