// The crop page asks for the last value of each sensor every 30 seconds. Readings that the backend reads over TCP
// from the ESP32 never pass through the event service, so the live stream (events/live.ts) does not carry them; this
// keeps an open page current anyway, and says whether the device is still sending.
import { isArrayOf, isRecord, requestJson } from './api/client.js';
import { all, find } from './core/dom.js';
import { deviceStatus, newest } from './device-status.js';
import { showReading } from './events/live.js';
const POLL_MS = 30_000;
const TONES = { online: 'success', stale: 'warning', none: 'warning' };
const ICONS = { online: 'bi-broadcast', stale: 'bi-clock-history', none: 'bi-clock-history' };
const isNullableString = (value) => value === null || typeof value === 'string';
const isLatestReading = (value) => isRecord(value) &&
    typeof value['sensorCode'] === 'string' &&
    typeof value['value'] === 'number' &&
    isNullableString(value['unit']) &&
    isNullableString(value['recordedAt']);
const isCropLatest = (value) => isRecord(value) && isArrayOf(isLatestReading)(value['readings']) && isNullableString(value['lastReadingAt']);
class CropLive {
    status;
    url;
    onlineWithinMs;
    timer;
    lastReading;
    /** The time of the reading each sensor shows now, as the server rendered it or as painted since. */
    seen = new Map();
    constructor(status, url, onlineWithinMs) {
        this.status = status;
        this.url = url;
        this.onlineWithinMs = onlineWithinMs;
        this.lastReading = newest([status.dataset['lastReading'] ?? null]);
        for (const holder of all('[data-sensor-code]', HTMLElement)) {
            const shown = holder.querySelector('time[datetime]');
            const when = shown ? new Date(shown.dateTime).getTime() : Number.NaN;
            if (holder.dataset['sensorCode'] && !Number.isNaN(when))
                this.seen.set(holder.dataset['sensorCode'], when);
        }
    }
    start() {
        this.paint();
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
            const latest = await requestJson(this.url, isCropLatest, { timeoutMs: 15_000 });
            for (const reading of latest.readings) {
                // Only what is newer than what the page shows, so a reading is not flashed as new every 30 seconds.
                const when = reading.recordedAt ? new Date(reading.recordedAt).getTime() : Number.NaN;
                const shown = this.seen.get(reading.sensorCode);
                if (Number.isNaN(when) || (shown !== undefined && when <= shown))
                    continue;
                this.seen.set(reading.sensorCode, when);
                showReading(reading);
            }
            this.lastReading = newest([latest.lastReadingAt]);
        }
        catch {
            // The page keeps what it shows; the indicator ages on its own and the next poll tries again.
        }
        this.paint();
        this.schedule();
    }
    paint() {
        const { state, label } = deviceStatus(this.lastReading, new Date(), this.onlineWithinMs);
        const pill = this.status.querySelector('[data-device-pill]');
        if (pill)
            pill.className = `status-pill status-pill--${TONES[state]}`;
        const icon = this.status.querySelector('[data-device-icon]');
        if (icon)
            icon.className = `bi ${ICONS[state]}`;
        const text = this.status.querySelector('[data-device-label]');
        if (text)
            text.textContent = label;
    }
}
function start() {
    const status = find('deviceStatus', HTMLElement);
    const url = status?.dataset['latestUrl'];
    if (!status || !url)
        return;
    const live = new CropLive(status, url, Number(status.dataset['onlineMs']) || 120_000);
    live.start();
    window.addEventListener('pagehide', () => live.stop());
    window.addEventListener('pageshow', (event) => {
        if (event.persisted)
            live.start();
    });
}
start();
