package com.agrosense.frontend.service;

import com.agrosense.frontend.dto.Views.AlertStat;
import com.agrosense.frontend.dto.Views.CropView;
import com.agrosense.frontend.dto.Views.EstateView;
import com.agrosense.frontend.dto.Views.EstateWater;
import com.agrosense.frontend.dto.Views.IrrigationView;
import com.agrosense.frontend.dto.Views.ReportView;
import com.agrosense.frontend.dto.Views.SensorStat;
import com.agrosense.frontend.dto.Views.SensorView;
import com.agrosense.frontend.dto.Views.TypeWater;
import com.agrosense.frontend.dto.Views.WaterTotals;
import com.agrosense.frontend.entity.enums.AlertType;
import com.agrosense.frontend.entity.enums.IrrigationType;
import com.agrosense.frontend.entity.enums.Severity;
import com.agrosense.frontend.repository.AlertRepository;
import com.agrosense.frontend.repository.SensorReadingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Figures of the reports page: water, sensor readings and alerts over a chosen period. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    /** The periods the page offers, in days; 0 stands for everything on record. */
    public static final List<Integer> PERIODS = List.of(7, 30, 90, 0);
    public static final int DEFAULT_PERIOD = 30;
    /** Days drawn in the daily chart when the period is "everything". */
    private static final int LONGEST_DAILY_CHART = 90;
    private static final LocalDateTime BEGINNING = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final IrrigationService irrigationService;
    private final EstateService estateService;
    private final SensorService sensorService;
    private final SensorReadingRepository readingRepository;
    private final AlertRepository alertRepository;

    /** @param days one of {@link #PERIODS}; anything else falls back to the default period */
    public ReportView build(String email, int days) {
        int period = PERIODS.contains(days) ? days : DEFAULT_PERIOD;
        // Whole days: a 7-day period is today and the six days before it.
        LocalDateTime since = period == 0 ? BEGINNING : LocalDate.now().minusDays(period - 1L).atStartOfDay();
        List<IrrigationView> history = irrigationService.findHistory(email);
        List<IrrigationView> current = history.stream()
                .filter(irrigation -> irrigation.startedAt() != null && !irrigation.startedAt().isBefore(since))
                .toList();
        List<EstateView> estates = estateService.findEstates(email);

        return new ReportView(
                period,
                totals(current, period == 0 ? null : previous(history, since, period)),
                irrigationService.waterReport(current),
                byEstate(current, estates),
                byType(current),
                irrigationService.dailyWater(current, period == 0 ? LONGEST_DAILY_CHART : period),
                sensorStats(email, since, estates),
                alertStats(email, since));
    }

    /** The irrigations of the period of the same length right before this one. */
    private static List<IrrigationView> previous(List<IrrigationView> history, LocalDateTime since, int days) {
        LocalDateTime from = since.minusDays(days);
        return history.stream()
                .filter(irrigation -> irrigation.startedAt() != null
                        && !irrigation.startedAt().isBefore(from) && irrigation.startedAt().isBefore(since))
                .toList();
    }

    private static WaterTotals totals(List<IrrigationView> current, List<IrrigationView> previous) {
        BigDecimal liters = liters(current);
        long minutes = current.stream().mapToLong(item -> item.durationMin() == null ? 0 : item.durationMin()).sum();
        BigDecimal average = current.isEmpty()
                ? BigDecimal.ZERO
                : liters.divide(BigDecimal.valueOf(current.size()), 1, RoundingMode.HALF_UP);
        // A change can only be stated against a period that had water; "from nothing" is not a percentage.
        BigDecimal before = previous == null ? BigDecimal.ZERO : liters(previous);
        Integer change = before.signum() == 0
                ? null
                : liters.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before, 0, RoundingMode.HALF_UP).intValue();
        return new WaterTotals(liters, current.size(), minutes, average, change);
    }

    private static List<EstateWater> byEstate(List<IrrigationView> current, List<EstateView> estates) {
        Map<String, BigDecimal> litersByEstate = new LinkedHashMap<>();
        current.forEach(item -> litersByEstate.merge(item.estateName(), orZero(item.waterLiters()), BigDecimal::add));
        return litersByEstate.entrySet().stream()
                .map(entry -> {
                    BigDecimal area = estates.stream()
                            .filter(estate -> estate.name().equals(entry.getKey()))
                            .map(EstateView::areaHa)
                            .filter(Objects::nonNull)
                            .findFirst().orElse(null);
                    return new EstateWater(entry.getKey(), entry.getValue(), area == null || area.signum() == 0
                            ? null
                            : entry.getValue().divide(area, 1, RoundingMode.HALF_UP));
                })
                .sorted(Comparator.comparing(EstateWater::liters).reversed())
                .toList();
    }

    private static List<TypeWater> byType(List<IrrigationView> current) {
        return Arrays.stream(IrrigationType.values())
                .map(type -> {
                    List<IrrigationView> ofType = current.stream().filter(item -> item.type() == type).toList();
                    return new TypeWater(type, liters(ofType), ofType.size());
                })
                .filter(type -> type.irrigations() > 0)
                .toList();
    }

    private List<SensorStat> sensorStats(String email, LocalDateTime since, List<EstateView> estates) {
        Map<Integer, Object[]> statistics = new LinkedHashMap<>();
        readingRepository.findStatistics(email, since).forEach(row -> statistics.put((Integer) row[0], row));
        List<SensorStat> stats = new ArrayList<>();
        for (SensorView sensor : sensorService.findSensors(email)) {
            Object[] row = statistics.get(sensor.id());
            if (row == null) {
                continue;
            }
            BigDecimal average = BigDecimal.valueOf(((Number) row[3]).doubleValue()).setScale(1, RoundingMode.HALF_UP);
            BigDecimal[] range = range(sensor, estates);
            stats.add(new SensorStat(sensor.code(), sensor.type(), sensor.cropName(), sensor.estateName(), sensor.unit(),
                    scaled(row[1]), scaled(row[2]), average, ((Number) row[4]).longValue(), range[0], range[1],
                    range[0] == null || range[1] == null
                            ? null
                            : average.compareTo(range[0]) >= 0 && average.compareTo(range[1]) <= 0));
        }
        return stats;
    }

    /** The acceptable {min, max} of the sensor's crop for what it measures; both null when there is none. */
    private static BigDecimal[] range(SensorView sensor, List<EstateView> estates) {
        CropView crop = estates.stream()
                .filter(estate -> estate.id().equals(sensor.estateId()))
                .flatMap(estate -> estate.crops().stream())
                .filter(candidate -> candidate.name().equals(sensor.cropName()))
                .findFirst().orElse(null);
        if (crop == null) {
            return new BigDecimal[] {null, null};
        }
        return switch (sensor.type()) {
            case SOIL_MOISTURE, RELATIVE_HUMIDITY -> new BigDecimal[] {crop.humidityMin(), crop.humidityMax()};
            case AIR_TEMPERATURE, SOIL_TEMPERATURE -> new BigDecimal[] {crop.tempMin(), crop.tempMax()};
            case PH -> new BigDecimal[] {crop.phMin(), crop.phMax()};
            default -> new BigDecimal[] {null, null};
        };
    }

    private List<AlertStat> alertStats(String email, LocalDateTime since) {
        // Open and acknowledged alerts of one kind arrive as two rows; they are added up here.
        Map<List<Object>, long[]> counts = new LinkedHashMap<>();
        alertRepository.countByKindSince(email, since).forEach(row -> {
            long[] count = counts.computeIfAbsent(List.of(row[0], row[1]), kind -> new long[2]);
            long amount = ((Number) row[3]).longValue();
            count[0] += amount;
            if (!Boolean.TRUE.equals(row[2])) {
                count[1] += amount;
            }
        });
        return counts.entrySet().stream()
                .map(entry -> new AlertStat((AlertType) entry.getKey().get(0), (Severity) entry.getKey().get(1),
                        entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparing(AlertStat::severity).reversed().thenComparing(AlertStat::type))
                .toList();
    }

    private static BigDecimal scaled(Object number) {
        return new BigDecimal(number.toString()).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal liters(List<IrrigationView> irrigations) {
        return irrigations.stream().map(item -> orZero(item.waterLiters())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
