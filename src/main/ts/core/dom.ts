type ElementClass<T extends Element> = abstract new (...args: never[]) => T;

export function find<T extends Element>(id: string, type: ElementClass<T>): T | null {
  const element = document.getElementById(id);
  return element instanceof type ? element : null;
}

export function need<T extends Element>(id: string, type: ElementClass<T>): T {
  const element = find(id, type);
  if (!element) throw new Error(`Missing #${id} (${type.name}) in the page`);
  return element;
}

export function all<T extends Element>(selector: string, type: ElementClass<T>, root: ParentNode = document): T[] {
  return [...root.querySelectorAll(selector)].filter((element): element is T => element instanceof type);
}

export function el<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  className = '',
  text = '',
): HTMLElementTagNameMap[K] {
  const element = document.createElement(tag);
  if (className) element.className = className;
  if (text) element.textContent = text;
  return element;
}

export function icon(name: string): HTMLElement {
  const element = el('i', `bi ${name}`);
  element.setAttribute('aria-hidden', 'true');
  return element;
}

export function setButtonBusy(button: HTMLButtonElement, busy: boolean): void {
  button.disabled = busy;
  button.setAttribute('aria-busy', String(busy));
  if (!button.classList.contains('button')) return;
  button.classList.toggle('is-loading', busy);
  if (busy && !button.querySelector('.button-spinner')) {
    const spinner = el('span', 'button-spinner');
    spinner.setAttribute('aria-hidden', 'true');
    button.prepend(spinner);
  }
}

export const radiusOfArea = (areaHa: number): number => Math.sqrt((areaHa * 10_000) / Math.PI);

export function areaOfOutline(corners: ReadonlyArray<readonly [number, number]>): number {
  if (corners.length < 3) return 0;
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

export const prefersReducedMotion = (): boolean =>
  window.matchMedia('(prefers-reduced-motion: reduce)').matches ||
  document.documentElement.classList.contains('reduce-motion');

/** Kept in this browser; theme-init.ts applies it before the first paint. */
export const REDUCE_MOTION_KEY = 'agrosense.reduceMotion';

export function setReducedMotion(reduce: boolean): void {
  document.documentElement.classList.toggle('reduce-motion', reduce);
  try {
    if (reduce) localStorage.setItem(REDUCE_MOTION_KEY, '1');
    else localStorage.removeItem(REDUCE_MOTION_KEY);
  } catch {
    // Without storage the choice lasts until the page changes.
  }
}
