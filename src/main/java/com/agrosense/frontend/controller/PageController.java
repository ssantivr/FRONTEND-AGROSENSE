package com.agrosense.frontend.controller;

import com.agrosense.frontend.dto.IrrigationForm;
import com.agrosense.frontend.dto.Views;
import com.agrosense.frontend.dto.Views.AlertView;
import com.agrosense.frontend.dto.Views.DashboardView;
import com.agrosense.frontend.dto.Views.EstateView;
import com.agrosense.frontend.dto.Views.ReportView;
import com.agrosense.frontend.service.ReportService;
import org.springframework.web.bind.annotation.RequestParam;
import com.agrosense.frontend.dto.Views.SensorView;
import com.agrosense.frontend.exception.NotFoundException;
import com.agrosense.frontend.service.AlertService;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.LinkedHashMap;
import java.util.Map;
import com.agrosense.frontend.service.DashboardService;
import com.agrosense.frontend.service.EstateService;
import com.agrosense.frontend.service.IrrigationService;
import com.agrosense.frontend.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import tools.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper;
    private final AlertService alertService;
    private final ReportService reportService;

    private static final int ESTATE_ALERTS = 10;
    private static final int ESTATE_IRRIGATIONS = 10;

    @GetMapping("/login")
    public String login(Principal principal) {
        if (principal != null) {
            return "redirect:/";
        }
        return "login";
    }

    /** The dashboard for a signed-in user; the public front page for everybody else. */
    @GetMapping("/")
    public String dashboard(Principal principal, Model model) {
        if (principal == null) {
            return "landing";
        }
        DashboardView dashboard = dashboardService.build(principal.getName());
        model.addAttribute("dashboard", dashboard);
        // The map and the water chart draw from these, in the shape /api/ui sends, without asking again.
        model.addAttribute("markersJson", objectMapper.writeValueAsString(dashboard.markers()));
        model.addAttribute("waterUsageJson", objectMapper.writeValueAsString(dashboard.waterUsage()));
        model.addAttribute("irrigationForm", new IrrigationForm());
        return "dashboard";
    }

    @GetMapping("/parcelas")
    public String estates(Principal principal, Model model) {
        model.addAttribute("estates", estateService.findEstates(principal.getName()));
        return "estates";
    }

    /** Everything about one estate in one place: its map, crops, sensors, irrigations and open alerts. */
    @GetMapping("/parcelas/{estateId}")
    public String estate(@PathVariable Integer estateId, Principal principal, Model model) {
        String email = principal.getName();
        List<EstateView> estates = estateService.findEstates(email);
        EstateView estate = estates.stream()
                .filter(candidate -> candidate.id().equals(estateId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Estate not found"));
        List<SensorView> sensors = sensorService.findSensors(email).stream()
                .filter(sensor -> sensor.estateId().equals(estateId))
                .toList();
        List<AlertView> alerts = alertService.findOpenByEstate(email, estateId, ESTATE_ALERTS);
        model.addAttribute("estate", estate);
        model.addAttribute("sensors", sensors);
        model.addAttribute("alerts", alerts);
        model.addAttribute("irrigations", irrigationService.findByEstate(email, estateId, ESTATE_IRRIGATIONS));
        // One marker, in the shape the dashboard map uses, so the same drawing code serves both pages.
        model.addAttribute("estateJson", objectMapper.writeValueAsString(
                estateService.cardsOf(List.of(estate), sensors, Map.of(estateId, alerts.size())).get(0)));
        return "estate";
    }

    @GetMapping("/sensores")
    public String sensors(Principal principal, Model model) {
        model.addAttribute("sensors", sensorService.findSensors(principal.getName()));
        return "sensors";
    }

    @GetMapping("/reportes")
    public String reports(@RequestParam(name = "dias", required = false) Integer days, Principal principal,
            Model model) {
        ReportView report = reportService.build(principal.getName(), days == null ? ReportService.DEFAULT_PERIOD : days);
        model.addAttribute("report", report);
        model.addAttribute("periods", ReportService.PERIODS);
        // The same figures the tables show, for the charts.
        Map<String, Object> charts = new LinkedHashMap<>();
        charts.put("byCrop", report.byCrop().stream()
                .map(row -> Map.of("label", row.cropName(), "estate", row.estateName(), "liters", row.totalLiters(),
                        "tone", Views.toneOf(row.cropName())))
                .toList());
        charts.put("byEstate", report.byEstate().stream()
                .map(row -> {
                    Map<String, Object> estate = new LinkedHashMap<>();
                    estate.put("label", row.estateName());
                    estate.put("liters", row.liters());
                    estate.put("litersPerHa", row.litersPerHa());
                    return estate;
                })
                .toList());
        charts.put("byType", report.byType().stream()
                .map(row -> Map.of("type", row.type().name(), "liters", row.liters(), "irrigations", row.irrigations()))
                .toList());
        charts.put("daily", report.daily());
        model.addAttribute("chartsJson", objectMapper.writeValueAsString(charts));
        return "reports";
    }
}
