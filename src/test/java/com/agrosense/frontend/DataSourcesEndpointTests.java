package com.agrosense.frontend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** GET /api/ui/data-sources: only for a signed-in user, and with the static catalogue while there is no backend. */
@SpringBootTest
@ActiveProfiles("demo")
class DataSourcesEndpointTests {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserDetailsService userDetailsService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void anonymousVisitorsGetNothing() throws Exception {
        int status = mvc.perform(get("/api/ui/data-sources")).andReturn().getResponse().getStatus();

        assertThat(status).isNotEqualTo(200);
    }

    @Test
    void aSignedInUserGetsTheFallbackCatalogueWithTheFrontendPart() throws Exception {
        var response = mvc.perform(get("/api/ui/data-sources")
                        .with(user(userDetailsService.loadUserByUsername("demo@agrosense.co"))))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"fallback\":true", "\"frontendComponent\"",
                "readings.esp32", "sensor_readings.reading_value");
    }

    @Test
    void everyPageHasTheDrawerAndItsTrigger() throws Exception {
        String page = mvc.perform(get("/").with(user(userDetailsService.loadUserByUsername("demo@agrosense.co"))))
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("id=\"dataSourcesOpen\"", "id=\"dataSourcesDrawer\"", "role=\"dialog\"",
                "aria-modal=\"true\"", "data-kpi", "data-live-source");
    }
}
