import { isArrayOf, isRecord, requestJson } from './client.js';
const isNumber = (value) => typeof value === 'number' && Number.isFinite(value);
const isString = (value) => typeof value === 'string';
const isNullable = (guard) => (value) => value === null || guard(value);
const isReadingPoint = (value) => isRecord(value) && isString(value['recordedAt']) && isNumber(value['value']);
const isReadingSeries = (value) => isRecord(value) &&
    isString(value['sensorCode']) &&
    isNullable(isString)(value['unit']) &&
    isNullable(isNumber)(value['thresholdMin']) &&
    isNullable(isNumber)(value['thresholdMax']) &&
    isArrayOf(isReadingPoint)(value['points']);
const isDailyWater = (value) => isRecord(value) && isString(value['date']) && isNumber(value['liters']);
const isCorner = (value) => Array.isArray(value) && value.length === 2 && isNumber(value[0]) && isNumber(value[1]);
/** An outline needs at least three corners to enclose anything. */
export const isBoundary = (value) => isArrayOf(isCorner)(value) && value.length >= 3;
const isSensorSpot = (value) => isRecord(value) &&
    isNumber(value['id']) &&
    isString(value['code']) &&
    isString(value['typeLabel']) &&
    isNumber(value['latitude']) &&
    isNumber(value['longitude']) &&
    isNumber(value['coverageM']) &&
    typeof value['active'] === 'boolean' &&
    isNullable(isNumber)(value['lastValue']) &&
    isNullable(isString)(value['unit']);
export const isPickerSite = (value) => isRecord(value) &&
    isString(value['name']) &&
    isNullable(isNumber)(value['latitude']) &&
    isNullable(isNumber)(value['longitude']) &&
    isNullable(isNumber)(value['areaHa']) &&
    isNullable(isBoundary)(value['boundary']);
const isEstateMarker = (value) => isRecord(value) &&
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
const isEmailAvailability = (value) => isRecord(value) && typeof value['available'] === 'boolean';
/** Reads data the server embedded in the page as JSON; null when it is absent or not what was promised. */
function embedded(json, guard) {
    if (!json)
        return null;
    try {
        const value = JSON.parse(json);
        return guard(value) ? value : null;
    }
    catch {
        return null;
    }
}
export const embeddedEstateMarkers = (json) => embedded(json, isArrayOf(isEstateMarker));
/** One estate, as its own page embeds it. */
export function embeddedEstate(json) {
    return embedded(json, isEstateMarker);
}
export const embeddedWaterUsage = (json) => embedded(json, isArrayOf(isDailyWater));
export const fetchReadings = (sensorId, signal) => requestJson(`/api/ui/sensors/${encodeURIComponent(sensorId)}/readings`, isReadingSeries, signal ? { signal } : {});
export const fetchWaterUsage = () => requestJson('/api/ui/water-usage', isArrayOf(isDailyWater));
export const fetchEstateMarkers = () => requestJson('/api/ui/estates/markers', isArrayOf(isEstateMarker));
/** The created irrigation is not read: the page reloads to show it everywhere. */
export const startIrrigation = (irrigation) => requestJson('/api/ui/irrigations', isRecord, { method: 'POST', body: irrigation });
export const checkEmail = (url, email, signal) => requestJson(`${url}?email=${encodeURIComponent(email)}`, isEmailAvailability, { signal, timeoutMs: 8_000 });
