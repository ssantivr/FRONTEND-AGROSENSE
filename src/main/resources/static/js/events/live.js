import { showToast } from '../app.js';
import { ApiError, requestJson } from '../api/client.js';
import { all, find } from '../core/dom.js';
import { agoText, clockTime, nextBackoff } from '../core/time.js';
import { LatestStream } from './latest-stream.js';
import { FIRST_WAIT_MS, LONGEST_WAIT_MS, OFFLINE_AFTER_FAILURES, ReadingHistory, SOURCE_ICONS, SOURCE_LABELS, STATE_ICONS, STATE_LABELS, TREND_ICONS, TREND_LABELS, WARN_AFTER_ERRORS_WITHOUT_READY, averageOf, badgeText, disabledState, isAccess, isPolledReadings, latestUrl, nextSince, pollDelay, pollState, parseLiveEvent, sourceOf, sparklinePoints, streamUrlWithToken, trendOf, trendOfPolyline, } from './live-model.js';
/** What the badge's tooltip explains for each state. */
const STATE_TITLES = {
    live: 'Las lecturas nuevas aparecen solas mientras ves la página.',
    reconnecting: 'Se perdió el canal en vivo; se reintenta solo.',
    offline: 'Sin conexión con el canal en vivo. La página muestra la última lectura conocida.',
    simulated: 'Las lecturas que llegan vienen del simulador, no de un sensor físico.',
    ai: 'Datos calculados por el asistente de IA.',
    unavailable: 'El canal en vivo no está configurado o no responde. Los datos se refrescan al recargar.',
};
const RENEW_BEFORE_MS = 30_000;
const AGO_REFRESH_MS = 30_000;
const SVG_NS = 'http://www.w3.org/2000/svg';
const decimal = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const shortDate = new Intl.DateTimeFormat('es-CO', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
});
const history = new ReadingHistory();
/**
 * The fallback while the event service is off or failing (it is not deployed in production, and the backend's
 * simulator only publishes over STOMP): asks GET /api/ui/readings/latest?since=… every 10 s, backs off up to 30 s after
 * errors, sleeps while the tab is hidden, and paints each reading exactly like a stream `reading` event.
 */
