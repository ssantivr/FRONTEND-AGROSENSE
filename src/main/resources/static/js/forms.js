import { all, find } from './core/dom.js';
export const LIMITS = {
    name: { min: 2, max: 18 },
    password: { min: 8, max: 32 },
};
/** Spaces at the ends and repeated ones in between do not count: the server trims them before checking. */
export const normalizedText = (value) => value.trim().replace(/\s+/g, ' ');
// A hard maxlength would cut a pasted compound surname or password without a word, so the limit is soft
// (data-max): the counter turns red past it and the field's check explains it.
export function initCounters() {
    all('input[data-counter]', HTMLInputElement).forEach((input) => {
        const counter = find(input.dataset['counter'] ?? '', HTMLElement);
        const max = Number(input.dataset['max'] ?? input.maxLength);
        if (!counter || !(max > 0))
            return;
        const counted = () => (input.type === 'password' ? input.value : normalizedText(input.value)).length;
        const update = () => {
            const length = counted();
            counter.textContent = `${length}/${max}`;
            counter.classList.toggle('is-limit', length === max);
            counter.classList.toggle('is-over', length > max);
            counter.classList.toggle('is-short', length > 0 && length < input.minLength);
        };
        input.addEventListener('input', update);
        update();
    });
}
export function passwordStrength(password) {
    const { min } = LIMITS.password;
    const { length } = password;
    if (length === 0)
        return { level: 0, label: 'Escribe una contraseña para ver qué tan segura es.' };
    if (length < min) {
        const missing = min - length;
        return { level: 1, label: `Muy corta: ${missing === 1 ? 'falta 1 carácter' : `faltan ${missing} caracteres`}.` };
    }
    const kinds = [/\p{Ll}/u, /\p{Lu}/u, /\p{N}/u, /[^\p{L}\p{N}]/u].filter((kind) => kind.test(password)).length;
    const score = kinds + (length >= 12 ? 1 : 0) + (length >= 16 ? 1 : 0);
    if (score <= 2)
        return { level: 2, label: 'Débil: alárgala o combina mayúsculas, números y símbolos.' };
    if (score <= 4)
        return { level: 3, label: 'Aceptable.' };
    return { level: 4, label: 'Fuerte.' };
}
export function initPasswordMeters() {
    all('[data-strength-for]', HTMLElement).forEach((meter) => {
        const input = find(meter.dataset['strengthFor'] ?? '', HTMLInputElement);
        const label = meter.querySelector('[data-strength-label]');
        if (!input || !label)
            return;
        const update = () => {
            const strength = passwordStrength(input.value);
            meter.dataset['level'] = String(strength.level);
            label.textContent = strength.label;
        };
        input.addEventListener('input', update);
        update();
    });
}
const PASSWORD_RULES = {
    length: (password) => password.length >= LIMITS.password.min && password.length <= LIMITS.password.max,
    mixed: (password) => /\p{Ll}/u.test(password) && /\p{Lu}/u.test(password),
    digit: (password) => /\p{N}/u.test(password),
    symbol: (password) => /[^\p{L}\p{N}]/u.test(password),
};
export function initPasswordRules() {
    all('[data-rules-for]', HTMLElement).forEach((list) => {
        const input = find(list.dataset['rulesFor'] ?? '', HTMLInputElement);
        if (!input)
            return;
        const lines = all('[data-rule]', HTMLElement, list);
        const update = () => {
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
export function initCapsWarnings() {
    all('input[data-caps-warning]', HTMLInputElement).forEach((input) => {
        const hint = find(input.dataset['capsWarning'] ?? '', HTMLElement);
        if (!hint)
            return;
        const check = (event) => {
            hint.hidden = !event.getModifierState('CapsLock');
        };
        input.addEventListener('keydown', check);
        input.addEventListener('keyup', check);
        input.addEventListener('blur', () => {
            hint.hidden = true;
        });
    });
}
export function setFieldError(input, message) {
    const slot = find(`${input.name}Error`, HTMLElement);
    const text = slot?.firstElementChild;
    input.setAttribute('aria-invalid', String(message !== null));
    if (message !== null)
        input.closest('.field')?.classList.remove('is-valid');
    input.closest('.field')?.querySelector('[data-server-error]')?.remove();
    if (!slot || !text)
        return;
    if (message !== null)
        text.textContent = message;
    slot.classList.toggle('is-open', message !== null);
}
export function validateField(rule, { quietWhenEmpty = false } = {}) {
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
export function validateFields(rules) {
    const failed = rules.filter((rule) => !validateField(rule));
    failed[0]?.input.focus();
    return failed.length === 0;
}
