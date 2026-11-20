// «Monitoreo»: asks the server for the page again every 15 s and brings its cards up to date, while the tab is visible.
// The server renders it (Thymeleaf); this only copies nodes and texts, never sets HTML from a string.
import { ApiError, requestHtml } from './api/client.js';
import { RefreshTracker } from './core/time.js';
const REFRESH_MS = 15_000;
/** After this many failed rounds in a row the page says since when it is stale and offers to retry. */
const WARN_AFTER_FAILURES = 2;
/**
 * B6: brings `current` in line with `fresh` touching only what changed: texts and attributes in place when the shape is
 * the same, so a focused link or button keeps the focus; a node whose shape changed is swapped whole.
 */
export function syncNode(current, fresh) {
    if (current.nodeType === Node.TEXT_NODE && fresh.nodeType === Node.TEXT_NODE) {
        if (current.nodeValue !== fresh.nodeValue)
            current.nodeValue = fresh.nodeValue;
        return;
    }
    if (!(current instanceof Element) || !(fresh instanceof Element) || current.tagName !== fresh.tagName) {
        current.parentNode?.replaceChild(document.importNode(fresh, true), current);
        return;
    }
    for (const name of current.getAttributeNames())
        if (!fresh.hasAttribute(name))
            current.removeAttribute(name);
    for (const name of fresh.getAttributeNames()) {
        const value = fresh.getAttribute(name) ?? '';
        if (current.getAttribute(name) !== value)
            current.setAttribute(name, value);
    }
    syncChildren(current, fresh);
}
function syncChildren(current, fresh) {
    const now = Array.from(current.childNodes);
    const next = Array.from(fresh.childNodes);
    if (now.length !== next.length) {
        current.replaceChildren(...next.map((node) => document.importNode(node, true)));
        return;
    }
    now.forEach((node, index) => {
        const other = next[index];
        if (other)
            syncNode(node, other);
    });
}
function start(page) {
    const status = document.querySelector('[data-monitoring-updated]');
    const retry = document.querySelector('[data-monitoring-retry]');
    const tracker = new RefreshTracker(new Date(), WARN_AFTER_FAILURES);
    let running = false;
    const paint = () => {
        if (status) {
            const text = tracker.text();
            if (text && status.textContent !== text)
                status.textContent = text;
            status.dataset['stale'] = String(tracker.stale);
        }
        if (retry)
            retry.hidden = !tracker.stale;
    };
    const refresh = async (force = false) => {
        if (running || (!force && document.visibilityState !== 'visible'))
            return;
        running = true;
        page.setAttribute('aria-busy', 'true');
        try {
            const html = await requestHtml(window.location.pathname, { timeoutMs: 12_000 });
            const fresh = new DOMParser().parseFromString(html, 'text/html').querySelector('[data-monitoring]');
            if (!fresh)
                throw new ApiError('unexpected');
            syncChildren(page, fresh);
            tracker.succeeded(new Date());
        }
        catch (error) {
            // The session expired: reloading takes the user to the login page instead of showing old data for ever.
            if (error instanceof ApiError && error.kind === 'unauthorized') {
                window.location.reload();
                return;
            }
            tracker.failed();
        }
        finally {
            running = false;
            page.setAttribute('aria-busy', 'false');
        }
        paint();
    };
    retry?.addEventListener('click', () => void refresh(true));
    const timer = window.setInterval(() => void refresh(), REFRESH_MS);
    window.addEventListener('pagehide', () => window.clearInterval(timer), { once: true });
    paint();
}
if (typeof document !== 'undefined') {
    const page = document.querySelector('[data-monitoring]');
    if (page)
        start(page);
}
