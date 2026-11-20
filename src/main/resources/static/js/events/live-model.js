// What the live stream carries and how the page words it. Pure: no DOM, so node --test checks it (see live.ts).
import { isArrayOf, isRecord } from '../api/client.js';
export const SOURCE_LABELS = {
    DEVICE: 'Sensor físico',
    ESP32_TCP: 'ESP32',
    SIMULATED: 'Simulado',
    SEED: 'Semilla',
    MANUAL: 'Manual',
};
export const SOURCE_ICONS = {
    DEVICE: 'bi-cpu',
    ESP32_TCP: 'bi-router',
    SIMULATED: 'bi-shuffle',
    SEED: 'bi-database',
    MANUAL: 'bi-pencil',
};
export const isReadingSource = (value) => typeof value === 'string' && Object.prototype.hasOwnProperty.call(SOURCE_LABELS, value);
const isOptionalString = (value) => value === undefined || value === null || typeof value === 'string';
export const isAccess = (value) => isRecord(value) &&
    typeof value['enabled'] === 'boolean' &&
    (value['enabled'] === false ||
        (typeof value['token'] === 'string' &&
            typeof value['streamUrl'] === 'string' &&
            typeof value['expiresInSeconds'] === 'number'));
/** A reading with an unknown `source` is still shown; only the chip is left out (see sourceOf). */
export const isLiveReading = (value) => isRecord(value) &&
    typeof value['sensorCode'] === 'string' &&
    typeof value['value'] === 'number' &&
    Number.isFinite(value['value']) &&
    isOptionalString(value['unit']) &&
    isOptionalString(value['recordedAt']);
