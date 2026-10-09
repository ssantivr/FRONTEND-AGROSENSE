package com.agrosense.frontend.backend;

import com.agrosense.frontend.backend.BackendClient.IrrigationResponse;
import com.agrosense.frontend.backend.BackendClient.ReadingResponse;
import com.agrosense.frontend.entity.enums.SensorType;
import com.agrosense.frontend.exception.BusinessRuleException;
import com.agrosense.frontend.exception.NotFoundException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The client against a local HTTP server that answers like the backend API. */
class BackendClientTests {

    private static final String SECRET = "test-secret-that-is-at-least-64-bytes-long-for-hs512-signatures-0123456789";
    private static final String EMAIL = "farmer@agrosense.test";

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();

    private HttpServer server;
    private BackendClient client;
    private volatile int status = 200;
    private volatile String responseBody = "{}";
    private volatile long delayMs;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/health", exchange -> respond(exchange, 200, "{\"status\":\"UP\"}"));
        server.createContext("/api/", exchange -> {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sleep(delayMs);
            respond(exchange, status, responseBody);
        });
        server.start();
        client = new BackendClient("http://127.0.0.1:" + server.getAddress().getPort() + "/", SECRET, 1000, 600, 600);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void isOffWithoutAUrlOrASecret() {
        BackendClient unconfigured = new BackendClient("", "", 1000, 600, 600);

        assertThat(unconfigured.isAvailable()).isFalse();
        assertThat(unconfigured.attempt(() -> "never called")).isEmpty();
    }

    @Test
    void callsCarryATokenTheBackendCanVerifyForTheUser() throws Exception {
        client.acknowledgeAlert(EMAIL, 12);

        assertThat(requests).containsExactly("PATCH /api/alerts/12/acknowledge");
        String[] token = authorizations.get(0).substring("Bearer ".length()).split("\\.");
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        byte[] expected = mac.doFinal((token[0] + "." + token[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(Base64.getUrlDecoder().decode(token[2])).isEqualTo(expected);
        assertThat(new String(Base64.getUrlDecoder().decode(token[0]), StandardCharsets.UTF_8)).contains("HS512");
        assertThat(new String(Base64.getUrlDecoder().decode(token[1]), StandardCharsets.UTF_8))
                .contains("\"sub\":\"" + EMAIL + "\"").contains("\"exp\":");
    }

    @Test
    void sensorsAndIrrigationsAreSentInTheShapeTheBackendExpects() {
        client.createSensor(EMAIL, 3, SensorType.SOIL_MOISTURE, "AS-900", null);
        responseBody = "{\"idIrrigation\":41,\"idCrop\":3,\"startedAt\":\"2026-10-09T10:00:00\","
                + "\"endedAt\":\"2026-10-09T10:20:00\",\"durationMin\":20,\"waterLiters\":80.50,"
                + "\"type\":\"MANUAL\",\"reason\":\"Riego manual\"}";

        IrrigationResponse irrigation = client.startIrrigation(EMAIL, 3, new BigDecimal("80.50"), 20, "Riego manual");

        assertThat(requests).containsExactly("POST /api/sensors", "POST /api/riego");
        assertThat(bodies.get(0)).contains("\"cropId\":3", "\"sensorType\":\"SOIL_MOISTURE\"", "\"sensorCode\":\"AS-900\"");
        assertThat(bodies.get(1)).contains("\"cropId\":3", "\"waterLiters\":80.50", "\"durationMinutes\":20",
                "\"reason\":\"Riego manual\"");
        assertThat(irrigation.idIrrigation()).isEqualTo(41);
        assertThat(irrigation.endedAt()).isEqualTo(irrigation.startedAt().plusMinutes(20));
        assertThat(irrigation.waterLiters()).isEqualByComparingTo("80.5");
    }

    @Test
    void readingsAreReadFromTheBackend() {
        responseBody = "[{\"idReading\":9,\"idSensor\":1,\"sensorCode\":\"AS-001\",\"sensorType\":\"SOIL_MOISTURE\","
                + "\"value\":52.3000,\"unit\":\"%\",\"quality\":\"OK\",\"recordedAt\":\"2026-10-09T09:30:00\"}]";

        List<ReadingResponse> readings = client.latestReadings(EMAIL, 1, 500);

        assertThat(requests).containsExactly("GET /api/sensors/1/readings?limit=500");
        assertThat(readings).hasSize(1);
        assertThat(readings.get(0).value()).isEqualByComparingTo("52.3");
        assertThat(readings.get(0).unit()).isEqualTo("%");
        assertThat(readings.get(0).recordedAt()).hasToString("2026-10-09T09:30");
    }

    @Test
    void theBackendsOwnAnswersKeepTheirMeaning() {
        status = 404;
        responseBody = "{\"message\":\"No se encontró el recurso solicitado.\"}";
        assertThatThrownBy(() -> client.acknowledgeAlert(EMAIL, 99)).isInstanceOf(NotFoundException.class);

        status = 409;
        responseBody = "{\"message\":\"Ya existe un sensor con ese código.\"}";
        assertThatThrownBy(() -> client.createSensor(EMAIL, 3, SensorType.PH, "AS-001", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Ya existe un sensor con ese código.");
    }

    @Test
    void serverErrorsAndTimeoutsMakeSafeCallsFallBack() {
        status = 500;
        assertThatThrownBy(() -> client.acknowledgeAlert(EMAIL, 1)).isInstanceOf(BackendUnavailableException.class);
        assertThat(client.attempt(() -> client.acknowledgeAlert(EMAIL, 1))).isEmpty();

        status = 200;
        responseBody = "[]";
        delayMs = 1500;
        Optional<List<ReadingResponse>> slow = client.attempt(() -> client.latestReadings(EMAIL, 1, 500));
        assertThat(slow).isEmpty();
        // After a timeout the client stops trying for a while instead of making every page wait.
        delayMs = 0;
        int callsSoFar = requests.size();
        assertThat(client.attempt(() -> client.latestReadings(EMAIL, 1, 500))).isEmpty();
        assertThat(requests).hasSize(callsSoFar);
    }

    @Test
    void anUnreachableBackendIsReportedAsUnavailable() {
        server.stop(0);

        assertThat(client.isAvailable()).isFalse();
        assertThat(client.attempt(() -> client.acknowledgeAlert(EMAIL, 1))).isEmpty();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
