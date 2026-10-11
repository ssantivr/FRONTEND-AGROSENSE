// Typed access to the elements the templates render. Ids are a contract with the Thymeleaf templates,
// so a lookup states the element type it expects and is checked at run time.

type ElementClass<T extends Element> = abstract new (...args: never[]) => T;

/** The element with this id when it exists and is of the expected type; otherwise null. */
export function find<T extends Element>(id: string, type: ElementClass<T>): T | null {
  const element = document.getElementById(id);
  return element instanceof type ? element : null;
}

/** Like find, for elements the page cannot work without. */
export function need<T extends Element>(id: string, type: ElementClass<T>): T {
  const element = find(id, type);
  if (!element) throw new Error(`Missing #${id} (${type.name}) in the page`);
  return element;
}

export function all<T extends Element>(selector: string, type: ElementClass<T>, root: ParentNode = document): T[] {
  return [...root.querySelectorAll(selector)].filter((element): element is T => element instanceof type);
}

/** Builds an element with a class and, optionally, text. Text is never parsed as HTML. */
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

/** A decorative Bootstrap icon. */
export function icon(name: string): HTMLElement {
  const element = el('i', `bi ${name}`);
  element.setAttribute('aria-hidden', 'true');
  return element;
}

/**
 * Shows or clears the waiting state of a button: locked, announced as busy and, for text buttons, with a
 * spinner in front of the label. The button keeps its label, so its meaning does not change while it waits.
 */
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

/** Radius, in metres, of a circle with this area in hectares: how an estate without a drawn boundary is shown. */
export const radiusOfArea = (areaHa: number): number => Math.sqrt((areaHa * 10_000) / Math.PI);

/**
 * Area, in hectares, enclosed by corners given as [latitude, longitude]. They are laid flat around their
 * middle, which is exact enough at the size of a farm; Boundary.areaHa on the server does the same.
 */
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
