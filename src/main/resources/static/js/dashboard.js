// Dashboard widgets: readings chart, water chart, estate map and the quick irrigation form.
// Chart.js and Leaflet are loaded as classic scripts by the layout and expose the globals Chart and L.
import { confirmAction, csrfHeaders, showToast } from './app.js';

const styles = getComputedStyle(document.documentElement);
const token = (name) => styles.getPropertyValue(name).trim();
const numberFormat = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 });
const timeFormat = new Intl.DateTimeFormat('es-CO', { hour: '2-digit', minute: '2-digit' });
const dateTimeFormat = new Intl.DateTimeFormat('es-CO', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});
const dayFormat = new Intl.DateTimeFormat('es-CO', { weekday: 'short', day: 'numeric' });

async function getJson(url) {
  const response = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!response.ok) throw new Error(`Request failed with status ${response.status}`);
  return response.json();
}

function showMessage(boxId, messageId, text) {
  document.getElementById(boxId).hidden = true;
  const message = document.getElementById(messageId);
  message.textContent = text;
  message.hidden = false;
}

function baseOptions() {
  return {
    responsive: true,
    maintainAspectRatio: false,
    // Draw the final state at once: entry animations stall in background tabs and add nothing here.
    animation: false,
    interaction: { mode: 'index', intersect: false },
    plugins: { legend: { display: false } },
    scales: {
      x: { grid: { display: false }, ticks: { color: token('--text-muted'), maxTicksLimit: 8 } },
      y: { grid: { color: token('--chart-grid') }, ticks: { color: token('--text-muted') } },
    },
  };
}

/** Draws the crop's acceptable range as dashed lines with a label at the right edge. */
const thresholdPlugin = {
  id: 'thresholds',
  afterDatasetsDraw(chart, _args, options) {
    const { ctx, chartArea, scales } = chart;
    (options.lines ?? []).forEach(({ value, label }) => {
      const y = scales.y.getPixelForValue(value);
      if (y < chartArea.top || y > chartArea.bottom) return;
      ctx.save();
      ctx.strokeStyle = token('--chart-threshold');
      ctx.setLineDash([5, 5]);
      ctx.beginPath();
      ctx.moveTo(chartArea.left, y);
      ctx.lineTo(chartArea.right, y);
      ctx.stroke();
      ctx.fillStyle = token('--chart-threshold');
      ctx.font = '11px Inter, system-ui, sans-serif';
      ctx.textAlign = 'right';
      ctx.fillText(label, chartArea.right - 4, y - 5);
      ctx.restore();
    });
  },
};

let readingsChart;

async function loadReadings(sensorId) {
  const box = document.getElementById('readingsChartBox');
  const message = document.getElementById('readingsMessage');
  const tableBody = document.getElementById('readingsTable');
  box.hidden = false;
  box.classList.add('skeleton');
  message.hidden = true;

  let series;
  try {
    series = await getJson(`/api/ui/sensors/${encodeURIComponent(sensorId)}/readings`);
  } catch {
    showMessage('readingsChartBox', 'readingsMessage', 'No se pudieron cargar las lecturas.');
    return;
  }
  box.classList.remove('skeleton');
  readingsChart?.destroy();
  tableBody.replaceChildren();

  if (series.points.length === 0) {
    showMessage('readingsChartBox', 'readingsMessage', 'Este sensor no tiene lecturas en las últimas 24 horas.');
    return;
  }

  const unit = series.unit ?? '';
  const values = series.points.map((point) => Number(point.value));
  const lines = [];
  if (series.thresholdMin !== null) {
    lines.push({ value: Number(series.thresholdMin), label: `Mín ${numberFormat.format(series.thresholdMin)}` });
  }
  if (series.thresholdMax !== null) {
    lines.push({ value: Number(series.thresholdMax), label: `Máx ${numberFormat.format(series.thresholdMax)}` });
  }
  const bounds = [...values, ...lines.map((line) => line.value)];
  const padding = (Math.max(...bounds) - Math.min(...bounds) || 1) * 0.1;

  const options = baseOptions();
  options.scales.y.suggestedMin = Math.min(...bounds) - padding;
  options.scales.y.suggestedMax = Math.max(...bounds) + padding;
  options.scales.y.title = { display: unit !== '', text: unit, color: token('--text-muted') };
  options.plugins.thresholds = { lines };
  options.plugins.tooltip = {
    callbacks: { label: (context) => `${numberFormat.format(context.parsed.y)} ${unit}` },
  };

  readingsChart = new Chart(document.getElementById('readingsChart'), {
    type: 'line',
    data: {
      labels: series.points.map((point) => timeFormat.format(new Date(point.recordedAt))),
      datasets: [
        {
          data: values,
          borderColor: token('--chart-series'),
          backgroundColor: token('--chart-series-soft'),
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 5,
          tension: 0.3,
          fill: true,
        },
      ],
    },
    options,
    plugins: [thresholdPlugin],
  });

  series.points.forEach((point) => {
    const row = document.createElement('tr');
    const time = document.createElement('td');
    time.textContent = dateTimeFormat.format(new Date(point.recordedAt));
    const value = document.createElement('td');
    value.className = 'numeric';
    value.textContent = `${numberFormat.format(point.value)} ${unit}`;
    row.append(time, value);
    tableBody.append(row);
  });
}

