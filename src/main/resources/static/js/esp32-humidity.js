import { isArrayOf, isRecord, requestJson } from './api/client.js';
import { el, icon } from './core/dom.js';
const POLL_MS = 30_000;
const ROW_FIELDS = ['recordedAt', 'recordedAtText', 'sensorCode', 'measure', 'valueText', 'level', 'tone', 'icon', 'label'];
const TONES = new Set(['success', 'warning', 'danger', 'neutral', 'info', 'critical']);
const isRow = (value) => isRecord(value) && ROW_FIELDS.every((field) => typeof value[field] === 'string');
const isSilence = (value) => isRecord(value) &&
    typeof value['silent'] === 'boolean' &&
    (value['text'] === null || typeof value['text'] === 'string') &&
    (value['silent'] === false || typeof value['text'] === 'string');
export const isHumidityFeed = (value) => isRecord(value) &&
    isArrayOf(isRow)(value['readings']) &&
    (value['newestAt'] === null || typeof value['newestAt'] === 'string') &&
    isSilence(value['silence']);
const isNumberOrNull = (value) => value === null || typeof value === 'number';
const isPoint = (value) => isRecord(value) && typeof value['at'] === 'string' && typeof value['value'] === 'number';
const isSeries = (value) => isRecord(value) &&
    typeof value['sensorCode'] === 'string' &&
    typeof value['measure'] === 'string' &&
    isNumberOrNull(value['min']) &&
    isNumberOrNull(value['max']) &&
    isArrayOf(isPoint)(value['points']);
export const isHumidityDay = (value) => isRecord(value) && isArrayOf(isSeries)(value['series']) && typeof value['bucketMinutes'] === 'number';
/**
 * Puts every series on one shared time axis (the union of their buckets, oldest first). A sensor without a value in a
 * bucket gets null there, so the chart shows a gap instead of joining across the time the board was off.
 */
