package com.agrosense.frontend.controller;

import com.agrosense.frontend.dto.Views.SensorDetail;
import com.agrosense.frontend.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class SensorController {

    private final SensorService sensorService;

    /** The same device the backend reads over TCP (ESP32_DEVICE_ID). */
    @Value("${agrosense.esp32.device-id:RPI-DEMO-01}")
    private String esp32DeviceId;

    @GetMapping("/sensores/{sensorId:\\d+}")
    public String sensor(@PathVariable Integer sensorId, Principal principal, Model model) {
        SensorDetail detail = sensorService.findDetail(principal.getName(), sensorId);
        model.addAttribute("detail", detail);
        model.addAttribute("sensor", detail.sensor());
        return "sensor";
    }

    /** Only the humidity of the connected ESP32 and everything about the plant it measures. */
    @GetMapping("/sensor-conectado")
    public String connected(Principal principal, Model model) {
        model.addAttribute("deviceCode", esp32DeviceId);
        model.addAttribute("connected", sensorService.findConnected(principal.getName(), esp32DeviceId).orElse(null));
        return "connected-sensor";
    }
}
