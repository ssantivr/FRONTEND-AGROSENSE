package com.agrosense.frontend.backend;

import com.agrosense.frontend.entity.enums.SensorType;
import com.agrosense.frontend.exception.BusinessRuleException;
import com.agrosense.frontend.exception.NotFoundException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calls the AgroSense backend API on behalf of the signed-in user. The frontend still reads the database
 * itself for everything the API does not offer; this client covers the operations it does.
 *
 * <p>It is off unless both {@code BACKEND_URL} and {@code BACKEND_JWT_SECRET} are set. The backend may be
 * asleep (free hosting stops idle services and takes minutes to start them), so callers ask
 * {@link #isAvailable()} first and keep using the database until it answers.
 */
@Slf4j
@Component
public class BackendClient {

    private static final Duration AVAILABLE_FOR = Duration.ofSeconds(60);
    private static final Duration UNAVAILABLE_FOR = Duration.ofSeconds(15);
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final RestClient api;
    private final RestClient health;
    private final BackendToken token;

    private volatile Availability availability = new Availability(false, Instant.EPOCH);

    public BackendClient(@Value("${agrosense.backend.url:}") String url,
            @Value("${agrosense.backend.jwt-secret:}") String jwtSecret,
            @Value("${agrosense.backend.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${agrosense.backend.read-timeout-ms:10000}") long readTimeoutMs,
            @Value("${agrosense.backend.health-timeout-ms:3000}") long healthTimeoutMs) {
        if (url.isBlank() || jwtSecret.isBlank()) {
            this.api = null;
            this.health = null;
            this.token = null;
            log.info("Backend API not configured: every operation uses the database");
            return;
        }
        String baseUrl = url.strip().replaceAll("/+$", "");
        Duration connectTimeout = Duration.ofMillis(connectTimeoutMs);
        this.api = client(baseUrl, connectTimeout, Duration.ofMillis(readTimeoutMs));
        this.health = client(baseUrl, connectTimeout, Duration.ofMillis(healthTimeoutMs));
        this.token = new BackendToken(jwtSecret);
        log.info("Backend API configured at {}", baseUrl);
    }

    /**
     * Whether the backend answered its health check a moment ago. A sleeping backend starts waking up
     * with this request, so a later call finds it ready.
     */
    public boolean isAvailable() {
        if (api == null) {
            return false;
        }
        Availability known = availability;
        if (Instant.now().isBefore(known.until())) {
            return known.up();
        }
        boolean up = checkHealth();
        availability = new Availability(up, Instant.now().plus(up ? AVAILABLE_FOR : UNAVAILABLE_FOR));
        return up;
    }

    /**
     * Runs a call that is safe to replace with a database operation: empty when the backend is not
     * available or stops answering, so the caller falls back.
     */
    public <T> Optional<T> attempt(Supplier<T> call) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(call.get());
        } catch (BackendUnavailableException exception) {
            log.warn("Backend API call failed, using the database instead: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    public boolean acknowledgeAlert(String email, Integer alertId) {
        execute(() -> api.patch().uri("/api/alerts/{alertId}/acknowledge", alertId)
                .header(HttpHeaders.AUTHORIZATION, token.bearerFor(email))
                .retrieve().onStatus(HttpStatusCode::isError, BackendClient::fail).toBodilessEntity());
        return true;
    }

    public boolean createSensor(String email, Integer cropId, SensorType type, String sensorCode, String location) {
        execute(() -> api.post().uri("/api/sensors")
                .header(HttpHeaders.AUTHORIZATION, token.bearerFor(email))
                .body(new SensorRequest(cropId, type.name(), sensorCode, location))
                .retrieve().onStatus(HttpStatusCode::isError, BackendClient::fail).toBodilessEntity());
        return true;
    }

    public IrrigationResponse startIrrigation(String email, Integer cropId, BigDecimal waterLiters,
            Integer durationMinutes, String reason) {
        return execute(() -> api.post().uri("/api/riego")
                .header(HttpHeaders.AUTHORIZATION, token.bearerFor(email))
                .body(new IrrigationRequest(cropId, waterLiters, durationMinutes, reason))
                .retrieve().onStatus(HttpStatusCode::isError, BackendClient::fail).body(IrrigationResponse.class));
    }

    /** The newest readings of a sensor, newest first. */
    public List<ReadingResponse> latestReadings(String email, Integer sensorId, int limit) {
        return execute(() -> api.get().uri("/api/sensors/{sensorId}/readings?limit={limit}", sensorId, limit)
                .header(HttpHeaders.AUTHORIZATION, token.bearerFor(email))
                .retrieve().onStatus(HttpStatusCode::isError, BackendClient::fail)
                .body(new ParameterizedTypeReference<List<ReadingResponse>>() {
                }));
    }

    /** Whether nobody has signed up with this e-mail address yet. Public: there is no user to sign for. */
    public boolean isEmailAvailable(String email) {
        return execute(() -> api.get().uri("/api/auth/email-available?email={email}", email)
                .retrieve().onStatus(HttpStatusCode::isError, BackendClient::fail)
                .body(EmailAvailabilityResponse.class)).available();
    }

    private boolean checkHealth() {
        try {
            health.get().uri("/api/health").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException exception) {
            log.info("Backend API not available yet: {}", exception.getMessage());
            return false;
        }
    }

    /** Timeouts and connection failures mean the backend cannot be relied on right now. */
    private <T> T execute(Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientException exception) {
            availability = new Availability(false, Instant.now().plus(UNAVAILABLE_FOR));
            throw new BackendUnavailableException(exception.getMessage(), exception);
        }
    }

    /** The backend's own answers keep their meaning; anything else counts as the backend being unusable. */
    private static void fail(org.springframework.http.HttpRequest request, ClientHttpResponse response)
            throws IOException {
        int status = response.getStatusCode().value();
        String message = messageOf(response);
        switch (status) {
            case 404 -> throw new NotFoundException("Backend API: not found");
            case 400, 409 -> throw new BusinessRuleException(
                    message == null ? "El servidor rechazó los datos enviados." : message);
            default -> throw new BackendUnavailableException(
                    "HTTP " + status + " from " + request.getMethod() + " " + request.getURI().getPath());
        }
    }

    private static String messageOf(ClientHttpResponse response) throws IOException {
        Matcher matcher = MESSAGE.matcher(new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8));
        return matcher.find() ? matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : null;
    }

    private static RestClient client(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        factory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    private record Availability(boolean up, Instant until) {
    }

    private record SensorRequest(Integer cropId, String sensorType, String sensorCode, String location) {
    }

    private record IrrigationRequest(Integer cropId, BigDecimal waterLiters, Integer durationMinutes, String reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmailAvailabilityResponse(boolean available) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IrrigationResponse(Integer idIrrigation, LocalDateTime startedAt, LocalDateTime endedAt,
            Integer durationMin, BigDecimal waterLiters) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReadingResponse(BigDecimal value, String unit, LocalDateTime recordedAt) {
    }
}
