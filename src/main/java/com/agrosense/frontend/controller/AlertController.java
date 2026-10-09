package com.agrosense.frontend.controller;

import com.agrosense.frontend.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/alertas")
@RequiredArgsConstructor
public class AlertController {

    private static final String FILTER_ALL = "todas";

    private final AlertService alertService;

    @GetMapping
    public String alerts(@RequestParam(defaultValue = "pendientes") String estado,
            @RequestParam(defaultValue = "0") int page, Principal principal, Model model) {
        boolean onlyOpen = !FILTER_ALL.equals(estado);
        model.addAttribute("alerts", alertService.findAlerts(principal.getName(), onlyOpen, page));
        model.addAttribute("onlyOpen", onlyOpen);
        model.addAttribute("pageUrl", "/alertas?estado=" + (onlyOpen ? "pendientes" : FILTER_ALL));
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
