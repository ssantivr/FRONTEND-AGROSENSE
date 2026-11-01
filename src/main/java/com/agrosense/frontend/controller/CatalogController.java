package com.agrosense.frontend.controller;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.dto.CropForm;
import com.agrosense.frontend.dto.EstateForm;
import com.agrosense.frontend.dto.SensorForm;
import com.agrosense.frontend.dto.SensorPositionForm;
import com.agrosense.frontend.dto.Views.CropView;
import com.agrosense.frontend.dto.Views.EstateView;
import com.agrosense.frontend.dto.Views.SensorView;
import com.agrosense.frontend.exception.NotFoundException;
import com.agrosense.frontend.service.SensorService;
import tools.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class CatalogController {

    private final BackendClient backendClient;
    private final CatalogService catalogService;
    private final EstateService estateService;
    private final SensorService sensorService;
    private final ObjectMapper objectMapper;

    @GetMapping("/parcelas/nueva")
    public String newEstate(@RequestParam(name = GUIDE, required = false) String guide, Model model) {
        guide(model, guide, 1);
        model.addAttribute("estateForm", new EstateForm());
        return estateFormView(model, "Nueva parcela", "/parcelas");
    }

    @PostMapping("/parcelas")
    public String createEstate(@Valid @ModelAttribute("estateForm") EstateForm form, BindingResult result,
            @RequestParam(name = GUIDE, required = false) String guide,
            Principal principal, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            guide(model, guide, 1);
            return estateFormView(model, "Nueva parcela", "/parcelas");
        }
        Integer estateId = catalogService.createEstate(principal.getName(), form);
        return saved(redirect, "Parcela creada.",
                guide == null ? "/parcelas" : "/parcelas/" + estateId + "/cultivos/nuevo?" + GUIDE + "=1");
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

    @GetMapping("/parcelas/{estateId}/cultivos/nuevo")
    public String newCrop(@PathVariable Integer estateId,
            @RequestParam(name = GUIDE, required = false) String guide, Principal principal, Model model) {
        guide(model, guide, 2);
        String estateName = catalogService.findEstateName(principal.getName(), estateId);
        model.addAttribute("cropForm", new CropForm());
        model.addAttribute("cropTemplates", backendClient.cropTemplates(principal.getName()));
        return cropFormView(model, "Nuevo cultivo en " + estateName, "/parcelas/" + estateId + "/cultivos");
    }

    @PostMapping("/parcelas/{estateId}/cultivos")
    public String createCrop(@PathVariable Integer estateId, @Valid @ModelAttribute("cropForm") CropForm form,
            BindingResult result, @RequestParam(name = GUIDE, required = false) String guide,
            Principal principal, Model model, RedirectAttributes redirect) {
        String estateName = catalogService.findEstateName(principal.getName(), estateId);
        if (result.hasErrors()) {
            guide(model, guide, 2);
            model.addAttribute("cropTemplates", backendClient.cropTemplates(principal.getName()));
            return cropFormView(model, "Nuevo cultivo en " + estateName, "/parcelas/" + estateId + "/cultivos");
        }
        Integer cropId = catalogService.createCrop(principal.getName(), estateId, form);
        return saved(redirect, "Cultivo creado.",
                guide == null ? "/parcelas" : "/sensores/nuevo?" + GUIDE + "=1&cultivo=" + cropId);
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

    @GetMapping("/sensores/nuevo")
    public String newSensor(@RequestParam(name = GUIDE, required = false) String guide,
            @RequestParam(name = "cultivo", required = false) Integer cropId, Principal principal, Model model) {
        guide(model, guide, 3);
        SensorForm form = new SensorForm();
        form.setCropId(cropId);
        model.addAttribute("sensorForm", form);
        return sensorFormView(principal, model);
    }

    @PostMapping("/sensores")
    public String createSensor(@Valid @ModelAttribute("sensorForm") SensorForm form, BindingResult result,
            @RequestParam(name = GUIDE, required = false) String guide,
            Principal principal, Model model, RedirectAttributes redirect) {
        guide(model, guide, 3);
        if (!result.hasErrors()) {
            try {
                catalogService.createSensor(principal.getName(), form);
                return guide == null
                        ? saved(redirect, "Sensor registrado.", "/sensores")
                        : saved(redirect, "Todo listo: tu parcela, su cultivo y su sensor ya están registrados.", "/");
            } catch (BusinessRuleException exception) {
                result.rejectValue("sensorCode", "sensor.code.taken", exception.getMessage());
            }
        }
        return sensorFormView(principal, model);
    }

    @GetMapping("/sensores/{sensorId}/ubicacion")
    public String sensorPosition(@PathVariable Integer sensorId, Principal principal, Model model) {
        model.addAttribute("positionForm", catalogService.findSensorPosition(principal.getName(), sensorId));
        return sensorPositionView(sensorId, principal, model);
    }

    @PostMapping("/sensores/{sensorId}/ubicacion")
    public String placeSensor(@PathVariable Integer sensorId,
            @Valid @ModelAttribute("positionForm") SensorPositionForm form, BindingResult result,
            Principal principal, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return sensorPositionView(sensorId, principal, model);
        }
        catalogService.placeSensor(principal.getName(), sensorId, form);
        return saved(redirect, form.isEmpty() ? "Sensor retirado del mapa." : "Ubicación del sensor guardada.",
                "/sensores");
    }

    @PostMapping("/sensores/{sensorId}/eliminar")
    public String deleteSensor(@PathVariable Integer sensorId, Principal principal, RedirectAttributes redirect) {
        catalogService.deleteSensor(principal.getName(), sensorId);
        return saved(redirect, "Sensor eliminado.", "/sensores");
    }

    private static final String GUIDE = "guia";

    private static void guide(Model model, String guide, int step) {
        if (guide != null) {
            model.addAttribute("guideStep", step);
        }
    }

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

    private String sensorPositionView(Integer sensorId, Principal principal, Model model) {
        SensorView sensor = sensorService.findSensors(principal.getName()).stream()
                .filter(candidate -> candidate.id().equals(sensorId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Sensor not found"));
        model.addAttribute("sensor", sensor);
        model.addAttribute("siteJson", objectMapper.writeValueAsString(
                estateService.findEstates(principal.getName()).stream()
                        .filter(estate -> estate.id().equals(sensor.estateId()))
                        .map(CatalogController::toSite)
                        .findFirst().orElse(null)));
        return "sensor-position";
    }

    private static Map<String, Object> toSite(EstateView estate) {
        Map<String, Object> site = new LinkedHashMap<>();
        site.put("name", estate.name());
        site.put("latitude", estate.latitude());
        site.put("longitude", estate.longitude());
        site.put("areaHa", estate.areaHa());
        site.put("boundary", estate.boundary());
        return site;
    }

    private String sensorFormView(Principal principal, Model model) {
        Map<String, Object> sites = new LinkedHashMap<>();
        estateService.findEstates(principal.getName()).forEach(estate -> estate.crops().stream()
                .filter(CropView::active)
                .forEach(crop -> sites.put(String.valueOf(crop.id()), toSite(estate))));
        model.addAttribute("sitesJson", objectMapper.writeValueAsString(sites));
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
