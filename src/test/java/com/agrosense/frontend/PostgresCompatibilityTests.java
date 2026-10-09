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

/**
 * The schema script, the sample data, the frontend entities and the stored sessions on a real PostgreSQL;
 * the rest of the suite runs on H2 emulating it. Skipped unless POSTGRES_TEST_URL is set, for example:
 *
 * <pre>
 * POSTGRES_TEST_URL=jdbc:postgresql://localhost:5432/postgres
 * POSTGRES_TEST_USERNAME=postgres
 * POSTGRES_TEST_PASSWORD=...
 * </pre>
 *
 * Everything happens inside a schema created for the run and dropped at the end, so the database the
 * URL points at is left as it was. The session test is inherited and runs here against PostgreSQL.
 */
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
        // These replace the in-memory database of the demo profile, which still loads the sample data.
        registry.add("spring.datasource.url", () -> System.getenv("POSTGRES_TEST_URL"));
        registry.add("spring.datasource.username", PostgresCompatibilityTests::username);
        registry.add("spring.datasource.password", PostgresCompatibilityTests::password);
        // Every pooled connection selects the schema itself. The currentSchema URL parameter is not enough:
        // hosted PostgreSQL behind a proxy (Neon) ignores it and the run would write into public.
        registry.add("spring.datasource.hikari.connection-init-sql", () -> SELECT_SCHEMA);
    }

    @AfterAll
    static void dropThePrivateSchema() throws SQLException {
        execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    /** Starting the context already applied schema.sql and seed_demo.sql and validated every entity. */
    @Test
    void schemaSampleDataAndEntitiesAgreeOnPostgres() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getSchema()).isEqualTo(SCHEMA);
        }
        assertThat(jdbc.queryForObject("select count(*) from sensor_readings", Integer.class)).isEqualTo(120);
        // Applying the script again, as a restart with SQL_INIT_MODE=always does, changes nothing.
        assertThatCode(() -> new ResourceDatabasePopulator(new FileSystemResource(databaseDir + "/schema.sql"))
                .execute(dataSource)).doesNotThrowAnyException();
    }

    /** Stops the run before the application starts unless the server really switches to the private schema. */
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
