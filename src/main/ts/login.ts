import { forgetConversation } from './ai/storage.js';
import { messageOf } from './api/client.js';
import { api } from './api/facade.js';
import { all, find, icon, need, prefersReducedMotion } from './core/dom.js';
import {
  type FieldRule,
  initCapsWarnings,
  initCounters,
  initPasswordMeters,
  initPasswordRules,
  LIMITS,
  normalizedText,
  setFieldError,
  validateField,
  validateFields,
} from './forms.js';
import { initThemeToggles } from './theme.js';

forgetConversation();
initThemeToggles();
initCounters();
initPasswordMeters();
initPasswordRules();
initCapsWarnings();

type PhotoState = 'loading' | 'loaded' | 'error';

function initHeroPhoto(): void {
  const hero = find('authHero', HTMLElement);
  const photo = find('authHeroPhoto', HTMLImageElement);
  if (!hero || !photo) return;
  const settle = (state: PhotoState): void => {
    hero.dataset['state'] = state;
  };
  const reveal = (): void => {
    photo.decode().then(
      () => settle('loaded'),
      () => settle('loaded'),
    );
  };
  if (photo.complete) {
    if (photo.naturalWidth > 0) reveal();
    else settle('error');
  } else {
    photo.addEventListener('load', reveal, { once: true });
    photo.addEventListener('error', () => settle('error'), { once: true });
  }
}

function initPasswordToggles(): void {
  all('[data-toggle-password]', HTMLButtonElement).forEach((toggle) => {
    const input = find(toggle.dataset['togglePassword'] ?? '', HTMLInputElement);
    if (!input) return;
    toggle.addEventListener('click', () => {
      const reveal = input.type === 'password';
      input.type = reveal ? 'text' : 'password';
      toggle.setAttribute('aria-pressed', String(reveal));
      toggle.setAttribute('aria-label', reveal ? 'Ocultar contraseña' : 'Mostrar contraseña');
      toggle.replaceChildren(icon(reveal ? 'bi-eye-slash' : 'bi-eye'));
    });
  });
}

type Availability = 'unknown' | 'checking' | 'available' | 'taken' | 'failed';

function initEmailAvailability(): () => Availability {
  const input = document.querySelector<HTMLInputElement>('input[data-email-availability]');
  const status = find('emailAvailability', HTMLElement);
  let availability: Availability = 'unknown';
  if (!input || !status) return () => availability;
  const url = input.dataset['emailAvailability'] ?? '';
  let timer: number | undefined;
  let request: AbortController | undefined;

  const show = (next: Availability, text: string): void => {
    availability = next;
    status.textContent = text;
    status.className =
      next === 'available'
        ? 'field-hint field-hint--ok'
        : next === 'taken' || next === 'failed'
          ? 'field-hint field-hint--taken'
          : next === 'checking'
            ? 'field-hint is-checking'
            : 'field-hint';
    status.hidden = text === '';
  };

  const check = async (): Promise<void> => {
    request = new AbortController();
    show('checking', 'Comprobando…');
    try {
      const { available } = await api.checkEmail(url, input.value.trim(), request.signal);
      if (available) show('available', 'Correo disponible.');
      else show('taken', 'Este correo ya está registrado. Inicia sesión o usa otro.');
    } catch (error) {
      if (error instanceof DOMException) return;
      show('failed', `No pudimos comprobar el correo. ${messageOf(error)}`);
    }
  };

  input.addEventListener('input', () => {
    window.clearTimeout(timer);
    request?.abort();
    show('unknown', '');
    if (input.value.trim() && input.checkValidity()) timer = window.setTimeout(() => void check(), 400);
  });
  return () => availability;
}

type AuthStatus =
  | { kind: 'idle' }
  | { kind: 'validating' }
  | { kind: 'submitting' }
  | { kind: 'success'; redirectTo: string }
  | { kind: 'invalid-credentials' }
  | { kind: 'rate-limited'; retryAfterSec?: number }
  | { kind: 'expired' }
  | { kind: 'not-found' }
  | { kind: 'network-error' }
  | { kind: 'server-error' };

type ProblemKind = Exclude<AuthStatus['kind'], 'idle' | 'validating' | 'submitting' | 'success'>;

const REQUEST_TIMEOUT_MS = 15_000;
const SUCCESS_PAUSE_MS = 350;

const PROBLEMS: Record<ProblemKind, { text: string; retry: boolean }> = {
  'invalid-credentials': { text: 'No se pudo iniciar sesión. Comprueba tus credenciales.', retry: false },
  'rate-limited': { text: 'Demasiados intentos fallidos. Espera unos minutos antes de volver a intentarlo.', retry: false },
  expired: { text: 'Esta página caducó. Recárgala y vuelve a intentarlo.', retry: false },
  'not-found': { text: 'El servicio de inicio de sesión no está disponible en esta dirección.', retry: true },
  'network-error': { text: 'No hay conexión con el servidor. Revisa tu red e inténtalo de nuevo.', retry: true },
  'server-error': { text: 'El servidor no pudo atender la solicitud. Inténtalo de nuevo en unos minutos.', retry: true },
};

