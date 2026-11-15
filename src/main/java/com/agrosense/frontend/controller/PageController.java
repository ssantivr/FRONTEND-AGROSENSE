package com.agrosense.frontend.controller;

import com.agrosense.frontend.dto.IrrigationForm;
import com.agrosense.frontend.dto.Views;
import com.agrosense.frontend.dto.Views.AlertView;
import com.agrosense.frontend.dto.Views.ConnectedSensor;
import com.agrosense.frontend.dto.Views.Esp32Silence;
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
import com.agrosense.frontend.service.Esp32SilencePolicy;
import com.agrosense.frontend.service.EstateService;
import com.agrosense.frontend.service.IrrigationService;
import com.agrosense.frontend.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import tools.jackson.databind.ObjectMapper;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

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
    private final Esp32SilencePolicy esp32SilencePolicy;

    /** The same device the backend reads over TCP (ESP32_DEVICE_ID). */
    @Value("${agrosense.esp32.device-id:ESP32-PLANTICAS}")
    private String esp32DeviceId;

    private static final int ESTATE_ALERTS = 10;
    private static final int ESTATE_IRRIGATIONS = 10;

    @GetMapping("/login")
    public String login(Principal principal) {
        if (principal != null) {
            return "redirect:/";
        }
        return "login";
    }

    @GetMapping("/")
    public String dashboard(Principal principal, Model model) {
        if (principal == null) {
            return "landing";
        }
        DashboardView dashboard = dashboardService.build(principal.getName());
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("markersJson", objectMapper.writeValueAsString(dashboard.markers()));
        model.addAttribute("waterUsageJson", objectMapper.writeValueAsString(dashboard.waterUsage()));
        model.addAttribute("irrigationForm", new IrrigationForm());
        return "dashboard";
    }

    @GetMapping("/parcelas")
    public String estates(Principal principal, Model model) {
        List<EstateView> estates = estateService.findEstates(principal.getName());
        model.addAttribute("estates", estates);
        model.addAttribute("readingsByCrop", readingsByCrop(estates, sensorService.findSensors(principal.getName())));
        return "estates";
    }

    /** What each crop's activated sensors measure, so a crop shows only its own sensors' data. */
    private static Map<Integer, List<SensorView>> readingsByCrop(List<EstateView> estates, List<SensorView> sensors) {
        Map<Integer, List<SensorView>> readings = new LinkedHashMap<>();
        estates.forEach(estate -> estate.crops()
                .forEach(crop -> readings.put(crop.id(), SensorView.readingsOf(crop.id(), sensors))));
        return readings;
    }

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
        model.addAttribute("readingsByCrop", readingsByCrop(List.of(estate), sensors));
        model.addAttribute("alerts", alerts);
        model.addAttribute("irrigations", irrigationService.findByEstate(email, estateId, ESTATE_IRRIGATIONS));
        model.addAttribute("estateJson", objectMapper.writeValueAsString(
                estateService.cardsOf(List.of(estate), sensors, Map.of(estateId, alerts.size())).get(0)));
        return "estate";
    }

    @GetMapping("/sensores")
    public String sensors(Principal principal, Model model) {
        List<SensorView> sensors = sensorService.findSensors(principal.getName());
        model.addAttribute("sensors", sensors);
        // The connected ESP32: its plant and only its humidity, shown in the same page.
        model.addAttribute("deviceCode", esp32DeviceId);
        ConnectedSensor connected = sensorService.findConnected(principal.getName(), esp32DeviceId, sensors).orElse(null);
        model.addAttribute("connected", connected);
        model.addAttribute("esp32Silence", connected == null ? Esp32Silence.LISTENING : esp32SilencePolicy.of(
                connected.readings().isEmpty() ? null : connected.readings().get(0).recordedAt(), LocalDateTime.now()));
        return "sensors";
    }

    @GetMapping("/reportes")
    public String reports(@RequestParam(name = "dias", required = false) Integer days, Principal principal,
            Model model) {
        ReportView report = reportService.build(principal.getName(), days == null ? ReportService.DEFAULT_PERIOD : days);
        model.addAttribute("report", report);
        model.addAttribute("periods", ReportService.PERIODS);
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
