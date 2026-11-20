package com.agrosense.frontend.dto;

import com.agrosense.frontend.backend.BackendClient.Esp32Status;
import com.agrosense.frontend.backend.BackendClient.MonitoringResponse;
import com.agrosense.frontend.backend.BackendClient.WatchRun;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/** The «Monitoreo» page in Spanish: the receiver's state with its tone, the averaging, the rule and the watches. */
public record MonitoringView(
        boolean enabled,
        boolean connected,
        String deviceId,
        String linkLabel,
        String linkTone,
        String linkIcon,
        String aggregation,
        int aggregateSeconds,
        LocalDateTime lastBlockAt,
        LocalDateTime lastStoredAt,
        long receivedBlocks,
        long storedReadings,
        long discardedBlocks,
        String wateringRule,
        List<Watch> watches) {

    public record Watch(String name, String purpose, LocalDateTime startedAt, long tookMillis) {
    }

    private static final Map<String, String> TONES = Map.of(
            "RECEIVING", "success", "WAITING", "info", "SILENT", "warning", "DISCONNECTED", "danger");
    private static final Map<String, String> ICONS = Map.of(
            "RECEIVING", "bi-broadcast", "WAITING", "bi-hourglass-split", "SILENT", "bi-volume-mute",
            "DISCONNECTED", "bi-plug");
    private static final Map<String, String> AGGREGATIONS = Map.of("mean", "Promedio", "median", "Mediana");
    private static final Map<String, String[]> WATCHES = Map.of(
            "silent devices", new String[] {"Dispositivos sin reportar", "Avisa si un ESP32 deja de enviar datos."},
            "frost", new String[] {"Riesgo de helada", "Compara el pronóstico con el mínimo de cada cultivo."},
            "irrigation results", new String[] {"Resultado de los riegos", "Mide si el suelo subió después de regar."});

    public static MonitoringView of(MonitoringResponse response, ZoneId zone) {
        Esp32Status esp32 = response.esp32();
        String link = esp32.link() == null ? "DISCONNECTED" : esp32.link();
        List<Watch> watches = response.watches() == null ? List.of() : response.watches().stream()
                .map(run -> watch(run, zone)).toList();
        return new MonitoringView(esp32.enabled(), esp32.connected(), esp32.deviceId(),
                esp32.enabled() ? esp32.linkLabel() : "Receptor apagado",
                esp32.enabled() ? TONES.getOrDefault(link, "neutral") : "neutral",
                esp32.enabled() ? ICONS.getOrDefault(link, "bi-question-circle") : "bi-power",
                AGGREGATIONS.getOrDefault(esp32.aggregation(), esp32.aggregation()), esp32.aggregateSeconds(),
                local(esp32.lastBlockAt(), zone), local(esp32.lastStoredAt(), zone), esp32.receivedBlocks(),
                esp32.storedReadings(), esp32.discardedBlocks(), response.wateringRule(), watches);
    }

    private static Watch watch(WatchRun run, ZoneId zone) {
        String[] known = WATCHES.getOrDefault(run.command(), new String[] {run.command(), ""});
        return new Watch(known[0], known[1], LocalDateTime.ofInstant(run.startedAt(), zone), run.tookMillis());
    }

    private static LocalDateTime local(OffsetDateTime time, ZoneId zone) {
        return time == null ? null : time.atZoneSameInstant(zone).toLocalDateTime();
    }
}
