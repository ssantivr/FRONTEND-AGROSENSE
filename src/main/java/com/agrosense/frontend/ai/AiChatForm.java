package com.agrosense.frontend.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** A question for the assistant with the turns that came before it. The backend applies the same limits. */
public record AiChatForm(
        @NotBlank(message = "Escribe tu pregunta.")
        @Size(max = MAX_MESSAGE_LENGTH, message = "La pregunta no puede superar los 1000 caracteres.")
        String message,
        @Size(max = MAX_HISTORY_TURNS, message = "La conversación enviada es demasiado larga.")
        List<@Valid Turn> history) {

    public static final int MAX_MESSAGE_LENGTH = 1000;
    public static final int MAX_HISTORY_TURNS = 12;

    public AiChatForm {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public record Turn(
            @NotBlank(message = "Cada mensaje de la conversación necesita un autor.")
            @Pattern(regexp = "user|assistant", message = "El autor de un mensaje no es válido.")
            String role,
            @NotBlank(message = "Los mensajes de la conversación no pueden estar vacíos.")
            @Size(max = 4000, message = "Un mensaje de la conversación es demasiado largo.")
            String text) {
    }
}