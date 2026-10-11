// Sign-in and sign-up pages: theme switch, hero photograph, password toggles, counters, e-mail availability
// and the two forms. Without JavaScript both forms still post normally and the server renders the outcome.
import { forgetConversation } from './ai/storage.js';
import { messageOf } from './api/client.js';
import { checkEmail } from './api/ui.js';
import { all, find, icon, need, prefersReducedMotion } from './core/dom.js';
import {
  type FieldRule,
  initCapsWarnings,
  initCounters,
  initPasswordMeters,
  initPasswordRules,
  LIMITS,
  setFieldError,
  validateField,
  validateFields,
} from './forms.js';
import { initThemeToggles } from './theme.js';

// These pages are only shown without a session: whatever the last user asked the assistant goes.
forgetConversation();
initThemeToggles();
initCounters();
initPasswordMeters();
initPasswordRules();
initCapsWarnings();

// ---------- Hero photograph: loading -> loaded | error ----------

type PhotoState = 'loading' | 'loaded' | 'error';

function initHeroPhoto(): void {
  const hero = find('authHero', HTMLElement);
  const photo = find('authHeroPhoto', HTMLImageElement);
  if (!hero || !photo) return;
  const settle = (state: PhotoState): void => {
    hero.dataset['state'] = state;
  };
  const reveal = (): void => {
    // decode() resolves once the image can be painted without a flash of half-decoded rows.
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

// ---------- Show/hide toggles for password fields: <button data-toggle-password="inputId"> ----------

function initPasswordToggles(): void {
  all('[data-toggle-password]', HTMLButtonElement).forEach((toggle) => {
    const input = find(toggle.dataset['togglePassword'] ?? '', HTMLInputElement);
    if (!input) return;
    toggle.addEventListener('click', () => {
      const reveal = input.type === 'password';
      input.type = reveal ? 'text' : 'password';
      toggle.setAttribute('aria-pressed', String(reveal));
      toggle.setAttribute('aria-label', reveal ? 'Ocultar contraseña' : 'Mostrar contraseña');
      // Replacing the icon element restarts its CSS fade-in, which gives the cross-fade.
      toggle.replaceChildren(icon(reveal ? 'bi-eye-slash' : 'bi-eye'));
    });
  });
}

// ---------- Live e-mail availability on the sign-up form: <input data-email-availability="url"> ----------

type Availability = 'unknown' | 'checking' | 'available' | 'taken' | 'failed';

/** Returns a reader of the latest known answer, so the form can refuse an address it knows is taken. */
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
      const { available } = await checkEmail(url, input.value.trim(), request.signal);
      if (available) show('available', 'Correo disponible.');
      else show('taken', 'Este correo ya está registrado. Inicia sesión o usa otro.');
    } catch (error) {
      // A newer keystroke aborted this request: its own check will report.
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

// ---------- Sign-in ----------

/** Where the sign-in attempt stands. Only the server's answer moves it to `success` or to an error. */
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

/** Message, and whether "Reintentar" makes sense, for every state that reports a problem. */
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
    // Read-only, not disabled: the values must stay in the request and nothing may be edited meanwhile.
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

  /** Sends the form and reads the outcome from where Spring Security redirects, or from the status code. */
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
      // 403 is a stale CSRF token; 400 is a form the server could not read. Both need a fresh page.
      if (response.status === 403 || response.status === 400) return { kind: 'expired' };
      if (response.status === 404) return { kind: 'not-found' };
      if (response.ok && !backAtLogin) return { kind: 'success', redirectTo: response.url };
      return { kind: 'server-error' };
    } catch {
      // A timeout aborts the request and is reported like any other failure to reach the server.
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
    // Messages the server rendered for the previous page load no longer describe this attempt.
    all('[data-server-message]', HTMLElement).forEach((message) => message.remove());
    render({ kind: 'submitting' });
    const outcome = await request();
    render(outcome);

    if (outcome.kind === 'success') {
      // A short beat for the confirmation, then the page the server chose. Never longer than this.
      setTimeout(() => window.location.assign(outcome.redirectTo), SUCCESS_PAUSE_MS);
    } else if (outcome.kind === 'invalid-credentials') {
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
  // Returning with the back button restores the page as it was left: make it usable again.
  window.addEventListener('pageshow', (event) => {
    if (event.persisted) render({ kind: 'idle' });
  });
}

// ---------- Sign-up ----------

/** Same wording as the server's validation, so a message does not change when the server repeats it. */
function lengthRule(input: HTMLInputElement, missing: string, outOfRange: string, min: number, max: number): FieldRule {
  return {
    input,
    // The length is checked on the text as typed: spaces count.
    check: (value) => required(missing)(value) ?? (value.length < min || value.length > max ? outOfRange : null),
  };
}

function initSignUp(form: HTMLFormElement, emailAvailability: () => Availability): void {
  const submit = need('registerSubmit', HTMLButtonElement);
  const label = need('registerSubmitLabel', HTMLElement);
  const idleLabel = label.textContent ?? '';
  const email = namedInput(form, 'email');
  const password = namedInput(form, 'password');
  const confirmation = namedInput(form, 'confirmPassword');
  const { name, password: passwordLimits } = LIMITS;
  const rules: FieldRule[] = [
    lengthRule(namedInput(form, 'name'), 'Ingresa tu nombre.', `El nombre debe tener entre ${name.min} y ${name.max} caracteres.`, name.min, name.max),
    lengthRule(namedInput(form, 'lastName'), 'Ingresa tu apellido.', `El apellido debe tener entre ${name.min} y ${name.max} caracteres.`, name.min, name.max),
    {
      input: email,
      check: (value) =>
        emailRule(email).check(value) ??
        (emailAvailability() === 'taken' ? 'Este correo ya está registrado. Inicia sesión o usa otro.' : null),
    },
    {
      input: password,
      check: (value) =>
        value === ''
          ? 'Ingresa una contraseña.'
          : value.length < passwordLimits.min || value.length > passwordLimits.max
            ? `La contraseña debe tener entre ${passwordLimits.min} y ${passwordLimits.max} caracteres.`
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
    // The form posts normally from here; the button is locked so it cannot be sent twice.
    setBusy(true);
  });
  const matchHint = find('confirmPasswordMatch', HTMLElement);
  /** "Las contraseñas coinciden", only once both are typed and equal. */
  const showMatch = (): void => {
    if (matchHint) matchHint.hidden = !(confirmation.value !== '' && confirmation.value === password.value);
  };
  rules.forEach((rule) => {
    const { input } = rule;
    // While typing, an error goes away; the verdict comes when the field is left.
    input.addEventListener('input', () => {
      setFieldError(input, null);
      input.closest('.field')?.classList.remove('is-valid');
    });
    input.addEventListener('blur', () => validateField(rule, { quietWhenEmpty: true }));
  });
  // Tell the mismatch as soon as the confirmation is complete enough to compare.
  confirmation.addEventListener('input', () => {
    const differs = confirmation.value.length >= password.value.length && confirmation.value !== password.value;
    setFieldError(confirmation, differs ? 'Las contraseñas no coinciden.' : null);
    showMatch();
  });
  // Changing the password after confirming it makes the confirmation stale.
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
