package com.agrosense.frontend.config;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.security.BackendAuthenticationProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
public class SecurityConfig {

    /**
     * Everything is same-origin except map tiles (OpenStreetMap streets, Esri satellite imagery). Inline
     * scripts and styles are blocked.
     * The referrer policy sends only the origin to other sites: tile providers reject requests without one.
     */
    private static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "font-src 'self'",
            "img-src 'self' data: https://tile.openstreetmap.org https://server.arcgisonline.com",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, BackendClient backendClient,
            UserDetailsService userDetailsService,
            @Value("${agrosense.backend.sign-in:true}") boolean signInThroughBackend) throws Exception {
        if (signInThroughBackend) {
            // Tried first; the database provider Spring builds from the UserDetailsService stays as the fallback.
            http.authenticationProvider(new BackendAuthenticationProvider(backendClient, userDetailsService));
        }
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/registro", "/registro/disponibilidad", "/css/**", "/js/**",
                                "/img/**", "/fonts/**", "/webjars/**", "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .defaultSuccessUrl("/", true)
                        // A lock after too many failed attempts is told apart from wrong credentials.
                        .failureHandler((request, response, exception) -> response.sendRedirect(
                                request.getContextPath()
                                        + (exception instanceof LockedException ? "/login?locked" : "/login?error"))))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
