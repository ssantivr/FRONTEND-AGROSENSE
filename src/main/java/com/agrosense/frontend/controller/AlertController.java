package com.agrosense.frontend.controller;

import com.agrosense.frontend.dto.Views.AlertDay;
import com.agrosense.frontend.dto.Views.AlertView;
import com.agrosense.frontend.entity.enums.Severity;
import com.agrosense.frontend.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/alertas")
@RequiredArgsConstructor
public class AlertController {

    private static final String FILTER_ALL = "todas";
    private static final String FILTER_OPEN = "pendientes";

    public static final Map<String, Severity> SEVERITY_FILTERS = new LinkedHashMap<>(Map.of(
            "informativa", Severity.LOW,
            "advertencia", Severity.MEDIUM,
            "alta", Severity.HIGH,
            "critica", Severity.VERY_HIGH));

    public record SeverityFilter(String value, Severity severity, boolean selected) {
    }

    private final AlertService alertService;

    @GetMapping
    public String alerts(@RequestParam(name = "estado", defaultValue = FILTER_OPEN) String status,
            @RequestParam(name = "severidad", required = false) String severity,
            @RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        boolean onlyOpen = !FILTER_ALL.equals(status);
        Severity selected = severity == null ? null : SEVERITY_FILTERS.get(severity);
        String severityValue = selected == null ? null : severity;
        Page<AlertView> alerts = alertService.findAlerts(principal.getName(), onlyOpen, selected, page);
        model.addAttribute("alerts", alerts);
        model.addAttribute("alertDays", alerts.getContent().stream()
                .collect(Collectors.groupingBy(alert -> alert.createdAt().toLocalDate(), LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(day -> new AlertDay(day.getKey(), day.getValue()))
                .toList());
        model.addAttribute("onlyOpen", onlyOpen);
        model.addAttribute("statusValue", onlyOpen ? FILTER_OPEN : FILTER_ALL);
        model.addAttribute("severityValue", severityValue);
        model.addAttribute("severityFilters", List.of(
                new SeverityFilter("critica", Severity.VERY_HIGH, Severity.VERY_HIGH == selected),
                new SeverityFilter("alta", Severity.HIGH, Severity.HIGH == selected),
                new SeverityFilter("advertencia", Severity.MEDIUM, Severity.MEDIUM == selected),
                new SeverityFilter("informativa", Severity.LOW, Severity.LOW == selected)));
        model.addAttribute("pageUrl", "/alertas?estado=" + (onlyOpen ? FILTER_OPEN : FILTER_ALL)
                + (severityValue == null ? "" : "&severidad=" + severityValue));
        return "alerts";
    }

    @PostMapping("/{alertId}/atender")
    public String acknowledge(@PathVariable Integer alertId, Principal principal, RedirectAttributes redirect) {
        alertService.acknowledge(principal.getName(), alertId);
        redirect.addFlashAttribute("toastMessage", "Alerta marcada como atendida.");
        redirect.addFlashAttribute("toastType", "success");
        return "redirect:/alertas";
    }
}
