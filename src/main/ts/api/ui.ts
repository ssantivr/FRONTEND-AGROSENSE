// The JSON endpoints of this application, each with the guard that validates its answer.
import type {
  Corner,
  DailyWater,
  EmailAvailability,
  EstateMarker,
  IrrigationRequest,
  PickerSite,
  ReadingPoint,
  ReadingSeries,
  SensorSpot,
} from '../types/api.js';
import { isArrayOf, isRecord, requestJson } from './client.js';

const isNumber = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value);
const isString = (value: unknown): value is string => typeof value === 'string';
const isNullable =
  <T>(guard: (value: unknown) => value is T) =>
  (value: unknown): value is T | null =>
    value === null || guard(value);

const isReadingPoint = (value: unknown): value is ReadingPoint =>
  isRecord(value) && isString(value['recordedAt']) && isNumber(value['value']);

const isReadingSeries = (value: unknown): value is ReadingSeries =>
  isRecord(value) &&
  isString(value['sensorCode']) &&
  isNullable(isString)(value['unit']) &&
  isNullable(isNumber)(value['thresholdMin']) &&
  isNullable(isNumber)(value['thresholdMax']) &&
  isArrayOf(isReadingPoint)(value['points']);

const isDailyWater = (value: unknown): value is DailyWater =>
  isRecord(value) && isString(value['date']) && isNumber(value['liters']);

const isCorner = (value: unknown): value is Corner =>
  Array.isArray(value) && value.length === 2 && isNumber(value[0]) && isNumber(value[1]);

/** An outline needs at least three corners to enclose anything. */
export const isBoundary = (value: unknown): value is Corner[] => isArrayOf(isCorner)(value) && value.length >= 3;

const isSensorSpot = (value: unknown): value is SensorSpot =>
  isRecord(value) &&
  isNumber(value['id']) &&
  isString(value['code']) &&
  isString(value['typeLabel']) &&
  isNumber(value['latitude']) &&
  isNumber(value['longitude']) &&
  isNumber(value['coverageM']) &&
  typeof value['active'] === 'boolean' &&
  isNullable(isNumber)(value['lastValue']) &&
  isNullable(isString)(value['unit']);

export const isPickerSite = (value: unknown): value is PickerSite =>
  isRecord(value) &&
  isString(value['name']) &&
  isNullable(isNumber)(value['latitude']) &&
  isNullable(isNumber)(value['longitude']) &&
  isNullable(isNumber)(value['areaHa']) &&
  isNullable(isBoundary)(value['boundary']);

const isEstateMarker = (value: unknown): value is EstateMarker =>
  isRecord(value) &&
  isNumber(value['id']) &&
  isString(value['name']) &&
  isNullable(isString)(value['location']) &&
  isNullable(isNumber)(value['areaHa']) &&
  isNullable(isNumber)(value['latitude']) &&
  isNullable(isNumber)(value['longitude']) &&
  isArrayOf(isString)(value['crops']) &&
  isNumber(value['sensorCount']) &&
  isNumber(value['openAlerts']) &&
  isArrayOf(isSensorSpot)(value['sensors']) &&
  isNullable(isBoundary)(value['boundary']);

const isEmailAvailability = (value: unknown): value is EmailAvailability =>
  isRecord(value) && typeof value['available'] === 'boolean';

/** Reads data the server embedded in the page as JSON; null when it is absent or not what was promised. */
function embedded<T>(json: string | undefined, guard: (value: unknown) => value is T): T | null {
  if (!json) return null;
  try {
    const value: unknown = JSON.parse(json);
    return guard(value) ? value : null;
  } catch {
    return null;
  }
}

export const embeddedEstateMarkers = (json: string | undefined): EstateMarker[] | null =>
  embedded(json, isArrayOf(isEstateMarker));

/** One estate, as its own page embeds it. */
export function embeddedEstate(json: string | undefined): EstateMarker | null {
  return embedded(json, isEstateMarker);
}

export const embeddedWaterUsage = (json: string | undefined): DailyWater[] | null =>
  embedded(json, isArrayOf(isDailyWater));

export const fetchReadings = (sensorId: string, signal?: AbortSignal): Promise<ReadingSeries> =>
  requestJson(`/api/ui/sensors/${encodeURIComponent(sensorId)}/readings`, isReadingSeries, signal ? { signal } : {});

export const fetchWaterUsage = (): Promise<DailyWater[]> => requestJson('/api/ui/water-usage', isArrayOf(isDailyWater));

export const fetchEstateMarkers = (): Promise<EstateMarker[]> =>
  requestJson('/api/ui/estates/markers', isArrayOf(isEstateMarker));

/** The created irrigation is not read: the page reloads to show it everywhere. */
export const startIrrigation = (irrigation: IrrigationRequest): Promise<Record<string, unknown>> =>
  requestJson('/api/ui/irrigations', isRecord, { method: 'POST', body: irrigation });

export const checkEmail = (url: string, email: string, signal: AbortSignal): Promise<EmailAvailability> =>
  requestJson(`${url}?email=${encodeURIComponent(email)}`, isEmailAvailability, { signal, timeoutMs: 8_000 });
