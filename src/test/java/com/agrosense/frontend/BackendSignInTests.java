package com.agrosense.frontend;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-in and sign-up when the backend API answers; WebFlowTests covers the same pages without it. */
@SpringBootTest
@ActiveProfiles("demo")
@Transactional
class BackendSignInTests {

    private static final String DEMO_EMAIL = "demo@agrosense.co";

    @Autowired
    private WebApplicationContext context;
    @MockitoBean
    private BackendClient backendClient;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void theBackendsAcceptanceSignsTheUserInWithTheirOwnProfile() throws Exception {
        doReturn(Optional.of(true)).when(backendClient).attempt(any());

        mvc.perform(formLogin("/login").userParameter("email").user(" Demo@AgroSense.co ").password("checked-by-backend"))
                .andExpect(authenticated().withUsername(DEMO_EMAIL))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void aLockStopsTheSignInEvenWithTheRightPassword() throws Exception {
        doThrow(new LockedException("Demasiados intentos fallidos.")).when(backendClient).attempt(any());

        mvc.perform(formLogin("/login").userParameter("email").user(DEMO_EMAIL).password("agrosense"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?locked"));
        mvc.perform(get("/login?locked"))
                .andExpect(content().string(containsString("Demasiados intentos fallidos.")));
    }

    @Test
    void wrongCredentialsAndASilentBackendEndAtTheDatabaseCheck() throws Exception {
        doThrow(new BadCredentialsException("wrong")).when(backendClient).attempt(any());
        mvc.perform(formLogin("/login").userParameter("email").user(DEMO_EMAIL).password("wrong"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));

        doReturn(Optional.empty()).when(backendClient).attempt(any());
        mvc.perform(formLogin("/login").userParameter("email").user(DEMO_EMAIL).password("agrosense"))
                .andExpect(authenticated().withUsername(DEMO_EMAIL));
        mvc.perform(formLogin("/login").userParameter("email").user(DEMO_EMAIL).password("wrong"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void theBackendsRefusalOfASignUpIsShownInTheForm() throws Exception {
        doThrow(new BusinessRuleException("Ya existe una cuenta con ese correo.")).when(backendClient).attempt(any());

        mvc.perform(post("/registro").with(csrf())
                        .param("name", "Ana").param("lastName", "Pérez").param("email", "ana@agrosense.test")
                        .param("password", "a-long-password").param("confirmPassword", "a-long-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ya existe una cuenta con ese correo.")));

        doReturn(Optional.of(true)).when(backendClient).attempt(any());
        mvc.perform(post("/registro").with(csrf())
                        .param("name", "Ana").param("lastName", "Pérez").param("email", "ana@agrosense.test")
                        .param("password", "a-long-password").param("confirmPassword", "a-long-password"))
                .andExpect(redirectedUrl("/login?registered"));
    }
}
