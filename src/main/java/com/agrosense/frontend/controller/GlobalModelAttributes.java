package com.agrosense.frontend.controller;

import com.agrosense.frontend.dto.Views.NavItem;
import com.agrosense.frontend.service.AlertService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.util.List;

/** Adds the data every page layout needs: navigation, open-alert count and the demo flag. */
@ControllerAdvice(annotations = Controller.class)
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private static final List<NavItem> NAVIGATION = List.of(
            new NavItem("/", "Inicio", "bi-house-door", false),
            new NavItem("/parcelas", "Parcelas", "bi-map", false),
            new NavItem("/sensores", "Sensores", "bi-broadcast", false),
            new NavItem("/riego", "Riego", "bi-droplet", false),
            new NavItem("/dispositivos", "Dispositivos", "bi-cpu", false),
            new NavItem("/alertas", "Alertas", "bi-bell", false),
            new NavItem("/reportes", "Reportes", "bi-bar-chart", false),
            new NavItem("/configuracion", "Configuración", "bi-gear", false));

    private final AlertService alertService;
    private final Environment environment;

    @ModelAttribute
    public void addLayoutAttributes(Model model, HttpServletRequest request, Principal principal) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        model.addAttribute("navigation", NAVIGATION.stream()
                .map(item -> new NavItem(item.path(), item.label(), item.icon(), isActive(item.path(), path)))
                .toList());
        model.addAttribute("demoMode", environment.acceptsProfiles(Profiles.of("demo")));
        if (principal != null) {
            model.addAttribute("openAlertCount", alertService.countOpen(principal.getName()));
        }
    }

    private static boolean isActive(String itemPath, String currentPath) {
        return itemPath.equals("/") ? currentPath.equals("/") : currentPath.startsWith(itemPath);
    }
}
