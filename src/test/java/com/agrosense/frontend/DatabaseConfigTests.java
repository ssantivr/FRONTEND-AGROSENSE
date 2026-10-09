package com.agrosense.frontend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/** Checks what database/datasource.properties resolves to with and without the "demo" profile. */
class DatabaseConfigTests {

    @Test
    void defaultsPointAtPostgresAndNeverTouchTheSchema() {
        StandardEnvironment environment = load();

        assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:postgresql://");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(environment.getProperty("spring.sql.init.schema-locations"))
                .isEqualTo("file:../database/schema.sql");
        assertThat(environment.getProperty("spring.sql.init.data-locations")).isNull();
    }

    @Test
    void demoProfileUsesAnInMemoryDatabaseBuiltFromTheSameScripts() {
        StandardEnvironment environment = load("demo");

        assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:h2:mem:").contains("MODE=PostgreSQL");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("always");
        assertThat(environment.getProperty("spring.sql.init.data-locations"))
                .isEqualTo("file:../database/seed_demo.sql");
    }

    private static StandardEnvironment load(String... profiles) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profiles);
        ConfigDataEnvironmentPostProcessor.applyTo(environment, new DefaultResourceLoader(), null);
        return environment;
    }
}
