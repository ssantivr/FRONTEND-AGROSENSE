package com.agrosense.frontend.controller;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.dto.Views.CropCard;
import com.agrosense.frontend.dto.Views.CropDetail;
import com.agrosense.frontend.entity.enums.CropStage;
import com.agrosense.frontend.service.CropService;
import com.agrosense.frontend.service.CropService.CropFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class CropController {

    private final CropService cropService;
    private final BackendClient backendClient;

    @GetMapping("/cultivos")
    public String crops(@RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "etapa", required = false) String stage,
            @RequestParam(name = "parcela", required = false) Integer estateId,
            @RequestParam(name = "orden", required = false) String order,
            Principal principal, Model model) {
        String email = principal.getName();
        CropFilter filter = new CropFilter(query == null ? null : query.strip(), stageOf(stage), estateId,
                order != null && CropService.ORDERS.contains(order) ? order : CropService.ORDER_NAME);
        List<CropCard> all = cropService.findCards(email);
        model.addAttribute("crops", CropService.filter(all, filter));
        model.addAttribute("cropTotal", all.size());
        model.addAttribute("filter", filter);
        model.addAttribute("stages", CropStage.values());
        model.addAttribute("estates", all.stream()
                .map(card -> new EstateOption(card.estateId(), card.estateName()))
                .distinct()
                .toList());
        return "crops";
    }

    @GetMapping("/cultivos/{cropId}")
    public String crop(@PathVariable Integer cropId, Principal principal, Model model) {
        CropDetail detail = cropService.findDetail(principal.getName(), cropId);
        model.addAttribute("detail", detail);
        model.addAttribute("crop", detail.card().crop());
        model.addAttribute("prediction", backendClient.latestPrediction(principal.getName(), cropId).orElse(null));
        return "crop";
    }

    public record EstateOption(Integer id, String name) {
    }

    private static CropStage stageOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(CropStage.values())
                .filter(stage -> stage.name().equalsIgnoreCase(value))
                .findFirst()
                .orElse(null);
    }
}
