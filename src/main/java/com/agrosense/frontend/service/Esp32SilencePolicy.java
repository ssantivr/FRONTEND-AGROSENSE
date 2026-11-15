package com.agrosense.frontend.service;

import com.agrosense.frontend.dto.Views.Esp32Silence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * When the ESP32 counts as silent. The backend stores one reading per minute while the board talks, so a few minutes
 * without one means the cable, the network or the board failed, long before a sensor counts as stale (one hour).
 * The text is worded on the server, in the server's clock, so the browser's time zone never changes the answer.
 */
@Component
public class Esp32SilencePolicy {

    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", SPANISH);
    private static final DateTimeFormatter DAY_AND_TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm", SPANISH);
    private static final String CHECK = " Revisa que esté encendido y el cable USB o la red PLANTICAS.";

    private final Duration silentAfter;

    public Esp32SilencePolicy(@Value("${agrosense.esp32.silent-after-minutes:5}") long silentAfterMinutes) {
        if (silentAfterMinutes <= 0) {
            throw new IllegalArgumentException("agrosense.esp32.silent-after-minutes must be positive");
        }
        this.silentAfter = Duration.ofMinutes(silentAfterMinutes);
    }

    /** Silent when the newest humidity reading is older than the limit, or when there has never been one. */
    public Esp32Silence of(LocalDateTime newest, LocalDateTime now) {
        if (newest == null) {
            return new Esp32Silence(true, "El ESP32 todavía no ha enviado lecturas de humedad." + CHECK);
        }
        Duration age = Duration.between(newest, now);
        if (age.compareTo(silentAfter) <= 0) {
            return Esp32Silence.LISTENING;
        }
        String since = newest.toLocalDate().equals(now.toLocalDate())
                ? "las " + TIME.format(newest) : "el " + DAY_AND_TIME.format(newest);
        return new Esp32Silence(true, "Sin datos del ESP32 desde " + since + " (hace " + describe(age) + ")." + CHECK);
    }

    static String describe(Duration age) {
        long minutes = age.toMinutes();
        if (minutes < 60) {
            return minutes == 1 ? "1 minuto" : minutes + " minutos";
        }
        long hours = age.toHours();
        if (hours < 48) {
            return hours == 1 ? "1 hora" : hours + " horas";
        }
        return age.toDays() + " días";
    }
}
