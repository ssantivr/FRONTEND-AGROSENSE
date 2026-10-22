import { isRecord, requestJson } from './api/client.js';
import { find } from './core/dom.js';

export type BackendState = 'off' | 'unknown' | 'up' | 'waking' | 'down';

interface BackendStatus {
  state: BackendState;
}

const STATES: readonly BackendState[] = ['off', 'unknown', 'up', 'waking', 'down'];

const LABELS: Record<BackendState, string> = {
  up: 'Servidor conectado',
  waking: 'Despertando el servidor, puede tardar hasta un minuto…',
  down: 'Respaldo: base de datos directa',
  off: 'Modo local: base de datos',
  unknown: 'Comprobando el servidor…',
};

const TITLES: Record<BackendState, string> = {
  up: 'Las operaciones pasan por el servidor de AgroSense.',
  waking: 'El servidor estaba dormido. Mientras despierta, los datos se leen directamente de la base de datos compartida.',
  down: 'El servidor no responde: los datos se leen directamente de la base de datos compartida (respaldo).',
  off: 'Sin servidor configurado: los datos se leen directamente de la base de datos.',
  unknown: 'Comprobando el servidor…',
};

// The server keeps waking the backend in the background: the page only asks how it went, less often each time.
const POLL_DELAYS_MS = [4_000, 8_000, 15_000, 30_000, 30_000];

const isBackendStatus = (value: unknown): value is BackendStatus =>
  isRecord(value) && typeof value['state'] === 'string' && STATES.includes(value['state'].toLowerCase() as BackendState);

const listeners = new Set<(state: BackendState) => void>();

export function onBackendState(listener: (state: BackendState) => void): void {
  listeners.add(listener);
}

export function currentBackendState(): BackendState {
  const state = find('backendStatus', HTMLElement)?.dataset['state'];
  return STATES.includes(state as BackendState) ? (state as BackendState) : 'unknown';
}

function show(badge: HTMLElement, state: BackendState): void {
  if (badge.dataset['state'] === state) return;
  badge.dataset['state'] = state;
  badge.title = TITLES[state];
  const label = badge.querySelector<HTMLElement>('[data-connection-label]');
  if (label) label.textContent = LABELS[state];
  listeners.forEach((listener) => listener(state));
}

export function initConnectionStatus(): void {
  const badge = find('backendStatus', HTMLElement);
  const url = badge?.dataset['statusUrl'];
  if (!badge || !url) return;
  let polls = 0;

  const check = async (): Promise<void> => {
    let state: BackendState;
    try {
      const status = await requestJson(url, isBackendStatus, { timeoutMs: 10_000 });
      state = status.state.toLowerCase() as BackendState;
    } catch {
      return;
    }
    show(badge, state);
    const delay = POLL_DELAYS_MS[polls];
    if ((state === 'waking' || state === 'unknown') && delay !== undefined) {
      polls += 1;
      window.setTimeout(() => void check(), delay);
    }
  };

  if (currentBackendState() !== 'off') void check();
}
