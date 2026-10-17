import { showToast } from '../app.js';
import { ApiError, isArrayOf, isRecord, requestJson } from '../api/client.js';
import { all, find } from '../core/dom.js';
const isAccess = (value) => isRecord(value) &&
    typeof value['enabled'] === 'boolean' &&
    (value['enabled'] === false ||
        (typeof value['token'] === 'string' &&
            typeof value['streamUrl'] === 'string' &&
            typeof value['expiresInSeconds'] === 'number'));
const isReading = (value) => isRecord(value) && typeof value['sensorCode'] === 'string' && typeof value['value'] === 'number';
const isAlert = (value) => isRecord(value) && typeof value['alertType'] === 'string' && typeof value['message'] === 'string';
const isEvent = (value) => isRecord(value) && isArrayOf(isReading)(value['readings']) && isArrayOf(isAlert)(value['alerts']);
const FIRST_WAIT_MS = 1_000;
const LONGEST_WAIT_MS = 60_000;
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
    tokenUrl;
    status;
    source = null;
    wait = FIRST_WAIT_MS;
    timer;
    constructor(tokenUrl, status) {
        this.tokenUrl = tokenUrl;
        this.status = status;
    }
    async connect() {
        this.close();
        let access;
        try {
            access = await requestJson(this.tokenUrl, isAccess, { timeoutMs: 20_000 });
        }
        catch (error) {
            if (error instanceof ApiError && error.kind === 'unauthorized')
                return;
            this.retryLater();
            return;
        }
        if (!access.enabled || !access.token || !access.streamUrl)
            return;
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
        source.addEventListener('error', () => {
            this.close();
            this.retryLater();
        });
    }
    deliver(message) {
        if (!(message instanceof MessageEvent) || typeof message.data !== 'string')
            return;
        let event;
        try {
            event = JSON.parse(message.data);
        }
        catch {
            return;
        }
        if (!isEvent(event))
            return;
        event.readings.forEach(showReading);
        event.alerts.forEach(showAlert);
    }
    retryLater() {
        this.schedule(this.wait);
        this.wait = Math.min(this.wait * 2, LONGEST_WAIT_MS);
    }
    schedule(delayMs) {
        window.clearTimeout(this.timer);
        this.timer = window.setTimeout(() => void this.connect(), delayMs);
    }
    close() {
        window.clearTimeout(this.timer);
        this.source?.close();
        this.source = null;
        this.status.hidden = true;
    }
}
function showReading(reading) {
    const when = reading.recordedAt ? new Date(reading.recordedAt) : new Date();
    for (const holder of all('[data-sensor-code]', HTMLElement)) {
        if (holder.dataset['sensorCode'] !== reading.sensorCode)
            continue;
        for (const value of all('[data-live-value]', HTMLElement, holder)) {
            const unit = value.dataset['unit'];
            value.textContent = decimal.format(reading.value) + (unit ? ` ${unit}` : '');
        }
        for (const time of all('[data-live-time]', HTMLElement, holder)) {
            time.textContent = time.dataset['liveTime'] === 'ago' ? 'Última lectura hace un momento' : shortDate.format(when);
            if (time instanceof HTMLTimeElement)
                time.dateTime = when.toISOString();
        }
        holder.classList.remove('is-live-updated');
        void holder.offsetWidth;
        holder.classList.add('is-live-updated');
    }
}
function showAlert(alert) {
    showToast(`Nueva alerta${alert.cropName ? ` en ${alert.cropName}` : ''}: ${alert.message}`, 'error');
    const badge = document.querySelector('.sidebar-badge');
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
function start() {
    const status = find('liveStatus', HTMLElement);
    const tokenUrl = status?.dataset['tokenUrl'];
    if (!status || !tokenUrl || typeof EventSource === 'undefined')
        return;
    const live = new LiveEvents(tokenUrl, status);
    void live.connect();
    window.addEventListener('pagehide', () => live.close());
}
start();
