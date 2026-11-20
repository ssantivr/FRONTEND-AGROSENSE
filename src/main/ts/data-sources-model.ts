// What GET /api/ui/data-sources answers and how the «Origen de datos» drawer words it. Pure: node --test checks it.
import { isArrayOf, isRecord } from './api/client.js';
import { SOURCE_LABELS, isReadingSource, type ReadingSource } from './events/live-model.js';

export type Origin = 'SENSOR_FISICO' | 'SENSOR_VIRTUAL' | 'ENDPOINT' | 'IA' | 'SEED' | 'CALCULO';

export const ORIGIN_LABELS: Readonly<Record<Origin, string>> = {
  SENSOR_FISICO: 'Sensor físico',
  SENSOR_VIRTUAL: 'Sensor virtual (simulador)',
  ENDPOINT: 'Endpoint del servidor',
  IA: 'Inteligencia artificial',
  SEED: 'Datos de ejemplo (seed)',
  CALCULO: 'Cálculo',
};

export const ORIGIN_ICONS: Readonly<Record<Origin, string>> = {
  SENSOR_FISICO: 'bi-cpu',
  SENSOR_VIRTUAL: 'bi-shuffle',
  ENDPOINT: 'bi-hdd-network',
  IA: 'bi-stars',
  SEED: 'bi-database',
  CALCULO: 'bi-calculator',
};

export interface DataSourceItem {
  key: string;
  label: string;
  origin: string;
  producer: string | null;
  storage: string | null;
  endpoint: string | null;
  file: string | null;
  frontendComponent: string | null;
}

export interface DataSources {
  fallback: boolean;
  simulatorEnabled: boolean | null;
  readingCountsBySource: Record<string, number>;
  items: DataSourceItem[];
}

const isOptionalText = (value: unknown): boolean => value === null || value === undefined || typeof value === 'string';

const isItem = (value: unknown): value is DataSourceItem =>
  isRecord(value) &&
  typeof value['key'] === 'string' &&
  typeof value['label'] === 'string' &&
  typeof value['origin'] === 'string' &&
  ['producer', 'storage', 'endpoint', 'file', 'frontendComponent'].every((field) => isOptionalText(value[field]));

const isCounts = (value: unknown): value is Record<string, number> =>
  isRecord(value) && Object.values(value).every((count) => typeof count === 'number' && Number.isFinite(count));

export const isDataSources = (value: unknown): value is DataSources =>
  isRecord(value) &&
  typeof value['fallback'] === 'boolean' &&
  (value['simulatorEnabled'] === null || value['simulatorEnabled'] === undefined || typeof value['simulatorEnabled'] === 'boolean') &&
  isCounts(value['readingCountsBySource']) &&
  isArrayOf(isItem)(value['items']);

export const isOrigin = (value: string): value is Origin => Object.prototype.hasOwnProperty.call(ORIGIN_LABELS, value);

export const originLabel = (origin: string): string => (isOrigin(origin) ? ORIGIN_LABELS[origin] : origin);

export interface SourceCount {
  source: ReadingSource;
  label: string;
  count: number;
}

/** The counts in a fixed order (that of SOURCE_LABELS), worded like the reading chips; unknown sources are left out. */
export function sourceCounts(counts: Readonly<Record<string, number>>): SourceCount[] {
  return (Object.keys(SOURCE_LABELS) as ReadingSource[])
    .filter((source) => isReadingSource(source) && typeof counts[source] === 'number')
    .map((source) => ({ source, label: SOURCE_LABELS[source], count: counts[source] ?? 0 }));
}

export function simulatorText(enabled: boolean | null | undefined): string {
  if (enabled === true) return 'Simulador activo: hay lecturas generadas por el sensor virtual.';
  if (enabled === false) return 'Simulador apagado: las lecturas nuevas vienen de sensores reales.';
  return 'No se pudo saber si el simulador está activo.';
}