const isProblem = (kind: AuthStatus['kind']): kind is ProblemKind => kind in PROBLEMS;

const namedInput = (form: HTMLFormElement, name: string): HTMLInputElement => {
  const input = form.elements.namedItem(name);
  if (!(input instanceof HTMLInputElement)) throw new Error(`Missing field "${name}" in the form`);
  return input;
};

const required =
  (message: string) =>
  (value: string): string | null =>
    value.trim() === '' ? message : null;

function emailRule(input: HTMLInputElement): FieldRule {
  return {
    input,
    check: (value) =>
      required('Ingresa tu correo electrónico.')(value) ??
      (input.validity.typeMismatch ? 'Ingresa un correo electrónico válido.' : null),
  };
}

function initSignIn(form: HTMLFormElement): void {
  const shell = document.querySelector<HTMLElement>('.auth');
  const banner = need('authStatus', HTMLElement);
  const bannerText = need('authStatusText', HTMLElement);
  const retry = need('authRetry', HTMLButtonElement);
  const submit = need('authSubmit', HTMLButtonElement);
  const label = need('authSubmitLabel', HTMLElement);
  const idleLabel = label.textContent ?? '';
  const email = namedInput(form, 'email');
  const password = namedInput(form, 'password');
  const rules: FieldRule[] = [emailRule(email), { input: password, check: required('Ingresa tu contraseña.') }];
  let status: AuthStatus = { kind: 'idle' };

  const render = (next: AuthStatus): void => {
    status = next;
    const busy = next.kind === 'submitting';
    const done = next.kind === 'success';
    form.classList.toggle('is-busy', busy);
    form.setAttribute('aria-busy', String(busy));
    submit.disabled = busy || done;
    submit.setAttribute('aria-busy', String(busy));
    submit.classList.toggle('is-loading', busy);
    submit.classList.toggle('is-success', done);
    label.textContent = busy ? 'Verificando credenciales…' : done ? 'Acceso autorizado' : idleLabel;
    email.readOnly = busy || done;
    password.readOnly = busy || done;
    if (shell) shell.dataset['auth'] = next.kind;

    banner.hidden = !isProblem(next.kind);
    if (!isProblem(next.kind)) return;
    const problem = PROBLEMS[next.kind];
    bannerText.textContent =
      next.kind === 'rate-limited' && next.retryAfterSec
        ? `Demasiados intentos. Espera ${next.retryAfterSec} s antes de volver a intentarlo.`
        : problem.text;
    retry.hidden = !problem.retry;
  };

  const request = async (): Promise<AuthStatus> => {
    const loginPath = new URL(form.action).pathname;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    const fields = new URLSearchParams();
    new FormData(form).forEach((value, name) => {
      if (typeof value === 'string') fields.append(name, value);
    });
    try {
      const response = await fetch(form.action, {
        method: 'POST',
        body: fields,
        credentials: 'same-origin',
        signal: controller.signal,
      });
      const landed = new URL(response.url);
      const backAtLogin = landed.pathname === loginPath;
      if (response.status === 429 || (backAtLogin && landed.searchParams.has('locked'))) {
        const seconds = Number(response.headers.get('Retry-After'));
        return Number.isFinite(seconds) && seconds > 0
          ? { kind: 'rate-limited', retryAfterSec: seconds }
          : { kind: 'rate-limited' };
      }
      if (response.status === 401 || (backAtLogin && landed.searchParams.has('error'))) {
        return { kind: 'invalid-credentials' };
      }
      if (response.status === 403 || response.status === 400) return { kind: 'expired' };
      if (response.status === 404) return { kind: 'not-found' };
      if (response.ok && !backAtLogin) return { kind: 'success', redirectTo: response.url };
      return { kind: 'server-error' };
    } catch {
      return { kind: 'network-error' };
    } finally {
      clearTimeout(timeout);
    }
  };

  const signIn = async (): Promise<void> => {
    if (status.kind === 'submitting' || status.kind === 'success') return;
    render({ kind: 'validating' });
    if (!validateFields(rules)) {
      render({ kind: 'idle' });
      return;
    }
    all('[data-server-message]', HTMLElement).forEach((message) => message.remove());
    render({ kind: 'submitting' });
    const outcome = await request();
    render(outcome);

    if (outcome.kind === 'success') {
      setTimeout(() => window.location.assign(outcome.redirectTo), SUCCESS_PAUSE_MS);
    } else if (outcome.kind === 'invalid-credentials') {
      all('.field.is-valid', HTMLElement, form).forEach((field) => field.classList.remove('is-valid'));
      password.value = '';
      email.focus();
      if (!prefersReducedMotion()) {
        form.animate(
          [0, -4, 4, -4, 0].map((x) => ({ transform: `translateX(${x}px)` })),
          { duration: 200 },
        );
      }
    } else if (isProblem(outcome.kind) && PROBLEMS[outcome.kind].retry) {
      retry.focus();
    }
  };

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    void signIn();
  });
  retry.addEventListener('click', () => void signIn());
  rules.forEach(({ input }) => input.addEventListener('input', () => setFieldError(input, null)));
  window.addEventListener('pageshow', (event) => {
    if (event.persisted) render({ kind: 'idle' });
  });
}

