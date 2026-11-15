package com.agrosense.frontend.controller;

import com.agrosense.frontend.dto.Views.HumidityDayReport;
import com.agrosense.frontend.dto.Views.IrrigationDay;
import com.agrosense.frontend.dto.Views.IrrigationView;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import com.agrosense.frontend.dto.IrrigationForm;
import com.agrosense.frontend.exception.BusinessRuleException;
import com.agrosense.frontend.service.EstateService;
import com.agrosense.frontend.service.IrrigationService;
import com.agrosense.frontend.service.SensorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import java.util.stream.IntStream;
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
    private final SensorService sensorService;
    private final MessageSource messages;

    /** The same device the backend reads over TCP (ESP32_DEVICE_ID). */
    @Value("${agrosense.esp32.device-id:ESP32-PLANTICAS}")
    private String esp32DeviceId;

    @GetMapping
    public String irrigation(Principal principal, Model model) {
        if (!model.containsAttribute("irrigationForm")) {
            model.addAttribute("irrigationForm", new IrrigationForm());
        }
        model.addAttribute("cropOptions", estateService.findActiveCropOptions(principal.getName()));
        List<IrrigationView> history = irrigationService.findHistory(principal.getName());
        model.addAttribute("irrigations", history);
        model.addAttribute("running", history.stream().filter(IrrigationView::running).toList());
        List<IrrigationDay> days = history.stream()
                .filter(irrigation -> irrigation.startedAt() != null)
                .collect(Collectors.groupingBy(irrigation -> irrigation.startedAt().toLocalDate(), LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(day -> new IrrigationDay(day.getKey(), day.getValue(), day.getValue().stream()
                        .map(irrigation -> irrigation.waterLiters() == null ? BigDecimal.ZERO : irrigation.waterLiters())
                        .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();
        model.addAttribute("irrigationDays", days);
        // The day's humidity of the ESP32's crop: what tells whether irrigating is needed.
        HumidityDayReport humidity = sensorService.findHumidityDayReport(principal.getName(), esp32DeviceId,
                type -> messages.getMessage("sensorType." + type, null, type.name(), LocaleContextHolder.getLocale()))
                .orElse(null);
        model.addAttribute("humidityDay", humidity);
        model.addAttribute("humidityDaySummaries", humidity == null ? List.of()
                : IntStream.range(0, humidity.series().size())
                        .mapToObj(index -> SensorService.summarize(humidity.types().get(index),
                                humidity.series().get(index), SensorService.DAY_BUCKET_MINUTES))
                        .toList());
        model.addAttribute("maxDayLiters", days.stream().map(IrrigationDay::liters).max(BigDecimal::compareTo)
                .filter(liters -> liters.signum() > 0).orElse(BigDecimal.ONE));
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
