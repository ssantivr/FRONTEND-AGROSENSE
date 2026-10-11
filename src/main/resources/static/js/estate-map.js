// Outline editor of the estate form: each click on the map adds a corner, corners can be dragged, and the
// result travels in a hidden field as JSON, [[latitude, longitude], ...].
import { isBoundary } from './api/ui.js';
import { areaOfOutline, find } from './core/dom.js';
const MAX_CORNERS = 200;
const TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
function initEditor() {
    const container = find('boundaryMap', HTMLElement);
    const field = document.querySelector('input[name="boundary"]');
    const summary = find('boundarySummary', HTMLElement);
    const undo = find('boundaryUndo', HTMLButtonElement);
    const clear = find('boundaryClear', HTMLButtonElement);
    const locate = find('boundaryLocate', HTMLButtonElement);
    const latitude = document.querySelector('input[name="latitude"]');
    const longitude = document.querySelector('input[name="longitude"]');
    const areaHa = document.querySelector('input[name="areaHa"]');
    if (!container || !field || !summary || !undo || !clear || !locate || !latitude || !longitude || !areaHa)
        return;
    if (typeof L === 'undefined') {
        summary.textContent = 'No se pudo cargar el mapa; la parcela se puede guardar sin contorno.';
        return;
    }
    const leaflet = L;
    let stored = null;
    try {
        stored = field.value ? JSON.parse(field.value) : null;
    }
    catch {
        // An unreadable value is treated as no outline; saving will replace it.
    }
    const corners = isBoundary(stored) ? stored.map((corner) => [corner[0], corner[1]]) : [];
    container.hidden = false;
    const map = leaflet.map(container, { scrollWheelZoom: false, doubleClickZoom: false });
    leaflet
        .tileLayer(TILE_URL, {
        maxZoom: 19,
        className: 'map-tiles-streets',
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    })
        .addTo(map);
    map.on('focus', () => map.scrollWheelZoom.enable());
    map.on('blur', () => map.scrollWheelZoom.disable());
    const outline = leaflet.polygon([], { className: 'map-boundary', interactive: false }).addTo(map);
    let handles = [];
    /** Whether the area and centre fields hold values this editor wrote, and so may be rewritten by it. */
    const owns = { area: areaHa.value === '', centre: latitude.value === '' && longitude.value === '' };
    /** Writes the corners to the hidden field and, where the user left them empty, the area and the centre. */
    const sync = () => {
        const closed = corners.length >= 3;
        field.value = closed ? JSON.stringify(corners.map(([lat, lon]) => [Number(lat.toFixed(7)), Number(lon.toFixed(7))])) : '';
        undo.disabled = corners.length === 0;
        clear.disabled = corners.length === 0;
        outline.setLatLngs(corners);
        if (!closed) {
            const missing = 3 - corners.length;
            summary.textContent =
                corners.length === 0
                    ? 'Sin contorno. Haz clic en el mapa para marcar la primera esquina.'
                    : `${corners.length === 1 ? '1 punto' : '2 puntos'}: ${missing === 1 ? 'falta 1' : `faltan ${missing}`} para cerrar el contorno.`;
            if (owns.area)
                areaHa.value = '';
            if (owns.centre) {
                latitude.value = '';
                longitude.value = '';
            }
            return;
        }
        const hectares = areaOfOutline(corners);
        summary.textContent = `${corners.length} puntos · ${hectares.toLocaleString('es-CO', { maximumFractionDigits: 2 })} ha dentro del contorno.`;
        if (owns.area)
            areaHa.value = Math.max(0.01, hectares).toFixed(2);
        if (owns.centre) {
            latitude.value = (corners.reduce((sum, corner) => sum + corner[0], 0) / corners.length).toFixed(7);
            longitude.value = (corners.reduce((sum, corner) => sum + corner[1], 0) / corners.length).toFixed(7);
        }
    };
    /** One draggable handle per corner; rebuilt whenever a corner is added or removed. */
    const drawHandles = () => {
        handles.forEach((handle) => handle.remove());
        handles = corners.map((corner, index) => {
            const handle = leaflet
                .marker(corner, {
                draggable: true,
                keyboard: true,
                title: `Esquina ${index + 1}: arrastra para moverla`,
                icon: leaflet.divIcon({ className: 'map-corner-handle', iconSize: [18, 18], iconAnchor: [9, 9] }),
            })
                .addTo(map);
            handle.on('drag', () => {
                const at = handle.getLatLng();
                corners[index] = [at.lat, at.lng];
                sync();
            });
            return handle;
        });
    };
    const changed = () => {
        drawHandles();
        sync();
    };
    map.on('click', (event) => {
        if (corners.length >= MAX_CORNERS) {
            summary.textContent = `El contorno admite hasta ${MAX_CORNERS} puntos.`;
            return;
        }
        corners.push([event.latlng.lat, event.latlng.lng]);
        changed();
    });
    undo.addEventListener('click', () => {
        corners.pop();
        changed();
    });
    clear.addEventListener('click', () => {
        corners.length = 0;
        changed();
    });
    // Typing a value by hand makes the field the user's again: the editor stops rewriting it.
    areaHa.addEventListener('input', () => {
        owns.area = areaHa.value === '';
    });
    [latitude, longitude].forEach((input) => input.addEventListener('input', () => {
        owns.centre = latitude.value === '' && longitude.value === '';
    }));
    locate.addEventListener('click', () => {
        if (!('geolocation' in navigator)) {
            summary.textContent = 'Este navegador no ofrece la ubicación.';
            return;
        }
        locate.disabled = true;
        navigator.geolocation.getCurrentPosition((position) => {
            locate.disabled = false;
            map.setView([position.coords.latitude, position.coords.longitude], 17);
        }, () => {
            locate.disabled = false;
            summary.textContent = 'No se pudo obtener tu ubicación. Acerca el mapa a mano hasta la parcela.';
        }, { enableHighAccuracy: true, timeout: 10_000 });
    });
    // Start on the outline, else on the estate's point, else on the whole country.
    const typedLat = latitude.valueAsNumber;
    const typedLon = longitude.valueAsNumber;
    if (corners.length >= 3)
        map.fitBounds(leaflet.latLngBounds(corners).pad(0.3), { maxZoom: 19 });
    else if (Number.isFinite(typedLat) && Number.isFinite(typedLon))
        map.setView([typedLat, typedLon], 16);
    else
        map.setView([4.6, -74.1], 6);
    changed();
}
initEditor();
