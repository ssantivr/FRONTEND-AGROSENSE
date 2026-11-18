// The ESP32's humidity list in the Sensors page asks for its newest readings every 30 seconds. Readings that the backend
// reads over TCP never pass through the event service, so the live stream (events/live.ts) does not carry them; this
// keeps an open page current anyway, like crop-live.ts does for the crop page.
import type { Chart as ChartInstance, Plugin } from 'chart.js';
import { isArrayOf, isRecord, requestJson } from './api/client.js';
import { el, icon } from './core/dom.js';

export interface HumidityRow {
  recordedAt: string;
  recordedAtText: string;
  sensorCode: string;
  measure: string;
  valueText: string;
  level: string;
  tone: string;
  icon: string;
  label: string;
  advice: string;
}

/** Worded by the server in its own clock, so the browser's time zone never changes it; text is null while it talks. */
export interface Esp32Silence {
  silent: boolean;
  text: string | null;
}

export interface HumidityFeed {
  readings: HumidityRow[];
  newestAt: string | null;
  silence: Esp32Silence;
}

export interface HumidityPoint {
  at: string;
  value: number;
}

export interface HumiditySeries {
  sensorCode: string;
  measure: string;
  min: number | null;
  max: number | null;
  points: HumidityPoint[];
}

export interface HumidityDay {
  series: HumiditySeries[];
  bucketMinutes: number;
}

const POLL_MS = 30_000;
const ROW_FIELDS = ['recordedAt', 'recordedAtText', 'sensorCode', 'measure', 'valueText', 'level', 'tone', 'icon', 'label', 'advice'];
const TONES = new Set(['success', 'warning', 'danger', 'neutral', 'info', 'critical']);

const isRow = (value: unknown): value is HumidityRow =>
  isRecord(value) && ROW_FIELDS.every((field) => typeof value[field] === 'string');

const isSilence = (value: unknown): value is Esp32Silence =>
  isRecord(value) &&
  typeof value['silent'] === 'boolean' &&
  (value['text'] === null || typeof value['text'] === 'string') &&
  (value['silent'] === false || typeof value['text'] === 'string');

export const isHumidityFeed = (value: unknown): value is HumidityFeed =>
  isRecord(value) &&
  isArrayOf(isRow)(value['readings']) &&
  (value['newestAt'] === null || typeof value['newestAt'] === 'string') &&
  isSilence(value['silence']);

const isNumberOrNull = (value: unknown): boolean => value === null || typeof value === 'number';

const isPoint = (value: unknown): value is HumidityPoint =>
  isRecord(value) && typeof value['at'] === 'string' && typeof value['value'] === 'number';

const isSeries = (value: unknown): value is HumiditySeries =>
  isRecord(value) &&
  typeof value['sensorCode'] === 'string' &&
  typeof value['measure'] === 'string' &&
  isNumberOrNull(value['min']) &&
  isNumberOrNull(value['max']) &&
  isArrayOf(isPoint)(value['points']);

export const isHumidityDay = (value: unknown): value is HumidityDay =>
  isRecord(value) && isArrayOf(isSeries)(value['series']) && typeof value['bucketMinutes'] === 'number';

/**
 * Puts every series on one shared time axis (the union of their buckets, oldest first). A sensor without a value in a
 * bucket gets null there, so the chart shows a gap instead of joining across the time the board was off.
 */
export function alignSeries(series: readonly HumiditySeries[]): { times: string[]; values: (number | null)[][] } {
  const times = [...new Set(series.flatMap((one) => one.points.map((point) => point.at)))].sort();
  const values = series.map((one) => {
    const byTime = new Map(one.points.map((point) => [point.at, point.value]));
    return times.map((time) => byTime.get(time) ?? null);
  });
  return { times, values };
}

/** Repaint only when the server has a reading newer than the one the page shows. */
export function isNewer(shown: string | null, latest: string | null): boolean {
  if (!latest) return false;
  const latestMs = new Date(latest).getTime();
  if (Number.isNaN(latestMs)) return false;
  if (!shown) return true;
  const shownMs = new Date(shown).getTime();
  return Number.isNaN(shownMs) || latestMs > shownMs;
}

