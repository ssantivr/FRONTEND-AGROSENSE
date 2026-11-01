package com.agrosense.frontend;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.backend.BackendClient.PredictionResponse;
import com.agrosense.frontend.repository.CropRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@ActiveProfiles("demo")
@TestPropertySource(properties = "agrosense.irrigation.actuator=relay")
class CropPredictionTests {

    private static final String DEMO_EMAIL = "demo@agrosense.co";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private CropRepository cropRepository;
    @Autowired
    private UserDetailsService userDetailsService;
    @MockitoSpyBean
    private BackendClient backendClient;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String pageWith(Optional<PredictionResponse> prediction) throws Exception {
        doReturn(prediction).when(backendClient).latestPrediction(any(), any());
        Integer cropId = cropRepository.findAll().get(0).getIdCrop();
        return mvc.perform(get("/cultivos/" + cropId).with(user(userDetailsService.loadUserByUsername(DEMO_EMAIL))))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void aLocalPredictionIsShownAsTheFallbackItIs() throws Exception {
        String page = pageWith(Optional.of(new PredictionResponse(new BigDecimal("0.5"), "Riega: el suelo está seco.",
                "agrosense-reglas-locales", LocalDateTime.now())));

        assertThat(page).contains("Respaldo local", "El servicio de IA no respondió", "Riega: el suelo está seco.")
                .doesNotContain("Modelo de reglas");
    }

    @Test
    void aPredictionOfTheServiceIsNotCalledAFallback() throws Exception {
        String page = pageWith(Optional.of(new PredictionResponse(new BigDecimal("0.9"), "Riega 20 minutos.",
                "agrosense-riego-reglas-1.0", LocalDateTime.now())));

        assertThat(page).contains("Modelo de reglas", "Riega 20 minutos.").doesNotContain("Respaldo local");
    }

    @Test
    void withoutAPredictionTheCardIsNotThere() throws Exception {
        assertThat(pageWith(Optional.empty())).doesNotContain("Última recomendación de riego");
    }

    @Test
    void thePagesTellTheTruthAboutARelay() throws Exception {
        String page = mvc.perform(get("/riego").with(user(userDetailsService.loadUserByUsername(DEMO_EMAIL)))).andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Modo relé").doesNotContain("Modo simulado");
    }
}
