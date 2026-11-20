package com.agrosense.frontend.service;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.backend.BackendClient.DataSourceItem;
import com.agrosense.frontend.backend.BackendClient.DataSourcesResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The «Origen de datos» drawer (data-sources.ts): where every figure on the pages comes from. The backend knows the
 * producers, tables and counts (GET /api/data-sources); this adds, for each one, the template and script that paint it.
 * Without the backend it answers a static copy of the catalogue, marked {@code fallback: true}.
 */
@Service
public class DataSourceCatalog {

    private static final String BACKEND = "backend/agrosense/src/main/java/com/agrosense/backend/";

    /** Kept in step with the backend's DataSourceCatalogService.CATALOGUE; used only while the backend is away. */
    static final List<DataSourceItem> FALLBACK = List.of(
            new DataSourceItem("readings.esp32", "Lecturas del ESP32 (promedio por ventana)", "SENSOR_FISICO",
                    "Esp32TcpIngestionService", "sensor_readings.reading_value (source = ESP32_TCP)",
                    "GET /api/sensors/{sensorId}/readings",
                    BACKEND + "pattern/structural/adapter/esp32/Esp32TcpIngestionService.java"),
            new DataSourceItem("readings.device", "Lecturas de dispositivos (evento o MQTT)", "SENSOR_FISICO",
                    "DeviceIngestionFacade", "sensor_readings.reading_value (source = DEVICE)",
                    "POST /api/internal/events", BACKEND + "pattern/structural/facade/DeviceIngestionFacade.java"),
            new DataSourceItem("readings.simulated", "Lecturas del sensor virtual", "SENSOR_VIRTUAL",
                    "VirtualSensorSimulator", "sensor_readings.reading_value (source = SIMULATED)",
                    "GET /api/sensors/{sensorId}/readings", BACKEND + "service/VirtualSensorSimulator.java"),
            new DataSourceItem("readings.manual", "Lecturas registradas a mano", "ENDPOINT", "SensorService",
                    "sensor_readings.reading_value (source = MANUAL)", "POST /api/readings",
                    BACKEND + "service/SensorService.java"),
            new DataSourceItem("readings.seed", "Lecturas de ejemplo", "SEED", "database/seed_demo.sql",
                    "sensor_readings.reading_value (source = SEED)", "GET /api/sensors/{sensorId}/readings",
                    "database/seed_demo.sql"),
            new DataSourceItem("alerts", "Alertas", "CALCULO", "SensorFacade", "alerts.alert_type", "GET /api/alerts",
                    BACKEND + "pattern/structural/facade/SensorFacade.java"),
            new DataSourceItem("predictions", "Recomendaciones de riego de la IA", "IA", "PredictionService",
                    "ai_predictions.recommendation", "GET /api/crops/{cropId}/predictions",
                    BACKEND + "service/PredictionService.java"),
            new DataSourceItem("irrigations", "Riegos", "ENDPOINT", "IrrigationService", "irrigations.duration_min",
                    "GET /api/riego/cultivo/{cropId}", BACKEND + "service/IrrigationService.java"),
            new DataSourceItem("dashboard", "Indicadores del panel", "CALCULO", "DashboardService", null,
                    "GET /api/dashboard", BACKEND + "service/DashboardService.java"),
            new DataSourceItem("chat", "Asistente (chat de IA)", "IA", "AiAssistantService", null, "POST /api/ai/chat",
                    BACKEND + "ai/service/AiAssistantService.java"));

    private static final String TS = "frontend/src/main/ts/";
    private static final String HTML = "frontend/src/main/resources/templates/";

