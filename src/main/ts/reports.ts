import type { Chart as ChartInstance } from 'chart.js';
import { isArrayOf, isRecord } from './api/client.js';
import { find } from './core/dom.js';
import { THEME_EVENT } from './theme.js';

interface CropWater {
  label: string;
  estate: string;
  liters: number;
  tone: number;
}

interface EstateWater {
  label: string;
  liters: number;
  litersPerHa: number | null;
}

interface TypeWater {
  type: string;
  liters: number;
  irrigations: number;
}

interface DayWater {
  date: string;
  liters: number;
}

interface ReportData {
  byCrop: CropWater[];
  byEstate: EstateWater[];
  byType: TypeWater[];
  daily: DayWater[];
}

const isNumber = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value);
const isString = (value: unknown): value is string => typeof value === 'string';
const isCropWater = (value: unknown): value is CropWater =>
  isRecord(value) && isString(value['label']) && isString(value['estate']) && isNumber(value['liters']) && isNumber(value['tone']);
const isEstateWater = (value: unknown): value is EstateWater =>
  isRecord(value) &&
  isString(value['label']) &&
  isNumber(value['liters']) &&
  (value['litersPerHa'] === null || isNumber(value['litersPerHa']));
const isTypeWater = (value: unknown): value is TypeWater =>
  isRecord(value) && isString(value['type']) && isNumber(value['liters']) && isNumber(value['irrigations']);
const isDayWater = (value: unknown): value is DayWater =>
  isRecord(value) && isString(value['date']) && isNumber(value['liters']);
const isReportData = (value: unknown): value is ReportData =>
  isRecord(value) &&
  isArrayOf(isCropWater)(value['byCrop']) &&
  isArrayOf(isEstateWater)(value['byEstate']) &&
  isArrayOf(isTypeWater)(value['byType']) &&
  isArrayOf(isDayWater)(value['daily']);

const styles = getComputedStyle(document.documentElement);
const token = (name: string): string => styles.getPropertyValue(name).trim();
if (typeof Chart !== 'undefined') Chart.defaults.font.family = token('--font-body');
const numberFormat = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 1 });
const dayFormat = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short' });
const TYPE_LABELS: Readonly<Record<string, string>> = { MANUAL: 'Manual', AUTOMATIC: 'Automático' };

let charts: Array<ChartInstance<'bar'> | ChartInstance<'doughnut'>> = [];

function explain(boxId: string, messageId: string, text: string): void {
  const box = find(boxId, HTMLElement);
  const message = find(messageId, HTMLElement);
  if (box) box.hidden = true;
  if (!message) return;
  message.textContent = text;
  message.hidden = false;
}

function bars(canvasId: string, boxId: string, labels: string[], values: number[], unit: string, colours?: string[]): void {
  const canvas = find(canvasId, HTMLCanvasElement);
  find(boxId, HTMLElement)?.classList.remove('skeleton');
  if (!canvas) return;
  charts.push(
    new Chart(canvas, {
      type: 'bar',
      data: {
        labels,
        datasets: [{ data: values, backgroundColor: colours ?? token('--chart-1'), borderRadius: 4, maxBarThickness: 44 }],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        plugins: {
          legend: { display: false },
          tooltip: { callbacks: { label: (context) => `${numberFormat.format(context.parsed.y)} ${unit}` } },
        },
        scales: {
          x: { grid: { display: false }, ticks: { color: token('--text-muted'), maxTicksLimit: 12 } },
          y: { beginAtZero: true, grid: { color: token('--chart-grid') }, ticks: { color: token('--text-muted') } },
        },
      },
    }),
  );
}

function split(types: TypeWater[]): void {
  const canvas = find('typeChart', HTMLCanvasElement);
  find('typeChartBox', HTMLElement)?.classList.remove('skeleton');
  if (!canvas) return;
  charts.push(
    new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels: types.map((type) => `${TYPE_LABELS[type.type] ?? type.type} · ${type.irrigations} ${type.irrigations === 1 ? 'riego' : 'riegos'}`),
        datasets: [
          {
            data: types.map((type) => type.liters),
            backgroundColor: types.map((type) => token(type.type === 'MANUAL' ? '--chart-1' : '--chart-2')),
            borderColor: token('--surface'),
            borderWidth: 3,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        cutout: '62%',
        plugins: {
          legend: { position: 'bottom', labels: { color: token('--text'), usePointStyle: true, padding: 16 } },
          tooltip: { callbacks: { label: (context) => `${numberFormat.format(context.parsed)} L` } },
        },
      },
    }),
  );
}

function render(data: ReportData): void {
  charts.forEach((chart) => chart.destroy());
  charts = [];
  if (data.daily.every((day) => day.liters === 0)) {
    explain('dailyChartBox', 'dailyMessage', 'No hay riegos con litros registrados en estos días.');
  } else {
    bars(
      'dailyChart',
      'dailyChartBox',
      data.daily.map((day) => dayFormat.format(new Date(`${day.date}T12:00:00`))),
      data.daily.map((day) => day.liters),
      'L',
    );
  }
  bars(
    'cropChart',
    'cropChartBox',
    data.byCrop.map((crop) => (data.byEstate.length > 1 ? [crop.label, crop.estate].join(' · ') : crop.label)),
    data.byCrop.map((crop) => crop.liters),
    'L',
    data.byCrop.map((crop) => token(`--crop-${crop.tone}`)),
  );
  const measured = data.byEstate.flatMap((estate) =>
    estate.litersPerHa === null ? [] : [{ label: estate.label, litersPerHa: estate.litersPerHa }],
  );
  if (measured.length === 0) {
    explain('estateChartBox', 'estateMessage', 'Registra el área de tus parcelas para comparar los litros por hectárea.');
  } else {
    bars('estateChart', 'estateChartBox', measured.map((estate) => estate.label), measured.map((estate) => estate.litersPerHa), 'L/ha');
  }
  split(data.byType);
}

function init(): void {
  const container = find('reportCharts', HTMLElement);
  if (!container) return;
  let data: unknown = null;
  try {
    data = JSON.parse(container.dataset['charts'] ?? 'null') as unknown;
  } catch {
  }
  if (!isReportData(data)) {
    container.hidden = true;
    return;
  }
  const report = data;
  render(report);
  document.addEventListener(THEME_EVENT, () => render(report));
}

init();
