package com.agrosense.frontend;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A login is stored in the spring_session tables of database/schema.sql and read back from them. Unlike
 * WebFlowTests, requests go through the Spring Session filter, so nothing is kept in a mock session.
 */
@SpringBootTest
@ActiveProfiles("demo")
class SessionStorageTests {

    private static final String DEMO_EMAIL = "demo@agrosense.co";
    private static final String SESSIONS_OF_DEMO_USER = "select count(*) from spring_session where principal_name = ?";

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private SessionRepositoryFilter<?> sessionFilter;

    @Value("${agrosense.demo.password}")
    private String demoPassword;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(sessionFilter).apply(springSecurity()).build();
    }

    @Test
    void aLoginIsStoredInTheDatabaseAndEndsWithLogout() throws Exception {
        Cookie session = mvc.perform(formLogin("/login").userParameter("email").user(DEMO_EMAIL).password(demoPassword))
                .andExpect(redirectedUrl("/"))
                .andReturn().getResponse().getCookie("SESSION");

        assertThat(session).isNotNull();
        assertThat(jdbc.queryForObject(SESSIONS_OF_DEMO_USER, Integer.class, DEMO_EMAIL)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from spring_session_attributes", Integer.class)).isPositive();

        // Only the cookie travels: the signed-in user comes from the stored session.
        mvc.perform(get("/configuracion").cookie(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(DEMO_EMAIL)));

        mvc.perform(post("/logout").cookie(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        assertThat(jdbc.queryForObject(SESSIONS_OF_DEMO_USER, Integer.class, DEMO_EMAIL)).isZero();
        mvc.perform(get("/configuracion").cookie(session)).andExpect(redirectedUrl("/login"));
    }
}
