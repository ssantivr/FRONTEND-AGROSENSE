package com.agrosense.frontend.ai;

import com.agrosense.frontend.backend.BackendClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;

/**
 * What the chat widget calls. The assistant itself lives in the backend, which holds the key of the AI
 * service; this controller only forwards the signed-in user's question, so the key never reaches a browser.
 */
@RestController
@RequestMapping("/api/ui/ai")
@RequiredArgsConstructor
public class AiAssistantController {

    private final BackendClient backend;

    @PostMapping("/chat")
    public AiReply chat(@Valid @RequestBody AiChatForm form, Principal principal) {
        requireBackend();
        return backend.askAssistant(principal.getName(), form);
    }

    /** Multipart form with {@code audio} (the recording), {@code transcript} (its text), or both. */
    @PostMapping(value = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AiReply voice(@RequestParam(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "transcript", required = false) String transcript, Principal principal)
            throws IOException {
        boolean hasAudio = audio != null && !audio.isEmpty();
        if (!hasAudio && (transcript == null || transcript.isBlank())) {
            throw new AiAssistantException(HttpStatus.BAD_REQUEST, "No se recibió ningún audio.");
        }
        requireBackend();
        return backend.askAssistantByVoice(principal.getName(),
                hasAudio ? audio.getBytes() : null,
                hasAudio ? audio.getContentType() : null,
                transcript);
    }

    /** There is no database fallback for the assistant: without the backend it cannot answer. */
    private void requireBackend() {
        if (!backend.isAvailable()) {
            throw new AiAssistantException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El asistente se está iniciando. Inténtalo de nuevo en un momento.");
        }
    }
}