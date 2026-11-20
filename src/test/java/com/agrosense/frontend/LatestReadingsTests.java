package com.agrosense.frontend;

import com.agrosense.frontend.entity.Sensor;
import com.agrosense.frontend.entity.SensorReading;
import com.agrosense.frontend.entity.enums.ReadingSource;
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

/** GET /api/ui/readings/latest: the polling fallback of the live badge, only new readings of the user's own sensors. */
@SpringBootTest
@ActiveProfiles("demo")
class LatestReadingsTests {

    private static final String DEMO_EMAIL = "demo@agrosense.co";

    @Autowired
    private WebApplicationContext context;
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
    void onlyReadingsAfterSinceComeBackWithTheirSourceAndOnlyForTheirOwner() throws Exception {
        Sensor soil = sensorRepository.findAll().stream()
                .filter(sensor -> sensor.getSensorCode().equals("AS-001")).findFirst().orElseThrow();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        SensorReading older = readingRepository.save(SensorReading.builder().sensor(soil)
                .value(new BigDecimal("30.0")).unit("%").recordedAt(now.minusSeconds(30))
                .source(ReadingSource.ESP32_TCP).build());
        SensorReading newer = readingRepository.save(SensorReading.builder().sensor(soil)
                .value(new BigDecimal("31.5")).unit("%").recordedAt(now.minusSeconds(5))
                .source(ReadingSource.SIMULATED).build());
        try {
            String since = now.minusSeconds(20).toString();
            String mine = mvc.perform(get("/api/ui/readings/latest").param("since", since)
                            .with(user(userDetailsService.loadUserByUsername(DEMO_EMAIL))))
                    .andReturn().getResponse().getContentAsString();

            assertThat(mine).contains("\"sensorCode\":\"AS-001\"", "\"value\":31.5", "\"source\":\"SIMULATED\"",
                    "\"recordedAt\":\"").doesNotContain("\"value\":30.0");

            String someoneElse = mvc.perform(get("/api/ui/readings/latest").param("since", since)
                            .with(user("intruso@example.com").roles("USER")))
                    .andReturn().getResponse().getContentAsString();
            assertThat(someoneElse).isEqualTo("[]");
        } finally {
            // The demo database is shared with the other tests, which count this sensor's readings.
            readingRepository.delete(newer);
            readingRepository.delete(older);
        }
    }

    @Test
    void aSinceThatIsNotADateIsABadRequestAndAnonymousVisitorsGetNothing() throws Exception {
        int bad = mvc.perform(get("/api/ui/readings/latest").param("since", "ayer")
                        .with(user(userDetailsService.loadUserByUsername(DEMO_EMAIL))))
                .andReturn().getResponse().getStatus();
        int anonymous = mvc.perform(get("/api/ui/readings/latest")).andReturn().getResponse().getStatus();

        assertThat(bad).isEqualTo(400);
        assertThat(anonymous).isNotEqualTo(200);
    }
}
