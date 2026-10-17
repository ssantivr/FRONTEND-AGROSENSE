import { all, find } from './core/dom.js';

export const LIMITS = {
  name: { min: 2, max: 18 },
  password: { min: 8, max: 32 },
} as const;

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

export function passwordStrength(password: string): Strength {
  const { min } = LIMITS.password;
  const { length } = password;
  if (length === 0) return { level: 0, label: 'Escribe una contraseña para ver qué tan segura es.' };
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

const PASSWORD_RULES: Readonly<Record<string, (password: string) => boolean>> = {
  length: (password) => password.length >= LIMITS.password.min && password.length <= LIMITS.password.max,
  mixed: (password) => /\p{Ll}/u.test(password) && /\p{Lu}/u.test(password),
  digit: (password) => /\p{N}/u.test(password),
  symbol: (password) => /[^\p{L}\p{N}]/u.test(password),
};

export function initPasswordRules(): void {
  all('[data-rules-for]', HTMLElement).forEach((list) => {
    const input = find(list.dataset['rulesFor'] ?? '', HTMLInputElement);
    if (!input) return;
    const lines = all('[data-rule]', HTMLElement, list);
    const update = (): void => {
      lines.forEach((line) => {
        const met = PASSWORD_RULES[line.dataset['rule'] ?? '']?.(input.value) ?? false;
        line.classList.toggle('is-met', met);
        line.setAttribute('aria-label', `${line.textContent?.trim() ?? ''}: ${met ? 'cumplido' : 'pendiente'}`);
      });
    };
    input.addEventListener('input', update);
    update();
  });
}

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
  check: (value: string) => string | null;
}

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

export function validateFields(rules: readonly FieldRule[]): boolean {
  const failed = rules.filter((rule) => !validateField(rule));
  failed[0]?.input.focus();
  return failed.length === 0;
}
