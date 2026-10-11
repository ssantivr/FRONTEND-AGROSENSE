// Feedback while the server works: a waiting state on the button that sent a form, and a thin bar at the
// top of the page when a navigation takes long enough to notice.
import { all, el, prefersReducedMotion, setButtonBusy } from './core/dom.js';

/** Navigations that answer faster than this show no bar at all. */
const BAR_DELAY_MS = 180;

let barTimer: number | undefined;

function startNavigation(): void {
  window.clearTimeout(barTimer);
  barTimer = window.setTimeout(() => document.documentElement.classList.add('is-navigating'), BAR_DELAY_MS);
}

function endNavigation(): void {
  window.clearTimeout(barTimer);
  document.documentElement.classList.remove('is-navigating');
}

/** A plain left click on a link that loads another page of this application in the same tab. */
function leavesThePage(event: MouseEvent): boolean {
  if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
    return false;
  }
  const link = event.target instanceof Element ? event.target.closest('a[href]') : null;
  if (!(link instanceof HTMLAnchorElement) || link.target || link.hasAttribute('download')) return false;
  const url = new URL(link.href);
  const samePage = url.pathname === window.location.pathname && url.search === window.location.search;
  return url.origin === window.location.origin && !(samePage && url.hash !== '');
}

const COUNT_KEY = 'agrosense.shown.';
const COUNT_MS = 600;

/** A number as the page prints it ("1.750", "55,8"), with how many decimals it shows. */
function printed(text: string): { value: number; decimals: number } | null {
  if (!/^-?[\d.]+(,\d+)?$/.test(text.trim())) return null;
  const [whole = '', fraction = ''] = text.trim().split(',');
  const value = Number(`${whole.replaceAll('.', '')}.${fraction || '0'}`);
  return Number.isFinite(value) ? { value, decimals: fraction.length } : null;
}

/**
 * <span data-countup="key">: when the figure differs from the one this browser showed last time under the
 * same key, it counts from the old one to the new one. A figure that did not change does not move.
 */
function initCountUps(): void {
  all('[data-countup]', HTMLElement).forEach((element) => {
    const final = element.textContent ?? '';
    const target = printed(final);
    const key = COUNT_KEY + window.location.pathname + '|' + (element.dataset['countup'] ?? '');
    let before: string | null = null;
    try {
      before = sessionStorage.getItem(key);
      sessionStorage.setItem(key, final);
    } catch {
      return;
    }
    const from = before === null ? null : printed(before);
    if (!target || !from || from.value === target.value || prefersReducedMotion()) return;
    const format = new Intl.NumberFormat('es-CO', {
      minimumFractionDigits: target.decimals,
      maximumFractionDigits: target.decimals,
    });
    const started = performance.now();
    const step = (now: number): void => {
      const progress = Math.min(1, (now - started) / COUNT_MS);
      // Ease out: fast at first, settling on the figure.
      const eased = 1 - (1 - progress) ** 3;
      element.textContent = progress < 1 ? format.format(from.value + (target.value - from.value) * eased) : final;
      if (progress < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  });
}

/** Hours, minutes and seconds left, in words. */
function timeLeft(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  if (minutes >= 60) return `Quedan ${Math.floor(minutes / 60)} h ${minutes % 60} min`;
  if (minutes >= 1) return `Quedan ${minutes} min ${seconds % 60} s`;
  return `Quedan ${seconds} s`;
}

/**
 * [data-countdown data-remaining data-total]: a running irrigation. Its <progress> and its
 * [data-countdown-text] advance every second from the time the server said was left; when it ends, the
 * page is loaded again so everything shows it as finished.
 */
function initCountdowns(): void {
  const running = all('[data-countdown]', HTMLElement).flatMap((element) => {
    const remaining = Number(element.dataset['remaining']);
    const total = Number(element.dataset['total']);
    if (!Number.isFinite(remaining) || !Number.isFinite(total) || remaining <= 0 || total <= 0) return [];
    const bar = element instanceof HTMLProgressElement ? element : element.querySelector('progress');
    return [{ remaining, total, bar, text: element.querySelector<HTMLElement>('[data-countdown-text]') }];
  });
  if (running.length === 0) return;
  const loadedAt = Date.now();
  const tick = (): void => {
    const elapsed = Math.floor((Date.now() - loadedAt) / 1000);
    let finished = false;
    running.forEach(({ remaining, total, bar, text }) => {
      const left = Math.max(0, remaining - elapsed);
      if (bar) bar.value = total - left;
      if (text) text.textContent = left > 0 ? timeLeft(left) : 'Terminando…';
      finished ||= left === 0;
    });
    if (finished) {
      window.clearInterval(timer);
      window.setTimeout(() => window.location.reload(), 1500);
    }
  };
  const timer = window.setInterval(tick, 1000);
  tick();
}

export function initFeedback(): void {
  // <button data-print>: the browser's print dialog, which also saves the page as a PDF.
  all('[data-print]', HTMLButtonElement).forEach((button) => button.addEventListener('click', () => window.print()));
  initCountUps();
  initCountdowns();
  const bar = el('div', 'nav-progress');
  bar.setAttribute('aria-hidden', 'true');
  document.body.append(bar);

  document.addEventListener('click', (event) => {
    if (leavesThePage(event)) startNavigation();
  });

  // At the document, so it runs after the form's own listeners: a form that was cancelled (validation,
  // a pending confirmation, a request sent by script) is left alone.
  document.addEventListener('submit', (event) => {
    const form = event.target;
    if (event.defaultPrevented || !(form instanceof HTMLFormElement) || form.method === 'dialog') return;
    const submitter =
      event.submitter instanceof HTMLButtonElement ? event.submitter : form.querySelector<HTMLButtonElement>('button[type="submit"]');
    // After this turn of the event loop the browser has already collected the form's data, so locking the
    // button cannot drop anything from the request; it only stops a second send.
    if (submitter) window.setTimeout(() => setButtonBusy(submitter, true), 0);
    startNavigation();
  });

  // Coming back with the back button restores the page as it was left: clear every waiting state.
  window.addEventListener('pageshow', (event) => {
    endNavigation();
    if (!event.persisted) return;
    all('button[aria-busy="true"]', HTMLButtonElement).forEach((button) => setButtonBusy(button, false));
    all('form[data-confirmed]', HTMLFormElement).forEach((form) => delete form.dataset['confirmed']);
  });
}