/** The newest reading of each sensor in a feed that is newest first: what each humidity card shows. */
export function newestBySensor(readings: readonly HumidityRow[]): Map<string, HumidityRow> {
  const newest = new Map<string, HumidityRow>();
  for (const reading of readings) if (!newest.has(reading.sensorCode)) newest.set(reading.sensorCode, reading);
  return newest;
}

/** "32,1 %" becomes "32,1": the card prints the unit apart, in smaller type. */
export const numberOf = (valueText: string): string => valueText.replace(/\s*%$/, '');

/** "15 nov, 16:43" becomes "16:43": the list only says the time, like the page. */
export const timeOf = (recordedAtText: string): string => recordedAtText.split(', ').pop() ?? recordedAtText;

function pillOf(reading: HumidityRow): HTMLElement {
  // The tone comes from the server; anything unexpected falls back to a neutral pill instead of an unknown class.
  const pill = el('span', `status-pill status-pill--${TONES.has(reading.tone) ? reading.tone : 'neutral'}`);
  pill.append(icon(reading.icon), el('span', '', reading.label));
  return pill;
}

function rowOf(reading: HumidityRow): HTMLLIElement {
  const row = el('li');
  row.dataset['level'] = reading.level;
  const time = el('time', '', timeOf(reading.recordedAtText));
  time.dateTime = reading.recordedAt;
  row.append(time, el('span', 'humidity-log-what', reading.measure), el('strong', 'humidity-log-value', reading.valueText),
    pillOf(reading));
  return row;
}

function paintCard(card: HTMLElement, reading: HumidityRow): void {
  card.dataset['level'] = reading.level;
  const value = card.querySelector<HTMLElement>('[data-humidity-last]');
  if (value) value.textContent = numberOf(reading.valueText);
  card.querySelector<HTMLElement>('[data-humidity-pill]')?.replaceChildren(pillOf(reading));
  const advice = card.querySelector<HTMLElement>('[data-humidity-advice]');
  if (advice) advice.textContent = reading.advice;
  const time = card.querySelector<HTMLElement>('[data-humidity-time]');
  if (time) time.textContent = `Última lectura a las ${timeOf(reading.recordedAtText)}`;
}

// One color per series, in the order the server sends them (by sensor code: the soil's AS-001 before the air's AS-007).
const SERIES_COLORS = ['--chart-1', '--chart-2', '--chart-3', '--chart-4'];
const timeFormat = new Intl.DateTimeFormat('es-CO', { hour: '2-digit', minute: '2-digit', hour12: false });
const numberFormat = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 1 });

interface RangeLine {
  value: number;
  label: string;
  color: string;
  /** Labels of neighbouring series go on opposite edges so close ranges (80 % and 85 %) do not overlap. */
  left: boolean;
}

/** "Suelo" or "Aire": the short name of a humidity measure for the range labels. */
export const shortMeasure = (measure: string): string => (/suelo/i.test(measure) ? 'Suelo' : 'Aire');

const token = (name: string): string => getComputedStyle(document.documentElement).getPropertyValue(name).trim();

