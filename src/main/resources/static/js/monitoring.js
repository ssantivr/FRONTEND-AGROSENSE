"use strict";
// «Monitoreo»: asks the server for the page again every 15 s and swaps in the new cards, while the tab is visible.
// The server renders it (Thymeleaf); this only replaces nodes, never sets HTML from a string.
const REFRESH_MS = 15_000;
const page = document.querySelector('[data-monitoring]');
async function refresh(target) {
    if (document.visibilityState !== 'visible') {
        return;
    }
    try {
        const response = await fetch(window.location.pathname, { headers: { Accept: 'text/html' }, credentials: 'same-origin' });
        if (!response.ok || response.redirected) {
            return;
        }
        const fresh = new DOMParser().parseFromString(await response.text(), 'text/html').querySelector('[data-monitoring]');
        if (fresh) {
            target.replaceChildren(...Array.from(fresh.childNodes, (node) => document.importNode(node, true)));
        }
    }
    catch {
        // Offline for a moment: the next round tries again.
    }
}
if (page) {
    window.setInterval(() => void refresh(page), REFRESH_MS);
}
