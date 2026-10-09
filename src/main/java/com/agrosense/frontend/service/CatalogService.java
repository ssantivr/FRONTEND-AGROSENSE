package com.agrosense.frontend.service;

import com.agrosense.frontend.dto.CropForm;
import com.agrosense.frontend.dto.EstateForm;
import com.agrosense.frontend.dto.SensorForm;
import com.agrosense.frontend.entity.Crop;
import com.agrosense.frontend.entity.Estate;
import com.agrosense.frontend.entity.Sensor;
import com.agrosense.frontend.entity.User;
import com.agrosense.frontend.exception.BusinessRuleException;
import com.agrosense.frontend.exception.NotFoundException;
import com.agrosense.frontend.repository.AlertRepository;
import com.agrosense.frontend.repository.CropRepository;
import com.agrosense.frontend.repository.EstateRepository;
import com.agrosense.frontend.repository.IrrigationRepository;
import com.agrosense.frontend.repository.SensorReadingRepository;
import com.agrosense.frontend.repository.SensorRepository;
import com.agrosense.frontend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** Creates, edits and removes the user's own estates, crops and sensors. */
@Service
@RequiredArgsConstructor
@Transactional
public class CatalogService {

    private final UserRepository userRepository;
    private final EstateRepository estateRepository;
    private final CropRepository cropRepository;
    private final SensorRepository sensorRepository;
    private final SensorReadingRepository readingRepository;
    private final AlertRepository alertRepository;
    private final IrrigationRepository irrigationRepository;

    // ---------- Estates ----------

    @Transactional(readOnly = true)
    public EstateForm findEstateForm(String email, Integer estateId) {
        Estate estate = findEstate(email, estateId);
        EstateForm form = new EstateForm();
        form.setName(estate.getName());
        form.setLocation(estate.getLocation());
        form.setAreaHa(estate.getAreaHa());
        form.setLatitude(estate.getLatitude());
        form.setLongitude(estate.getLongitude());
        return form;
    }

    @Transactional(readOnly = true)
    public String findEstateName(String email, Integer estateId) {
        return findEstate(email, estateId).getName();
    }

    public void createEstate(String email, EstateForm form) {
        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found"));
        Estate estate = Estate.builder().user(owner).build();
        apply(form, estate);
        estateRepository.save(estate);
    }

    public void updateEstate(String email, Integer estateId, EstateForm form) {
        apply(form, findEstate(email, estateId));
    }

    public void deleteEstate(String email, Integer estateId) {
        findEstate(email, estateId);
        // Readings, alerts and irrigations have no cascade from crops, so they go first.
        readingRepository.deleteByEstate(estateId);
        alertRepository.deleteByEstate(estateId);
        irrigationRepository.deleteByEstate(estateId);
        estateRepository.delete(findEstate(email, estateId));
    }

    // ---------- Crops ----------

    @Transactional(readOnly = true)
    public CropForm findCropForm(String email, Integer cropId) {
        Crop crop = findCrop(email, cropId);
        CropForm form = new CropForm();
        form.setName(crop.getName());
        form.setVariety(crop.getVariety());
        form.setSowingDate(crop.getSowingDate());
        form.setStage(crop.getStage());
        form.setHumidityMin(crop.getHumidityMin());
        form.setHumidityMax(crop.getHumidityMax());
        form.setTempMin(crop.getTempMin());
        form.setTempMax(crop.getTempMax());
        form.setPhMin(crop.getPhMin());
        form.setPhMax(crop.getPhMax());
        form.setActive(Boolean.TRUE.equals(crop.getActive()));
        return form;
    }

    public void createCrop(String email, Integer estateId, CropForm form) {
        Crop crop = Crop.builder().estate(findEstate(email, estateId)).build();
        apply(form, crop);
        cropRepository.save(crop);
    }

    public void updateCrop(String email, Integer cropId, CropForm form) {
        apply(form, findCrop(email, cropId));
    }

    public void deleteCrop(String email, Integer cropId) {
        findCrop(email, cropId);
        readingRepository.deleteByCrop(cropId);
        alertRepository.deleteByCrop(cropId);
        irrigationRepository.deleteByCrop(cropId);
        Crop crop = findCrop(email, cropId);
        // Removing it from the estate's collection lets orphan removal delete the crop and its sensors.
        crop.getEstate().getCrops().remove(crop);
        cropRepository.delete(crop);
    }

    // ---------- Sensors ----------

    public void createSensor(String email, SensorForm form) {
        Crop crop = findCrop(email, form.getCropId());
        if (!Boolean.TRUE.equals(crop.getActive())) {
            // The form only offers active crops, so this is a stale or hand-made request.
            throw new NotFoundException("Crop is not active");
        }
        String code = form.getSensorCode().trim().toUpperCase(Locale.ROOT);
        if (sensorRepository.existsBySensorCodeIgnoreCase(code)) {
            throw new BusinessRuleException("Ese código de sensor ya está en uso.");
        }
        sensorRepository.save(Sensor.builder()
                .crop(crop)
                .sensorCode(code)
                .sensorType(form.getSensorType())
                .location(blankToNull(form.getLocation()))
                .build());
    }

    public void deleteSensor(String email, Integer sensorId) {
        findSensor(email, sensorId);
        readingRepository.deleteBySensor(sensorId);
        alertRepository.clearSensor(sensorId);
        Sensor sensor = findSensor(email, sensorId);
        sensor.getCrop().getSensors().remove(sensor);
        sensorRepository.delete(sensor);
    }

    // ---------- Helpers ----------

    private Estate findEstate(String email, Integer estateId) {
        return estateRepository.findByIdEstateAndUserEmail(estateId, email)
                .orElseThrow(() -> new NotFoundException("Estate not found"));
    }

    private Crop findCrop(String email, Integer cropId) {
        return cropRepository.findByIdCropAndEstateUserEmail(cropId, email)
                .orElseThrow(() -> new NotFoundException("Crop not found"));
    }

    private Sensor findSensor(String email, Integer sensorId) {
        return sensorRepository.findByIdSensorAndCropEstateUserEmail(sensorId, email)
                .orElseThrow(() -> new NotFoundException("Sensor not found"));
    }

    private static void apply(EstateForm form, Estate estate) {
        estate.setName(form.getName().trim());
        estate.setLocation(blankToNull(form.getLocation()));
        estate.setAreaHa(form.getAreaHa());
        estate.setLatitude(form.getLatitude());
        estate.setLongitude(form.getLongitude());
    }

    private static void apply(CropForm form, Crop crop) {
        crop.setName(form.getName().trim());
        crop.setVariety(blankToNull(form.getVariety()));
        crop.setSowingDate(form.getSowingDate());
        crop.setStage(form.getStage());
        crop.setHumidityMin(form.getHumidityMin());
        crop.setHumidityMax(form.getHumidityMax());
        crop.setTempMin(form.getTempMin());
        crop.setTempMax(form.getTempMax());
        crop.setPhMin(form.getPhMin());
        crop.setPhMax(form.getPhMax());
        crop.setActive(form.isActive());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
