package com.agrosense.frontend.backend;

import com.agrosense.frontend.ai.AiAssistantException;
import com.agrosense.frontend.ai.AiChatForm;
import com.agrosense.frontend.ai.AiReply;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;

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
        client = new BackendClient("http://127.0.0.1:" + server.getAddress().getPort() + "/", SECRET, 1000, 600, 600, 600);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void isOffWithoutAUrlOrASecret() {
        BackendClient unconfigured = new BackendClient("", "", 1000, 600, 600, 600);

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
    void accountsAreCreatedAndCheckedThroughTheBackendWithoutAToken() {
        client.register("Ana María", "Pérez", "ana@agrosense.test", "a-long-password");
        responseBody = "{\"exists\":true,\"available\":false}";

        assertThat(client.isEmailAvailable("ana@agrosense.test")).isFalse();
        assertThat(requests).containsExactly("POST /api/auth/register",
                "GET /api/auth/check-user?email=ana%40agrosense.test");
        assertThat(bodies.get(0)).contains("\"name\":\"Ana María\"", "\"lastName\":\"Pérez\"",
                "\"email\":\"ana@agrosense.test\"", "\"password\":\"a-long-password\"");
        assertThat(authorizations).containsOnly("null");

        status = 409;
        responseBody = "{\"message\":\"Ya existe una cuenta con ese correo.\"}";
        assertThatThrownBy(() -> client.register("Ana", "Pérez", "ana@agrosense.test", "a-long-password"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Ya existe una cuenta con ese correo.");
        status = 400;
        responseBody = "{\"message\":\"El nombre debe tener entre 2 y 18 caracteres.\",\"fields\":{}}";
        assertThatThrownBy(() -> client.register("A", "Pérez", "ana@agrosense.test", "a-long-password"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("El nombre debe tener entre 2 y 18 caracteres.");
    }

    @Test
    void signInTellsWrongCredentialsFromALockAndFromABackendThatCannotAnswer() {
        assertThat(client.signIn(EMAIL, "right-password")).isTrue();
        assertThat(requests).containsExactly("POST /api/auth/login");
        assertThat(bodies.get(0)).contains("\"email\":\"" + EMAIL + "\"", "\"password\":\"right-password\"");

        status = 401;
        responseBody = "{\"message\":\"Credenciales incorrectas.\"}";
        assertThatThrownBy(() -> client.signIn(EMAIL, "wrong")).isInstanceOf(BadCredentialsException.class);
        status = 429;
        responseBody = "{\"message\":\"Demasiados intentos fallidos. Inténtalo de nuevo en 15 minutos.\"}";
        assertThatThrownBy(() -> client.signIn(EMAIL, "wrong")).isInstanceOf(LockedException.class)
                .hasMessageContaining("15 minutos");

        // An older backend without the endpoint, or one that fails, lets the caller fall back to the database.
        status = 404;
        assertThat(client.attempt(() -> client.register("Ana", "Pérez", "ana@agrosense.test", "a-long-password")))
                .isEmpty();
        status = 500;
        assertThatThrownBy(() -> client.signIn(EMAIL, "any")).isInstanceOf(BackendUnavailableException.class);
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

    @Test
    void theAssistantIsAskedThroughTheBackendInWritingAndByVoice() {
        responseBody = "{\"reply\":\"Riega hoy.\",\"source\":\"local\"}";
        AiReply written = client.askAssistant(EMAIL,
                new AiChatForm("¿Riego hoy?", List.of(new AiChatForm.Turn("assistant", "Hola"))));

        assertThat(written.reply()).isEqualTo("Riega hoy.");
        assertThat(written.transcript()).isNull();
        // Who wrote the answer reaches the chat window, which says so under an answer given without the model.
        assertThat(written.source()).isEqualTo("local");
        assertThat(requests).containsExactly("POST /api/ai/chat");
        assertThat(authorizations.get(0)).startsWith("Bearer ");
        assertThat(bodies.get(0)).contains("\"message\":\"¿Riego hoy?\"")
                .contains("\"role\":\"assistant\"").contains("\"text\":\"Hola\"");

        responseBody = "{\"reply\":\"Está seco.\",\"transcript\":\"¿Cómo está el suelo?\"}";
        AiReply spoken = client.askAssistantByVoice(EMAIL, new byte[] {1, 2, 3}, "audio/webm;codecs=opus", null);

        assertThat(spoken.transcript()).isEqualTo("¿Cómo está el suelo?");
        assertThat(requests).last().isEqualTo("POST /api/ai/voice");
        // The recording travels as a file part with its type; without text there is no transcript part.
        assertThat(bodies.get(1)).contains("name=\"audio\"").contains("filename=\"voice\"").contains("audio/webm")
                .doesNotContain("name=\"transcript\"");

        client.askAssistantByVoice(EMAIL, null, null, "¿Riego hoy?");
        assertThat(bodies.get(2)).contains("name=\"transcript\"").contains("¿Riego hoy?").doesNotContain("name=\"audio\"");
    }

    @Test
    void whenTheAssistantCannotAnswerTheUserIsToldWhy() {
        AiChatForm question = new AiChatForm("Hola", null);

        // The backend's own wording and status reach the user, including the ones it answers with 5xx.
        status = 429;
        responseBody = "{\"message\":\"Has hecho muchas consultas seguidas.\"}";
        assertThatThrownBy(() -> client.askAssistant(EMAIL, question))
                .isInstanceOfSatisfying(AiAssistantException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(error.getMessage()).isEqualTo("Has hecho muchas consultas seguidas.");
                });
        status = 503;
        responseBody = "{\"message\":\"El asistente de IA no está configurado todavía.\"}";
        assertThatThrownBy(() -> client.askAssistant(EMAIL, question))
                .isInstanceOfSatisfying(AiAssistantException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(error.getMessage()).contains("no está configurado");
                });

        // A refused token or an older backend without the endpoint is not something the user can fix.
        for (int refused : new int[] {401, 403, 404}) {
            status = refused;
            responseBody = "{\"message\":\"Debes iniciar sesión para acceder a este recurso.\"}";
            assertThatThrownBy(() -> client.askAssistant(EMAIL, question))
                    .isInstanceOfSatisfying(AiAssistantException.class, error -> {
                        assertThat(error.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(error.getMessage()).doesNotContain("iniciar sesión");
                    });
        }

        status = 200;
        responseBody = "{\"reply\":\"  \"}";
        assertThatThrownBy(() -> client.askAssistant(EMAIL, question))
                .isInstanceOfSatisfying(AiAssistantException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));

        responseBody = "{\"reply\":\"Tarde\"}";
        delayMs = 1500;
        assertThatThrownBy(() -> client.askAssistant(EMAIL, question))
                .isInstanceOfSatisfying(AiAssistantException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
        // A slow assistant says nothing about the rest of the backend: other calls go on using it.
        // (The test server answers one request at a time, so it first finishes the slow one.)
        delayMs = 0;
        sleep(1300);
        assertThat(client.isAvailable()).isTrue();
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
