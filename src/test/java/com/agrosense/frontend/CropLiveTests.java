package com.agrosense.frontend;

import com.agrosense.frontend.entity.Crop;
import com.agrosense.frontend.entity.Sensor;
import com.agrosense.frontend.entity.SensorReading;
import com.agrosense.frontend.repository.CropRepository;
import com.agrosense.frontend.repository.SensorReadingRepository;
import com.agrosense.frontend.repository.SensorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A reading stored by any road, the ESP32's TCP one included, is what the crop page and its 30-second poll show. */
@SpringBootTest
@ActiveProfiles("demo")
class CropLiveTests {

    private static final String DEMO_EMAIL = "demo@agrosense.co";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private CropRepository cropRepository;
    @Autowired
    private SensorRepository sensorRepository;
    @Autowired
    private SensorReadingRepository readingRepository;
    @Autowired
    private UserDetailsService userDetailsService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void theCropPageSaysTheDeviceIsConnectedAndThePollGivesTheNewestReadings() throws Exception {
        Crop coffee = cropRepository.findAll().stream().filter(crop -> crop.getName().equals("Café")).findFirst()
                .orElseThrow();
        Sensor soil = sensorRepository.findAll().stream()
                .filter(sensor -> sensor.getSensorCode().equals("AS-001")).findFirst().orElseThrow();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime shownBefore = soil.getLastReadingAt();
        SensorReading added = readingRepository.save(SensorReading.builder().sensor(soil).value(new BigDecimal("12.5"))
                .unit("%").recordedAt(now).build());
        soil.setLastReadingAt(now);
        sensorRepository.save(soil);
        try {
            checkPageAndPoll(coffee);
        } finally {
            // The demo database is shared with the other tests, which count this sensor's readings.
            readingRepository.delete(added);
            soil.setLastReadingAt(shownBefore);
            sensorRepository.save(soil);
        }
    }

    private void checkPageAndPoll(Crop coffee) throws Exception {
        String page = mvc.perform(get("/cultivos/" + coffee.getIdCrop()).with(owner()))
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("id=\"deviceStatus\"", "ESP32 conectado",
                "/api/ui/crops/" + coffee.getIdCrop() + "/latest", "/js/crop-live.js");

        mvc.perform(get("/api/ui/crops/" + coffee.getIdCrop() + "/latest").with(owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cropId").value(coffee.getIdCrop()))
                .andExpect(jsonPath("$.readings[?(@.sensorCode == 'AS-001')].value").value(12.5))
                .andExpect(jsonPath("$.lastReadingAt").isNotEmpty());
    }

    @Test
    void anotherUsersCropIsNotFound() throws Exception {
        mvc.perform(get("/api/ui/crops/999999/latest").with(owner())).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor owner() {
        return user(userDetailsService.loadUserByUsername(DEMO_EMAIL));
    }
}