export function alignSeries(series) {
    const times = [...new Set(series.flatMap((one) => one.points.map((point) => point.at)))].sort();
    const values = series.map((one) => {
        const byTime = new Map(one.points.map((point) => [point.at, point.value]));
        return times.map((time) => byTime.get(time) ?? null);
    });
    return { times, values };
}
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
// One color per series, in the order the server sends them (by sensor code: the soil's AS-001 before the air's AS-007).
const SERIES_COLORS = ['--chart-1', '--chart-2', '--chart-3', '--chart-4'];
const timeFormat = new Intl.DateTimeFormat('es-CO', { hour: '2-digit', minute: '2-digit', hour12: false });
const numberFormat = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 1 });
/** "Suelo" or "Aire": the short name of a humidity measure for the range labels. */
export const shortMeasure = (measure) => (/suelo/i.test(measure) ? 'Suelo' : 'Aire');
const token = (name) => getComputedStyle(document.documentElement).getPropertyValue(name).trim();
// Each range's minimum and maximum as a dashed line in its series' color, labeled at the right edge.
const rangeLines = {
    id: 'humidityRanges',
    afterDatasetsDraw(chart, _args, options) {
        const { ctx, chartArea } = chart;
        const scale = chart.scales['y'];
        if (!scale)
            return;
        for (const { value, label, color, left } of options.lines) {
            const y = scale.getPixelForValue(value);
            if (y < chartArea.top || y > chartArea.bottom)
                continue;
            ctx.save();
            ctx.strokeStyle = color;
            ctx.setLineDash([5, 5]);
            ctx.beginPath();
            ctx.moveTo(chartArea.left, y);
            ctx.lineTo(chartArea.right, y);
            ctx.stroke();
            ctx.fillStyle = color;
            ctx.font = `11px ${token('--font-body')}`;
            ctx.textAlign = left ? 'left' : 'right';
            ctx.fillText(label, left ? chartArea.left + 4 : chartArea.right - 4, y - 5);
            ctx.restore();
        }
    },
};
class HumidityDayChart {
    box;
    canvas;
    message;
    url;
    chart;
    day;
    constructor(box, canvas, message, url) {
        this.box = box;
        this.canvas = canvas;
        this.message = message;
        this.url = url;
    }
    async load() {
        try {
            this.day = await requestJson(this.url, isHumidityDay, { timeoutMs: 15_000 });
            this.render();
        }
        catch {
            if (!this.day)
                this.show('No se pudo cargar la gráfica. Se intentará de nuevo con la próxima lectura.');
        }
    }
    render() {
        const day = this.day;
        if (!day)
            return;
        this.box.classList.remove('skeleton');
        if (typeof Chart === 'undefined') {
            this.show('La gráfica no está disponible en este navegador.');
            return;
        }
        const { times, values } = alignSeries(day.series);
        if (times.length === 0) {
            this.chart?.destroy();
            this.chart = undefined;
            this.show('El ESP32 no ha enviado lecturas de humedad en las últimas 24 horas.');
            return;
        }
        this.box.hidden = false;
        this.message.hidden = true;
        const colors = day.series.map((_, index) => token(SERIES_COLORS[index % SERIES_COLORS.length] ?? '--chart-1'));
        const lines = day.series.flatMap((one, index) => {
            const color = colors[index] ?? token('--chart-threshold');
            const short = shortMeasure(one.measure);
            const left = index % 2 === 1;
            const found = [];
            if (one.min !== null)
                found.push({ value: one.min, label: `${short} mín ${numberFormat.format(one.min)} %`, color, left });
            if (one.max !== null)
                found.push({ value: one.max, label: `${short} máx ${numberFormat.format(one.max)} %`, color, left });
            return found;
        });
        this.chart?.destroy();
        this.chart = new Chart(this.canvas, {
            type: 'line',
            data: {
                labels: times.map((time) => timeFormat.format(new Date(time))),
                datasets: day.series.map((one, index) => ({
                    label: `${one.measure} (${one.sensorCode})`,
                    data: values[index] ?? [],
                    borderColor: colors[index] ?? token('--chart-1'),
                    backgroundColor: colors[index] ?? token('--chart-1'),
                    borderWidth: 2,
                    pointRadius: 0,
                    pointHoverRadius: 4,
                    tension: 0.3,
                    spanGaps: false,
                })),
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: false,
                interaction: { mode: 'index', intersect: false },
                plugins: {
                    legend: { position: 'bottom', labels: { color: token('--text-muted'), boxWidth: 12, boxHeight: 2 } },
                    humidityRanges: { lines },
                    tooltip: {
                        callbacks: {
                            label: (context) => `${context.dataset.label ?? ''}: ${numberFormat.format(context.parsed.y ?? 0)} %`,
                        },
                    },
                },
                scales: {
                    x: { grid: { display: false }, ticks: { color: token('--text-muted'), maxTicksLimit: 8 } },
                    y: {
                        min: 0,
                        max: 100,
                        grid: { color: token('--chart-grid') },
                        ticks: { color: token('--text-muted') },
                        title: { display: true, text: '%', color: token('--text-muted') },
                    },
                },
            },
            plugins: [rangeLines],
        });
    }
    show(text) {
        this.box.hidden = true;
        this.message.textContent = text;
        this.message.hidden = false;
    }
}
class Esp32Humidity {
    section;
    url;
    newestAt;
    chart;
    timer;
    constructor(section, url, newestAt, chart) {
        this.section = section;
        this.url = url;
        this.newestAt = newestAt;
        this.chart = chart;
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
                void this.chart?.load();
                const last = lastValues(feed.readings);
                for (const row of this.section.querySelectorAll('[data-humidity-sensor]')) {
                    const value = last.get(row.dataset['humiditySensor'] ?? '');
                    const cell = row.querySelector('[data-humidity-last]');
                    if (value && cell)
                        cell.textContent = value;
                }
            }
            this.showSilence(feed.silence);
            const updated = this.section.querySelector('[data-humidity-updated]');
            if (updated)
                updated.textContent = `Actualizado a las ${new Date().toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit', hour12: false })}`;
        }
        catch {
            // The page keeps what it shows and the next poll tries again.
        }
        this.schedule();
    }
    showSilence(silence) {
        const banner = this.section.querySelector('[data-esp32-silence]');
        const text = banner?.querySelector('[data-esp32-silence-text]');
        if (!banner || !text)
            return;
        banner.hidden = !silence.silent;
        if (silence.silent && silence.text !== null && text.textContent !== silence.text)
            text.textContent = silence.text;
    }
}
function start() {
    const section = document.getElementById('sensor-esp32');
    const url = section?.dataset['humidityUrl'];
    if (!section || !url)
        return;
    const box = section.querySelector('[data-humidity-chart-box]');
    const canvas = section.querySelector('[data-humidity-chart]');
    const message = section.querySelector('[data-humidity-chart-message]');
    const dayUrl = section.dataset['humidityDayUrl'];
    const chart = box && canvas && message && dayUrl ? new HumidityDayChart(box, canvas, message, dayUrl) : undefined;
    void chart?.load();
    // The chart reads its colors from the tokens, so a theme change redraws it.
    document.addEventListener('agrosense:theme', () => chart?.render());
    const live = new Esp32Humidity(section, url, section.dataset['newestAt'] || null, chart);
    live.start();
    window.addEventListener('pagehide', () => live.stop());
    window.addEventListener('pageshow', (event) => {
        if (event.persisted)
            live.start();
    });
}
if (typeof document !== 'undefined')
    start();
