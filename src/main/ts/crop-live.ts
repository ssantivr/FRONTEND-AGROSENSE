// The crop page asks for the last value of each sensor every 30 seconds. Readings that the backend reads over TCP
// from the ESP32 never pass through the event service, so the live stream (events/live.ts) does not carry them; this
// keeps an open page current anyway, and says whether the device is still sending.
import { isArrayOf, isRecord, requestJson } from './api/client.js';
import { all, find } from './core/dom.js';
import { deviceStatus, newest, type DeviceState } from './device-status.js';
import { showReading, type LiveReading } from './events/live.js';

interface LatestReading {
  sensorCode: string;
  value: number;
  unit: string | null;
  recordedAt: string | null;
}

interface CropLatest {
  readings: LatestReading[];
  lastReadingAt: string | null;
}

const POLL_MS = 30_000;
const TONES: Record<DeviceState, string> = { online: 'success', stale: 'warning', none: 'warning' };
const ICONS: Record<DeviceState, string> = { online: 'bi-broadcast', stale: 'bi-clock-history', none: 'bi-clock-history' };

const isNullableString = (value: unknown): value is string | null => value === null || typeof value === 'string';

const isLatestReading = (value: unknown): value is LatestReading =>
  isRecord(value) &&
  typeof value['sensorCode'] === 'string' &&
  typeof value['value'] === 'number' &&
  isNullableString(value['unit']) &&
  isNullableString(value['recordedAt']);

const isCropLatest = (value: unknown): value is CropLatest =>
  isRecord(value) && isArrayOf(isLatestReading)(value['readings']) && isNullableString(value['lastReadingAt']);

class CropLive {
  private timer: number | undefined;
  private lastReading: Date | null;
  /** The time of the reading each sensor shows now, as the server rendered it or as painted since. */
  private readonly seen = new Map<string, number>();

  constructor(
    private readonly status: HTMLElement,
    private readonly url: string,
    private readonly onlineWithinMs: number,
  ) {
    this.lastReading = newest([status.dataset['lastReading'] ?? null]);
    for (const holder of all('[data-sensor-code]', HTMLElement)) {
      const shown = holder.querySelector<HTMLTimeElement>('time[datetime]');
      const when = shown ? new Date(shown.dateTime).getTime() : Number.NaN;
      if (holder.dataset['sensorCode'] && !Number.isNaN(when)) this.seen.set(holder.dataset['sensorCode'], when);
    }
  }

  start(): void {
    this.paint();
    this.schedule();
  }

  stop(): void {
    window.clearTimeout(this.timer);
  }

  private schedule(): void {
    window.clearTimeout(this.timer);
    this.timer = window.setTimeout(() => void this.refresh(), POLL_MS);
  }

  private async refresh(): Promise<void> {
    try {
      const latest = await requestJson(this.url, isCropLatest, { timeoutMs: 15_000 });
      for (const reading of latest.readings) {
        // Only what is newer than what the page shows, so a reading is not flashed as new every 30 seconds.
        const when = reading.recordedAt ? new Date(reading.recordedAt).getTime() : Number.NaN;
        const shown = this.seen.get(reading.sensorCode);
        if (Number.isNaN(when) || (shown !== undefined && when <= shown)) continue;
        this.seen.set(reading.sensorCode, when);
        showReading(reading satisfies LiveReading);
      }
      this.lastReading = newest([latest.lastReadingAt]);
    } catch {
      // The page keeps what it shows; the indicator ages on its own and the next poll tries again.
    }
    this.paint();
    this.schedule();
  }

  private paint(): void {
    const { state, label } = deviceStatus(this.lastReading, new Date(), this.onlineWithinMs);
    const pill = this.status.querySelector<HTMLElement>('[data-device-pill]');
    if (pill) pill.className = `status-pill status-pill--${TONES[state]}`;
    const icon = this.status.querySelector<HTMLElement>('[data-device-icon]');
    if (icon) icon.className = `bi ${ICONS[state]}`;
    const text = this.status.querySelector<HTMLElement>('[data-device-label]');
    if (text) text.textContent = label;
  }
}

function start(): void {
  const status = find('deviceStatus', HTMLElement);
  const url = status?.dataset['latestUrl'];
  if (!status || !url) return;
  const live = new CropLive(status, url, Number(status.dataset['onlineMs']) || 120_000);
  live.start();
  window.addEventListener('pagehide', () => live.stop());
  window.addEventListener('pageshow', (event) => {
    if (event.persisted) live.start();
  });
}

start();
