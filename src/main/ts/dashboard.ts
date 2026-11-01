import type { Chart as ChartInstance, Plugin } from 'chart.js';
import { ApiError, messageOf } from './api/client.js';
import { api } from './api/facade.js';
import { embeddedEstateMarkers, embeddedWaterUsage } from './api/ui.js';
import { confirmAction, showToast } from './app.js';
import { all, el, find, prefersReducedMotion, setButtonBusy } from './core/dom.js';
import { createBaseLayers, cropChip, estateGround, hasValidCoordinates, initMapLayers } from './map-shapes.js';
import { THEME_EVENT } from './theme.js';
import type { DailyWater, EstateMarker, ReadingSeries } from './types/api.js';
import type { ThresholdLine } from './types/globals.js';

const styles = getComputedStyle(document.documentElement);
const token = (name: string): string => styles.getPropertyValue(name).trim();
if (typeof Chart !== 'undefined') Chart.defaults.font.family = token('--font-body');
const numberFormat = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 });
const timeFormat = new Intl.DateTimeFormat('es-CO', { hour: '2-digit', minute: '2-digit' });
const dateTimeFormat = new Intl.DateTimeFormat('es-CO', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});
const dayFormat = new Intl.DateTimeFormat('es-CO', { weekday: 'short', day: 'numeric' });

function showMessage(boxId: string, messageId: string, text: string): void {
  const box = find(boxId, HTMLElement);
  const message = find(messageId, HTMLElement);
  if (box) box.hidden = true;
  if (!message) return;
  message.textContent = text;
  message.hidden = false;
}

const thresholdPlugin: Plugin<'line', { lines: ThresholdLine[] }> = {
  id: 'thresholds',
  afterDatasetsDraw(chart, _args, options) {
    const { ctx, chartArea } = chart;
    const scale = chart.scales['y'];
    if (!scale) return;
    options.lines.forEach(({ value, label }) => {
      const y = scale.getPixelForValue(value);
      if (y < chartArea.top || y > chartArea.bottom) return;
      ctx.save();
      ctx.strokeStyle = token('--chart-threshold');
      ctx.setLineDash([5, 5]);
      ctx.beginPath();
      ctx.moveTo(chartArea.left, y);
      ctx.lineTo(chartArea.right, y);
      ctx.stroke();
      ctx.fillStyle = token('--chart-threshold');
      ctx.font = `11px ${token('--font-body')}`;
      ctx.textAlign = 'right';
      ctx.fillText(label, chartArea.right - 4, y - 5);
      ctx.restore();
    });
  },
};

let readingsChart: ChartInstance<'line', number[], string> | undefined;
let readingsSeries: ReadingSeries | undefined;
let readingsRequest: AbortController | undefined;
let readingsDrawn = false;
let readingsHours = 24;

const WINDOW_TEXT: Record<number, string> = { 6: '6 horas', 24: '24 horas', 72: '3 días', 168: '7 días' };

function renderSource(series: ReadingSeries | null): void {
  const line = find('readingsSource', HTMLElement);
  if (!line) return;
  if (!series) {
    line.hidden = true;
    return;
  }
  const last = series.points[series.points.length - 1];
  const origin =
    series.source === 'api'
      ? 'Origen: servidor de AgroSense'
      : 'Origen: base de datos de respaldo (el servidor no respondió)';
  const lastText = last
    ? `última lectura conocida: ${dateTimeFormat.format(new Date(last.recordedAt))}`
    : 'sin lecturas en el periodo';
  line.dataset['source'] = series.source;
  line.textContent = `${origin} · ${lastText} · consultado a las ${timeFormat.format(new Date(series.loadedAt))}`;
  line.hidden = false;
}

