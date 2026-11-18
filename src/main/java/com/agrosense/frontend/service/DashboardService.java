package com.agrosense.frontend.service;

import com.agrosense.frontend.dto.Views.ActivityItem;
import com.agrosense.frontend.dto.Views.AlertView;
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
import com.agrosense.frontend.dto.Views;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;
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
    private static final int ACTIVITY_ITEMS = 6;
    private static final Locale SPANISH = Locale.forLanguageTag("es");

    private final EstateService estateService;
    private final SensorService sensorService;
    private final AlertService alertService;
    private final IrrigationService irrigationService;
    private final MessageSource messageSource;

    public DashboardView build(String email) {
        List<EstateView> estates = estateService.findEstates(email);
        List<SensorView> sensors = sensorService.findSensors(email);
        List<IrrigationView> irrigations = irrigationService.findSince(email, windowStart());
        List<DailyWater> waterUsage = irrigationService.dailyWater(irrigations, WATER_WINDOW_DAYS);

        List<SensorSummary> summaries = sensorService.summarize(sensors, sensorService.findHourlyTrends(email));

        List<AlertView> recentAlerts = alertService.findRecentOpen(email, RECENT_ALERTS);

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
                recentAlerts,
                estateService.activeCropOptionsOf(estates),
                irrigationService.findRunning(irrigations).orElse(null),
                estateCards,
                estateCards.stream().filter(EstateMarker::hasCoordinates).toList(),
                waterUsage,
                humidityRange(estates, summaries),
                waterUsage.stream().allMatch(day -> day.liters().signum() == 0)
                        ? null
                        : Sparkline.points(waterUsage.stream().map(DailyWater::liters).toList()),
                sensors,
                activity(irrigations, recentAlerts));
    }

    private List<ActivityItem> activity(List<IrrigationView> irrigations, List<AlertView> alerts) {
        Stream<ActivityItem> irrigationItems = irrigations.stream()
                .filter(irrigation -> irrigation.startedAt() != null)
                .map(irrigation -> new ActivityItem(
                        "irrigation",
                        irrigation.running() ? "bi-play-circle" : "bi-droplet",
                        irrigation.running() ? "success" : "neutral",
                        "Riego · " + irrigation.cropName(),
                        irrigationDetail(irrigation),
                        irrigation.startedAt(),
                        "/riego"));
        Stream<ActivityItem> alertItems = alerts.stream()
                .skip(DashboardView.ATTENTION_ALERTS)
                .map(alert -> new ActivityItem(
                        "alert",
                        "bi-bell",
                        Views.levelOf(alert.severity()),
                        messageSource.getMessage("alertType." + alert.type(), null, alert.type().name(), SPANISH)
                                + " · " + alert.cropName(),
                        alert.estateName(),
                        alert.createdAt(),
                        "/cultivos/" + alert.cropId()));
        return Stream.concat(irrigationItems, alertItems)
                .sorted(Comparator.comparing(ActivityItem::at).reversed())
                .limit(ACTIVITY_ITEMS)
                .toList();
    }

    private static String irrigationDetail(IrrigationView irrigation) {
        String liters = irrigation.waterLiters() == null ? null
                : irrigation.waterLiters().setScale(0, RoundingMode.HALF_UP).toPlainString() + " L";
        String minutes = irrigation.durationMin() == null ? null : irrigation.durationMin() + " min";
        String state = irrigation.running() ? "en curso" : null;
        return Stream.of(liters, minutes, state).filter(Objects::nonNull).reduce((a, b) -> a + " · " + b)
                .orElse(irrigation.estateName());
    }
    public List<DailyWater> waterUsage(String email) {
        return irrigationService.dailyWater(irrigationService.findSince(email, windowStart()), WATER_WINDOW_DAYS);
    }

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
        return new RangeBar(min.doubleValue(), max.doubleValue(),
                Math.max(0, Math.min(100, value.doubleValue())),
                min.toPlainString(), max.toPlainString(),
                value.compareTo(min) >= 0 && value.compareTo(max) <= 0);
    }

    private static LocalDateTime windowStart() {
        return LocalDate.now().minusDays(WATER_WINDOW_DAYS - 1L).atStartOfDay();
    }
}
