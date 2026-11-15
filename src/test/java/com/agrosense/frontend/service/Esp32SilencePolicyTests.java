package com.agrosense.frontend.service;

import com.agrosense.frontend.dto.Views.Esp32Silence;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Esp32SilencePolicyTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 14, 44, 10);
    private final Esp32SilencePolicy policy = new Esp32SilencePolicy(5);

    @Test
    void anEsp32ThatSentWithinTheLimitIsListening() {
        assertThat(policy.of(NOW.minusMinutes(1), NOW)).isEqualTo(Esp32Silence.LISTENING);
        assertThat(policy.of(NOW.minusMinutes(5), NOW)).as("the limit itself is still listening")
                .isEqualTo(Esp32Silence.LISTENING);
    }

    @Test
    void pastTheLimitItSaysSinceWhenAndWhatToCheck() {
        Esp32Silence silence = policy.of(LocalDateTime.of(2026, 11, 15, 14, 32), NOW);

        assertThat(silence.silent()).isTrue();
        assertThat(silence.text()).startsWith("Sin datos del ESP32 desde las 14:32 (hace 12 minutos).")
                .contains("cable USB", "PLANTICAS");
    }

    @Test
    void anOlderSilenceSaysTheDayAndCountsHoursThenDays() {
        assertThat(policy.of(LocalDateTime.of(2026, 11, 14, 22, 5), NOW).text())
                .startsWith("Sin datos del ESP32 desde el 14 nov").contains("(hace 16 horas)");
        assertThat(policy.of(NOW.minusDays(3), NOW).text()).contains("(hace 3 días)");
        assertThat(policy.of(NOW.minusMinutes(61), NOW).text()).contains("(hace 1 hora)");
    }

    @Test
    void anEsp32ThatNeverSentIsSilentToo() {
        Esp32Silence silence = policy.of(null, NOW);

        assertThat(silence.silent()).isTrue();
        assertThat(silence.text()).startsWith("El ESP32 todavía no ha enviado lecturas de humedad.");
    }

    @Test
    void theLimitMustBePositive() {
        assertThatThrownBy(() -> new Esp32SilencePolicy(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
