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

import java.security.Principal;
import java.util.Map;

/**
 * What the live-events script asks for: where the event service is and a 5-minute token, signed by the
 * backend, that opens the signed-in user's stream there. No key of any service reaches the browser.
 *
 * <p>Without {@code EVENTS_PUBLIC_URL} live events are off ({@code enabled: false}) and pages show what they
 * loaded, as they always did. While the backend does not answer the script waits and asks again (503).
 */
@Slf4j
@RestController
@RequestMapping("/api/ui/events")
public class LiveEventsController {

    private final BackendClient backend;
    private final String streamUrl;

    public LiveEventsController(BackendClient backend, @Value("${agrosense.events.public-url:}") String publicUrl) {
        this.backend = backend;
        this.streamUrl = publicUrl.strip().replaceAll("/+$", "");
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
