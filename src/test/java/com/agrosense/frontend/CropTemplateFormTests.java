package com.agrosense.frontend;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.backend.BackendClient.CropTemplateResponse;
import com.agrosense.frontend.repository.EstateRepository;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@ActiveProfiles("demo")
class CropTemplateFormTests {

    private static final String DEMO_EMAIL = "demo@agrosense.co";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private EstateRepository estateRepository;
    @Autowired
    private UserDetailsService userDetailsService;
    @MockitoSpyBean
    private BackendClient backendClient;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String formWith(List<CropTemplateResponse> templates) throws Exception {
        doReturn(templates).when(backendClient).cropTemplates(any());
        Integer estateId = estateRepository.findAll().get(0).getIdEstate();
        return mvc.perform(get("/parcelas/" + estateId + "/cultivos/nuevo")
                        .with(user(userDetailsService.loadUserByUsername(DEMO_EMAIL))))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void theFormOffersTheTemplatesWithTheirRanges() throws Exception {
        String page = formWith(List.of(new CropTemplateResponse("TOMATE", "Tomate", new BigDecimal("60"),
                new BigDecimal("80"), new BigDecimal("18"), new BigDecimal("27"), new BigDecimal("5.5"),
                new BigDecimal("6.8"))));

        assertThat(page).contains("id=\"cropTemplate\"", ">Tomate<", "data-humidity-min=\"60\"", "data-temp-max=\"27\"",
                "/js/crop-form.js", "no validados agronómicamente");
        // The ESP32 measures no pH, so the template's pH is not copied into a form that has no pH field.
        assertThat(page).doesNotContain("data-ph-max", "phMin");
    }

    @Test
    void withoutTheBackendTheFormStillWorksWithoutTheSelector() throws Exception {
        String page = formWith(List.of());

        assertThat(page).doesNotContain("id=\"cropTemplate\"").contains("name=\"humidityMin\"");
    }
}