function initReadings() {
  const select = document.getElementById('chartSensor');
  if (!select) return;
  select.addEventListener('change', () => loadReadings(select.value));
  loadReadings(select.value);
}

async function initWater() {
  const box = document.getElementById('waterChartBox');
  if (!box) return;
  let days;
  try {
    days = await getJson('/api/ui/water-usage');
  } catch {
    showMessage('waterChartBox', 'waterMessage', 'No se pudo cargar el consumo de agua.');
    return;
  }
  box.classList.remove('skeleton');
  if (days.every((day) => Number(day.liters) === 0)) {
    showMessage('waterChartBox', 'waterMessage', 'No hay riegos con litros registrados en los últimos 7 días.');
    return;
  }
  const options = baseOptions();
  options.scales.y.beginAtZero = true;
  options.plugins.tooltip = {
    callbacks: { label: (context) => `${numberFormat.format(context.parsed.y)} L` },
  };
  new Chart(document.getElementById('waterChart'), {
    type: 'bar',
    data: {
      // The API sends plain dates; appending a time keeps them in the local day.
      labels: days.map((day) => dayFormat.format(new Date(`${day.date}T12:00:00`))),
      datasets: [
        {
          data: days.map((day) => Number(day.liters)),
          backgroundColor: token('--chart-series'),
          borderRadius: 4,
          maxBarThickness: 28,
        },
      ],
    },
    options,
  });
}

function renderEstateDetail(estate) {
  const detail = document.getElementById('estateDetail');
  const title = document.createElement('h3');
  title.textContent = estate.name;
  const list = document.createElement('dl');
  const rows = [
    ['Ubicación', estate.location ?? 'Sin ubicación'],
    ['Área', estate.areaHa === null ? '—' : `${numberFormat.format(estate.areaHa)} ha`],
    ['Cultivos activos', estate.crops.length > 0 ? estate.crops.join(', ') : 'Ninguno'],
  ];
  rows.forEach(([term, value]) => {
    const dt = document.createElement('dt');
    dt.textContent = term;
    const dd = document.createElement('dd');
    dd.textContent = value;
    list.append(dt, dd);
  });
  detail.replaceChildren(title, list);
}

async function initMap() {
  const container = document.getElementById('estateMap');
  if (!container) return;
  let estates;
  try {
    estates = await getJson('/api/ui/estates/markers');
  } catch {
    container.classList.remove('skeleton');
    container.textContent = 'No se pudo cargar el mapa.';
    return;
  }
  container.classList.remove('skeleton');

  const map = L.map(container, { scrollWheelZoom: false });
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 18,
    attribution: '&copy; OpenStreetMap',
  }).addTo(map);

  const markers = estates.map((estate) => {
    const marker = L.circleMarker([Number(estate.latitude), Number(estate.longitude)], {
      radius: 10,
      color: token('--surface'),
      weight: 2,
      fillColor: token('--green-600'),
      fillOpacity: 1,
    }).addTo(map);
    marker.bindTooltip(estate.name);
    marker.on('click', () => renderEstateDetail(estate));
    return marker;
  });
  map.fitBounds(L.featureGroup(markers).getBounds().pad(0.3), { maxZoom: 12 });
  if (estates.length > 0) renderEstateDetail(estates[0]);
}

function initQuickIrrigation() {
  const form = document.getElementById('quickIrrigation');
  if (!form) return;
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    const cropLabel = form.cropId.selectedOptions[0].textContent.trim();
    const minutes = Number(form.durationMin.value);
    if (!(await confirmAction(`¿Iniciar un riego de ${minutes} minutos en ${cropLabel}?`))) return;

    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      const response = await fetch('/api/ui/irrigations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...csrfHeaders() },
        body: JSON.stringify({ cropId: Number(form.cropId.value), durationMin: minutes }),
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        showToast(body.message ?? 'No se pudo registrar el riego.', 'error');
        return;
      }
      // Reload so the pump status, indicators and history reflect the new irrigation.
      window.location.reload();
    } catch {
      showToast('No se pudo conectar con el servidor.', 'error');
    } finally {
      button.disabled = false;
    }
  });
}

initReadings();
initWater();
initMap();
initQuickIrrigation();
