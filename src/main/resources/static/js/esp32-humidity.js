// The ESP32's humidity list in the Sensors page asks for its newest readings every 30 seconds. Readings that the backend
// reads over TCP never pass through the event service, so the live stream (events/live.ts) does not carry them; this
// keeps an open page current anyway, like crop-live.ts does for the crop page.
import { isArrayOf, isRecord, requestJson } from './api/client.js';
import { el, icon } from './core/dom.js';
const POLL_MS = 30_000;
const ROW_FIELDS = ['recordedAt', 'recordedAtText', 'sensorCode', 'measure', 'valueText', 'level', 'tone', 'icon', 'label'];
const TONES = new Set(['success', 'warning', 'danger', 'neutral', 'info', 'critical']);
const isRow = (value) => isRecord(value) && ROW_FIELDS.every((field) => typeof value[field] === 'string');
export const isHumidityFeed = (value) => isRecord(value) &&
    isArrayOf(isRow)(value['readings']) &&
    (value['newestAt'] === null || typeof value['newestAt'] === 'string');
/** The newest value of each sensor in a feed that is newest first: what the sensors table shows as last reading. */
export function lastValues(readings) {
    const last = new Map();
    for (const reading of readings)
        if (!last.has(reading.sensorCode))
            last.set(reading.sensorCode, reading.valueText);
    return last;
}
/** Repaint only when the server has a reading newer than the one the page shows. */
export function isNewer(shown, latest) {
    if (!latest)
        return false;
    const latestMs = new Date(latest).getTime();
    if (Number.isNaN(latestMs))
        return false;
    if (!shown)
        return true;
    const shownMs = new Date(shown).getTime();
    return Number.isNaN(shownMs) || latestMs > shownMs;
}
function rowOf(reading) {
    const row = el('tr');
    row.dataset['level'] = reading.level;
    const time = el('time', '', reading.recordedAtText);
    time.dateTime = reading.recordedAt;
    const when = el('td');
    when.append(time);
    // The tone comes from the server; anything unexpected falls back to a neutral pill instead of an unknown class.
    const pill = el('span', `status-pill status-pill--${TONES.has(reading.tone) ? reading.tone : 'neutral'}`);
    pill.append(icon(reading.icon), el('span', '', reading.label));
    const level = el('td');
    level.append(pill);
    row.append(when, el('td', '', reading.sensorCode), el('td', '', reading.measure), el('td', 'numeric', reading.valueText), level);
    return row;
}
class Esp32Humidity {
    section;
    url;
    newestAt;
    timer;
    constructor(section, url, newestAt) {
        this.section = section;
        this.url = url;
        this.newestAt = newestAt;
    }
    start() {
        this.schedule();
    }
    stop() {
        window.clearTimeout(this.timer);
    }
    schedule() {
        window.clearTimeout(this.timer);
        this.timer = window.setTimeout(() => void this.refresh(), POLL_MS);
    }
    async refresh() {
        try {
            const feed = await requestJson(this.url, isHumidityFeed, { timeoutMs: 15_000 });
            const body = this.section.querySelector('[data-humidity-list] tbody');
            if (body && isNewer(this.newestAt, feed.newestAt)) {
                body.replaceChildren(...feed.readings.map(rowOf));
                this.newestAt = feed.newestAt;
                const last = lastValues(feed.readings);
                for (const row of this.section.querySelectorAll('[data-humidity-sensor]')) {
                    const value = last.get(row.dataset['humiditySensor'] ?? '');
                    const cell = row.querySelector('[data-humidity-last]');
                    if (value && cell)
                        cell.textContent = value;
                }
            }
            const updated = this.section.querySelector('[data-humidity-updated]');
            if (updated)
                updated.textContent = `Actualizado a las ${new Date().toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit', hour12: false })}`;
        }
        catch {
            // The page keeps what it shows and the next poll tries again.
        }
        this.schedule();
    }
}
function start() {
    const section = document.getElementById('sensor-esp32');
    const url = section?.dataset['humidityUrl'];
    if (!section || !url)
        return;
    const live = new Esp32Humidity(section, url, section.dataset['newestAt'] || null);
    live.start();
    window.addEventListener('pagehide', () => live.stop());
    window.addEventListener('pageshow', (event) => {
        if (event.persisted)
            live.start();
    });
}
if (typeof document !== 'undefined')
    start();
