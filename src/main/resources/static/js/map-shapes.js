import { all, el, radiusOfArea } from './core/dom.js';
const MAP_LAYER_KEY = 'agrosense.mapLayer';
const numberFormat = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 });
export function hasValidCoordinates(estate) {
    return (estate.latitude !== null &&
        estate.longitude !== null &&
        Math.abs(estate.latitude) <= 90 &&
        Math.abs(estate.longitude) <= 180);
}
export function toneOf(name) {
    const text = name.trim().toLowerCase();
    let sum = 0;
    for (let index = 0; index < text.length; index++)
        sum += text.charCodeAt(index) * (index + 1);
    return (sum % 6) + 1;
}
export function cropChip(name) {
    const chip = el('li', 'chip');
    const dot = el('span', `crop-dot crop-tone-${toneOf(name)}`, name.trim().slice(0, 1).toUpperCase() || '?');
    dot.setAttribute('aria-hidden', 'true');
    chip.append(dot, el('span', '', name));
    return chip;
}
export function createBaseLayers(leaflet) {
    return {
        streets: leaflet.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            className: 'map-tiles-streets',
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
        }),
        satellite: leaflet.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
            maxZoom: 19,
            maxNativeZoom: 18,
            className: 'map-tiles-satellite',
            attribution: 'Imágenes &copy; Esri, Maxar, Earthstar Geographics',
        }),
    };
}
export function initMapLayers(map, layers) {
    const options = all('[data-map-layer]', HTMLButtonElement);
    const isLayer = (value) => value === 'streets' || value === 'satellite';
    const show = (layer) => {
        Object.keys(layers).forEach((name) => {
            if (name === layer)
                layers[name].addTo(map);
            else
                layers[name].remove();
        });
        options.forEach((option) => option.setAttribute('aria-pressed', String(option.dataset['mapLayer'] === layer)));
        map.getContainer().dataset['layer'] = layer;
    };
    let saved = null;
    try {
        saved = localStorage.getItem(MAP_LAYER_KEY);
    }
    catch {
    }
    show(isLayer(saved) ? saved : 'streets');
    options.forEach((option) => option.addEventListener('click', () => {
        const layer = option.dataset['mapLayer'];
        if (!isLayer(layer))
            return;
        show(layer);
        try {
            localStorage.setItem(MAP_LAYER_KEY, layer);
        }
        catch {
        }
    }));
}
function sensorTip(sensor) {
    const tip = el('div', 'map-tip');
    const value = sensor.lastValue === null ? 'Sin lecturas' : `${numberFormat.format(sensor.lastValue)} ${sensor.unit ?? ''}`.trim();
    tip.append(el('strong', '', sensor.code), el('span', '', sensor.typeLabel), el('span', '', sensor.active ? value : 'Inactivo'), el('span', 'map-tip-muted', `Cubre ${sensor.coverageM} m a la redonda`));
    return tip;
}
export function estateGround(leaflet, estate) {
    const shapes = [];
    if (estate.boundary) {
        shapes.push(leaflet.polygon(estate.boundary, { className: 'map-boundary', interactive: false }));
    }
    else if (estate.areaHa !== null) {
        shapes.push(leaflet.circle([estate.latitude, estate.longitude], {
            radius: radiusOfArea(estate.areaHa),
            className: 'map-area',
            interactive: false,
        }));
    }
    estate.sensors.forEach((sensor) => {
        const state = sensor.active ? '' : ' map-sensor--inactive';
        const at = [sensor.latitude, sensor.longitude];
        shapes.push(leaflet.circle(at, { radius: sensor.coverageM, className: `map-coverage${state}`, interactive: false }), leaflet
            .circleMarker(at, { radius: 6, className: `map-sensor${state}` })
            .bindTooltip(sensorTip(sensor), { direction: 'top', offset: [0, -6] }));
    });
    return shapes;
}
