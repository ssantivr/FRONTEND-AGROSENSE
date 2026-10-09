package com.agrosense.frontend.controller;

import com.agrosense.frontend.dto.IrrigationForm;
import com.agrosense.frontend.dto.Views.IrrigationView;
import com.agrosense.frontend.service.DashboardService;
import com.agrosense.frontend.service.EstateService;
import com.agrosense.frontend.service.IrrigationService;
import com.agrosense.frontend.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.List;

/** Read-only pages. Pages that accept form posts have their own controllers. */
@Controller
@RequiredArgsConstructor
public class PageController {

    private final DashboardService dashboardService;
    private final EstateService estateService;
    private final SensorService sensorService;
    private final IrrigationService irrigationService;

    @Value("${agrosense.demo.email:}")
    private String demoEmail;

    @Value("${agrosense.demo.password:}")
    private String demoPassword;

    @GetMapping("/login")
    public String login(Principal principal, Model model) {
        if (principal != null) {
            return "redirect:/";
        }
        model.addAttribute("demoEmail", demoEmail);
        model.addAttribute("demoPassword", demoPassword);
        return "login";
    }

    @GetMapping("/")
    public String dashboard(Principal principal, Model model) {
        model.addAttribute("dashboard", dashboardService.build(principal.getName()));
        model.addAttribute("irrigationForm", new IrrigationForm());
        return "dashboard";
    }

    @GetMapping("/parcelas")
    public String estates(Principal principal, Model model) {
        model.addAttribute("estates", estateService.findEstates(principal.getName()));
        return "estates";
    }

    @GetMapping("/sensores")
    public String sensors(Principal principal, Model model) {
        model.addAttribute("sensors", sensorService.findSensors(principal.getName()));
        return "sensors";
    }

    @GetMapping("/dispositivos")
    public String devices() {
        return "devices";
    }

    @GetMapping("/reportes")
    public String reports(Principal principal, Model model) {
        List<IrrigationView> history = irrigationService.findHistory(principal.getName());
        model.addAttribute("waterReport", irrigationService.waterReport(history));
        return "reports";
    }

    @GetMapping("/configuracion")
    public String settings() {
        return "settings";
    }
}
