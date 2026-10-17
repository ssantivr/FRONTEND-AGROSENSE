package com.agrosense.frontend.events;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.backend.BackendClient.EventStreamToken;
import com.agrosense.frontend.backend.BackendUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What the live-events script is told: off, where to connect and with which token, or to try later. */
class LiveEventsTests {

    private static final String EMAIL = "demo@agrosense.co";
    private static final Principal USER = () -> EMAIL;

    private final BackendClient backend = mock(BackendClient.class);

    @Test
    void withoutThePublicAddressLiveEventsAreOffAndTheBackendIsNotAsked() {
        ResponseEntity<Map<String, Object>> answer = new LiveEventsController(backend, " ").token(USER);

        assertThat(answer.getStatusCode().value()).isEqualTo(200);
        assertThat(answer.getBody()).isEqualTo(Map.of("enabled", false));
        verify(backend, never()).eventStreamToken(EMAIL);
    }

    @Test
    void theBrowserGetsTheStreamsAddressAndTheBackendsTokenForItsOwnUser() {
        when(backend.isAvailable()).thenReturn(true);
        when(backend.eventStreamToken(EMAIL)).thenReturn(new EventStreamToken("signed-token", 300));

        ResponseEntity<Map<String, Object>> answer =
                new LiveEventsController(backend, "https://events.example/").token(USER);

        assertThat(answer.getStatusCode().value()).isEqualTo(200);
        assertThat(answer.getHeaders().getCacheControl()).contains("no-store");
        assertThat(answer.getBody()).containsEntry("enabled", true)
                .containsEntry("token", "signed-token")
                .containsEntry("streamUrl", "https://events.example/stream")
                .containsEntry("expiresInSeconds", 300L);
    }

    @Test
    void whileTheBackendDoesNotAnswerTheScriptIsToldToTryLater() {
        LiveEventsController controller = new LiveEventsController(backend, "https://events.example");

        when(backend.isAvailable()).thenReturn(false);
        assertThat(controller.token(USER).getStatusCode().value()).isEqualTo(503);

        when(backend.isAvailable()).thenReturn(true);
        when(backend.eventStreamToken(EMAIL)).thenThrow(new BackendUnavailableException("HTTP 503", null));
        assertThat(controller.token(USER).getStatusCode().value()).isEqualTo(503);
    }
}
