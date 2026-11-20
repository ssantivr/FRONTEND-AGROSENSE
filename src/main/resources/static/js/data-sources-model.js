// What GET /api/ui/data-sources answers and how the «Origen de datos» drawer words it. Pure: node --test checks it.
import { isArrayOf, isRecord } from './api/client.js';
import { SOURCE_LABELS, isReadingSource } from './events/live-model.js';
export const ORIGIN_LABELS = {
    SENSOR_FISICO: 'Sensor físico',
    SENSOR_VIRTUAL: 'Sensor virtual (simulador)',
    ENDPOINT: 'Endpoint del servidor',
    IA: 'Inteligencia artificial',
    SEED: 'Datos de ejemplo (seed)',
    CALCULO: 'Cálculo',
};
export const ORIGIN_ICONS = {
    SENSOR_FISICO: 'bi-cpu',
    SENSOR_VIRTUAL: 'bi-shuffle',
    ENDPOINT: 'bi-hdd-network',
    IA: 'bi-stars',
    SEED: 'bi-database',
    CALCULO: 'bi-calculator',
};
const isOptionalText = (value) => value === null || value === undefined || typeof value === 'string';
const isItem = (value) => isRecord(value) &&
    typeof value['key'] === 'string' &&
    typeof value['label'] === 'string' &&
    typeof value['origin'] === 'string' &&
    ['producer', 'storage', 'endpoint', 'file', 'frontendComponent'].every((field) => isOptionalText(value[field]));
const isCounts = (value) => isRecord(value) && Object.values(value).every((count) => typeof count === 'number' && Number.isFinite(count));
export const isDataSources = (value) => isRecord(value) &&
    typeof value['fallback'] === 'boolean' &&
    (value['simulatorEnabled'] === null || value['simulatorEnabled'] === undefined || typeof value['simulatorEnabled'] === 'boolean') &&
    isCounts(value['readingCountsBySource']) &&
    isArrayOf(isItem)(value['items']);
export const isOrigin = (value) => Object.prototype.hasOwnProperty.call(ORIGIN_LABELS, value);
export const originLabel = (origin) => (isOrigin(origin) ? ORIGIN_LABELS[origin] : origin);
/** The counts in a fixed order (that of SOURCE_LABELS), worded like the reading chips; unknown sources are left out. */
export function sourceCounts(counts) {
    return Object.keys(SOURCE_LABELS)
        .filter((source) => isReadingSource(source) && typeof counts[source] === 'number')
        .map((source) => ({ source, label: SOURCE_LABELS[source], count: counts[source] ?? 0 }));
}
export function simulatorText(enabled) {
    if (enabled === true)
        return 'Simulador activo: hay lecturas generadas por el sensor virtual.';
    if (enabled === false)
        return 'Simulador apagado: las lecturas nuevas vienen de sensores reales.';
    return 'No se pudo saber si el simulador está activo.';
}