    /** Which template and script paint each figure. */
    static final Map<String, String> FRONTEND = Map.ofEntries(
            Map.entry("readings.esp32", HTML + "sensors.html (tarjetas [data-sensor-code], #sensor-esp32) · "
                    + TS + "esp32-humidity.ts (Esp32Humidity) · " + TS + "events/live.ts (showReading)"),
            Map.entry("readings.device", HTML + "dashboard.html (#readingsChart) · " + HTML
                    + "fragments/stat-card.html (kpi-card [data-kpi]) · " + TS + "dashboard.ts (loadReadings) · "
                    + TS + "events/live.ts (showReading)"),
            Map.entry("readings.simulated", HTML + "fragments/navbar.html (#liveStatus «Simulado») · " + TS
                    + "events/live.ts (showReading, chip [data-live-source])"),
            Map.entry("readings.manual", HTML + "sensor.html (Lecturas recientes) · " + TS
                    + "dashboard.ts (readingsSeries)"),
            Map.entry("readings.seed", HTML + "dashboard.html (.kpi-grid) · " + HTML
                    + "fragments/stat-card.html (kpi-card)"),
            Map.entry("alerts", HTML + "alerts.html · " + HTML + "fragments/sidebar.html (.sidebar-badge) · " + TS
                    + "events/live.ts (showAlert)"),
            Map.entry("predictions", HTML + "crop.html (recomendación de riego) · " + TS + "crop-live.ts"),
            Map.entry("irrigations", HTML + "irrigation.html · " + HTML + "dashboard.html (#waterChart) · " + TS
                    + "dashboard.ts (initWater)"),
            Map.entry("dashboard", HTML + "dashboard.html (.kpi-grid) · " + HTML
                    + "fragments/stat-card.html (kpi-card, stat-card)"),
            Map.entry("chat", HTML + "fragments/ai-chat.html · " + TS + "ai/chat-widget.ts"),
            Map.entry("live", HTML + "fragments/navbar.html (#liveStatus) · " + TS + "events/live.ts (LiveEvents)"),
            Map.entry("backend-status", HTML + "fragments/navbar.html (#backendStatus) · " + TS
                    + "connection.ts (initConnectionStatus)"));

    /** Figures only the frontend computes or relays; always added. */
    static final List<DataSourceItem> FRONTEND_ONLY = List.of(
            new DataSourceItem("live", "Canal en vivo (SSE)", "ENDPOINT", "event-service (agrosense_events)", null,
                    "GET /api/ui/events/token → {EVENTS_PUBLIC_URL}/stream",
                    "frontend/src/main/java/com/agrosense/frontend/events/LiveEventsController.java"),
            new DataSourceItem("backend-status", "Estado del servidor", "CALCULO", "BackendClient.status()", null,
                    "GET /api/ui/backend-status",
                    "frontend/src/main/java/com/agrosense/frontend/backend/BackendClient.java"));

    private final BackendClient backendClient;

    public DataSourceCatalog(BackendClient backendClient) {
        this.backendClient = backendClient;
    }

    public DataSources describe(String email) {
        Optional<DataSourcesResponse> answer = backendClient.dataSources(email);
        boolean fallback = answer.isEmpty() || answer.get().items() == null;
        List<DataSourceItem> base = fallback ? FALLBACK : answer.get().items();
        List<Item> items = new ArrayList<>();
        base.stream().filter(item -> item != null && item.key() != null).forEach(item -> items.add(Item.of(item)));
        FRONTEND_ONLY.stream()
                .filter(extra -> items.stream().noneMatch(item -> extra.key().equals(item.key())))
                .forEach(extra -> items.add(Item.of(extra)));
        Map<String, Long> counts = answer.map(DataSourcesResponse::readingCountsBySource)
                .map(LinkedHashMap::new).orElseGet(LinkedHashMap::new);
        Boolean simulator = answer.map(DataSourcesResponse::simulatorEnabled).orElse(null);
        return new DataSources(fallback, simulator, counts, items);
    }

    /** What the browser gets; {@code simulatorEnabled} is null when nobody could say (fallback). */
    public record DataSources(boolean fallback, Boolean simulatorEnabled, Map<String, Long> readingCountsBySource,
            List<Item> items) {
    }

    public record Item(String key, String label, String origin, String producer, String storage, String endpoint,
            String file, String frontendComponent) {

        static Item of(DataSourceItem item) {
            return new Item(item.key(), item.label(), item.origin(), item.producer(), item.storage(), item.endpoint(),
                    item.file(), item.key() == null ? null : FRONTEND.get(item.key()));
        }
    }
}
