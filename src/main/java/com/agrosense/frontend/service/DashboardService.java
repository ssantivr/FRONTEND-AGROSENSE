package com.agrosense.frontend.service;

import com.agrosense.frontend.dto.Views.CropView;
import com.agrosense.frontend.dto.Views.DailyWater;
import com.agrosense.frontend.dto.Views.DashboardView;
import com.agrosense.frontend.dto.Views.EstateView;
import com.agrosense.frontend.dto.Views.IrrigationView;
import com.agrosense.frontend.dto.Views.SensorView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final int RECENT_ALERTS = 5;
    private static final int WATER_WINDOW_DAYS = 7;

    private final EstateService estateService;
    private final SensorService sensorService;
    private final AlertService alertService;
    private final IrrigationService irrigationService;

    public DashboardView build(String email) {
        List<EstateView> estates = estateService.findEstates(email);
        List<SensorView> sensors = sensorService.findSensors(email);
        List<IrrigationView> irrigations = irrigationService.findHistory(email);

        int activeCrops = (int) estates.stream()
                .flatMap(estate -> estate.crops().stream())
                .filter(CropView::active)
                .count();
        BigDecimal waterLiters = irrigationService.dailyWater(irrigations, WATER_WINDOW_DAYS).stream()
                .map(DailyWater::liters)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DashboardView(
                estates,
                activeCrops,
                waterLiters,
                sensorService.summarize(sensors),
                sensors.stream().filter(sensor -> sensor.active() && sensor.lastValue() != null).toList(),
                alertService.findRecentOpen(email, RECENT_ALERTS),
                estateService.findActiveCropOptions(email),
                irrigationService.findRunning(irrigations).orElse(null),
                estates.stream().anyMatch(estate -> estate.latitude() != null && estate.longitude() != null));
    }

    public List<DailyWater> waterUsage(String email) {
        return irrigationService.dailyWater(irrigationService.findHistory(email), WATER_WINDOW_DAYS);
    }
}
