package com.agrosense.frontend;

import com.agrosense.frontend.entity.Alert;
import com.agrosense.frontend.entity.Crop;
import com.agrosense.frontend.entity.Estate;
import com.agrosense.frontend.entity.Sensor;
import com.agrosense.frontend.entity.User;
import com.agrosense.frontend.entity.enums.AlertType;
import com.agrosense.frontend.entity.enums.SensorType;
import com.agrosense.frontend.repository.AlertRepository;
import com.agrosense.frontend.repository.CropRepository;
import com.agrosense.frontend.repository.EstateRepository;
import com.agrosense.frontend.repository.SensorRepository;
import com.agrosense.frontend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end checks of the web layer against the seeded demo database. */
@SpringBootTest
@ActiveProfiles("demo")
@Transactional
class WebFlowTests {

    private static final String DEMO_EMAIL = "demo@agrosense.co";
    private static final String DEMO_PASSWORD = "agrosense";
    private static final String OTHER_EMAIL = "other@agrosense.test";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EstateRepository estateRepository;
    @Autowired
    private CropRepository cropRepository;
    @Autowired
    private SensorRepository sensorRepository;
    @Autowired
    private AlertRepository alertRepository;

    private MockMvc mvc;
    private UserDetails demoUser;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        demoUser = userDetailsService.loadUserByUsername(DEMO_EMAIL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/parcelas", "/sensores", "/riego", "/dispositivos", "/alertas", "/reportes",
            "/configuracion", "/api/ui/water-usage"})
    void anonymousUsersAreSentToLogin(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginPageAndStaticAssetsArePublic() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Iniciar sesión")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")));
        mvc.perform(get("/css/styles.css")).andExpect(status().isOk());
        mvc.perform(get("/webjars/chart.js/4.4.1/dist/chart.umd.js")).andExpect(status().isOk());
        mvc.perform(get("/webjars/leaflet/1.9.4/leaflet.js")).andExpect(status().isOk());
    }

    @Test
    void validCredentialsSignInAndWrongOnesShowAGenericError() throws Exception {
        mvc.perform(formLogin("/login").userParameter("email").user(DEMO_EMAIL).password(DEMO_PASSWORD))
                .andExpect(authenticated().withUsername(DEMO_EMAIL))
                .andExpect(redirectedUrl("/"));
        mvc.perform(formLogin("/login").userParameter("email").user(DEMO_EMAIL).password("wrong"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(formLogin("/login").userParameter("email").user("nobody@agrosense.test").password("wrong"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void logoutRequiresPostWithCsrfAndEndsTheSession() throws Exception {
        mvc.perform(post("/logout").with(user(demoUser))).andExpect(status().isForbidden());
        mvc.perform(logout()).andExpect(redirectedUrl("/login?logout")).andExpect(unauthenticated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/parcelas|Finca La Esperanza", "/sensores|AS-001", "/riego|Iniciar riego manual",
            "/dispositivos|No disponible", "/alertas|Humedad baja", "/reportes|Consumo de agua por cultivo",
            "/configuracion|demo@agrosense.co"})
    void everyMenuPageRendersForTheSignedInUser(String pathAndText) throws Exception {
        String[] parts = pathAndText.split("\\|");
        mvc.perform(get(parts[0]).with(user(demoUser)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(parts[1])))
                .andExpect(content().string(containsString("aria-current=\"page\"")));
    }

    @Test
    void dashboardShowsIndicatorsDemoFlagAndUserInitials() throws Exception {
        mvc.perform(get("/").with(user(demoUser)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hola, <span>Usuario Demo</span>")))
                .andExpect(content().string(containsString("Vista de demostración")))
                .andExpect(content().string(containsString("Clima no configurado")))
                .andExpect(content().string(containsString("3 cultivos activos")))
                .andExpect(content().string(containsString(">UD</span>")))
                .andExpect(content().string(containsString("id=\"estateMap\"")));
    }

    @Test
    void uiApiReturnsOnlyTheUsersOwnData() throws Exception {
        Sensor own = sensorRepository.findByCropEstateUserEmailOrderBySensorCode(DEMO_EMAIL).get(0);
        Sensor foreign = createOtherUsersSensor();

        mvc.perform(get("/api/ui/sensors/{id}/readings", own.getIdSensor()).with(user(demoUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sensorCode").value("AS-001"))
                .andExpect(jsonPath("$.points", hasSize(24)))
                .andExpect(jsonPath("$.thresholdMin").value(40.0));
        mvc.perform(get("/api/ui/sensors/{id}/readings", foreign.getIdSensor()).with(user(demoUser)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
        mvc.perform(get("/api/ui/estates/markers").with(user(demoUser)))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/ui/water-usage").with(user(demoUser)))
                .andExpect(jsonPath("$", hasSize(7)));
    }

    @Test
    void irrigationFormValidatesInputAndRejectsOverlaps() throws Exception {
        Integer cropId = cropRepository.findByEstateUserEmailAndActiveTrueOrderByName(DEMO_EMAIL).get(0).getIdCrop();

        mvc.perform(post("/riego").with(user(demoUser)).param("cropId", cropId.toString()).param("durationMin", "30"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/riego").with(user(demoUser)).with(csrf())
                        .param("cropId", cropId.toString()).param("durationMin", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("La duración mínima es de 1 minuto.")));
        mvc.perform(post("/riego").with(user(demoUser)).with(csrf())
                        .param("cropId", cropId.toString()).param("durationMin", "30"))
                .andExpect(redirectedUrl("/riego"));
        mvc.perform(post("/riego").with(user(demoUser)).with(csrf())
                        .param("cropId", cropId.toString()).param("durationMin", "30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ya hay un riego en curso para este cultivo.")));
        mvc.perform(get("/").with(user(demoUser)))
                .andExpect(content().string(containsString("Riego en curso")));
    }

    @Test
    void irrigationApiValidatesJsonAndProtectsOtherUsersCrops() throws Exception {
        Integer foreignCropId = createOtherUsersSensor().getCrop().getIdCrop();
        Integer ownCropId = cropRepository.findByEstateUserEmailAndActiveTrueOrderByName(DEMO_EMAIL).get(0).getIdCrop();

        mvc.perform(post("/api/ui/irrigations").with(user(demoUser)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cropId\":" + ownCropId + ",\"durationMin\":30}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/ui/irrigations").with(user(demoUser)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cropId\":" + ownCropId + ",\"durationMin\":999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La duración máxima es de 240 minutos."));
        mvc.perform(post("/api/ui/irrigations").with(user(demoUser)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cropId\":" + foreignCropId + ",\"durationMin\":30}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/ui/irrigations").with(user(demoUser)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cropId\":" + ownCropId + ",\"durationMin\":30}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.type").value("MANUAL"));
    }

    @Test
    void alertsCanOnlyBeAcknowledgedByTheirOwner() throws Exception {
        Sensor foreign = createOtherUsersSensor();
        Alert foreignAlert = alertRepository.save(Alert.builder()
                .crop(foreign.getCrop())
                .alertType(AlertType.WATER_STRESS)
                .message("Private alert of another user")
                .build());
        long openBefore = alertRepository.countByCropEstateUserEmailAndAcknowledgedFalse(DEMO_EMAIL);
        Integer ownAlertId = alertRepository
                .findByCropEstateUserEmailAndAcknowledgedFalse(DEMO_EMAIL, org.springframework.data.domain.Pageable.ofSize(1))
                .getContent().get(0).getIdAlert();

        mvc.perform(get("/alertas?estado=todas").with(user(demoUser)))
                .andExpect(content().string(not(containsString("Private alert of another user"))));
        mvc.perform(post("/alertas/{id}/atender", foreignAlert.getIdAlert()).with(user(demoUser)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/alertas/{id}/atender", ownAlertId).with(user(demoUser)).with(csrf()))
                .andExpect(redirectedUrl("/alertas"));

        assertThat(alertRepository.countByCropEstateUserEmailAndAcknowledgedFalse(DEMO_EMAIL)).isEqualTo(openBefore - 1);
        assertThat(alertRepository.findById(foreignAlert.getIdAlert()).orElseThrow().getAcknowledged()).isFalse();
    }

    @Test
    void unknownPagesRenderTheErrorTemplate() throws Exception {
        mvc.perform(get("/no-existe").with(user(demoUser)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Página no encontrada")));
    }

    private Sensor createOtherUsersSensor() {
        User other = userRepository.save(User.builder()
                .name("Other")
                .lastName("Farmer")
                .email(OTHER_EMAIL)
                .passwordHash("{noop}unused")
                .role("farmer")
                .build());
        Estate estate = estateRepository.save(Estate.builder().user(other).name("Private estate").build());
        Crop crop = cropRepository.save(Crop.builder().estate(estate).name("Private crop").build());
        return sensorRepository.save(Sensor.builder()
                .crop(crop)
                .sensorCode("OTHER-001")
                .sensorType(SensorType.PH)
                .build());
    }
}
