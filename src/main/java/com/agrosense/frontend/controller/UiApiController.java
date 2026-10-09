package com.agrosense.frontend.controller;

import com.agrosense.frontend.dto.IrrigationForm;
import com.agrosense.frontend.dto.Views.DailyWater;
import com.agrosense.frontend.dto.Views.EstateMarker;
import com.agrosense.frontend.dto.Views.IrrigationView;
import com.agrosense.frontend.dto.Views.ReadingSeries;
import com.agrosense.frontend.service.DashboardService;
import com.agrosense.frontend.service.EstateService;
import com.agrosense.frontend.service.IrrigationService;
import com.agrosense.frontend.service.SensorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/** JSON consumed by the dashboard's own scripts (charts, map, irrigation button). Session-authenticated. */
@RestController
@RequestMapping("/api/ui")
@RequiredArgsConstructor
public class UiApiController {

    private final SensorService sensorService;
    private final EstateService estateService;
    private final IrrigationService irrigationService;
    private final DashboardService dashboardService;

    @GetMapping("/sensors/{sensorId}/readings")
    public ReadingSeries readings(@PathVariable Integer sensorId, Principal principal) {
        return sensorService.findLast24Hours(principal.getName(), sensorId);
    }

    @GetMapping("/estates/markers")
    public List<EstateMarker> markers(Principal principal) {
        return estateService.findMarkers(principal.getName());
    }

    @GetMapping("/water-usage")
    public List<DailyWater> waterUsage(Principal principal) {
        return dashboardService.waterUsage(principal.getName());
    }

    @PostMapping("/irrigations")
    @ResponseStatus(HttpStatus.CREATED)
    public IrrigationView startIrrigation(@Valid @RequestBody IrrigationForm form, Principal principal) {
        return irrigationService.start(principal.getName(), form);
    }
}