function nameRule(input: HTMLInputElement, missing: string, what: string): FieldRule {
  const { min, max } = LIMITS.name;
  // Extra spaces are removed when leaving the field, so they never count against the limit.
  input.addEventListener('change', () => {
    input.value = normalizedText(input.value);
  });
  return {
    input,
    check: (value) => {
      const length = normalizedText(value).length;
      if (required(missing)(value)) return missing;
      if (length < min) return `${what} debe tener al menos ${min} caracteres.`;
      if (length > max) {
        return `${what} tiene ${length} caracteres y el servidor admite ${max} por ahora (espacios incluidos). Abrevia una parte, por ejemplo la última palabra.`;
      }
      return null;
    },
  };
}

function initSignUp(form: HTMLFormElement, emailAvailability: () => Availability): void {
  const submit = need('registerSubmit', HTMLButtonElement);
  const label = need('registerSubmitLabel', HTMLElement);
  const idleLabel = label.textContent ?? '';
  const email = namedInput(form, 'email');
  const password = namedInput(form, 'password');
  const confirmation = namedInput(form, 'confirmPassword');
  const availabilityHint = find('emailAvailability', HTMLElement);
  const { password: passwordLimits } = LIMITS;
  const takenEmail = (): string | null => {
    if (emailAvailability() !== 'taken') return null;
    if (availabilityHint) availabilityHint.hidden = true;
    return 'Este correo ya está registrado. Inicia sesión o usa otro.';
  };
  const rules: FieldRule[] = [
    nameRule(namedInput(form, 'name'), 'Ingresa tu nombre.', 'El nombre'),
    nameRule(namedInput(form, 'lastName'), 'Ingresa tu apellido.', 'El apellido'),
    {
      input: email,
      check: (value) => emailRule(email).check(value) ?? takenEmail(),
    },
    {
      input: password,
      check: (value) =>
        value === ''
          ? 'Ingresa una contraseña.'
          : value.length < passwordLimits.min
            ? `La contraseña debe tener al menos ${passwordLimits.min} caracteres.`
            : value.length > passwordLimits.max
              ? `La contraseña tiene ${value.length} caracteres y el servidor admite ${passwordLimits.max} por ahora. Acórtala; no se recorta sola.`
              : null,
    },
    { input: confirmation, check: (value) => (value === password.value ? null : 'Las contraseñas no coinciden.') },
  ];

  const setBusy = (busy: boolean): void => {
    submit.disabled = busy;
    submit.setAttribute('aria-busy', String(busy));
    submit.classList.toggle('is-loading', busy);
    label.textContent = busy ? 'Creando cuenta…' : idleLabel;
  };

  form.addEventListener('submit', (event) => {
    if (submit.disabled || !validateFields(rules)) {
      event.preventDefault();
      return;
    }
    setBusy(true);
  });
  const matchHint = find('confirmPasswordMatch', HTMLElement);
  const showMatch = (): void => {
    if (matchHint) matchHint.hidden = !(confirmation.value !== '' && confirmation.value === password.value);
  };
  rules.forEach((rule) => {
    const { input } = rule;
    input.addEventListener('input', () => {
      setFieldError(input, null);
      input.closest('.field')?.classList.remove('is-valid');
    });
    input.addEventListener('blur', () => validateField(rule, { quietWhenEmpty: true }));
  });
  confirmation.addEventListener('input', () => {
    const differs = confirmation.value.length >= password.value.length && confirmation.value !== password.value;
    setFieldError(confirmation, differs ? 'Las contraseñas no coinciden.' : null);
    showMatch();
  });
  password.addEventListener('input', () => {
    showMatch();
    if (confirmation.value !== '') validateField({ input: confirmation, check: (value) => (value === password.value ? null : 'Las contraseñas no coinciden.') });
  });
  window.addEventListener('pageshow', (event) => {
    if (event.persisted) setBusy(false);
  });
}

initHeroPhoto();
initPasswordToggles();
const emailAvailability = initEmailAvailability();
const loginForm = find('loginForm', HTMLFormElement);
if (loginForm) initSignIn(loginForm);
const registerForm = find('registerForm', HTMLFormElement);
if (registerForm) initSignUp(registerForm, emailAvailability);
