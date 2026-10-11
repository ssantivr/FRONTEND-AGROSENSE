package com.agrosense.frontend.service;

import com.agrosense.frontend.dto.Boundary;
import com.agrosense.frontend.dto.Views.CropOption;
import com.agrosense.frontend.dto.Views.CropView;
import com.agrosense.frontend.dto.Views.EstateMarker;
import com.agrosense.frontend.dto.Views.EstateView;
import com.agrosense.frontend.dto.Views.SensorSpot;
import com.agrosense.frontend.dto.Views.SensorView;
import com.agrosense.frontend.entity.Crop;
import com.agrosense.frontend.entity.Estate;
import com.agrosense.frontend.repository.CropRepository;
import com.agrosense.frontend.repository.EstateRepository;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EstateService {

    private final EstateRepository estateRepository;
    private final CropRepository cropRepository;
    private final SensorService sensorService;
    private final AlertService alertService;
    private final MessageSource messages;

    public List<EstateView> findEstates(String email) {
        return estateRepository.findByUserEmailOrderByName(email).stream()
                .map(EstateService::toView)
                .toList();
    }

    /** Estates that have coordinates and can therefore be drawn on the map. */
    public List<EstateMarker> findMarkers(String email) {
        return cardsOf(findEstates(email), sensorService.findSensors(email), alertService.countOpenByEstate(email))
                .stream()
                .filter(EstateMarker::hasCoordinates)
                .toList();
    }

    /**
     * Every estate as the dashboard shows it, with or without coordinates, from estates that were already
     * read. {@code sensors} are all the user's sensors; {@code openAlerts} counts open alerts by estate id.
     */
    public List<EstateMarker> cardsOf(List<EstateView> estates, List<SensorView> sensors,
            Map<Integer, Integer> openAlerts) {
        Map<Integer, List<SensorView>> byEstate = sensors.stream().collect(Collectors.groupingBy(SensorView::estateId));
        return estates.stream()
                .map(estate -> new EstateMarker(
                        estate.id(),
                        estate.name(),
                        estate.location(),
                        estate.areaHa(),
                        estate.latitude(),
                        estate.longitude(),
                        estate.crops().stream().filter(CropView::active).map(CropView::name).toList(),
                        byEstate.getOrDefault(estate.id(), List.of()).size(),
                        openAlerts.getOrDefault(estate.id(), 0),
                        byEstate.getOrDefault(estate.id(), List.of()).stream()
                                .filter(SensorView::placed)
                                .map(this::toSpot)
                                .toList(),
                        estate.boundary()))
                .toList();
    }

    /** The active crops of estates that were already read, labelled and ordered like findActiveCropOptions. */
    public List<CropOption> activeCropOptionsOf(List<EstateView> estates) {
        return estates.stream()
                .flatMap(estate -> estate.crops().stream()
                        .filter(CropView::active)
                        .map(crop -> Map.entry(crop.name(), new CropOption(crop.id(), crop.name() + " · " + estate.name()))))
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
    }

    private SensorSpot toSpot(SensorView sensor) {
        return new SensorSpot(
                sensor.id(),
                sensor.code(),
                messages.getMessage("sensorType." + sensor.type(), null, sensor.type().name(),
                        LocaleContextHolder.getLocale()),
                sensor.latitude(),
                sensor.longitude(),
                sensor.coverageM(),
                sensor.active(),
                sensor.lastValue(),
                sensor.unit());
    }

    public List<CropOption> findActiveCropOptions(String email) {
        return cropRepository.findByEstateUserEmailAndActiveTrueOrderByName(email).stream()
                .map(crop -> new CropOption(crop.getIdCrop(), crop.getName() + " · " + crop.getEstate().getName()))
                .toList();
    }

    private static EstateView toView(Estate estate) {
        List<CropView> crops = estate.getCrops().stream()
                .sorted(Comparator.comparing(Crop::getName))
                .map(EstateService::toView)
                .toList();
        return new EstateView(
                estate.getIdEstate(),
                estate.getName(),
                estate.getLocation(),
                estate.getAreaHa(),
                estate.getLatitude(),
                estate.getLongitude(),
                crops,
                // Stored through Boundary, so it parses; null when the estate has no outline.
                estate.getBoundary() == null ? null : Boundary.parse(estate.getBoundary()).corners());
    }

    private static CropView toView(Crop crop) {
        return new CropView(
                crop.getIdCrop(),
                crop.getName(),
                crop.getVariety(),
                crop.getSowingDate(),
                crop.getStage(),
                crop.getHumidityMin(),
                crop.getHumidityMax(),
                crop.getTempMin(),
                crop.getTempMax(),
                crop.getPhMin(),
                crop.getPhMax(),
                Boolean.TRUE.equals(crop.getActive()));
    }
}