class ReadingPoller {
    url;
    onState;
    baseline;
    since = new Date().toISOString();
    failures = 0;
    timer;
    active = false;
    state = 'live';
    constructor(url, onState, baseline = 'live') {
        this.url = url;
        this.onState = onState;
        this.baseline = baseline;
        this.state = baseline;
    }
    get running() {
        return this.active;
    }
    start() {
        if (this.active)
            return;
        this.active = true;
        this.schedule(0);
    }
    stop() {
        this.active = false;
        window.clearTimeout(this.timer);
    }
    pause() {
        window.clearTimeout(this.timer);
    }
    resume() {
        if (this.active)
            this.schedule(0);
    }
    schedule(delayMs) {
        window.clearTimeout(this.timer);
        this.timer = window.setTimeout(() => void this.round(), delayMs);
    }
    async round() {
        if (!this.active || document.visibilityState === 'hidden')
            return;
        let readings = [];
        let ok = true;
        try {
            const polled = await requestJson(latestUrl(this.url, this.since), isPolledReadings, { timeoutMs: 10_000 });
            this.since = nextSince(this.since, polled);
            readings = polled;
            this.failures = 0;
        }
        catch (error) {
            if (error instanceof ApiError && error.kind === 'unauthorized') {
                this.stop();
                return;
            }
            ok = false;
            this.failures += 1;
        }
        if (!this.active)
            return;
        this.state = pollState(ok, readings, this.state, this.baseline);
        this.onState(this.state);
        readings.forEach(showReading);
        this.schedule(pollDelay(this.failures));
    }
}
class LiveEvents {
    tokenUrl;
    status;
    demoMode;
    poller;
    stream = new LatestStream();
    wait = FIRST_WAIT_MS;
    failures = 0;
    errorsWithoutReady = 0;
    warned = false;
    timer;
    paused = false;
    connected = false;
    lastSource = null;
    constructor(tokenUrl, status, demoMode, poller) {
        this.tokenUrl = tokenUrl;
        this.status = status;
        this.demoMode = demoMode;
        this.poller = poller;
    }
    /** The stream cannot be used (off, 503, a bad address or failing again and again): poll instead, if possible. */
    fallBack() {
        if (!this.poller)
            return false;
        window.clearTimeout(this.timer);
        this.poller.start();
        return true;
    }
    async connect() {
        this.close();
        this.paused = false;
        try {
            await this.stream.open(() => requestJson(this.tokenUrl, isAccess, { timeoutMs: 20_000 }), (access) => this.listen(access));
        }
        catch (error) {
            if (error instanceof ApiError && error.kind === 'unauthorized')
                return;
            // A4: 503 means the server knows live data is unavailable right now; say so instead of "reconnecting".
            if (error instanceof ApiError && error.status === 503) {
                this.show(disabledState(this.demoMode));
                if (!this.fallBack())
                    this.scheduleRetry();
                return;
            }
            this.retryLater();
        }
    }
    listen(access) {
        if (!access.enabled || !access.token || !access.streamUrl) {
            this.show(disabledState(this.demoMode));
            this.fallBack();
            return null;
        }
        // A5: an address that is not http(s) cannot get better by retrying.
        const url = streamUrlWithToken(access.streamUrl, access.token, window.location.href);
        if (url === null) {
            console.warn('AgroSense: la dirección del canal en vivo no es una URL http(s) válida; revisa EVENTS_PUBLIC_URL.');
            this.show('offline');
            this.fallBack();
            return null;
        }
        const source = new EventSource(url);
        source.addEventListener('ready', () => {
            this.wait = FIRST_WAIT_MS;
            this.failures = 0;
            this.errorsWithoutReady = 0;
            this.connected = true;
            this.poller?.stop();
            this.show(this.lastSource === 'SIMULATED' ? 'simulated' : 'live');
            const lifetimeMs = (access.expiresInSeconds ?? 300) * 1000;
            this.schedule(Math.max(lifetimeMs - RENEW_BEFORE_MS, 5_000));
        });
        source.addEventListener('reading', (message) => this.deliver(message));
        source.addEventListener('alert', (message) => this.deliver(message));
        source.addEventListener('expired', () => void this.connect());
        source.addEventListener('error', () => {
            this.connected = false;
            this.errorsWithoutReady += 1;
            // A6: errors before any `ready` are usually the event service refusing this page's origin.
            if (!this.warned && this.errorsWithoutReady >= WARN_AFTER_ERRORS_WITHOUT_READY) {
                this.warned = true;
                console.warn(`AgroSense: el canal en vivo falló ${this.errorsWithoutReady} veces sin conectar. ` +
                    `Comprueba que EVENTS_ALLOWED_ORIGINS del servicio de eventos incluya ${window.location.origin}.`);
            }
            this.close();
            this.retryLater();
        });
        return source;
    }
    deliver(message) {
        if (!(message instanceof MessageEvent) || typeof message.data !== 'string')
            return;
        const event = parseLiveEvent(message.data);
        if (!event)
            return;
        event.readings.forEach((reading) => {
            const source = sourceOf(reading);
            if (source !== null) {
                this.lastSource = source;
                if (this.connected)
                    this.show(source === 'SIMULATED' ? 'simulated' : 'live');
            }
            showReading(reading);
        });
        event.alerts.forEach(showAlert);
    }
    retryLater() {
        this.failures += 1;
        if (this.failures >= OFFLINE_AFTER_FAILURES && navigator.onLine && this.fallBack()) {
            this.show('reconnecting');
            return;
        }
        this.show(this.failures >= OFFLINE_AFTER_FAILURES || !navigator.onLine ? 'offline' : 'reconnecting');
        this.scheduleRetry();
    }
    scheduleRetry() {
        if (this.paused)
            return;
        this.schedule(this.wait);
        this.wait = nextBackoff(this.wait, LONGEST_WAIT_MS);
    }
    wentOffline() {
        this.close();
        this.poller?.pause();
        this.show('offline');
    }
    cameBack() {
        this.wait = FIRST_WAIT_MS;
        this.show('reconnecting');
        if (this.poller?.running)
            this.poller.resume();
        else
            void this.connect();
    }
    /** A3: a hidden tab keeps no stream open and does not poll; it starts again when it is seen. */
    pause() {
        this.paused = true;
        this.close();
        this.poller?.pause();
    }
    resume() {
        if (!this.paused)
            return;
        this.paused = false;
        this.wait = FIRST_WAIT_MS;
        if (this.poller?.running)
            this.poller.resume();
        else
            void this.connect();
    }
    /** The badge, for the poller too. */
    showState(state) {
        this.show(state);
    }
    indicators() {
        return [this.status, ...all('[data-live-indicator]', HTMLElement)];
    }
    show(state) {
        for (const indicator of this.indicators()) {
            indicator.hidden = false;
            indicator.dataset['state'] = state;
            indicator.title = STATE_TITLES[state];
            const label = indicator.querySelector('[data-live-label]');
            if (label)
                label.textContent = STATE_LABELS[state];
            const glyph = indicator.querySelector('[data-live-icon]');
            if (glyph)
                glyph.className = `bi ${STATE_ICONS[state]} live-status-icon`;
        }
    }
    hide() {
        for (const indicator of this.indicators())
            indicator.hidden = true;
    }
    schedule(delayMs) {
        window.clearTimeout(this.timer);
        this.timer = window.setTimeout(() => void this.connect(), delayMs);
    }
    close() {
        window.clearTimeout(this.timer);
        this.connected = false;
        this.stream.close();
    }
}
function paintSource(chip, source) {
    chip.hidden = false;
    chip.dataset['source'] = source;
    chip.title = `Origen de la lectura: ${SOURCE_LABELS[source]}`;
    const glyph = chip.querySelector('i');
    if (glyph)
        glyph.className = `bi ${SOURCE_ICONS[source]}`;
    const label = chip.querySelector('[data-live-source-label]');
    if (label)
        label.textContent = SOURCE_LABELS[source];
}
function paintTrend(holder, trend) {
    if (trend === null)
        return;
    for (const badge of all('[data-live-trend]', HTMLElement, holder)) {
        badge.hidden = false;
        badge.dataset['trend'] = trend;
        const glyph = badge.querySelector('i');
        if (glyph)
            glyph.className = `bi ${TREND_ICONS[trend]}`;
        const label = badge.querySelector('[data-live-trend-label]');
        if (label)
            label.textContent = TREND_LABELS[trend];
    }
}
function paintSpark(holder, values) {
    const points = sparklinePoints(values);
    if (points === '')
        return;
    for (const svg of all('[data-live-spark]', SVGSVGElement, holder)) {
        let line = svg.querySelector('polyline');
        if (!line) {
            line = document.createElementNS(SVG_NS, 'polyline');
            svg.append(line);
        }
        line.setAttribute('points', points);
        svg.removeAttribute('hidden');
        svg.setAttribute('aria-label', `Últimas ${values.length} lecturas recibidas en vivo`);
    }
}
function paintUpdated(holder, when) {
    for (const stamp of all('[data-live-updated]', HTMLElement, holder)) {
        stamp.hidden = false;
        stamp.textContent = `Actualizado a las ${clockTime(when)}`;
        if (stamp instanceof HTMLTimeElement)
            stamp.dateTime = when.toISOString();
    }
}
function flash(holder) {
    holder.classList.remove('is-live-updated');
    void holder.offsetWidth;
    holder.classList.add('is-live-updated');
}
/** The dashboard's KPI cards sum up every sensor of one type: a reading updates its member and the average. */
function showOnKpis(reading, when, source) {
    for (const member of all('[data-kpi-member]', HTMLDataElement)) {
        if (member.dataset['kpiMember'] !== reading.sensorCode)
            continue;
        member.value = String(reading.value);
        const card = member.closest('[data-kpi]');
        if (!card)
            continue;
        const average = averageOf(all('[data-kpi-member]', HTMLDataElement, card).map((one) => (one.value === '' ? null : Number(one.value))));
        if (average === null)
            continue;
        for (const value of all('[data-kpi-value]', HTMLElement, card))
            value.textContent = decimal.format(average);
        for (const time of all('[data-kpi-time]', HTMLElement, card)) {
            if (time instanceof HTMLTimeElement)
                time.dateTime = when.toISOString();
            const label = time.querySelector('[data-kpi-time-label]');
            if (label)
                label.textContent = `Última lectura: ${shortDate.format(when)}`;
        }
        const values = history.add(`kpi:${card.dataset['kpiType'] ?? ''}`, average);
        if (values.length >= 2) {
            paintSpark(card, values);
            paintTrend(card, trendOf(values));
        }
        if (source !== null)
            for (const chip of all('[data-live-source]', HTMLElement, card))
                paintSource(chip, source);
        paintUpdated(card, new Date());
        flash(card);
    }
}
/** Paints one reading on every element of the page marked with its sensor code; crop-live.ts uses it too. */
export function showReading(reading) {
    const parsed = reading.recordedAt ? new Date(reading.recordedAt) : new Date();
    const when = Number.isNaN(parsed.getTime()) ? new Date() : parsed;
    const source = sourceOf(reading);
    const values = history.add(reading.sensorCode, reading.value);
    for (const holder of all('[data-sensor-code]', HTMLElement)) {
        if (holder.dataset['sensorCode'] !== reading.sensorCode)
            continue;
        for (const value of all('[data-live-value]', HTMLElement, holder)) {
            const unit = value.dataset['unit'] || reading.unit;
            value.textContent = decimal.format(reading.value) + (unit ? ` ${unit}` : '');
            value.classList.remove('text-muted');
        }
        for (const pill of all('[data-sensor-status]', HTMLElement, holder))
            markOnline(pill);
        for (const hint of all('[data-live-hint]', HTMLElement, holder))
            hint.hidden = true;
        for (const time of all('[data-live-time]', HTMLElement, holder)) {
            time.textContent = time.dataset['liveTime'] === 'ago' ? `Última lectura ${agoText(when, new Date())}` : shortDate.format(when);
            if (time instanceof HTMLTimeElement)
                time.dateTime = when.toISOString();
        }
        if (source !== null)
            for (const chip of all('[data-live-source]', HTMLElement, holder))
                paintSource(chip, source);
        if (values.length >= 2) {
            paintSpark(holder, values);
            paintTrend(holder, trendOf(values));
        }
        paintUpdated(holder, new Date());
        flash(holder);
    }
    showOnKpis(reading, when, source);
}
/** B4: "Última lectura hace N min" ages while the page stays open. */
function refreshAgo() {
    const now = new Date();
    for (const time of all('time[data-live-time="ago"]', HTMLTimeElement)) {
        const when = new Date(time.dateTime);
        if (!Number.isNaN(when.getTime()))
            time.textContent = `Última lectura ${agoText(when, now)}`;
    }
}
function markOnline(pill) {
    if (pill.dataset['sensorStatus'] === 'INACTIVE')
        return;
    pill.dataset['sensorStatus'] = 'ONLINE';
    pill.className = 'status-pill status-pill--success';
    pill.title = 'Lectura recibida en vivo.';
    const icon = pill.querySelector('i');
    if (icon)
        icon.className = 'bi bi-check-circle-fill';
    const label = pill.querySelector('span');
    if (label)
        label.textContent = 'En línea';
}
function showAlert(alert) {
    showToast(`Nueva alerta${alert.cropName ? ` en ${alert.cropName}` : ''}: ${alert.message}`, 'error');
    const badge = document.querySelector('.sidebar-badge');
    if (badge) {
        // A8: the real count lives in data-count; the text may be capped at "99+".
        const count = (Number(badge.dataset['count']) || 0) + 1;
        badge.dataset['count'] = String(count);
        badge.textContent = badgeText(count);
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
    // The day's trend of each KPI card, from the sparkline the server drew, until live readings replace it.
    for (const card of all('[data-kpi]', HTMLElement)) {
        const points = card.querySelector('[data-live-spark] polyline')?.getAttribute('points');
        if (points)
            paintTrend(card, trendOfPolyline(points));
    }
    if (all('time[data-live-time="ago"]', HTMLTimeElement).length > 0) {
        const agoTimer = window.setInterval(refreshAgo, AGO_REFRESH_MS);
        window.addEventListener('pagehide', () => window.clearInterval(agoTimer), { once: true });
    }
    const status = find('liveStatus', HTMLElement);
    const tokenUrl = status?.dataset['tokenUrl'];
    if (!status || !tokenUrl || typeof EventSource === 'undefined')
        return;
    const latest = status.dataset['latestUrl'];
    let live;
    const demoMode = status.dataset['demoMode'] === 'true';
    const poller = latest
        ? new ReadingPoller(latest, (state) => live?.showState(state), demoMode ? 'simulated' : 'live')
        : null;
    live = new LiveEvents(tokenUrl, status, demoMode, poller);
    void live.connect();
    window.addEventListener('offline', () => live.wentOffline());
    window.addEventListener('online', () => live.cameBack());
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'hidden')
            live.pause();
        else
            live.resume();
    });
    window.addEventListener('pagehide', () => {
        poller?.stop();
        live.close();
        live.hide();
    });
    // Going back restores the page from memory, and start() does not run again.
    window.addEventListener('pageshow', (event) => {
        if (event.persisted)
            void live.connect();
    });
}
if (typeof document !== 'undefined')
    start();
