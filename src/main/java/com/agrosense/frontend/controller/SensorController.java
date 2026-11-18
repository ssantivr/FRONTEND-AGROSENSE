package com.agrosense.frontend.controller;

import com.agrosense.frontend.dto.Views.SensorDetail;
import com.agrosense.frontend.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class SensorController {

    private final SensorService sensorService;

    @GetMapping("/sensores/{sensorId:\\d+}")
    public String sensor(@PathVariable Integer sensorId, Principal principal, Model model) {
        SensorDetail detail = sensorService.findDetail(principal.getName(), sensorId);
        model.addAttribute("detail", detail);
        model.addAttribute("sensor", detail.sensor());
        return "sensor";
    }
}
