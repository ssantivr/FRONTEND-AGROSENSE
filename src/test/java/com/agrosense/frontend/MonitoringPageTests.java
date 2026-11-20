package com.agrosense.frontend;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.backend.BackendClient.Esp32Status;
import com.agrosense.frontend.backend.BackendClient.MonitoringResponse;
import com.agrosense.frontend.backend.BackendClient.WatchRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** «Monitoreo» shows, in Spanish, what the backend reports about the ESP32, the watering rule and the watches. */
@SpringBootTest
@ActiveProfiles("demo")
class MonitoringPageTests {

    private static final String DEMO_EMAIL = "demo@agrosense.co";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserDetailsService userDetailsService;
    @MockitoSpyBean
    private BackendClient backendClient;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String pageWith(Optional<MonitoringResponse> monitoring) throws Exception {
        doReturn(monitoring).when(backendClient).monitoring(any());
        return mvc.perform(get("/monitoreo").with(user(userDetailsService.loadUserByUsername(DEMO_EMAIL))))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void theReceiverTheRuleAndTheWatchesAreShownInSpanish() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        String page = pageWith(Optional.of(new MonitoringResponse(
                new Esp32Status(true, true, "ESP32-PLANTICAS", 60, now, now, 304, 5, 0, "mean", "RECEIVING",
                        "Recibiendo datos del ESP32"),
                "suelo < 40 y aire < 60",
                List.of(new WatchRun("frost", Instant.now(), 12), new WatchRun("silent devices", Instant.now(), 3)))));

        assertThat(page).contains("Recibiendo datos del ESP32", "status-pill--success", "ESP32-PLANTICAS",
                "Promedio de los bloques de cada 60 s", "suelo &lt; 40 y aire &lt; 60", "Conviene regar",
                "Riesgo de helada", "Dispositivos sin reportar", "12 ms", "data-monitoring")
                .doesNotContain("El servidor no responde", ">frost<", ">mean<");
        assertThat(page).as("the page is in the menu and marked as the current one")
                .contains("title=\"Monitoreo\" aria-current=\"page\"");
    }

    @Test
    void aSilentLinkAMedianAndNoRuleAreToldPlainly() throws Exception {
        String page = pageWith(Optional.of(new MonitoringResponse(
                new Esp32Status(true, true, "ESP32-PLANTICAS", 60, null, null, 0, 0, 0, "median", "SILENT",
                        "El ESP32 está conectado pero no envía datos"),
                null, List.of())));

        assertThat(page).contains("status-pill--warning", "Mediana de los bloques", "Todavía ninguno",
                "Apagada: no hay regla de riego", "Todavía no ha corrido ninguna");
    }

    @Test
    void withoutTheBackendThePageSaysSoInsteadOfFailing() throws Exception {
        doReturn(true).when(backendClient).isConfigured();
        assertThat(pageWith(Optional.empty())).contains("El servidor no responde").doesNotContain("Regla de riego")
                .doesNotContain("no está configurado");
    }

    @Test
    void withoutABackendConfiguredThePageSaysSoAndNotThatItIsDown_A7() throws Exception {
        doReturn(false).when(backendClient).isConfigured();
        assertThat(pageWith(Optional.empty())).contains("El servidor no está configurado", "BACKEND_URL")
                .doesNotContain("El servidor no responde");
    }

    @Test
    void theRefreshStatusLivesOutsideTheRefreshedPart_B5() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        String page = pageWith(Optional.of(new MonitoringResponse(
                new Esp32Status(true, true, "ESP32-PLANTICAS", 60, now, now, 1, 1, 0, "mean", "RECEIVING", "Recibiendo"),
                null, List.of())));

        assertThat(page.indexOf("data-monitoring-updated")).isPositive()
                .isLessThan(page.indexOf("data-monitoring aria-busy"));
        assertThat(page).contains("Actualizado a las", "data-monitoring-retry");
    }
}