// Each range's minimum and maximum as a dashed line in its series' color, labeled at the right edge.
const rangeLines: Plugin<'line', { lines: RangeLine[] }> = {
  id: 'humidityRanges',
  afterDatasetsDraw(chart, _args, options) {
    const { ctx, chartArea } = chart;
    const scale = chart.scales['y'];
    if (!scale) return;
    for (const { value, label, color, left } of options.lines) {
      const y = scale.getPixelForValue(value);
      if (y < chartArea.top || y > chartArea.bottom) continue;
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
  private chart: ChartInstance<'line', (number | null)[], string> | undefined;
  private day: HumidityDay | undefined;

  constructor(
    private readonly box: HTMLElement,
    private readonly canvas: HTMLCanvasElement,
    private readonly message: HTMLElement,
    private readonly url: string,
  ) {}

  async load(): Promise<void> {
    try {
      this.day = await requestJson(this.url, isHumidityDay, { timeoutMs: 15_000 });
      this.render();
    } catch {
      if (!this.day) this.show('No se pudo cargar la gráfica. Se intentará de nuevo con la próxima lectura.');
    }
  }

  render(): void {
    const day = this.day;
    if (!day) return;
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
    const lines: RangeLine[] = day.series.flatMap((one, index) => {
      const color = colors[index] ?? token('--chart-threshold');
      const short = shortMeasure(one.measure);
      const left = index % 2 === 1;
      const found: RangeLine[] = [];
      if (one.min !== null) found.push({ value: one.min, label: `${short} mín ${numberFormat.format(one.min)} %`, color, left });
      if (one.max !== null) found.push({ value: one.max, label: `${short} máx ${numberFormat.format(one.max)} %`, color, left });
      return found;
    });
    this.chart?.destroy();
    this.chart = new Chart(this.canvas, {
      type: 'line',
      data: {
        labels: times.map((time) => timeFormat.format(new Date(time))),
        datasets: day.series.map((one, index) => ({
          label: one.measure,
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

  private show(text: string): void {
    this.box.hidden = true;
    this.message.textContent = text;
    this.message.hidden = false;
  }
}

class Esp32Humidity {
  private timer: number | undefined;

  constructor(
    private readonly section: HTMLElement,
    private readonly url: string,
    private newestAt: string | null,
    private readonly chart: HumidityDayChart | undefined,
  ) {}

  start(): void {
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
      const feed = await requestJson(this.url, isHumidityFeed, { timeoutMs: 15_000 });
      if (isNewer(this.newestAt, feed.newestAt)) {
        this.section.querySelector<HTMLOListElement>('[data-humidity-list]')?.replaceChildren(...feed.readings.map(rowOf));
        this.newestAt = feed.newestAt;
        void this.chart?.load();
        const newest = newestBySensor(feed.readings);
        for (const card of this.section.querySelectorAll<HTMLElement>('[data-humidity-card]')) {
          const reading = newest.get(card.dataset['humidityCard'] ?? '');
          if (reading) paintCard(card, reading);
        }
      }
      this.showSilence(feed.silence);
      const updated = this.section.querySelector<HTMLElement>('[data-humidity-updated]');
      if (updated) updated.textContent = `Actualizado a las ${new Date().toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit', hour12: false })}`;
    } catch {
      // The page keeps what it shows and the next poll tries again.
    }
    this.schedule();
  }

  private showSilence(silence: Esp32Silence): void {
    const banner = this.section.querySelector<HTMLElement>('[data-esp32-silence]');
    const text = banner?.querySelector<HTMLElement>('[data-esp32-silence-text]');
    if (!banner || !text) return;
    banner.hidden = !silence.silent;
    if (silence.silent && silence.text !== null && text.textContent !== silence.text) text.textContent = silence.text;
  }
}

// The irrigation page has only the day's chart; it is redrawn every few minutes, the time a new bucket takes to fill.
const CHART_ONLY_REFRESH_MS = 5 * 60_000;

function chartIn(host: HTMLElement): HumidityDayChart | undefined {
  const box = host.querySelector<HTMLElement>('[data-humidity-chart-box]');
  const canvas = host.querySelector<HTMLCanvasElement>('[data-humidity-chart]');
  const message = host.querySelector<HTMLElement>('[data-humidity-chart-message]');
  const dayUrl = host.dataset['humidityDayUrl'];
  return box && canvas && message && dayUrl ? new HumidityDayChart(box, canvas, message, dayUrl) : undefined;
}

function start(): void {
  const host = document.querySelector<HTMLElement>('[data-humidity-day-url]');
  const chart = host ? chartIn(host) : undefined;
  void chart?.load();
  // The chart reads its colors from the tokens, so a theme change redraws it.
  document.addEventListener('agrosense:theme', () => chart?.render());

  const section = document.getElementById('sensor-esp32');
  const url = section?.dataset['humidityUrl'];
  if (section && url) {
    const live = new Esp32Humidity(section, url, section.dataset['newestAt'] || null, chart);
    live.start();
    window.addEventListener('pagehide', () => live.stop());
    window.addEventListener('pageshow', (event) => {
      if (event.persisted) live.start();
    });
  } else if (chart) {
    const timer = window.setInterval(() => void chart.load(), CHART_ONLY_REFRESH_MS);
    window.addEventListener('pagehide', () => window.clearInterval(timer), { once: true });
  }
}

if (typeof document !== 'undefined') start();