function renderReadings(series: ReadingSeries): void {
  const canvas = find('readingsChart', HTMLCanvasElement);
  if (!canvas) return;
  const unit = series.unit ?? '';
  const values = series.points.map((point) => point.value);
  const lines: ThresholdLine[] = [];
  if (series.thresholdMin !== null) {
    lines.push({ value: series.thresholdMin, label: `Mín ${numberFormat.format(series.thresholdMin)}` });
  }
  if (series.thresholdMax !== null) {
    lines.push({ value: series.thresholdMax, label: `Máx ${numberFormat.format(series.thresholdMax)}` });
  }
  const bounds = [...values, ...lines.map((line) => line.value)];
  const lowest = Math.min(...bounds);
  const highest = Math.max(...bounds);
  const padding = (highest - lowest || 1) * 0.1;

  const labelFormat = series.windowHours > 24 ? dateTimeFormat : timeFormat;
  // The line draws itself only the first time the chart appears; later loads replace it in place.
  const animate = !readingsDrawn && !prefersReducedMotion();
  readingsDrawn = true;

  readingsChart?.destroy();
  readingsChart = new Chart(canvas, {
    type: 'line',
    data: {
      labels: series.points.map((point) => labelFormat.format(new Date(point.recordedAt))),
      datasets: [
        {
          label: unit === '' ? series.sensorCode : `${series.sensorCode} (${unit})`,
          data: values,
          borderColor: token('--chart-1'),
          backgroundColor: token('--chart-fill'),
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 5,
          tension: 0.3,
          fill: true,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      animation: animate ? { duration: 700, easing: 'easeOutCubic' } : false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: {
          display: true,
          position: 'bottom',
          labels: { color: token('--text-muted'), boxWidth: 12, boxHeight: 2 },
        },
        thresholds: { lines },
        tooltip: { callbacks: { label: (context) => `${numberFormat.format(context.parsed.y)} ${unit}` } },
      },
      scales: {
        x: { grid: { display: false }, ticks: { color: token('--text-muted'), maxTicksLimit: 8 } },
        y: {
          grid: { color: token('--chart-grid') },
          ticks: { color: token('--text-muted') },
          suggestedMin: lowest - padding,
          suggestedMax: highest + padding,
          title: { display: unit !== '', text: unit, color: token('--text-muted') },
        },
      },
    },
    plugins: [thresholdPlugin],
  });
}

function renderReadingsTable(series: ReadingSeries): void {
  const body = find('readingsTable', HTMLTableSectionElement);
  if (!body) return;
  const unit = series.unit ?? '';
  body.replaceChildren(
    ...series.points.map((point) => {
      const row = el('tr');
      row.append(
        el('td', '', dateTimeFormat.format(new Date(point.recordedAt))),
        el('td', 'numeric', `${numberFormat.format(point.value)} ${unit}`),
      );
      return row;
    }),
  );
}

async function loadReadings(sensorId: string): Promise<void> {
  const box = find('readingsChartBox', HTMLElement);
  const message = find('readingsMessage', HTMLElement);
  if (!box || !message) return;
  box.hidden = false;
  box.classList.add('skeleton');
  message.hidden = true;
  readingsRequest?.abort();
  readingsRequest = new AbortController();

  let series: ReadingSeries;
  try {
    series = await api.readings(sensorId, readingsHours, readingsRequest.signal);
  } catch (error) {
    if (error instanceof DOMException) return;
    renderSource(null);
    showMessage('readingsChartBox', 'readingsMessage', `No se pudieron cargar las lecturas. ${messageOf(error)}`);
    return;
  }
  box.classList.remove('skeleton');
  readingsChart?.destroy();
  readingsChart = undefined;
  readingsSeries = undefined;
  find('readingsTable', HTMLTableSectionElement)?.replaceChildren();
  renderSource(series);

  if (series.points.length === 0) {
    showMessage(
      'readingsChartBox',
      'readingsMessage',
      `Sin datos: este sensor no tiene lecturas en las últimas ${WINDOW_TEXT[series.windowHours] ?? `${series.windowHours} horas`}.`,
    );
    return;
  }
  readingsSeries = series;
  renderReadings(series);
  renderReadingsTable(series);
}

function initReadings(): void {
  const select = find('chartSensor', HTMLSelectElement);
  if (!select) return;
  select.addEventListener('change', () => void loadReadings(select.value));
  const windows = all('#readingsWindow [data-hours]', HTMLButtonElement);
  windows.forEach((button) =>
    button.addEventListener('click', () => {
      readingsHours = Number(button.dataset['hours']) || 24;
      windows.forEach((other) => other.setAttribute('aria-pressed', String(other === button)));
      void loadReadings(select.value);
    }),
  );
  void loadReadings(select.value);
}

let waterChart: ChartInstance<'bar', number[], string> | undefined;
let waterDays: DailyWater[] | undefined;

function renderWater(days: DailyWater[]): void {
  const canvas = find('waterChart', HTMLCanvasElement);
  if (!canvas) return;
  waterChart?.destroy();
  waterChart = new Chart(canvas, {
    type: 'bar',
    data: {
      labels: days.map((day) => dayFormat.format(new Date(`${day.date}T12:00:00`))),
      datasets: [
        {
          data: days.map((day) => day.liters),
          backgroundColor: token('--chart-1'),
          borderRadius: 4,
          maxBarThickness: 28,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      animation: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: { callbacks: { label: (context) => `${numberFormat.format(context.parsed.y)} L` } },
      },
      scales: {
        x: { grid: { display: false }, ticks: { color: token('--text-muted'), maxTicksLimit: 8 } },
        y: { beginAtZero: true, grid: { color: token('--chart-grid') }, ticks: { color: token('--text-muted') } },
      },
    },
  });
}

async function initWater(): Promise<void> {
  const box = find('waterChartBox', HTMLElement);
  if (!box) return;
  let days: DailyWater[];
  try {
    days = embeddedWaterUsage(box.dataset['days']) ?? (await api.waterUsage());
  } catch (error) {
    showMessage('waterChartBox', 'waterMessage', `No se pudo cargar el consumo de agua. ${messageOf(error)}`);
    return;
  }
  box.classList.remove('skeleton');
  if (days.every((day) => day.liters === 0)) {
    showMessage('waterChartBox', 'waterMessage', 'No hay riegos con litros registrados en los últimos 7 días.');
    return;
  }
  waterDays = days;
  renderWater(days);
}

const LEAF_PATH = 'M24 3C34.5 11 39 20.5 39 27.5 39 36.6 32.3 44 24 44S9 36.6 9 27.5C9 20.5 13.5 11 24 3Z';
const VEIN_PATH = 'M24 40V12M24 33l7.5-7.5M24 27l-7.5-7.5M24 21l5-5';
const FOCUS_ZOOM = 13;

function pinHtml(estate: EstateMarker): string {
  const badge = estate.openAlerts > 0 ? `<span class="map-pin-badge">${Math.trunc(estate.openAlerts)}</span>` : '';
  return (
    `<svg viewBox="0 0 48 48" aria-hidden="true" focusable="false"><path class="map-pin-leaf" d="${LEAF_PATH}"/>` +
    `<path class="map-pin-vein" d="${VEIN_PATH}"/></svg>${badge}`
  );
}

const plural = (count: number, one: string, many: string): string => (count === 1 ? `1 ${one}` : `${count} ${many}`);

function estateCard(estate: EstateMarker): HTMLElement {
  const card = el('div', 'map-card');
  const meta = [estate.location ?? 'Sin ubicación'];
  if (estate.areaHa !== null) meta.push(`${numberFormat.format(estate.areaHa)} ha`);
  card.append(el('h3', '', estate.name), el('p', 'map-card-meta', meta.join(' · ')));

  if (estate.crops.length > 0) {
    const crops = el('ul', 'chips');
    estate.crops.forEach((crop) => crops.append(cropChip(crop)));
    card.append(crops);
  } else {
    card.append(el('p', 'map-card-meta', 'Sin cultivos activos'));
  }

  const stats = el('p', 'map-card-stats');
  stats.append(el('span', '', plural(estate.sensorCount, 'sensor', 'sensores')));
  stats.append(
    estate.openAlerts > 0
      ? el('strong', 'map-card-alert', plural(estate.openAlerts, 'alerta sin atender', 'alertas sin atender'))
      : el('span', '', 'Sin alertas'),
  );
  const link = el('a', 'link map-card-link', 'Ver parcela');
  link.href = `/parcelas/${estate.id}`;
  card.append(stats, link);
  return card;
}

function showMapFallback(notice?: string): void {
  find('estateMap', HTMLElement)?.setAttribute('hidden', '');
  find('estateMapFallback', HTMLElement)?.removeAttribute('hidden');
  find('mapFit', HTMLButtonElement)?.setAttribute('hidden', '');
  find('mapExpand', HTMLButtonElement)?.setAttribute('hidden', '');
  find('mapLayers', HTMLElement)?.setAttribute('hidden', '');
  document.querySelector('.map-hint')?.setAttribute('hidden', '');
  const label = find('estateMapFallbackNotice', HTMLElement);
  if (notice && label) label.textContent = notice;
}

async function initMap(): Promise<void> {
  const container = find('estateMap', HTMLElement);
  if (!container) return;
  if (typeof L === 'undefined') {
    showMapFallback('No se pudo cargar el mapa.');
    return;
  }
  const leaflet = L;
  try {
    const markers = embeddedEstateMarkers(container.dataset['markers']) ?? (await api.estateMarkers());
    const estates = markers.filter(hasValidCoordinates);
    if (estates.length === 0) {
      showMapFallback('Sin coordenadas registradas');
      return;
    }
    container.classList.remove('skeleton');

    const map = leaflet.map(container, { scrollWheelZoom: false, zoomControl: false });
    leaflet.control.zoom({ position: 'bottomright', zoomInTitle: 'Acercar', zoomOutTitle: 'Alejar' }).addTo(map);
    leaflet.control.scale({ imperial: false, position: 'bottomleft' }).addTo(map);
    initMapLayers(map, createBaseLayers(leaflet));

    const items = new Map(
      all('[data-estate-tile]', HTMLButtonElement).map((item) => [Number(item.dataset['estateId']), item] as const),
    );
    const select = (id: number | null, reveal: boolean): void => {
      items.forEach((item, itemId) => {
        if (item.hasAttribute('aria-pressed')) item.setAttribute('aria-pressed', String(itemId === id));
      });
      if (id !== null && reveal) items.get(id)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    };

    const pins = new Map(
      estates.map((estate) => {
        const marker = leaflet
          .marker([estate.latitude, estate.longitude], {
            icon: leaflet.divIcon({
              className: estate.openAlerts > 0 ? 'map-pin map-pin--alert' : 'map-pin',
              html: pinHtml(estate),
              iconSize: [40, 40],
              iconAnchor: [20, 38],
              popupAnchor: [0, -34],
            }),
            title: estate.name,
            alt: estate.name,
            keyboard: true,
          })
          .addTo(map);
        marker.bindPopup(estateCard(estate), {
          closeButton: false,
          minWidth: 230,
          maxWidth: 280,
          autoPanPadding: [24, 24],
        });
        marker.on('popupopen', () => select(estate.id, true));
        marker.on('popupclose', () => select(null, false));
        return [estate.id, marker] as const;
      }),
    );
    const grounds = new Map(
      estates.map((estate) => {
        const shapes = estateGround(leaflet, estate);
        shapes.forEach((shape) => shape.addTo(map));
        return [estate.id, shapes] as const;
      }),
    );
    const everything = leaflet.featureGroup([...pins.values()]).getBounds().pad(0.35);
    const fitAll = (): void => {
      map.closePopup();
      map.flyToBounds(everything, { maxZoom: 12, duration: prefersReducedMotion() ? 0 : 0.8 });
    };
    map.fitBounds(everything, { maxZoom: 12 });

    items.forEach((item, id) => {
      const marker = pins.get(id);
      if (!marker) return;
      item.addEventListener('click', () => {
        const duration = prefersReducedMotion() ? 0 : 0.8;
        const shapes = grounds.get(id) ?? [];
        map.once('moveend', () => marker.openPopup());
        if (shapes.length > 0) {
          map.flyToBounds(leaflet.featureGroup(shapes).getBounds().pad(0.25), { maxZoom: 18, duration });
        } else {
          map.flyTo(marker.getLatLng(), Math.max(map.getZoom(), FOCUS_ZOOM), { duration });
        }
      });
    });
    find('mapFit', HTMLButtonElement)?.addEventListener('click', fitAll);

    const hint = document.querySelector<HTMLElement>('.map-hint');
    map.on('click', () => {
      map.scrollWheelZoom.enable();
      hint?.setAttribute('hidden', '');
    });
    container.addEventListener('mouseleave', () => map.scrollWheelZoom.disable());

    initMapExpand(() => {
      map.invalidateSize();
      map.fitBounds(everything, { maxZoom: 12 });
    });
  } catch {
    showMapFallback('No se pudo cargar el mapa.');
  }
}

function initMapExpand(onResize: () => void): void {
  const section = find('mapSection', HTMLElement);
  const button = find('mapExpand', HTMLButtonElement);
  const label = button?.querySelector('span');
  if (!section || !button || !label) return;
  const set = (expanded: boolean): void => {
    section.classList.toggle('is-expanded', expanded);
    document.documentElement.classList.toggle('has-expanded-map', expanded);
    button.setAttribute('aria-pressed', String(expanded));
    label.textContent = expanded ? 'Reducir' : 'Ampliar';
    requestAnimationFrame(onResize);
  };
  button.addEventListener('click', () => set(!section.classList.contains('is-expanded')));
  document.addEventListener(
    'keydown',
    (event) => {
      if (event.key === 'Escape' && section.classList.contains('is-expanded') && !document.querySelector('dialog[open]')) {
        set(false);
        button.focus();
      }
    },
    { capture: true },
  );
}

function initQuickIrrigation(): void {
  const form = find('quickIrrigation', HTMLFormElement);
  if (!form) return;
  const crop = form.elements.namedItem('cropId');
  const duration = form.elements.namedItem('durationMin');
  const water = form.elements.namedItem('waterLiters');
  const button = form.querySelector<HTMLButtonElement>('button[type="submit"]');
  if (
    !(crop instanceof HTMLSelectElement) ||
    !(duration instanceof HTMLInputElement) ||
    !(water instanceof HTMLInputElement) ||
    !button
  ) {
    return;
  }

  const submit = async (): Promise<void> => {
    if (button.disabled || !form.reportValidity()) return;
    const cropLabel = (crop.selectedOptions[0]?.textContent ?? '').trim();
    const minutes = Number(duration.value);
    if (
      !(await confirmAction(
        form.dataset['relay'] === 'true'
          ? `¿Regar ${minutes} minutos en ${cropLabel}? La orden llega al dispositivo en su próximo envío y la bomba funciona hasta 5 minutos seguidos.`
          : `¿Registrar un riego de ${minutes} minutos en ${cropLabel}? Modo simulado: AgroSense lo anota en el historial; no enciende ninguna bomba.`,
      ))
    ) {
      return;
    }

    setButtonBusy(button, true);
    try {
      await api.startIrrigation({ cropId: Number(crop.value), durationMin: minutes, waterLiters: Number(water.value) });
      window.location.reload();
    } catch (error) {
      showToast(error instanceof ApiError ? error.message : 'No se pudo registrar el riego.', 'error');
      setButtonBusy(button, false);
    }
  };
  form.addEventListener('submit', (event) => {
    event.preventDefault();
    void submit();
  });
}

function applyTheme(): void {
  if (readingsSeries) renderReadings(readingsSeries);
  if (waterDays) renderWater(waterDays);
}

document.addEventListener(THEME_EVENT, applyTheme);

initReadings();
void initWater();
void initMap();
initQuickIrrigation();
