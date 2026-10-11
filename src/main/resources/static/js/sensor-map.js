// Position picker of the sensor forms: click the map to place the sensor, drag it to move it, and see the
// circle of ground its readings stand for. The three inputs stay the source of truth and can be typed too.
import { isRecord } from './api/client.js';
import { isPickerSite } from './api/ui.js';
import { find, radiusOfArea } from './core/dom.js';
/** Radius given to a sensor the first time it is placed, until the user types another. */
const DEFAULT_COVERAGE_M = 30;
const TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
function parse(json) {
    if (!json)
        return null;
    try {
        return JSON.parse(json);
    }
    catch {
        return null;
    }
}
function initPicker() {
    const picker = find('sensorPicker', HTMLElement);
    const container = find('pickerMap', HTMLElement);
    const latitude = find('pickerLatitude', HTMLInputElement);
    const longitude = find('pickerLongitude', HTMLInputElement);
    const coverage = find('pickerCoverage', HTMLInputElement);
    const summary = find('pickerSummary', HTMLElement);
    const notice = find('pickerNotice', HTMLElement);
    const noticeText = find('pickerNoticeText', HTMLElement);
    const clear = find('pickerClear', HTMLButtonElement);
    if (!picker || !container || !latitude || !longitude || !coverage || !summary || !notice || !noticeText || !clear) {
        return;
    }
    const sites = parse(picker.dataset['sites']);
    const fixedSite = parse(picker.dataset['site']);
    const cropSelect = document.querySelector('select[data-picker-crop]');
    /** The estate the sensor will belong to: the one of the chosen crop, or the sensor's own. */
    const currentSite = () => {
        if (isPickerSite(fixedSite))
            return fixedSite;
        const site = isRecord(sites) && cropSelect ? sites[cropSelect.value] : null;
        return isPickerSite(site) ? site : null;
    };
    const say = (text) => {
        noticeText.textContent = text;
        notice.hidden = text === '';
    };
    if (typeof L === 'undefined') {
        say('No se pudo cargar el mapa. Puedes escribir la latitud, la longitud y el radio.');
        return;
    }
    const leaflet = L;
    container.hidden = false;
    const map = leaflet.map(container, { scrollWheelZoom: false });
    leaflet
        .tileLayer(TILE_URL, {
        maxZoom: 19,
        className: 'map-tiles-streets',
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    })
        .addTo(map);
    map.on('focus', () => map.scrollWheelZoom.enable());
    map.on('blur', () => map.scrollWheelZoom.disable());
    const area = leaflet.circle([0, 0], { radius: 1, className: 'map-area', interactive: false });
    const outline = leaflet.polygon([], { className: 'map-boundary', interactive: false });
    const reach = leaflet.circle([0, 0], { radius: DEFAULT_COVERAGE_M, className: 'map-coverage', interactive: false });
    const marker = leaflet.marker([0, 0], {
        draggable: true,
        keyboard: true,
        title: 'Sensor: arrastra para moverlo',
        icon: leaflet.divIcon({ className: 'map-sensor-handle', iconSize: [22, 22], iconAnchor: [11, 11] }),
    });
    const typedPoint = () => {
        const lat = latitude.valueAsNumber;
        const lon = longitude.valueAsNumber;
        return Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180 ? [lat, lon] : null;
    };
    const typedRadius = () => {
        const metres = coverage.valueAsNumber;
        return Number.isFinite(metres) && metres >= 1 && metres <= 2000 ? metres : null;
    };
    /** Draws what the three inputs say. */
    const draw = () => {
        const point = typedPoint();
        const radius = typedRadius();
        if (!point) {
            marker.remove();
            reach.remove();
            summary.textContent = 'El sensor aún no está en el mapa.';
            return;
        }
        marker.setLatLng(point).addTo(map);
        if (radius === null) {
            reach.remove();
            summary.textContent = 'Indica el radio de cobertura para dibujar el círculo.';
            return;
        }
        reach.setLatLng(point).setRadius(radius).addTo(map);
        const hectares = (Math.PI * radius * radius) / 10_000;
        const site = currentSite();
        const share = site?.areaHa ? ` (${Math.min(100, Math.round((hectares / site.areaHa) * 100))} % de la parcela)` : '';
        summary.textContent = `Cubre ${hectares.toLocaleString('es-CO', { maximumFractionDigits: 2 })} ha${share}.`;
    };
    /** Writes a point into the inputs, with a default radius the first time. */
    const place = (lat, lon) => {
        latitude.value = lat.toFixed(7);
        longitude.value = lon.toFixed(7);
        if (coverage.value === '')
            coverage.value = String(DEFAULT_COVERAGE_M);
        draw();
    };
    /** Moves the map to the estate and draws its approximate area. */
    const showSite = () => {
        const site = currentSite();
        area.remove();
        outline.remove();
        if (site?.boundary) {
            // The estate's own outline, when it has been drawn.
            say('');
            outline.setLatLngs(site.boundary);
            map.fitBounds(leaflet.latLngBounds(site.boundary).pad(0.3), { maxZoom: 19 });
            outline.addTo(map);
            return;
        }
        if (!site || site.latitude === null || site.longitude === null) {
            say(site
                ? `«${site.name}» no tiene coordenadas. Agrégalas en Parcelas, o busca el lugar en el mapa y haz clic.`
                : 'Elige un cultivo para ver su parcela en el mapa.');
            const point = typedPoint();
            map.setView(point ?? [4.6, -74.1], point ? 16 : 5);
            return;
        }
        say('');
        const centre = [site.latitude, site.longitude];
        if (site.areaHa !== null) {
            const radius = radiusOfArea(site.areaHa);
            // The view comes first: a circle cannot be measured on a map that has no view yet.
            map.fitBounds(leaflet.latLng(centre).toBounds(radius * 2).pad(0.3), { maxZoom: 18 });
            area.setLatLng(centre).setRadius(radius).addTo(map);
        }
        else {
            map.setView(centre, 16);
        }
    };
    map.on('click', (event) => place(event.latlng.lat, event.latlng.lng));
    marker.on('drag', () => {
        const at = marker.getLatLng();
        place(at.lat, at.lng);
    });
    [latitude, longitude, coverage].forEach((input) => input.addEventListener('input', draw));
    cropSelect?.addEventListener('change', showSite);
    clear.addEventListener('click', () => {
        latitude.value = '';
        longitude.value = '';
        coverage.value = '';
        draw();
    });
    showSite();
    draw();
}
initPicker();
