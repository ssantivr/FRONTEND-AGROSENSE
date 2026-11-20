package com.agrosense.frontend.events;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.backend.BackendClient.EventStreamToken;
import com.agrosense.frontend.backend.BackendUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.Principal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ui/events")
public class LiveEventsController {

    private final BackendClient backend;
    private final String streamUrl;

    public LiveEventsController(BackendClient backend, @Value("${agrosense.events.public-url:}") String publicUrl) {
        this.backend = backend;
        this.streamUrl = validated(publicUrl.strip().replaceAll("/+$", ""));
    }

    /** A5: only an absolute http(s) address reaches the browser; anything else turns live events off, with a warning. */
    static String validated(String url) {
        if (url.isEmpty()) {
            return url;
        }
        try {
            URI uri = URI.create(url);
            if (uri.getHost() != null && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))) {
                return url;
            }
        } catch (IllegalArgumentException notAUrl) {
            // Falls through to the warning below.
        }
        log.warn("Live events off: EVENTS_PUBLIC_URL is not an http(s) address");
        return "";
    }

    @GetMapping("/token")
    public ResponseEntity<Map<String, Object>> token(Principal principal) {
        if (streamUrl.isEmpty()) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("enabled", false));
        }
        if (!backend.isAvailable()) {
            return unavailable();
        }
        try {
            EventStreamToken token = backend.eventStreamToken(principal.getName());
            if (token == null || token.token() == null || token.token().isBlank()) {
                return unavailable();
            }
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                    "enabled", true,
                    "token", token.token(),
                    "streamUrl", streamUrl + "/stream",
                    "expiresInSeconds", token.expiresInSeconds()));
        } catch (BackendUnavailableException exception) {
            log.info("Live events: no token from the backend: {}", exception.getMessage());
            return unavailable();
        }
    }

    private static ResponseEntity<Map<String, Object>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore())
                .body(Map.of("message", "Los datos en vivo no están disponibles en este momento."));
    }
}
