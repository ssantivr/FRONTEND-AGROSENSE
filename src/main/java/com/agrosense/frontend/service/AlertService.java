package com.agrosense.frontend.service;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.dto.Views.AlertView;
import com.agrosense.frontend.entity.Alert;
import com.agrosense.frontend.exception.NotFoundException;
import com.agrosense.frontend.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertService {

    public static final int PAGE_SIZE = 10;

    private final AlertRepository alertRepository;
    private final BackendClient backendClient;

    public Page<AlertView> findAlerts(String email, boolean onlyOpen, int page) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Alert> alerts = onlyOpen
                ? alertRepository.findByCropEstateUserEmailAndAcknowledgedFalse(email, pageable)
                : alertRepository.findByCropEstateUserEmail(email, pageable);
        return alerts.map(AlertService::toView);
    }

    public List<AlertView> findRecentOpen(String email, int limit) {
        return alertRepository
                .findByCropEstateUserEmailAndAcknowledgedFalseOrderByCreatedAtDesc(email, Limit.of(limit)).stream()
                .map(AlertService::toView)
                .toList();
    }

    /** The newest open alerts of one of the user's estates. */
    public List<AlertView> findOpenByEstate(String email, Integer estateId, int limit) {
        return alertRepository
                .findByCropEstateIdEstateAndCropEstateUserEmailAndAcknowledgedFalseOrderByCreatedAtDesc(estateId, email,
                        Limit.of(limit))
                .stream().map(AlertService::toView).toList();
    }

    /** Open alerts by estate id; estates without any are absent. */
    public Map<Integer, Integer> countOpenByEstate(String email) {
        return alertRepository.countOpenByEstate(email).stream()
                .collect(Collectors.toMap(row -> (Integer) row[0], row -> ((Number) row[1]).intValue()));
    }

    public long countOpen(String email) {
        return alertRepository.countByCropEstateUserEmailAndAcknowledgedFalse(email);
    }

    @Transactional
    public void acknowledge(String email, Integer alertId) {
        Alert alert = alertRepository.findByIdAlertAndCropEstateUserEmail(alertId, email)
                .orElseThrow(() -> new NotFoundException("Alert not found"));
        // Acknowledging twice changes nothing, so the database can take over if the backend does not answer.
        if (backendClient.attempt(() -> backendClient.acknowledgeAlert(email, alertId)).isEmpty()) {
            alert.setAcknowledged(true);
        }
    }

    private static AlertView toView(Alert alert) {
        return new AlertView(
                alert.getIdAlert(),
                alert.getAlertType(),
                alert.getSeverity(),
                alert.getMessage(),
                alert.getCrop().getName(),
                alert.getCrop().getEstate().getName(),
                alert.getDetectedValue(),
                Boolean.TRUE.equals(alert.getAcknowledged()),
                alert.getCreatedAt());
    }
}
