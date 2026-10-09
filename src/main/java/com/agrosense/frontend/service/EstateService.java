package com.agrosense.frontend.service;

import com.agrosense.frontend.dto.Views.CropOption;
import com.agrosense.frontend.dto.Views.CropView;
import com.agrosense.frontend.dto.Views.EstateMarker;
import com.agrosense.frontend.dto.Views.EstateView;
import com.agrosense.frontend.entity.Crop;
import com.agrosense.frontend.entity.Estate;
import com.agrosense.frontend.repository.CropRepository;
import com.agrosense.frontend.repository.EstateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EstateService {

    private final EstateRepository estateRepository;
    private final CropRepository cropRepository;

    public List<EstateView> findEstates(String email) {
        return estateRepository.findByUserEmailOrderByName(email).stream()
                .map(EstateService::toView)
                .toList();
    }

    /** Estates that have coordinates and can therefore be drawn on the map. */
    public List<EstateMarker> findMarkers(String email) {
        return findEstates(email).stream()
                .filter(estate -> estate.latitude() != null && estate.longitude() != null)
                .map(estate -> new EstateMarker(
                        estate.id(),
                        estate.name(),
                        estate.location(),
                        estate.areaHa(),
                        estate.latitude(),
                        estate.longitude(),
                        estate.crops().stream().filter(CropView::active).map(CropView::name).toList()))
                .toList();
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
                crops);
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
