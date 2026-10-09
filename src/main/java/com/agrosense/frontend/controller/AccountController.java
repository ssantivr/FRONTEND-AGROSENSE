package com.agrosense.frontend.controller;

import com.agrosense.frontend.dto.RegistrationForm;
import com.agrosense.frontend.exception.BusinessRuleException;
import com.agrosense.frontend.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/** Sign-up, account settings and account deletion. */
@Controller
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/registro")
    public String registrationForm(Principal principal, Model model) {
        if (principal != null) {
            return "redirect:/";
        }
        model.addAttribute("registrationForm", new RegistrationForm());
        return "register";
    }

    @PostMapping("/registro")
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form, BindingResult result) {
        if (!result.hasErrors()) {
            try {
                accountService.register(form);
                return "redirect:/login?registered";
            } catch (BusinessRuleException exception) {
                result.reject("registration.rejected", exception.getMessage());
            }
        }
        return "register";
    }

    @GetMapping("/configuracion")
    public String settings(Principal principal, Model model) {
        model.addAttribute("profile", accountService.findProfile(principal.getName()));
        return "settings";
    }

    @PostMapping("/configuracion/eliminar-cuenta")
    public String deleteAccount(@RequestParam(required = false) String password, Authentication authentication,
            HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirect) {
        try {
            accountService.deleteAccount(authentication.getName(), password);
        } catch (BusinessRuleException exception) {
            redirect.addFlashAttribute("deleteError", exception.getMessage());
            return "redirect:/configuracion";
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?deleted";
    }
}
