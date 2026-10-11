package com.agrosense.frontend.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Living gallery of the brand, tokens and components. Only the demo profile has it: production has no such route. */
@Controller
@Profile("demo")
public class DesignSystemController {

    @GetMapping("/design-system")
    public String designSystem() {
        return "design-system";
    }
}
