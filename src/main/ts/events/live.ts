// Live readings and alerts. Asks this server for a short token (signed by the backend), opens the event
// service's stream with it and updates the page as events arrive: the sensors' latest values, and a notice
// plus the alert counters for every new alert. The token is renewed before it expires, and a dropped stream
// is opened again after a growing wait. When live events are off or fail, the page keeps what it loaded.
import { showToast } from '../app.js';
import { ApiError, isArrayOf, isRecord, requestJson } from '../api/client.js';
import { all, find } from '../core/dom.js';

interface StreamAccess {
  enabled: boolean;
  token?: string;
  streamUrl?: string;
  expiresInSeconds?: number;
}

interface LiveReading {
  sensorCode: string;
  value: number;
  unit: string | null;
  recordedAt: string | null;
}

interface LiveAlert {
  alertType: string;
  message: string;
  cropName: string | null;
}

interface LiveEvent {
  readings: LiveReading[];
  alerts: LiveAlert[];
}

const isAccess = (value: unknown): value is StreamAccess =>
  isRecord(value) &&
  typeof value['enabled'] === 'boolean' &&
  (value['enabled'] === false ||
    (typeof value['token'] === 'string' &&
      typeof value['streamUrl'] === 'string' &&
      typeof value['expiresInSeconds'] === 'number'));

const isReading = (value: unknown): value is LiveReading =>
  isRecord(value) && typeof value['sensorCode'] === 'string' && typeof value['value'] === 'number';

const isAlert = (value: unknown): value is LiveAlert =>
  isRecord(value) && typeof value['alertType'] === 'string' && typeof value['message'] === 'string';

const isEvent = (value: unknown): value is LiveEvent =>
  isRecord(value) && isArrayOf(isReading)(value['readings']) && isArrayOf(isAlert)(value['alerts']);

const FIRST_WAIT_MS = 1_000;
const LONGEST_WAIT_MS = 60_000;
/** The stream is reopened with a fresh token this long before the current one expires. */
const RENEW_BEFORE_MS = 30_000;

const decimal = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const shortDate = new Intl.DateTimeFormat('es-CO', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

class LiveEvents {
  private source: EventSource | null = null;
  private wait = FIRST_WAIT_MS;
  private timer: number | undefined;

  constructor(
    private readonly tokenUrl: string,
    private readonly status: HTMLElement,
  ) {}

  /** Opens the stream; whatever goes wrong only schedules another try, never an error on the page. */
  async connect(): Promise<void> {
    this.close();
    let access: StreamAccess;
    try {
      access = await requestJson(this.tokenUrl, isAccess, { timeoutMs: 20_000 });
    } catch (error) {
      // A signed-out user gets no stream; anything else may be a backend still waking up.
      if (error instanceof ApiError && error.kind === 'unauthorized') return;
      this.retryLater();
      return;
    }
    if (!access.enabled || !access.token || !access.streamUrl) return;
    const url = new URL(access.streamUrl);
    url.searchParams.set('token', access.token);
    const source = new EventSource(url.toString());
    this.source = source;
    source.addEventListener('ready', () => {
      this.wait = FIRST_WAIT_MS;
      this.status.hidden = false;
      const lifetimeMs = (access.expiresInSeconds ?? 300) * 1000;
      this.schedule(Math.max(lifetimeMs - RENEW_BEFORE_MS, 5_000));
    });
    source.addEventListener('reading', (message) => this.deliver(message));
    source.addEventListener('alert', (message) => this.deliver(message));
    source.addEventListener('expired', () => void this.connect());
    // The browser would reconnect on its own with the same token, which may have expired; this asks for a
    // new one after a wait that grows with each failure.
    source.addEventListener('error', () => {
      this.close();
      this.retryLater();
    });
  }

  private deliver(message: Event): void {
    if (!(message instanceof MessageEvent) || typeof message.data !== 'string') return;
    let event: unknown;
    try {
      event = JSON.parse(message.data);
    } catch {
      return;
    }
    if (!isEvent(event)) return;
    event.readings.forEach(showReading);
    event.alerts.forEach(showAlert);
  }

  private retryLater(): void {
    this.schedule(this.wait);
    this.wait = Math.min(this.wait * 2, LONGEST_WAIT_MS);
  }

  private schedule(delayMs: number): void {
    window.clearTimeout(this.timer);
    this.timer = window.setTimeout(() => void this.connect(), delayMs);
  }

  close(): void {
    window.clearTimeout(this.timer);
    this.source?.close();
    this.source = null;
    this.status.hidden = true;
  }
}

/** Every place of the page that shows this sensor's latest reading gets the new one. */
function showReading(reading: LiveReading): void {
  const when = reading.recordedAt ? new Date(reading.recordedAt) : new Date();
  for (const holder of all('[data-sensor-code]', HTMLElement)) {
    if (holder.dataset['sensorCode'] !== reading.sensorCode) continue;
    for (const value of all('[data-live-value]', HTMLElement, holder)) {
      const unit = value.dataset['unit'];
      value.textContent = decimal.format(reading.value) + (unit ? ` ${unit}` : '');
    }
    for (const time of all('[data-live-time]', HTMLElement, holder)) {
      time.textContent = time.dataset['liveTime'] === 'ago' ? 'Última lectura hace un momento' : shortDate.format(when);
      if (time instanceof HTMLTimeElement) time.dateTime = when.toISOString();
    }
    holder.classList.remove('is-live-updated');
    // Reading the size restarts the highlight when two readings arrive in a row.
    void holder.offsetWidth;
    holder.classList.add('is-live-updated');
  }
}

/** A notice, and the alert counters of the menu and the top bar, which a new alert changes. */
function showAlert(alert: LiveAlert): void {
  showToast(`Nueva alerta${alert.cropName ? ` en ${alert.cropName}` : ''}: ${alert.message}`, 'error');
  const badge = document.querySelector<HTMLElement>('.sidebar-badge');
  if (badge) {
    const count = (Number(badge.textContent) || 0) + 1;
    badge.textContent = String(count);
    badge.setAttribute('aria-label', `${count} alertas sin atender`);
  }
  const bell = find('topbarAlerts', HTMLAnchorElement);
  if (bell && !bell.querySelector('.topbar-dot')) {
    const dot = document.createElement('span');
    dot.className = 'topbar-dot';
    dot.setAttribute('aria-hidden', 'true');
    bell.append(dot);
    bell.setAttribute('aria-label', 'Hay alertas sin atender');
  }
}

function start(): void {
  const status = find('liveStatus', HTMLElement);
  const tokenUrl = status?.dataset['tokenUrl'];
  if (!status || !tokenUrl || typeof EventSource === 'undefined') return;
  const live = new LiveEvents(tokenUrl, status);
  void live.connect();
  window.addEventListener('pagehide', () => live.close());
}

start();