export const sourceOf = (reading) => isReadingSource(reading.source) ? reading.source : null;
export const isLiveAlert = (value) => isRecord(value) && typeof value['alertType'] === 'string' && typeof value['message'] === 'string';
export const isLiveEvent = (value) => isRecord(value) && isArrayOf(isLiveReading)(value['readings']) && isArrayOf(isLiveAlert)(value['alerts']);
/** Parses one SSE message body; anything malformed is null instead of an exception. */
export function parseLiveEvent(data) {
    try {
        const event = JSON.parse(data);
        return isLiveEvent(event) ? event : null;
    }
    catch {
        return null;
    }
}
export const STATE_LABELS = {
    live: 'En vivo',
    reconnecting: 'Reconectando…',
    offline: 'Sin conexión',
    simulated: 'Simulado',
    ai: 'IA',
    unavailable: 'Sin datos en vivo',
};
export const STATE_ICONS = {
    live: 'bi-broadcast',
    reconnecting: 'bi-arrow-repeat',
    offline: 'bi-wifi-off',
    simulated: 'bi-shuffle',
    ai: 'bi-stars',
    unavailable: 'bi-pause-circle',
};
export const FIRST_WAIT_MS = 1_000;
/** A3: never wait more than 30 s between reconnection attempts. */
export const LONGEST_WAIT_MS = 30_000;
export const OFFLINE_AFTER_FAILURES = 3;
/** A6: this many stream errors in a row without a single `ready` look like a CORS or origin problem. */
export const WARN_AFTER_ERRORS_WITHOUT_READY = 3;
/** A5: the stream address the server sent, with the token; null when it is not an http(s) URL. */
export function streamUrlWithToken(raw, token, base) {
    let url;
    try {
        url = base === undefined ? new URL(raw) : new URL(raw, base);
    }
    catch {
        return null;
    }
    if (url.protocol !== 'http:' && url.protocol !== 'https:')
        return null;
    url.searchParams.set('token', token);
    return url.toString();
}
/** What the badge shows when the server says live data is off (A4): the demo profile simulates, otherwise it is off. */
export const disabledState = (demoMode) => (demoMode ? 'simulated' : 'unavailable');
/** A8: the badge shows at most "99+", while the true count is kept apart. */
export const badgeText = (count) => (count > 99 ? '99+' : String(Math.max(0, Math.trunc(count))));
export const HISTORY_SIZE = 20;
/** The last readings of each sensor, oldest first, capped at HISTORY_SIZE: what a card's sparkline draws. */
export class ReadingHistory {
    size;
    bySensor = new Map();
    constructor(size = HISTORY_SIZE) {
        this.size = size;
    }
    add(sensorCode, value) {
        const values = this.bySensor.get(sensorCode) ?? [];
        values.push(value);
        if (values.length > this.size)
            values.splice(0, values.length - this.size);
        this.bySensor.set(sensorCode, values);
        return [...values];
    }
    of(sensorCode) {
        return [...(this.bySensor.get(sensorCode) ?? [])];
    }
}
/** Points for an SVG polyline in a 100 x 28 box (the sparkline's viewBox); a flat series is drawn in the middle. */
export function sparklinePoints(values, width = 100, height = 28) {
    if (values.length < 2)
        return '';
    const low = Math.min(...values);
    const high = Math.max(...values);
    const span = high - low;
    const pad = 2;
    const round = (n) => Math.round(n * 10) / 10;
    return values
        .map((value, index) => {
        const x = (index / (values.length - 1)) * width;
        const y = span === 0 ? height / 2 : pad + (1 - (value - low) / span) * (height - pad * 2);
        return `${round(x)},${round(y)}`;
    })
        .join(' ');
}
export const TREND_LABELS = { up: 'Subiendo', down: 'Bajando', flat: 'Estable' };
export const TREND_ICONS = {
    up: 'bi-arrow-up-right',
    down: 'bi-arrow-down-right',
    flat: 'bi-arrow-right',
};
/** Compares the last value with the one before; a change below `tolerance` (relative to the range) is "flat". */
export function trendOf(values, tolerance = 0.02) {
    if (values.length < 2)
        return null;
    const last = values[values.length - 1] ?? 0;
    const before = values[values.length - 2] ?? 0;
    const span = Math.max(...values) - Math.min(...values) || Math.abs(before) || 1;
    const change = (last - before) / span;
    if (Math.abs(change) <= tolerance)
        return 'flat';
    return change > 0 ? 'up' : 'down';
}
/** The server's sparkline is already scaled (smaller y is higher); its last two points say the trend of the day. */
export function trendOfPolyline(points) {
    const ys = points
        .trim()
        .split(/\s+/)
        .map((pair) => Number(pair.split(',')[1]))
        .filter((y) => Number.isFinite(y));
    return trendOf(ys.map((y) => -y));
}
/** The average of the sensors a KPI card sums up (dashboard), ignoring those without a value. */
export function averageOf(values) {
    let total = 0;
    let count = 0;
    for (const value of values) {
        if (value === null || !Number.isFinite(value))
            continue;
        total += value;
        count += 1;
    }
    return count === 0 ? null : total / count;
}
// ---- Polling fallback: GET /api/ui/readings/latest?since=… while the event service is off or failing. ----
/** One round every 10 s while it works. */
export const POLL_MS = 10_000;
const isPolledReading = (value) => isLiveReading(value) && typeof value.recordedAt === 'string';
export const isPolledReadings = (value) => isArrayOf(isPolledReading)(value);
/** The pause before the next round: POLL_MS while it works, then doubling per failure in a row, never above 30 s. */
export function pollDelay(failures) {
    if (failures <= 0)
        return POLL_MS;
    return Math.min(POLL_MS * 2 ** failures, LONGEST_WAIT_MS);
}
/** The newest recorded time among `since` and the readings, so a reading is never asked for twice. */
export function nextSince(since, readings) {
    let newest = since;
    let newestMs = Date.parse(since);
    for (const { recordedAt } of readings) {
        const ms = Date.parse(recordedAt);
        if (Number.isNaN(ms))
            continue;
        if (Number.isNaN(newestMs) || ms > newestMs) {
            newest = recordedAt;
            newestMs = ms;
        }
    }
    return newest;
}
/**
 * What the badge says after a round: errors → reconnecting; the newest reading simulated → simulated; a real one → live.
 * A round with nothing new keeps "simulated" and otherwise falls back to `baseline` ("simulated" in demo mode), so an
 * empty answer never claims live data the page does not have.
 */
export function pollState(ok, readings, previous, baseline = 'live') {
    if (!ok)
        return 'reconnecting';
    const last = readings[readings.length - 1];
    if (!last)
        return previous === 'simulated' ? 'simulated' : baseline;
    return sourceOf(last) === 'SIMULATED' ? 'simulated' : 'live';
}
export const latestUrl = (base, since) => `${base}?since=${encodeURIComponent(since)}`;
