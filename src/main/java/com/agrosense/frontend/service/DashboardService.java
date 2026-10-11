package com.agrosense.frontend.service;

import com.agrosense.frontend.dto.Views.CropView;
import com.agrosense.frontend.dto.Views.DailyWater;
import com.agrosense.frontend.dto.Views.DashboardView;
import com.agrosense.frontend.dto.Views.EstateMarker;
import com.agrosense.frontend.dto.Views.EstateView;
import com.agrosense.frontend.dto.Views.IrrigationView;
import com.agrosense.frontend.dto.Views.RangeBar;
import com.agrosense.frontend.dto.Views.SensorSummary;
import com.agrosense.frontend.dto.Views.SensorView;
import com.agrosense.frontend.entity.enums.SensorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

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
        List<IrrigationView> irrigations = irrigationService.findSince(email, windowStart());
        List<DailyWater> waterUsage = irrigationService.dailyWater(irrigations, WATER_WINDOW_DAYS);

        List<SensorSummary> summaries = sensorService.summarize(sensors, sensorService.findHourlyTrends(email));

        // The sensors of each estate come from the list above; only the open alerts per estate need a query.
        List<EstateMarker> estateCards =
                estateService.cardsOf(estates, sensors, alertService.countOpenByEstate(email));

        int activeCrops = (int) estates.stream()
                .flatMap(estate -> estate.crops().stream())
                .filter(CropView::active)
                .count();
        BigDecimal waterLiters = waterUsage.stream()
                .map(DailyWater::liters)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DashboardView(
                estates,
                activeCrops,
                waterLiters,
                summaries,
                sensors.stream().filter(sensor -> sensor.active() && sensor.lastValue() != null).toList(),
                alertService.findRecentOpen(email, RECENT_ALERTS),
                // Derived from the estates above instead of being queried again.
                estateService.activeCropOptionsOf(estates),
                irrigationService.findRunning(irrigations).orElse(null),
                estateCards,
                estateCards.stream().filter(EstateMarker::hasCoordinates).toList(),
                waterUsage,
                humidityRange(estates, summaries),
                waterUsage.stream().allMatch(day -> day.liters().signum() == 0)
                        ? null
                        : Sparkline.points(waterUsage.stream().map(DailyWater::liters).toList()));
    }

    public List<DailyWater> waterUsage(String email) {
        return irrigationService.dailyWater(irrigationService.findSince(email, windowStart()), WATER_WINDOW_DAYS);
    }

    /**
     * Where the average soil moisture falls against the crops' acceptable range. Only when every active crop
     * shares one range: an average over crops with different ranges cannot be judged against any of them.
     */
    private static RangeBar humidityRange(List<EstateView> estates, List<SensorSummary> summaries) {
        BigDecimal value = summaries.stream()
                .filter(summary -> summary.type() == SensorType.SOIL_MOISTURE)
                .map(SensorSummary::value)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        List<List<BigDecimal>> ranges = estates.stream()
                .flatMap(estate -> estate.crops().stream())
                .filter(CropView::active)
                .filter(crop -> crop.humidityMin() != null && crop.humidityMax() != null)
                .map(crop -> List.of(crop.humidityMin().stripTrailingZeros(), crop.humidityMax().stripTrailingZeros()))
                .distinct()
                .toList();
        if (value == null || ranges.size() != 1) {
            return null;
        }
        BigDecimal min = ranges.get(0).get(0);
        BigDecimal max = ranges.get(0).get(1);
        // Soil moisture is a percentage, so the bar runs from 0 to 100.
        return new RangeBar(min.doubleValue(), max.doubleValue(),
                Math.max(0, Math.min(100, value.doubleValue())),
                min.toPlainString(), max.toPlainString(),
                value.compareTo(min) >= 0 && value.compareTo(max) <= 0);
    }

    /** Midnight of the first day of the water window. A running irrigation always started after it. */
    private static LocalDateTime windowStart() {
        return LocalDate.now().minusDays(WATER_WINDOW_DAYS - 1L).atStartOfDay();
    }
}
