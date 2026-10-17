package com.agrosense.frontend.service;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.backend.BackendClient.IrrigationResponse;
import com.agrosense.frontend.backend.BackendUnavailableException;
import com.agrosense.frontend.dto.IrrigationForm;
import com.agrosense.frontend.dto.Views.CropWaterReport;
import com.agrosense.frontend.dto.Views.DailyWater;
import com.agrosense.frontend.dto.Views.IrrigationView;
import com.agrosense.frontend.entity.Crop;
import com.agrosense.frontend.entity.Irrigation;
import com.agrosense.frontend.entity.User;
import com.agrosense.frontend.entity.enums.IrrigationType;
import com.agrosense.frontend.exception.BusinessRuleException;
import com.agrosense.frontend.exception.NotFoundException;
import com.agrosense.frontend.repository.CropRepository;
import com.agrosense.frontend.repository.IrrigationRepository;
import com.agrosense.frontend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IrrigationService {

    private static final String MANUAL_REASON = "Riego manual desde la aplicación web";

    private final IrrigationRepository irrigationRepository;
    private final CropRepository cropRepository;
    private final UserRepository userRepository;
    private final BackendClient backendClient;

    public List<IrrigationView> findHistory(String email) {
        LocalDateTime now = LocalDateTime.now();
        return irrigationRepository.findByCropEstateUserEmailOrderByStartedAtDesc(email).stream()
                .map(irrigation -> toView(irrigation, now))
                .toList();
    }

    public List<IrrigationView> findSince(String email, LocalDateTime since) {
        LocalDateTime now = LocalDateTime.now();
        return irrigationRepository
                .findByCropEstateUserEmailAndStartedAtGreaterThanEqualOrderByStartedAtDesc(email, since).stream()
                .map(irrigation -> toView(irrigation, now))
                .toList();
    }

    public List<IrrigationView> findByEstate(String email, Integer estateId, int limit) {
        LocalDateTime now = LocalDateTime.now();
        return irrigationRepository
                .findByCropEstateIdEstateAndCropEstateUserEmailOrderByStartedAtDesc(estateId, email, Limit.of(limit))
                .stream().map(irrigation -> toView(irrigation, now)).toList();
    }

    public List<IrrigationView> findByCrop(String email, Integer cropId, int limit) {
        LocalDateTime now = LocalDateTime.now();
        return irrigationRepository
                .findByCropIdCropAndCropEstateUserEmailOrderByStartedAtDesc(cropId, email, Limit.of(limit))
                .stream().map(irrigation -> toView(irrigation, now)).toList();
    }

    public Optional<IrrigationView> findRunning(List<IrrigationView> history) {
        return history.stream().filter(IrrigationView::running).findFirst();
    }

    public List<DailyWater> dailyWater(List<IrrigationView> history, int days) {
        LocalDate today = LocalDate.now();
        return IntStream.rangeClosed(1, days)
                .mapToObj(offset -> today.minusDays(days - offset))
                .map(date -> new DailyWater(date, history.stream()
                        .filter(irrigation -> irrigation.startedAt() != null
                                && irrigation.startedAt().toLocalDate().equals(date))
                        .map(irrigation -> orZero(irrigation.waterLiters()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();
    }

    public List<CropWaterReport> waterReport(List<IrrigationView> history) {
        Map<String, List<IrrigationView>> byCrop = new LinkedHashMap<>();
        history.forEach(irrigation -> byCrop
                .computeIfAbsent(irrigation.cropName() + "|" + irrigation.estateName(), key -> new java.util.ArrayList<>())
                .add(irrigation));
        return byCrop.values().stream()
                .map(items -> new CropWaterReport(
                        items.get(0).cropName(),
                        items.get(0).estateName(),
                        items.size(),
                        items.stream().mapToLong(item -> item.durationMin() == null ? 0 : item.durationMin()).sum(),
                        items.stream().map(item -> orZero(item.waterLiters())).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();
    }

    @Transactional
    public IrrigationView start(String email, IrrigationForm form) {
        Crop crop = cropRepository.findByIdCropAndEstateUserEmail(form.getCropId(), email)
                .orElseThrow(() -> new NotFoundException("Crop not found"));
        if (!Boolean.TRUE.equals(crop.getActive())) {
            throw new BusinessRuleException("El cultivo está inactivo y no se puede regar.");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean alreadyRunning = irrigationRepository.findByCropIdCropOrderByStartedAtDesc(crop.getIdCrop()).stream()
                .anyMatch(irrigation -> isRunning(irrigation, now));
        if (alreadyRunning) {
            throw new BusinessRuleException("Ya hay un riego en curso para este cultivo.");
        }
        if (backendClient.isAvailable()) {
            try {
                IrrigationResponse registered = backendClient.startIrrigation(email, crop.getIdCrop(),
                        form.getWaterLiters(), form.getDurationMin(), MANUAL_REASON);
                return new IrrigationView(registered.idIrrigation(), crop.getName(), crop.getEstate().getName(),
                        registered.startedAt(), registered.endedAt(), registered.durationMin(),
                        registered.waterLiters(), IrrigationType.MANUAL, true);
            } catch (BackendUnavailableException exception) {
                throw new BusinessRuleException(
                        "El servidor no respondió a tiempo. Revisa el historial de riegos antes de intentarlo de nuevo.");
            }
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Irrigation irrigation = irrigationRepository.save(Irrigation.builder()
                .crop(crop)
                .startedAt(now)
                .durationMin(form.getDurationMin())
                .waterLiters(form.getWaterLiters())
                .type(IrrigationType.MANUAL)
                .activatedBy(user)
                .build());
        return toView(irrigation, now);
    }

    private static IrrigationView toView(Irrigation irrigation, LocalDateTime now) {
        return new IrrigationView(
                irrigation.getIdIrrigation(),
                irrigation.getCrop().getName(),
                irrigation.getCrop().getEstate().getName(),
                irrigation.getStartedAt(),
                expectedEnd(irrigation),
                irrigation.getDurationMin(),
                irrigation.getWaterLiters(),
                irrigation.getType(),
                isRunning(irrigation, now));
    }

    private static LocalDateTime expectedEnd(Irrigation irrigation) {
        if (irrigation.getEndedAt() != null) {
            return irrigation.getEndedAt();
        }
        if (irrigation.getStartedAt() == null || irrigation.getDurationMin() == null) {
            return null;
        }
        return irrigation.getStartedAt().plusMinutes(irrigation.getDurationMin());
    }

    private static boolean isRunning(Irrigation irrigation, LocalDateTime now) {
        LocalDateTime end = expectedEnd(irrigation);
        return irrigation.getStartedAt() != null
                && !irrigation.getStartedAt().isAfter(now)
                && end != null
                && end.isAfter(now);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
