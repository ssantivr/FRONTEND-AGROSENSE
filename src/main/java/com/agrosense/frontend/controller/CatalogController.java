package com.agrosense.frontend.controller;

import com.agrosense.frontend.dto.CropForm;
import com.agrosense.frontend.dto.EstateForm;
import com.agrosense.frontend.dto.SensorForm;
import com.agrosense.frontend.entity.enums.CropStage;
import com.agrosense.frontend.entity.enums.SensorType;
import com.agrosense.frontend.exception.BusinessRuleException;
import com.agrosense.frontend.service.CatalogService;
import com.agrosense.frontend.service.EstateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/** Forms to create, edit and remove estates, crops and sensors. */
@Controller
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    private final EstateService estateService;

    // ---------- Estates ----------

    @GetMapping("/parcelas/nueva")
    public String newEstate(Model model) {
        model.addAttribute("estateForm", new EstateForm());
        return estateFormView(model, "Nueva parcela", "/parcelas");
    }

    @PostMapping("/parcelas")
    public String createEstate(@Valid @ModelAttribute("estateForm") EstateForm form, BindingResult result,
            Principal principal, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return estateFormView(model, "Nueva parcela", "/parcelas");
        }
        catalogService.createEstate(principal.getName(), form);
        return saved(redirect, "Parcela creada.", "/parcelas");
    }

    @GetMapping("/parcelas/{estateId}/editar")
    public String editEstate(@PathVariable Integer estateId, Principal principal, Model model) {
        model.addAttribute("estateForm", catalogService.findEstateForm(principal.getName(), estateId));
        return estateFormView(model, "Editar parcela", "/parcelas/" + estateId);
    }

    @PostMapping("/parcelas/{estateId}")
    public String updateEstate(@PathVariable Integer estateId,
            @Valid @ModelAttribute("estateForm") EstateForm form, BindingResult result, Principal principal,
            Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            // Still confirm ownership, so the form of someone else's estate is never rendered.
            catalogService.findEstateName(principal.getName(), estateId);
            return estateFormView(model, "Editar parcela", "/parcelas/" + estateId);
        }
        catalogService.updateEstate(principal.getName(), estateId, form);
        return saved(redirect, "Parcela actualizada.", "/parcelas");
    }

    @PostMapping("/parcelas/{estateId}/eliminar")
    public String deleteEstate(@PathVariable Integer estateId, Principal principal, RedirectAttributes redirect) {
        catalogService.deleteEstate(principal.getName(), estateId);
        return saved(redirect, "Parcela eliminada.", "/parcelas");
    }

    // ---------- Crops ----------

    @GetMapping("/parcelas/{estateId}/cultivos/nuevo")
    public String newCrop(@PathVariable Integer estateId, Principal principal, Model model) {
        String estateName = catalogService.findEstateName(principal.getName(), estateId);
        model.addAttribute("cropForm", new CropForm());
        return cropFormView(model, "Nuevo cultivo en " + estateName, "/parcelas/" + estateId + "/cultivos");
    }

    @PostMapping("/parcelas/{estateId}/cultivos")
    public String createCrop(@PathVariable Integer estateId, @Valid @ModelAttribute("cropForm") CropForm form,
            BindingResult result, Principal principal, Model model, RedirectAttributes redirect) {
        String estateName = catalogService.findEstateName(principal.getName(), estateId);
        if (result.hasErrors()) {
            return cropFormView(model, "Nuevo cultivo en " + estateName, "/parcelas/" + estateId + "/cultivos");
        }
        catalogService.createCrop(principal.getName(), estateId, form);
        return saved(redirect, "Cultivo creado.", "/parcelas");
    }

    @GetMapping("/cultivos/{cropId}/editar")
    public String editCrop(@PathVariable Integer cropId, Principal principal, Model model) {
        model.addAttribute("cropForm", catalogService.findCropForm(principal.getName(), cropId));
        return cropFormView(model, "Editar cultivo", "/cultivos/" + cropId);
    }

    @PostMapping("/cultivos/{cropId}")
    public String updateCrop(@PathVariable Integer cropId, @Valid @ModelAttribute("cropForm") CropForm form,
            BindingResult result, Principal principal, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            catalogService.findCropForm(principal.getName(), cropId);
            return cropFormView(model, "Editar cultivo", "/cultivos/" + cropId);
        }
        catalogService.updateCrop(principal.getName(), cropId, form);
        return saved(redirect, "Cultivo actualizado.", "/parcelas");
    }

    @PostMapping("/cultivos/{cropId}/eliminar")
    public String deleteCrop(@PathVariable Integer cropId, Principal principal, RedirectAttributes redirect) {
        catalogService.deleteCrop(principal.getName(), cropId);
        return saved(redirect, "Cultivo eliminado.", "/parcelas");
    }

    // ---------- Sensors ----------

    @GetMapping("/sensores/nuevo")
    public String newSensor(Principal principal, Model model) {
        model.addAttribute("sensorForm", new SensorForm());
        return sensorFormView(principal, model);
    }

    @PostMapping("/sensores")
    public String createSensor(@Valid @ModelAttribute("sensorForm") SensorForm form, BindingResult result,
            Principal principal, Model model, RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                catalogService.createSensor(principal.getName(), form);
                return saved(redirect, "Sensor registrado.", "/sensores");
            } catch (BusinessRuleException exception) {
                result.rejectValue("sensorCode", "sensor.code.taken", exception.getMessage());
            }
        }
        return sensorFormView(principal, model);
    }

    @PostMapping("/sensores/{sensorId}/eliminar")
    public String deleteSensor(@PathVariable Integer sensorId, Principal principal, RedirectAttributes redirect) {
        catalogService.deleteSensor(principal.getName(), sensorId);
        return saved(redirect, "Sensor eliminado.", "/sensores");
    }

    // ---------- Helpers ----------

    private static String estateFormView(Model model, String title, String action) {
        model.addAttribute("formTitle", title);
        model.addAttribute("formAction", action);
        return "estate-form";
    }

    private static String cropFormView(Model model, String title, String action) {
        model.addAttribute("formTitle", title);
        model.addAttribute("formAction", action);
        model.addAttribute("stages", CropStage.values());
        return "crop-form";
    }

    private String sensorFormView(Principal principal, Model model) {
        model.addAttribute("cropOptions", estateService.findActiveCropOptions(principal.getName()));
        model.addAttribute("sensorTypes", SensorType.values());
        return "sensor-form";
    }

    private static String saved(RedirectAttributes redirect, String message, String target) {
        redirect.addFlashAttribute("toastMessage", message);
        redirect.addFlashAttribute("toastType", "success");
        return "redirect:" + target;
    }
}
