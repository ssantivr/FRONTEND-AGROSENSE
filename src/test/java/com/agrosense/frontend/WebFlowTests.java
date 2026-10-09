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

import java.util.List;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
    private static final String OTHER_PASSWORD = "other-password";

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

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
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy",
                        containsString("img-src 'self' data: https://tile.openstreetmap.org")))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
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

    @Test
    void unsupportedMethodsAndMediaTypesAreClientErrorsNotServerErrors() throws Exception {
        mvc.perform(post("/reportes").with(user(demoUser)).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/ui/irrigations").with(user(demoUser)).with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("cropId=1"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void registrationValidatesInputAndNeverStoresPlainPasswords() throws Exception {
        mvc.perform(get("/registro")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Crear cuenta")));
        mvc.perform(post("/registro").param("email", "new@agrosense.test")).andExpect(status().isForbidden());

        mvc.perform(post("/registro").with(csrf())
                        .param("name", " ").param("lastName", "Pérez").param("email", "not-an-email")
                        .param("password", "short").param("confirmPassword", "different"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ingresa tu nombre.")))
                .andExpect(content().string(containsString("Ingresa un correo electrónico válido.")))
                .andExpect(content().string(containsString("La contraseña debe tener entre 8 y 72 caracteres.")))
                .andExpect(content().string(containsString("Las contraseñas no coinciden.")))
                .andExpect(content().string(not(containsString("value=\"short\""))));
        assertThat(userRepository.findByEmail("not-an-email")).isEmpty();

        mvc.perform(post("/registro").with(csrf())
                        .param("name", "Ana").param("lastName", "Pérez").param("email", "  New@AgroSense.test ")
                        .param("password", "a-long-password").param("confirmPassword", "a-long-password"))
                .andExpect(redirectedUrl("/login?registered"));

        User created = userRepository.findByEmail("new@agrosense.test").orElseThrow();
        assertThat(created.getPasswordHash()).startsWith("$2").doesNotContain("a-long-password");
        assertThat(created.getRole()).isEqualTo("farmer");
        mvc.perform(formLogin("/login").userParameter("email").user("new@agrosense.test").password("a-long-password"))
                .andExpect(authenticated().withUsername("new@agrosense.test"));
    }

    @Test
    void registeringAnExistingEmailGivesAGenericErrorAndKeepsTheOriginalAccount() throws Exception {
        String originalHash = userRepository.findByEmail(DEMO_EMAIL).orElseThrow().getPasswordHash();

        mvc.perform(post("/registro").with(csrf())
                        .param("name", "Intruder").param("lastName", "User").param("email", DEMO_EMAIL)
                        .param("password", "another-password").param("confirmPassword", "another-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No pudimos crear la cuenta con esos datos.")));

        assertThat(userRepository.findByEmail(DEMO_EMAIL).orElseThrow().getPasswordHash()).isEqualTo(originalHash);
    }

    @Test
    void newAccountsSeeEmptyStatesAndTheSchematicMapInsteadOfErrors() throws Exception {
        createOtherUsersSensor();
        UserDetails other = userDetailsService.loadUserByUsername(OTHER_EMAIL);

        mvc.perform(get("/").with(user(other)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sin coordenadas registradas")))
                .andExpect(content().string(containsString("data-estate-tile")))
                .andExpect(content().string(not(containsString("id=\"estateMap\""))))
                .andExpect(content().string(not(containsString("Finca La Esperanza"))));
        for (String path : new String[] {"/parcelas", "/sensores", "/riego", "/alertas", "/reportes", "/configuracion"}) {
            mvc.perform(get(path).with(user(other))).andExpect(status().isOk());
        }
    }

    @Test
    void deletingAnAccountRequiresThePasswordAndRemovesOnlyThatUsersData() throws Exception {
        Sensor sensor = createOtherUsersSensor();
        Integer estateId = sensor.getCrop().getEstate().getIdEstate();
        alertRepository.save(Alert.builder().crop(sensor.getCrop()).alertType(AlertType.WATER_STRESS)
                .message("To be deleted").build());
        UserDetails other = userDetailsService.loadUserByUsername(OTHER_EMAIL);
        long demoEstates = estateRepository.findByUserEmailOrderByName(DEMO_EMAIL).size();

        mvc.perform(post("/configuracion/eliminar-cuenta").with(user(other)).param("password", OTHER_PASSWORD))
                .andExpect(status().isForbidden());
        mvc.perform(post("/configuracion/eliminar-cuenta").with(user(other)).with(csrf()).param("password", "wrong"))
                .andExpect(redirectedUrl("/configuracion"))
                .andExpect(flash().attribute("deleteError", "La contraseña no es correcta."));
        mvc.perform(post("/configuracion/eliminar-cuenta").with(user(other)).with(csrf()))
                .andExpect(redirectedUrl("/configuracion"));
        assertThat(userRepository.findByEmail(OTHER_EMAIL)).isPresent();

        mvc.perform(post("/configuracion/eliminar-cuenta").with(user(other)).with(csrf())
                        .param("password", OTHER_PASSWORD))
                .andExpect(redirectedUrl("/login?deleted"))
                .andExpect(unauthenticated());

        assertThat(userRepository.findByEmail(OTHER_EMAIL)).isEmpty();
        assertThat(estateRepository.findById(estateId)).isEmpty();
        assertThat(sensorRepository.findById(sensor.getIdSensor())).isEmpty();
        assertThat(alertRepository.countByCropEstateUserEmailAndAcknowledgedFalse(OTHER_EMAIL)).isZero();
        assertThat(estateRepository.findByUserEmailOrderByName(DEMO_EMAIL)).hasSize((int) demoEstates);
        mvc.perform(formLogin("/login").userParameter("email").user(OTHER_EMAIL).password(OTHER_PASSWORD))
                .andExpect(unauthenticated());
    }

    @Test
    void theDemoAccountCannotBeDeleted() throws Exception {
        mvc.perform(post("/configuracion/eliminar-cuenta").with(user(demoUser)).with(csrf())
                        .param("password", DEMO_PASSWORD))
                .andExpect(redirectedUrl("/configuracion"))
                .andExpect(flash().attribute("deleteError", "La cuenta de demostración no se puede eliminar."));
        assertThat(userRepository.findByEmail(DEMO_EMAIL)).isPresent();
    }

    @Test
    void estatesCanBeCreatedEditedAndAreValidated() throws Exception {
        mvc.perform(post("/parcelas").with(user(demoUser)).param("name", "No CSRF")).andExpect(status().isForbidden());
        mvc.perform(post("/parcelas").with(user(demoUser)).with(csrf())
                        .param("name", " ").param("latitude", "95").param("areaHa", "abc"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ingresa el nombre de la parcela.")))
                .andExpect(content().string(containsString("La latitud debe estar entre -90 y 90.")))
                .andExpect(content().string(containsString("Indica latitud y longitud, o deja ambas vacías.")))
                .andExpect(content().string(containsString("Ingresa un número válido.")));

        mvc.perform(post("/parcelas").with(user(demoUser)).with(csrf())
                        .param("name", "Finca Nueva").param("location", "Ipiales").param("areaHa", "3.5")
                        .param("latitude", "0.8303").param("longitude", "-77.6444"))
                .andExpect(redirectedUrl("/parcelas"));
        Estate created = estateRepository.findByUserEmailOrderByName(DEMO_EMAIL).stream()
                .filter(estate -> estate.getName().equals("Finca Nueva")).findFirst().orElseThrow();
        assertThat(created.getLatitude()).isEqualByComparingTo("0.8303");

        mvc.perform(get("/parcelas/{id}/editar", created.getIdEstate()).with(user(demoUser)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Finca Nueva\"")));
        mvc.perform(post("/parcelas/{id}", created.getIdEstate()).with(user(demoUser)).with(csrf())
                        .param("name", "Finca Renombrada"))
                .andExpect(redirectedUrl("/parcelas"));
        Estate updated = estateRepository.findById(created.getIdEstate()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Finca Renombrada");
        assertThat(updated.getLatitude()).isNull();
        mvc.perform(get("/api/ui/estates/markers").with(user(demoUser))).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void cropsAndSensorsCanBeCreatedWithValidation() throws Exception {
        Integer estateId = estateRepository.findByUserEmailOrderByName(DEMO_EMAIL).get(0).getIdEstate();

        mvc.perform(post("/parcelas/{id}/cultivos", estateId).with(user(demoUser)).with(csrf())
                        .param("name", "Maíz").param("stage", "GROWTH")
                        .param("humidityMin", "80").param("humidityMax", "40")
                        .param("tempMin", "10").param("tempMax", "30").param("phMin", "5").param("phMax", "15"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("La humedad mínima debe ser menor que la máxima.")))
                .andExpect(content().string(containsString("El pH debe estar entre 0 y 14.")));
        mvc.perform(post("/parcelas/{id}/cultivos", estateId).with(user(demoUser)).with(csrf())
                        .param("name", "Maíz").param("stage", "GROWTH").param("active", "true")
                        .param("humidityMin", "40").param("humidityMax", "80")
                        .param("tempMin", "10").param("tempMax", "30").param("phMin", "5.5").param("phMax", "7"))
                .andExpect(redirectedUrl("/parcelas"));
        Crop corn = cropRepository.findByEstateUserEmailAndActiveTrueOrderByName(DEMO_EMAIL).stream()
                .filter(crop -> crop.getName().equals("Maíz")).findFirst().orElseThrow();

        mvc.perform(post("/sensores").with(user(demoUser)).with(csrf())
                        .param("cropId", corn.getIdCrop().toString()).param("sensorCode", "as-001")
                        .param("sensorType", "PH"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ese código de sensor ya está en uso.")));
        mvc.perform(post("/sensores").with(user(demoUser)).with(csrf())
                        .param("cropId", corn.getIdCrop().toString()).param("sensorCode", "bad code!")
                        .param("sensorType", "PH"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("El código solo admite letras")));
        mvc.perform(post("/sensores").with(user(demoUser)).with(csrf())
                        .param("cropId", corn.getIdCrop().toString()).param("sensorCode", "as-900")
                        .param("sensorType", "PH").param("location", "Lote 3"))
                .andExpect(redirectedUrl("/sensores"));
        mvc.perform(get("/sensores").with(user(demoUser)))
                .andExpect(content().string(containsString("AS-900")));
    }

    @Test
    void catalogChangesAreLimitedToTheOwner() throws Exception {
        Sensor foreign = createOtherUsersSensor();
        Integer foreignCrop = foreign.getCrop().getIdCrop();
        Integer foreignEstate = foreign.getCrop().getEstate().getIdEstate();

        mvc.perform(get("/parcelas/{id}/editar", foreignEstate).with(user(demoUser))).andExpect(status().isNotFound());
        mvc.perform(post("/parcelas/{id}", foreignEstate).with(user(demoUser)).with(csrf()).param("name", "Hacked"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/parcelas/{id}/eliminar", foreignEstate).with(user(demoUser)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/cultivos/{id}/editar", foreignCrop).with(user(demoUser))).andExpect(status().isNotFound());
        mvc.perform(post("/cultivos/{id}/eliminar", foreignCrop).with(user(demoUser)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/sensores/{id}/eliminar", foreign.getIdSensor()).with(user(demoUser)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/sensores").with(user(demoUser)).with(csrf())
                        .param("cropId", foreignCrop.toString()).param("sensorCode", "X-1").param("sensorType", "PH"))
                .andExpect(status().isNotFound());

        assertThat(estateRepository.findById(foreignEstate).orElseThrow().getName()).isEqualTo("Private estate");
        assertThat(sensorRepository.findById(foreign.getIdSensor())).isPresent();
    }

    @Test
    void deletingRemovesDependentRecordsAndNothingElse() throws Exception {
        List<Estate> estates = estateRepository.findByUserEmailOrderByName(DEMO_EMAIL);
        Estate mirador = estates.get(0);
        Estate esperanza = estates.get(1);
        Sensor coffeeSensor = sensorRepository.findByCropEstateUserEmailOrderBySensorCode(DEMO_EMAIL).get(0);
        Crop plantain = cropRepository.findByEstateUserEmailAndActiveTrueOrderByName(DEMO_EMAIL).stream()
                .filter(crop -> crop.getName().equals("Plátano")).findFirst().orElseThrow();

        mvc.perform(post("/sensores/{id}/eliminar", coffeeSensor.getIdSensor()).with(user(demoUser)).with(csrf()))
                .andExpect(redirectedUrl("/sensores"));
        assertThat(sensorRepository.findById(coffeeSensor.getIdSensor())).isEmpty();
        assertThat(sensorRepository.findByCropEstateUserEmailOrderBySensorCode(DEMO_EMAIL)).hasSize(5);

        mvc.perform(post("/cultivos/{id}/eliminar", plantain.getIdCrop()).with(user(demoUser)).with(csrf()))
                .andExpect(redirectedUrl("/parcelas"));
        assertThat(cropRepository.findById(plantain.getIdCrop())).isEmpty();
        assertThat(sensorRepository.findByCropEstateUserEmailOrderBySensorCode(DEMO_EMAIL)).hasSize(3);

        mvc.perform(post("/parcelas/{id}/eliminar", esperanza.getIdEstate()).with(user(demoUser)).with(csrf()))
                .andExpect(redirectedUrl("/parcelas"));
        assertThat(estateRepository.findByUserEmailOrderByName(DEMO_EMAIL)).extracting(Estate::getName)
                .containsExactly(mirador.getName());
        assertThat(sensorRepository.findByCropEstateUserEmailOrderBySensorCode(DEMO_EMAIL)).hasSize(1);
        assertThat(alertRepository.countByCropEstateUserEmailAndAcknowledgedFalse(DEMO_EMAIL)).isEqualTo(1);
        mvc.perform(get("/").with(user(demoUser))).andExpect(status().isOk());
        mvc.perform(get("/riego").with(user(demoUser))).andExpect(status().isOk());
    }

    private Sensor createOtherUsersSensor() {
        User other = userRepository.save(User.builder()
                .name("Other")
                .lastName("Farmer")
                .email(OTHER_EMAIL)
                .passwordHash(passwordEncoder.encode(OTHER_PASSWORD))
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
