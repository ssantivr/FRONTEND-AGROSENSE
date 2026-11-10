package com.agrosense.frontend.controller;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.backend.BackendStatus;
import com.agrosense.frontend.dto.IrrigationForm;
import com.agrosense.frontend.dto.Views.CropLatest;
import com.agrosense.frontend.dto.Views.DailyWater;
import com.agrosense.frontend.dto.Views.EstateMarker;
import com.agrosense.frontend.dto.Views.IrrigationView;
import com.agrosense.frontend.dto.Views.ReadingSeries;
import com.agrosense.frontend.service.CropService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/ui")
@RequiredArgsConstructor
public class UiApiController {

    private final SensorService sensorService;
    private final EstateService estateService;
    private final IrrigationService irrigationService;
    private final DashboardService dashboardService;
    private final BackendClient backendClient;
    private final CropService cropService;

    @GetMapping("/sensors/{sensorId}/readings")
    public ReadingSeries readings(@PathVariable Integer sensorId,
            @RequestParam(defaultValue = "24") int hours, Principal principal) {
        return sensorService.findReadings(principal.getName(), sensorId, hours);
    }

    /** Polled by the crop page (crop-live.ts); only the owner's crops, a foreign one is 404. */
    @GetMapping("/crops/{cropId}/latest")
    public CropLatest latest(@PathVariable Integer cropId, Principal principal) {
        return cropService.findLatest(principal.getName(), cropId);
    }

    @GetMapping("/backend-status")
    public BackendStatus backendStatus() {
        return backendClient.status();
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
