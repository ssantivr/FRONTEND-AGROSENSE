package com.agrosense.frontend.service;

import com.agrosense.frontend.backend.BackendClient;
import com.agrosense.frontend.backend.BackendClient.DataSourceItem;
import com.agrosense.frontend.backend.BackendClient.DataSourcesResponse;
import com.agrosense.frontend.service.DataSourceCatalog.DataSources;
import com.agrosense.frontend.service.DataSourceCatalog.Item;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The «Origen de datos» drawer: the backend's catalogue, plus what paints each figure, or a static copy without it. */
class DataSourceCatalogTests {

    private static final String EMAIL = "demo@agrosense.co";

    private final BackendClient backend = mock(BackendClient.class);
    private final DataSourceCatalog catalog = new DataSourceCatalog(backend);

    @Test
    void withoutTheBackendTheStaticCatalogueIsMarkedAsFallbackAndHasNoCounts() {
        when(backend.dataSources(EMAIL)).thenReturn(Optional.empty());

        DataSources answer = catalog.describe(EMAIL);

        assertThat(answer.fallback()).isTrue();
        assertThat(answer.simulatorEnabled()).isNull();
        assertThat(answer.readingCountsBySource()).isEmpty();
        assertThat(answer.items()).extracting(Item::key)
                .contains("readings.esp32", "readings.simulated", "predictions", "live", "backend-status");
        assertThat(answer.items()).allSatisfy(item -> assertThat(item.frontendComponent()).isNotBlank());
    }

    @Test
    void theBackendsAnswerKeepsItsCountsAndGetsTheFrontendPartOfEachItem() {
        when(backend.dataSources(EMAIL)).thenReturn(Optional.of(new DataSourcesResponse(true,
                Map.of("ESP32_TCP", 12L, "SIMULATED", 3L),
                List.of(new DataSourceItem("readings.esp32", "Lecturas del ESP32", "SENSOR_FISICO", "Esp32",
                                "sensor_readings.reading_value", "GET /api/sensors/{id}/readings", "x.java"),
                        new DataSourceItem("new.thing", "Algo nuevo", "CALCULO", null, null, null, null),
                        new DataSourceItem(null, "Sin clave", "CALCULO", null, null, null, null)))));

        DataSources answer = catalog.describe(EMAIL);

        assertThat(answer.fallback()).isFalse();
        assertThat(answer.simulatorEnabled()).isTrue();
        assertThat(answer.readingCountsBySource()).containsEntry("ESP32_TCP", 12L).containsEntry("SIMULATED", 3L);
        assertThat(answer.items()).extracting(Item::key).containsExactly("readings.esp32", "new.thing", "live",
                "backend-status");
        assertThat(answer.items().get(0).frontendComponent()).contains("sensors.html", "events/live.ts");
        assertThat(answer.items().get(1).frontendComponent()).isNull();
    }

    @Test
    void theBackendsListIsReadWhetherItIsCalledItemsOrSources() throws Exception {
        JsonMapper json = JsonMapper.builder().build();
        String item = "{\"key\":\"alerts\",\"label\":\"Alertas\",\"origin\":\"CALCULO\"}";

        DataSourcesResponse asSources = json.readValue(
                "{\"sources\":[" + item + "],\"readingCountsBySource\":{\"SEED\":2},\"simulatorEnabled\":false}",
                DataSourcesResponse.class);
        DataSourcesResponse asItems = json.readValue("{\"items\":[" + item + "]}", DataSourcesResponse.class);

        assertThat(asSources.items()).extracting(DataSourceItem::key).containsExactly("alerts");
        assertThat(asSources.simulatorEnabled()).isFalse();
        assertThat(asItems.items()).extracting(DataSourceItem::key).containsExactly("alerts");
    }
}
