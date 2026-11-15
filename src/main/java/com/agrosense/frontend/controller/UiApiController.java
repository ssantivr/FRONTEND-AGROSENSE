package com.agrosense.frontend.controller;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.backend.BackendStatus;
import com.agrosense.frontend.dto.IrrigationForm;
import com.agrosense.frontend.dto.Views.CropLatest;
import com.agrosense.frontend.dto.Views.ConnectedSensor;
import com.agrosense.frontend.dto.Views.DailyWater;
import com.agrosense.frontend.dto.Views.Esp32Silence;
import com.agrosense.frontend.dto.Views.EstateMarker;
import com.agrosense.frontend.dto.Views.HumidityDay;
import com.agrosense.frontend.dto.Views.HumidityFeed;
import com.agrosense.frontend.dto.Views.HumidityRow;
import com.agrosense.frontend.dto.Views.IrrigationView;
import com.agrosense.frontend.dto.Views.ReadingSeries;
import com.agrosense.frontend.service.CropService;
import com.agrosense.frontend.service.DashboardService;
import com.agrosense.frontend.service.Esp32SilencePolicy;
import com.agrosense.frontend.service.EstateService;
import com.agrosense.frontend.service.IrrigationService;
import com.agrosense.frontend.service.SensorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/ui")
@RequiredArgsConstructor
public class UiApiController {

    private final SensorService sensorService;
    private final EstateService estateService;
    private final IrrigationService irrigationService;
    private final DashboardService dashboardService;
    private final BackendClient backendClient;
    private final CropService cropService;
    private final MessageSource messages;
    private final Esp32SilencePolicy silencePolicy;

    /** The same device the backend reads over TCP (ESP32_DEVICE_ID). */
    @Value("${agrosense.esp32.device-id:ESP32-PLANTICAS}")
    private String esp32DeviceId;

    /**
     * Polled by the Sensors page (esp32-humidity.ts): the ESP32's newest humidity readings, worded like the page. Its TCP
     * readings never pass through the event service, so the live stream cannot carry them.
     */
    @GetMapping("/esp32/humidity")
    public HumidityFeed esp32Humidity(Principal principal) {
        String email = principal.getName();
        Optional<ConnectedSensor> connected =
                sensorService.findConnected(email, esp32DeviceId, sensorService.findSensors(email));
        List<HumidityRow> rows = connected
                .map(found -> found.readings().stream()
                        .map(reading -> HumidityRow.of(reading, messages.getMessage("sensorType." + reading.type(), null,
                                reading.type().name(), LocaleContextHolder.getLocale())))
                        .toList())
                .orElse(List.of());
        // Only an ESP32 of this user can be silent; for anyone else there is nothing to listen to.
        Esp32Silence silence = connected.map(found -> silencePolicy.of(
                found.readings().isEmpty() ? null : found.readings().get(0).recordedAt(), LocalDateTime.now()))
                .orElse(Esp32Silence.LISTENING);
        return new HumidityFeed(rows, rows.isEmpty() ? null : rows.get(0).recordedAt(), silence);
    }

    /** The Sensors page's 24-hour chart of the ESP32's humidity (esp32-humidity.ts); empty for anyone but the owner. */
    @GetMapping("/esp32/humidity/day")
    public HumidityDay esp32HumidityDay(Principal principal) {
        return new HumidityDay(sensorService.findHumidityDay(principal.getName(), esp32DeviceId,
                type -> messages.getMessage("sensorType." + type, null, type.name(), LocaleContextHolder.getLocale())),
                SensorService.DAY_BUCKET_MINUTES);
    }

    @GetMapping("/sensors/{sensorId}/readings")
    public ReadingSeries readings(@PathVariable Integer sensorId,
            @RequestParam(defaultValue = "24") int hours, Principal principal) {
        return sensorService.findReadings(principal.getName(), sensorId, hours);
    }

    /** Polled by the crop page (crop-live.ts); only the owner's crops, a foreign one is 404. */
    @GetMapping("/crops/{cropId}/latest")
    public CropLatest latest(@PathVariable Integer cropId, Principal principal) {
        return cropService.findLatest(principal.getName(), cropId);
    }

    @GetMapping("/backend-status")
    public BackendStatus backendStatus() {
        return backendClient.status();
    }

    @GetMapping("/estates/markers")
    public List<EstateMarker> markers(Principal principal) {
        return estateService.findMarkers(principal.getName());
    }

    @GetMapping("/water-usage")
    public List<DailyWater> waterUsage(Principal principal) {
        return dashboardService.waterUsage(principal.getName());
    }

    @PostMapping("/irrigations")
    @ResponseStatus(HttpStatus.CREATED)
    public IrrigationView startIrrigation(@Valid @RequestBody IrrigationForm form, Principal principal) {
        return irrigationService.start(principal.getName(), form);
    }
}
