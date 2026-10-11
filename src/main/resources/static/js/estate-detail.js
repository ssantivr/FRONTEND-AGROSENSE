// Map of the estate page: its outline, its sensors and what each one covers, drawn with the same code as
// the dashboard map.
import { embeddedEstate } from './api/ui.js';
import { find } from './core/dom.js';
import { createBaseLayers, estateGround, hasValidCoordinates, initMapLayers } from './map-shapes.js';
function initDetailMap() {
    const container = find('detailMap', HTMLElement);
    if (!container)
        return;
    const estate = embeddedEstate(container.dataset['estate']);
    if (typeof L === 'undefined' || !estate || !hasValidCoordinates(estate)) {
        // Nothing can be drawn: hide the frame rather than leave an endless placeholder.
        container.closest('.map-stage')?.setAttribute('hidden', '');
        return;
    }
    const leaflet = L;
    container.classList.remove('skeleton');
    const map = leaflet.map(container, { scrollWheelZoom: false, zoomControl: false });
    leaflet.control.zoom({ position: 'bottomright', zoomInTitle: 'Acercar', zoomOutTitle: 'Alejar' }).addTo(map);
    leaflet.control.scale({ imperial: false, position: 'bottomleft' }).addTo(map);
    initMapLayers(map, createBaseLayers(leaflet));
    const centre = [estate.latitude, estate.longitude];
    const shapes = estateGround(leaflet, estate);
    if (shapes.length === 0) {
        // Neither an outline nor an area nor sensors: only the point is known.
        leaflet.circleMarker(centre, { radius: 8, className: 'map-sensor' }).addTo(map);
        map.setView(centre, 15);
    }
    else {
        // The view comes first: circles cannot be measured on a map without one.
        map.setView(centre, 16);
        shapes.forEach((shape) => shape.addTo(map));
        map.fitBounds(leaflet.featureGroup(shapes).getBounds().pad(0.2), { maxZoom: 18 });
    }
    map.on('click', () => map.scrollWheelZoom.enable());
    container.addEventListener('mouseleave', () => map.scrollWheelZoom.disable());
}
initDetailMap();
