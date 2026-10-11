// The one place that talks HTTP: CSRF header, timeout, and every failure turned into an ApiError with a
// kind the interface can explain. Answers are checked with a type guard before they are trusted.
import type { ApiMessage } from '../types/api.js';

export type ApiErrorKind =
  | 'validation' // 400: the server refused the data
  | 'unauthorized' // 401, or the session ended and the request was sent to the sign-in page
  | 'forbidden' // 403
  | 'not-found' // 404
  | 'conflict' // 409: a business rule, with a message meant for the user
  | 'rate-limited' // 429
  | 'server' // 5xx
  | 'network' // no answer at all
  | 'timeout'
  | 'unexpected'; // an answer that is not what the endpoint promises

const FALLBACK_MESSAGES: Record<ApiErrorKind, string> = {
  validation: 'Revisa los datos enviados.',
  unauthorized: 'Tu sesión caducó. Vuelve a iniciar sesión.',
  forbidden: 'No tienes permiso para realizar esta acción.',
  'not-found': 'No encontramos lo que buscabas.',
  conflict: 'La operación entra en conflicto con el estado actual.',
  'rate-limited': 'Demasiados intentos. Espera un momento antes de volver a intentarlo.',
  server: 'El servidor no pudo atender la solicitud. Inténtalo de nuevo en unos minutos.',
  network: 'No hay conexión con el servidor. Revisa tu red e inténtalo de nuevo.',
  timeout: 'El servidor tardó demasiado en responder. Inténtalo de nuevo.',
  unexpected: 'El servidor respondió algo inesperado.',
};

const KIND_BY_STATUS: Readonly<Record<number, ApiErrorKind>> = {
  400: 'validation',
  401: 'unauthorized',
  403: 'forbidden',
  404: 'not-found',
  409: 'conflict',
  422: 'validation',
  429: 'rate-limited',
};

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status: number | null;
  /** Seconds the server asked to wait, when it said so (429). */
  readonly retryAfterSec: number | null;

  constructor(kind: ApiErrorKind, options: { status?: number; message?: string; retryAfterSec?: number } = {}) {
    super(options.message ?? FALLBACK_MESSAGES[kind]);
    this.name = 'ApiError';
    this.kind = kind;
    this.status = options.status ?? null;
    this.retryAfterSec = options.retryAfterSec ?? null;
  }
}

/** The text to show for any thrown value. */
export function messageOf(error: unknown): string {
  return error instanceof ApiError ? error.message : FALLBACK_MESSAGES.unexpected;
}

export type Guard<T> = (value: unknown) => value is T;

export const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

export const isArrayOf =
  <T>(guard: Guard<T>): Guard<T[]> =>
  (value): value is T[] =>
    Array.isArray(value) && value.every(guard);

const isApiMessage = (value: unknown): value is ApiMessage => isRecord(value) && typeof value['message'] === 'string';

/** Headers that authorise a same-origin request that changes data. */
export function csrfHeaders(): Record<string, string> {
  const token = document.querySelector<HTMLMetaElement>('meta[name="_csrf"]')?.content;
  const header = document.querySelector<HTMLMetaElement>('meta[name="_csrf_header"]')?.content;
  return token && header ? { [header]: token } : {};
}

export interface RequestOptions {
  method?: 'GET' | 'POST';
  /** Sent as JSON. */
  body?: unknown;
  timeoutMs?: number;
  /** Aborts the request from outside, for example when a newer one replaces it. */
  signal?: AbortSignal;
}

const DEFAULT_TIMEOUT_MS = 15_000;
const RETRY_DELAY_MS = 600;

/**
 * Sends the request and resolves with a body the guard accepted; every other outcome rejects with an ApiError.
 * A GET that got no answer is tried once more; requests that change data never are.
 */
export async function requestJson<T>(url: string, guard: Guard<T>, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  try {
    return await attempt(url, guard, options);
  } catch (error) {
    const retriable = error instanceof ApiError && error.kind === 'network' && method === 'GET';
    if (!retriable || options.signal?.aborted) throw error;
    await new Promise((resolve) => setTimeout(resolve, RETRY_DELAY_MS));
    return attempt(url, guard, options);
  }
}

async function attempt<T>(url: string, guard: Guard<T>, options: RequestOptions): Promise<T> {
  const method = options.method ?? 'GET';
  const controller = new AbortController();
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, options.timeoutMs ?? DEFAULT_TIMEOUT_MS);
  const abortFromOutside = (): void => controller.abort();
  options.signal?.addEventListener('abort', abortFromOutside, { once: true });

  let response: Response;
  try {
    response = await fetch(url, {
      method,
      credentials: 'same-origin',
      signal: controller.signal,
      headers: {
        Accept: 'application/json',
        ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...(method === 'GET' ? {} : csrfHeaders()),
      },
      ...(options.body === undefined ? {} : { body: JSON.stringify(options.body) }),
    });
  } catch (error) {
    // The caller cancelled: let it see the abort instead of a made-up network failure.
    if (options.signal?.aborted && error instanceof DOMException) throw error;
    throw new ApiError(timedOut ? 'timeout' : 'network');
  } finally {
    clearTimeout(timer);
    options.signal?.removeEventListener('abort', abortFromOutside);
  }

  // An expired session answers JSON requests with a redirect to the sign-in page, which fetch follows.
  if (response.redirected && new URL(response.url).pathname.endsWith('/login')) {
    throw new ApiError('unauthorized', { status: 401 });
  }
  const body: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    const kind = KIND_BY_STATUS[response.status] ?? (response.status >= 500 ? 'server' : 'unexpected');
    const retryAfter = Number(response.headers.get('Retry-After'));
    throw new ApiError(kind, {
      status: response.status,
      // 5xx bodies are not written for users; the client's own wording is used for those.
      ...(isApiMessage(body) && response.status < 500 ? { message: body.message } : {}),
      ...(Number.isFinite(retryAfter) && retryAfter > 0 ? { retryAfterSec: retryAfter } : {}),
    });
  }
  if (!guard(body)) throw new ApiError('unexpected', { status: response.status });
  return body;
}
