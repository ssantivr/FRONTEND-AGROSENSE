package com.agrosense.frontend.config;

import com.agrosense.frontend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The demo data lives in /database/seed_demo.sql, which creates the demo user without a usable password.
 * With the "demo" profile this gives that user the configured demo password.
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoAccountInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${agrosense.demo.email}")
    private String demoEmail;

    @Value("${agrosense.demo.password}")
    private String demoPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userRepository.findByEmail(demoEmail).ifPresentOrElse(
                user -> user.setPasswordHash(passwordEncoder.encode(demoPassword)),
                () -> log.warn("Demo user {} not found: was database/seed_demo.sql applied?", demoEmail));
    }
}
