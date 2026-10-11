// Form helpers shared by the account pages: live character counters, the password meter and field messages.
import { all, find } from './core/dom.js';

/** Limits of the sign-up form; the server and the backend API enforce the same ones. */
export const LIMITS = {
  name: { min: 2, max: 18 },
  password: { min: 8, max: 32 },
} as const;

/**
 * <input data-counter="counterId" minlength maxlength> keeps "typed/max" up to date in #counterId.
 * The length is the text as typed: a space counts as one more character.
 */
export function initCounters(): void {
  all('input[data-counter]', HTMLInputElement).forEach((input) => {
    const counter = find(input.dataset['counter'] ?? '', HTMLElement);
    if (!counter || input.maxLength < 0) return;
    const update = (): void => {
      const { length } = input.value;
      counter.textContent = `${length}/${input.maxLength}`;
      counter.classList.toggle('is-limit', length >= input.maxLength);
      counter.classList.toggle('is-short', length > 0 && length < input.minLength);
    };
    input.addEventListener('input', update);
    update();
  });
}

export type StrengthLevel = 0 | 1 | 2 | 3 | 4;

export interface Strength {
  level: StrengthLevel;
  label: string;
}

/** Rates a password by length and by how many kinds of character it mixes. It is guidance, not a rule. */
export function passwordStrength(password: string): Strength {
  const { min, max } = LIMITS.password;
  const { length } = password;
  if (length === 0) return { level: 0, label: `Entre ${min} y ${max} caracteres; los espacios cuentan.` };
  if (length < min) {
    const missing = min - length;
    return { level: 1, label: `Muy corta: ${missing === 1 ? 'falta 1 carácter' : `faltan ${missing} caracteres`}.` };
  }
  const kinds = [/\p{Ll}/u, /\p{Lu}/u, /\p{N}/u, /[^\p{L}\p{N}]/u].filter((kind) => kind.test(password)).length;
  const score = kinds + (length >= 12 ? 1 : 0) + (length >= 16 ? 1 : 0);
  if (score <= 2) return { level: 2, label: 'Débil: alárgala o combina mayúsculas, números y símbolos.' };
  if (score <= 4) return { level: 3, label: 'Aceptable.' };
  return { level: 4, label: 'Fuerte.' };
}

/** <div data-strength-for="inputId"> shows the level as filled bars (data-level) and as text. */
export function initPasswordMeters(): void {
  all('[data-strength-for]', HTMLElement).forEach((meter) => {
    const input = find(meter.dataset['strengthFor'] ?? '', HTMLInputElement);
    const label = meter.querySelector<HTMLElement>('[data-strength-label]');
    if (!input || !label) return;
    const update = (): void => {
      const strength = passwordStrength(input.value);
      meter.dataset['level'] = String(strength.level);
      label.textContent = strength.label;
    };
    input.addEventListener('input', update);
    update();
  });
}

/** What each line of the password checklist asks for. Only "length" is required by the server. */
const PASSWORD_RULES: Readonly<Record<string, (password: string) => boolean>> = {
  length: (password) => password.length >= LIMITS.password.min && password.length <= LIMITS.password.max,
  mixed: (password) => /\p{Ll}/u.test(password) && /\p{Lu}/u.test(password),
  digit: (password) => /\p{N}/u.test(password),
  symbol: (password) => /[^\p{L}\p{N}]/u.test(password),
};

/** <ul data-rules-for="inputId"> with <li data-rule="length|mixed|digit|symbol">: marks each line as it is met. */
export function initPasswordRules(): void {
  all('[data-rules-for]', HTMLElement).forEach((list) => {
    const input = find(list.dataset['rulesFor'] ?? '', HTMLInputElement);
    if (!input) return;
    const lines = all('[data-rule]', HTMLElement, list);
    const update = (): void => {
      lines.forEach((line) => {
        const met = PASSWORD_RULES[line.dataset['rule'] ?? '']?.(input.value) ?? false;
        line.classList.toggle('is-met', met);
        // The mark is drawn by CSS; this says the same to a screen reader.
        line.setAttribute('aria-label', `${line.textContent?.trim() ?? ''}: ${met ? 'cumplido' : 'pendiente'}`);
      });
    };
    input.addEventListener('input', update);
    update();
  });
}

/** <input data-caps-warning="hintId">: shows the hint while Caps Lock is on and the field has the focus. */
export function initCapsWarnings(): void {
  all('input[data-caps-warning]', HTMLInputElement).forEach((input) => {
    const hint = find(input.dataset['capsWarning'] ?? '', HTMLElement);
    if (!hint) return;
    const check = (event: KeyboardEvent): void => {
      hint.hidden = !event.getModifierState('CapsLock');
    };
    input.addEventListener('keydown', check);
    input.addEventListener('keyup', check);
    input.addEventListener('blur', () => {
      hint.hidden = true;
    });
  });
}

/**
 * Shows or clears the message under a field, in the slot whose id is the field's name plus "Error".
 * A message the server rendered for the same field is removed: it described the previous attempt.
 */
export function setFieldError(input: HTMLInputElement, message: string | null): void {
  const slot = find(`${input.name}Error`, HTMLElement);
  const text = slot?.firstElementChild;
  input.setAttribute('aria-invalid', String(message !== null));
  if (message !== null) input.closest('.field')?.classList.remove('is-valid');
  input.closest('.field')?.querySelector('[data-server-error]')?.remove();
  if (!slot || !text) return;
  if (message !== null) text.textContent = message;
  slot.classList.toggle('is-open', message !== null);
}

export interface FieldRule {
  input: HTMLInputElement;
  /** The message to show, or null when the value is acceptable. */
  check: (value: string) => string | null;
}

/**
 * Checks one field: shows its message, or marks it as good when it has a value that passes. An empty field
 * the user has not reached yet is left unmarked instead of being called wrong.
 */
export function validateField(rule: FieldRule, { quietWhenEmpty = false } = {}): boolean {
  const { input, check } = rule;
  const field = input.closest('.field');
  if (quietWhenEmpty && input.value === '') {
    setFieldError(input, null);
    field?.classList.remove('is-valid');
    return false;
  }
  const message = check(input.value);
  setFieldError(input, message);
  field?.classList.toggle('is-valid', message === null);
  return message === null;
}

/** Runs every rule, shows the messages and focuses the first field with one. True when all passed. */
export function validateFields(rules: readonly FieldRule[]): boolean {
  const failed = rules.filter((rule) => !validateField(rule));
  failed[0]?.input.focus();
  return failed.length === 0;
}
