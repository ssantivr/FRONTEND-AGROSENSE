package com.agrosense.frontend.service;

import com.agrosense.frontend.dto.Views.AlertView;
import com.agrosense.frontend.entity.Alert;
import com.agrosense.frontend.exception.NotFoundException;
import com.agrosense.frontend.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertService {

    public static final int PAGE_SIZE = 10;

    private final AlertRepository alertRepository;

    public Page<AlertView> findAlerts(String email, boolean onlyOpen, int page) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Alert> alerts = onlyOpen
                ? alertRepository.findByCropEstateUserEmailAndAcknowledgedFalse(email, pageable)
                : alertRepository.findByCropEstateUserEmail(email, pageable);
        return alerts.map(AlertService::toView);
    }

    public List<AlertView> findRecentOpen(String email, int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        return alertRepository.findByCropEstateUserEmailAndAcknowledgedFalse(email, pageable)
                .map(AlertService::toView)
                .getContent();
    }

    public long countOpen(String email) {
        return alertRepository.countByCropEstateUserEmailAndAcknowledgedFalse(email);
    }

    @Transactional
    public void acknowledge(String email, Integer alertId) {
        Alert alert = alertRepository.findByIdAlertAndCropEstateUserEmail(alertId, email)
                .orElseThrow(() -> new NotFoundException("Alert not found"));
        alert.setAcknowledged(true);
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
