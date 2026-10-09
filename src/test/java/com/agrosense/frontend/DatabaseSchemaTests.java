package com.agrosense.frontend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts without the demo profile: tables come only from database/schema.sql and Hibernate validates the
 * frontend entities against them. H2 runs in PostgreSQL compatibility mode.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:agrosense-frontend-schema;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always"
})
class DatabaseSchemaTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void entitiesMatchTheSchemaScript() {
        assertThat(jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class))
                .contains("users", "estates", "crops", "sensors", "sensor_readings", "alerts", "irrigations");
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isZero();
    }
}
