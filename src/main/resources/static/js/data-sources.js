// «Origen de datos»: a side drawer, opened from the top bar, that says where each figure comes from and where it lives.
// Accessible modal: role=dialog, aria-modal, Esc closes, focus is kept inside and given back. Built with the DOM API only.
import { messageOf, requestJson } from './api/client.js';
import { el, find, icon } from './core/dom.js';
import { ORIGIN_ICONS, isDataSources, isOrigin, originLabel, simulatorText, sourceCounts, } from './data-sources-model.js';
const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select, textarea, [tabindex]:not([tabindex="-1"])';
function fact(list, term, value, code = false) {
    if (!value)
        return;
    const detail = el('dd');
    detail.append(code ? el('code', '', value) : document.createTextNode(value));
    list.append(el('dt', '', term), detail);
}
function itemView(item) {
    const card = el('article', 'source-item');
    card.dataset['origin'] = item.origin;
    const head = el('header', 'source-item-head');
    const chip = el('span', 'origin-chip');
    chip.dataset['origin'] = item.origin;
    chip.append(icon(isOrigin(item.origin) ? ORIGIN_ICONS[item.origin] : 'bi-question-circle'), el('span', '', originLabel(item.origin)));
    head.append(el('h3', '', item.label), chip);
    const facts = el('dl', 'source-item-facts');
    fact(facts, 'Quién lo produce', item.producer);
    fact(facts, 'Endpoint', item.endpoint, true);
    fact(facts, 'Dónde se guarda', item.storage ?? 'No se guarda: se calcula al pedirlo', item.storage !== null);
    fact(facts, 'Archivo', item.file, true);
    fact(facts, 'Dónde se pinta', item.frontendComponent, true);
    card.append(head, facts);
    return card;
}
function summaryView(sources) {
    const box = el('section', 'source-summary');
    box.setAttribute('aria-label', 'Resumen de orígenes');
    if (sources.fallback) {
        const note = el('p', 'note source-fallback');
        note.append(icon('bi-exclamation-triangle'), el('span', '', 'El servidor no respondió: este es el catálogo guardado en la página, sin conteos.'));
        box.append(note);
    }
    const simulator = el('p', 'source-simulator', simulatorText(sources.simulatorEnabled));
    simulator.dataset['enabled'] = String(sources.simulatorEnabled);
    box.append(simulator);
    const counts = sourceCounts(sources.readingCountsBySource);
    if (counts.length > 0) {
        box.append(el('h3', '', 'Lecturas de las últimas 24 h por origen'));
        const list = el('dl', 'source-counts');
        counts.forEach(({ source, label, count }) => {
            const value = el('dd', '', new Intl.NumberFormat('es-CO').format(count));
            value.dataset['source'] = source;
            list.append(el('dt', '', label), value);
        });
        box.append(list);
    }
    return box;
}
class DataSourcesDrawer {
    trigger;
    drawer;
    body;
    backdrop;
    url;
    returnFocus = null;
    loading = false;
    constructor(trigger, drawer, body, backdrop, url) {
        this.trigger = trigger;
        this.drawer = drawer;
        this.body = body;
        this.backdrop = backdrop;
        this.url = url;
        trigger.addEventListener('click', () => this.open());
        backdrop?.addEventListener('click', () => this.close());
        for (const button of drawer.querySelectorAll('[data-drawer-close]')) {
            button.addEventListener('click', () => this.close());
        }
        drawer.addEventListener('keydown', (event) => this.onKey(event));
    }
    get isOpen() {
        return !this.drawer.hidden;
    }
    open() {
        if (this.isOpen)
            return;
        this.returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : this.trigger;
        this.drawer.hidden = false;
        if (this.backdrop)
            this.backdrop.hidden = false;
        this.trigger.setAttribute('aria-expanded', 'true');
        find('appShell', HTMLElement)?.setAttribute('inert', '');
        document.documentElement.classList.add('has-drawer');
        this.drawer.querySelector('[data-drawer-close]')?.focus();
        void this.load();
    }
    close() {
        if (!this.isOpen)
            return;
        this.drawer.hidden = true;
        if (this.backdrop)
            this.backdrop.hidden = true;
        this.trigger.setAttribute('aria-expanded', 'false');
        find('appShell', HTMLElement)?.removeAttribute('inert');
        document.documentElement.classList.remove('has-drawer');
        (this.returnFocus?.isConnected ? this.returnFocus : this.trigger).focus();
    }
    onKey(event) {
        if (event.key === 'Escape') {
            event.preventDefault();
            this.close();
            return;
        }
        if (event.key !== 'Tab')
            return;
        const focusable = [...this.drawer.querySelectorAll(FOCUSABLE)].filter((node) => !node.closest('[hidden]'));
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (!first || !last) {
            event.preventDefault();
            return;
        }
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        }
        else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    }
    async load() {
        if (this.loading)
            return;
        this.loading = true;
        this.body.setAttribute('aria-busy', 'true');
        this.body.replaceChildren(el('p', 'text-muted', 'Cargando el origen de los datos…'));
        try {
            const sources = await requestJson(this.url, isDataSources, { timeoutMs: 15_000 });
            const list = el('div', 'source-list');
            sources.items.forEach((item) => list.append(itemView(item)));
            this.body.replaceChildren(summaryView(sources), list);
        }
        catch (error) {
            const retry = el('button', 'button button--ghost button--small');
            retry.type = 'button';
            retry.append(icon('bi-arrow-clockwise'), document.createTextNode(' Reintentar'));
            retry.addEventListener('click', () => void this.load());
            this.body.replaceChildren(el('p', 'chart-message', `No se pudo cargar el origen de los datos. ${messageOf(error)}`), retry);
        }
        finally {
            this.loading = false;
            this.body.setAttribute('aria-busy', 'false');
        }
    }
}
export function initDataSources() {
    const trigger = find('dataSourcesOpen', HTMLButtonElement);
    const drawer = find('dataSourcesDrawer', HTMLElement);
    const body = find('dataSourcesBody', HTMLElement);
    const url = trigger?.dataset['sourcesUrl'];
    if (!trigger || !drawer || !body || !url)
        return;
    new DataSourcesDrawer(trigger, drawer, body, find('dataSourcesBackdrop', HTMLElement), url);
}
