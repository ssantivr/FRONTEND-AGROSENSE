package com.agrosense.frontend;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
class PostgresCompatibilityTests extends SessionStorageTests {

    private static final String SCHEMA = "agrosense_test_" + Long.toHexString(System.nanoTime());
    private static final String SELECT_SCHEMA = "SET search_path TO " + SCHEMA;

    @Autowired
    private DataSource dataSource;

    @Value("${agrosense.database.dir}")
    private String databaseDir;

    @DynamicPropertySource
    static void useAPrivateSchema(DynamicPropertyRegistry registry) throws SQLException {
        execute("CREATE SCHEMA " + SCHEMA);
        requireThePrivateSchema();
        registry.add("spring.datasource.url", () -> System.getenv("POSTGRES_TEST_URL"));
        registry.add("spring.datasource.username", PostgresCompatibilityTests::username);
        registry.add("spring.datasource.password", PostgresCompatibilityTests::password);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> SELECT_SCHEMA);
    }

    @AfterAll
    static void dropThePrivateSchema() throws SQLException {
        execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void schemaSampleDataAndEntitiesAgreeOnPostgres() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getSchema()).isEqualTo(SCHEMA);
        }
        assertThat(jdbc.queryForObject("select count(*) from sensor_readings", Integer.class)).isEqualTo(120);
        assertThatCode(() -> new ResourceDatabasePopulator(new FileSystemResource(databaseDir + "/schema.sql"))
                .execute(dataSource)).doesNotThrowAnyException();
    }

    private static void requireThePrivateSchema() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                System.getenv("POSTGRES_TEST_URL"), username(), password());
                Statement statement = connection.createStatement()) {
            statement.execute(SELECT_SCHEMA);
            try (ResultSet current = statement.executeQuery("select current_schema()")) {
                current.next();
                if (!SCHEMA.equals(current.getString(1))) {
                    throw new IllegalStateException("Expected schema " + SCHEMA + " but the connection uses "
                            + current.getString(1) + "; refusing to run against it");
                }
            }
        }
    }

    private static void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                System.getenv("POSTGRES_TEST_URL"), username(), password());
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String username() {
        String username = System.getenv("POSTGRES_TEST_USERNAME");
        return username == null ? "postgres" : username;
    }

    private static String password() {
        String password = System.getenv("POSTGRES_TEST_PASSWORD");
        return password == null ? "" : password;
    }
}
