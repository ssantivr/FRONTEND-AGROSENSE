package com.agrosense.frontend.backend;

import com.agrosense.frontend.ai.AiAssistantException;
import com.agrosense.frontend.ai.AiChatForm;
import com.agrosense.frontend.ai.AiReply;
import com.agrosense.frontend.entity.enums.SensorType;
import com.agrosense.frontend.exception.BusinessRuleException;
import com.agrosense.frontend.exception.NotFoundException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class BackendClient {

    private static final Duration AVAILABLE_FOR = Duration.ofSeconds(60);
    private static final Duration UNAVAILABLE_FOR = Duration.ofSeconds(15);
    private static final Duration WAKE_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration WAKE_PAUSE = Duration.ofSeconds(2);
    private static final int WAKE_ATTEMPTS = 3;
    private static final int READ_ATTEMPTS = 3;
    private static final Duration READ_RETRY_PAUSE = Duration.ofMillis(300);
    private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final RestClient api;
    private final RestClient health;
    private final RestClient wake;
    private final RestClient assistant;
    private final BackendToken token;
    private final AtomicBoolean waking = new AtomicBoolean();

    private volatile Availability availability = new Availability(false, Instant.EPOCH);

    public BackendClient(@Value("${agrosense.backend.url:}") String url,
            @Value("${agrosense.backend.jwt-secret:}") String jwtSecret,
            @Value("${agrosense.backend.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${agrosense.backend.read-timeout-ms:10000}") long readTimeoutMs,
            @Value("${agrosense.backend.health-timeout-ms:3000}") long healthTimeoutMs,
            @Value("${agrosense.backend.assistant-timeout-ms:30000}") long assistantTimeoutMs) {
        if (url.isBlank() || jwtSecret.isBlank()) {
            this.api = null;
            this.health = null;
            this.wake = null;
            this.assistant = null;
            this.token = null;
            log.info("Backend API not configured: every operation uses the database");
            return;
        }
        String baseUrl = url.strip().replaceAll("/+$", "");
        Duration connectTimeout = Duration.ofMillis(connectTimeoutMs);
        this.api = client(baseUrl, connectTimeout, Duration.ofMillis(readTimeoutMs));
        this.health = client(baseUrl, connectTimeout, Duration.ofMillis(healthTimeoutMs));
        this.wake = client(baseUrl, WAKE_TIMEOUT, WAKE_TIMEOUT);
        this.assistant = client(baseUrl, connectTimeout, Duration.ofMillis(assistantTimeoutMs));
        this.token = new BackendToken(jwtSecret);
        log.info("Backend API configured at {}", baseUrl);
    }

    /** False when BACKEND_URL or BACKEND_JWT_SECRET is empty: there is no backend to ask, not one that is down. */
    public boolean isConfigured() {
        return api != null;
    }

    /**
     * The one place that decides between the backend API and the database: every operation that can use the
     * API asks here first.
     */
    public boolean isAvailable() {
        if (api == null) {
            return false;
        }
        Availability known = availability;
        if (Instant.now().isBefore(known.until())) {
            return known.up();
        }
        boolean up = checkHealth(health);
        availability = new Availability(up, Instant.now().plus(up ? AVAILABLE_FOR : UNAVAILABLE_FOR));
        if (!up) {
            wakeUp();
        }
        return up;
    }

    /** Asks the backend when the last answer is too old, so it may take the health check's timeout. */
    public BackendStatus status() {
        isAvailable();
        return knownStatus();
    }

    /** What is known now, without asking the backend. */
    public BackendStatus knownStatus() {
        if (api == null) {
            return new BackendStatus(BackendStatus.State.OFF, Instant.now());
        }
        Availability known = availability;
        Instant checkedAt = known.until().minus(known.up() ? AVAILABLE_FOR : UNAVAILABLE_FOR);
        if (known.up()) {
            return new BackendStatus(BackendStatus.State.UP, checkedAt);
        }
        if (waking.get()) {
            return new BackendStatus(BackendStatus.State.WAKING, checkedAt);
        }
        return known.until().equals(Instant.EPOCH)
                ? new BackendStatus(BackendStatus.State.UNKNOWN, Instant.now())
                : new BackendStatus(BackendStatus.State.DOWN, checkedAt);
    }

    // A free host answers the first request after a nap only when the service has started, which can take a
    // minute: one background request at a time waits that long, up to WAKE_ATTEMPTS times with growing pauses.
    private void wakeUp() {
        if (!waking.compareAndSet(false, true)) {
            return;
        }
        Thread.ofVirtual().name("backend-wake-up").start(() -> {
            try {
                for (int attempt = 1; attempt <= WAKE_ATTEMPTS; attempt++) {
                    if (checkHealth(wake)) {
                        availability = new Availability(true, Instant.now().plus(AVAILABLE_FOR));
                        log.info("Backend API answered after waking it up (attempt {})", attempt);
                        return;
                    }
                    if (attempt < WAKE_ATTEMPTS) {
                        Thread.sleep(WAKE_PAUSE.multipliedBy(1L << (attempt - 1)));
                    }
                }
                availability = new Availability(false, Instant.now().plus(UNAVAILABLE_FOR));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                waking.set(false);
            }
        });
    }

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

    public List<ReadingResponse> latestReadings(String email, Integer sensorId, int limit) {
        return executeRead(() -> api.get().uri("/api/sensors/{sensorId}/readings?limit={limit}", sensorId, limit)
                .header(HttpHeaders.AUTHORIZATION, token.bearerFor(email))
                .retrieve().onStatus(HttpStatusCode::isError, BackendClient::fail)
                .body(new ParameterizedTypeReference<List<ReadingResponse>>() {
                }));
    }

    public List<CropTemplateResponse> cropTemplates(String email) {
        return attempt(() -> executeRead(() -> api.get().uri("/api/crop-templates")
                .header(HttpHeaders.AUTHORIZATION, token.bearerFor(email))
                .retrieve().onStatus(HttpStatusCode::isError, BackendClient::fail)
                .body(new ParameterizedTypeReference<List<CropTemplateResponse>>() {
                }))).orElse(List.of());
    }

    public Optional<PredictionResponse> latestPrediction(String email, Integer cropId) {
        return attempt(() -> executeRead(() -> api.get().uri("/api/crops/{cropId}/predictions?limit=1", cropId)
                .header(HttpHeaders.AUTHORIZATION, token.bearerFor(email))
                .retrieve().onStatus(HttpStatusCode::isError, BackendClient::fail)
                .body(new ParameterizedTypeReference<List<PredictionResponse>>() {
                }))).flatMap(predictions -> predictions.stream().findFirst());
    }

    /** The ESP32 receiver, the watering rule and the last field watches; empty while the backend does not answer. */
    public Optional<MonitoringResponse> monitoring(String email) {
        return attempt(() -> executeRead(() -> api.get().uri("/api/devices/monitoring")
                .header(HttpHeaders.AUTHORIZATION, token.bearerFor(email))
                .retrieve().onStatus(HttpStatusCode::isError, BackendClient::fail).body(MonitoringResponse.class)));
    }

    /** Where each figure of the pages comes from (GET /api/data-sources); empty while the backend does not answer. */
    public Optional<DataSourcesResponse> dataSources(String email) {
        return attempt(() -> executeRead(() -> api.get().uri("/api/data-sources")
                .header(HttpHeaders.AUTHORIZATION, token.bearerFor(email))
                .retrieve().onStatus(HttpStatusCode::isError, BackendClient::fail).body(DataSourcesResponse.class)));
    }

    public EventStreamToken eventStreamToken(String email) {
        return executeRead(() -> api.get().uri("/api/events/token")
                .header(HttpHeaders.AUTHORIZATION, token.bearerFor(email))
                .retrieve().onStatus(HttpStatusCode::isError, BackendClient::fail).body(EventStreamToken.class));
    }

    public boolean isEmailAvailable(String email) {
        return executeRead(() -> api.get().uri("/api/auth/check-user?email={email}", email)
                .retrieve().onStatus(HttpStatusCode::isError, BackendClient::failAccount)
                .body(EmailAvailabilityResponse.class)).available();
    }

    public boolean register(String name, String lastName, String email, String password) {
        execute(() -> api.post().uri("/api/auth/register")
                .body(new RegisterRequest(name, lastName, email, password))
                .retrieve().onStatus(HttpStatusCode::isError, BackendClient::failAccount).toBodilessEntity());
        return true;
    }

    public boolean signIn(String email, String password) {
        execute(() -> api.post().uri("/api/auth/login")
                .body(new LoginRequest(email, password))
                .retrieve().onStatus(HttpStatusCode::isError, BackendClient::failAccount).toBodilessEntity());
        return true;
    }

    public AiReply askAssistant(String email, AiChatForm form) {
        return assistantCall(() -> assistant.post().uri("/api/ai/chat")
                .header(HttpHeaders.AUTHORIZATION, token.bearerFor(email))
                .body(form)
                .retrieve().onStatus(HttpStatusCode::isError, BackendClient::failAssistant).body(AiReply.class));
    }

    public AiReply askAssistantByVoice(String email, byte[] audio, String contentType, String transcript) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        if (audio != null) {
            HttpHeaders part = new HttpHeaders();
            part.setContentType(contentType == null
                    ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(contentType));
            form.add("audio", new HttpEntity<>(new ByteArrayResource(audio) {
                @Override
                public String getFilename() {
                    return "voice";
                }
            }, part));
        }
        if (transcript != null && !transcript.isBlank()) {
            form.add("transcript", transcript);
        }
        return assistantCall(() -> assistant.post().uri("/api/ai/voice")
                .header(HttpHeaders.AUTHORIZATION, token.bearerFor(email))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve().onStatus(HttpStatusCode::isError, BackendClient::failAssistant).body(AiReply.class));
    }

    private AiReply assistantCall(Supplier<AiReply> call) {
        try {
            AiReply reply = call.get();
            if (reply == null || reply.reply() == null || reply.reply().isBlank()) {
                throw new AiAssistantException(HttpStatus.BAD_GATEWAY,
                        "El asistente no generó una respuesta. Inténtalo de nuevo.");
            }
            return reply;
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("Assistant call failed: {}", exception.getMessage());
            throw new AiAssistantException(HttpStatus.GATEWAY_TIMEOUT,
                    "El asistente tardó demasiado en responder. Inténtalo de nuevo.");
        }
    }

    private static void failAssistant(org.springframework.http.HttpRequest request, ClientHttpResponse response)
            throws IOException {
        int status = response.getStatusCode().value();
        String message = messageOf(response);
        if (status == 401 || status == 403 || status == 404 || message == null) {
            log.warn("Assistant: HTTP {} from {}", status, request.getURI().getPath());
            throw new AiAssistantException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El asistente no está disponible en este momento. Inténtalo más tarde.");
        }
        HttpStatus resolved = HttpStatus.resolve(status);
        throw new AiAssistantException(resolved == null ? HttpStatus.BAD_GATEWAY : resolved, message);
    }

    private boolean checkHealth(RestClient client) {
        try {
            client.get().uri("/api/health").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException exception) {
            log.info("Backend API not available yet: {}", exception.getMessage());
            return false;
        }
    }

    // Reads change nothing, so one that could not even connect is tried again after a growing pause. A slow
    // answer is not: the backend got the request and asking again only adds to its load.
    private <T> T executeRead(Supplier<T> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                return execute(call);
            } catch (BackendUnavailableException exception) {
                if (attempt >= READ_ATTEMPTS || !couldNotConnect(exception)) {
                    throw exception;
                }
                try {
                    Thread.sleep(READ_RETRY_PAUSE.multipliedBy(1L << (attempt - 1)));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
            }
        }
    }

    private static boolean couldNotConnect(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.ConnectException
                    || cause instanceof java.net.http.HttpConnectTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private <T> T execute(Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientException exception) {
            availability = new Availability(false, Instant.now().plus(UNAVAILABLE_FOR));
            throw new BackendUnavailableException(exception.getMessage(), exception);
        }
    }

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

    private static void failAccount(org.springframework.http.HttpRequest request, ClientHttpResponse response)
            throws IOException {
        int status = response.getStatusCode().value();
        String message = messageOf(response);
        switch (status) {
            case 401 -> throw new BadCredentialsException("Backend API: wrong credentials");
            case 429 -> throw new LockedException(
                    message == null ? "Demasiados intentos fallidos. Inténtalo de nuevo más tarde." : message);
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

    private record RegisterRequest(String name, String lastName, String email, String password) {
    }

    private record LoginRequest(String email, String password) {
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PredictionResponse(BigDecimal confidence, String recommendation, String modelUsed,
            LocalDateTime createdAt) {

        private static final String LOCAL_MODEL = "agrosense-reglas-locales";

        public boolean localFallback() {
            return LOCAL_MODEL.equals(modelUsed);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CropTemplateResponse(String key, String label, BigDecimal humidityMin, BigDecimal humidityMax,
            BigDecimal tempMin, BigDecimal tempMax, BigDecimal phMin, BigDecimal phMax) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MonitoringResponse(Esp32Status esp32, String wateringRule, List<WatchRun> watches) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Esp32Status(boolean enabled, boolean connected, String deviceId, int aggregateSeconds,
            OffsetDateTime lastBlockAt, OffsetDateTime lastStoredAt, long receivedBlocks, long storedReadings,
            long discardedBlocks, String aggregation, String link, String linkLabel) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WatchRun(String command, Instant startedAt, long tookMillis) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventStreamToken(String token, long expiresInSeconds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DataSourcesResponse(Boolean simulatorEnabled, java.util.Map<String, Long> readingCountsBySource,
            @com.fasterxml.jackson.annotation.JsonAlias("sources") List<DataSourceItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DataSourceItem(String key, String label, String origin, String producer, String storage,
            String endpoint, String file) {
    }
}
