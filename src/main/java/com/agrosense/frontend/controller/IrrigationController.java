package com.agrosense.frontend.controller;

import com.agrosense.frontend.dto.IrrigationForm;
import com.agrosense.frontend.exception.BusinessRuleException;
import com.agrosense.frontend.service.EstateService;
import com.agrosense.frontend.service.IrrigationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/riego")
@RequiredArgsConstructor
public class IrrigationController {

    private final IrrigationService irrigationService;
    private final EstateService estateService;

    @GetMapping
    public String irrigation(Principal principal, Model model) {
        if (!model.containsAttribute("irrigationForm")) {
            model.addAttribute("irrigationForm", new IrrigationForm());
        }
        model.addAttribute("cropOptions", estateService.findActiveCropOptions(principal.getName()));
        model.addAttribute("irrigations", irrigationService.findHistory(principal.getName()));
        return "irrigation";
    }

    @PostMapping
    public String start(@Valid @ModelAttribute("irrigationForm") IrrigationForm form, BindingResult result,
            Principal principal, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                irrigationService.start(principal.getName(), form);
                redirect.addFlashAttribute("toastMessage", "Riego registrado correctamente.");
                redirect.addFlashAttribute("toastType", "success");
                return "redirect:/riego";
            } catch (BusinessRuleException exception) {
                result.reject("irrigation.rejected", exception.getMessage());
            }
        }
        return irrigation(principal, model);
    }
}
