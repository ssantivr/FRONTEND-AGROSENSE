export function find(id, type) {
    const element = document.getElementById(id);
    return element instanceof type ? element : null;
}
export function need(id, type) {
    const element = find(id, type);
    if (!element)
        throw new Error(`Missing #${id} (${type.name}) in the page`);
    return element;
}
export function all(selector, type, root = document) {
    return [...root.querySelectorAll(selector)].filter((element) => element instanceof type);
}
export function el(tag, className = '', text = '') {
    const element = document.createElement(tag);
    if (className)
        element.className = className;
    if (text)
        element.textContent = text;
    return element;
}
export function icon(name) {
    const element = el('i', `bi ${name}`);
    element.setAttribute('aria-hidden', 'true');
    return element;
}
export function setButtonBusy(button, busy) {
    button.disabled = busy;
    button.setAttribute('aria-busy', String(busy));
    if (!button.classList.contains('button'))
        return;
    button.classList.toggle('is-loading', busy);
    if (busy && !button.querySelector('.button-spinner')) {
        const spinner = el('span', 'button-spinner');
        spinner.setAttribute('aria-hidden', 'true');
        button.prepend(spinner);
    }
}
export const radiusOfArea = (areaHa) => Math.sqrt((areaHa * 10_000) / Math.PI);
export function areaOfOutline(corners) {
    if (corners.length < 3)
        return 0;
    const midLat = corners.reduce((sum, corner) => sum + corner[0], 0) / corners.length;
    const midLon = corners.reduce((sum, corner) => sum + corner[1], 0) / corners.length;
    const metresPerLat = (Math.PI / 180) * 6_371_008.8;
    const metresPerLon = metresPerLat * Math.cos((midLat * Math.PI) / 180);
    let twice = 0;
    corners.forEach((from, index) => {
        const to = corners[(index + 1) % corners.length] ?? from;
        twice +=
            (from[1] - midLon) * metresPerLon * ((to[0] - midLat) * metresPerLat) -
                (to[1] - midLon) * metresPerLon * ((from[0] - midLat) * metresPerLat);
    });
    return Math.abs(twice) / 2 / 10_000;
}
export const prefersReducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches ||
    document.documentElement.classList.contains('reduce-motion');
