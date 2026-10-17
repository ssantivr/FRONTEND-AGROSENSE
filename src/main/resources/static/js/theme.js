import { all } from './core/dom.js';
const THEME_KEY = 'agrosense.theme';
const THEME_COLORS = { premium: '#10291f', dark: '#0b1410', mono: '#0a0a0a' };
const THEMES = ['premium', 'dark', 'mono'];
const ARROW_STEPS = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 };
export const THEME_EVENT = 'agrosense:theme';
const isTheme = (value) => THEMES.some((theme) => theme === value);
export function currentTheme() {
    const theme = document.documentElement.dataset['theme'];
    return isTheme(theme) ? theme : 'premium';
}
export const nextTheme = () => THEMES[(THEMES.indexOf(currentTheme()) + 1) % THEMES.length] ?? 'premium';
export function setTheme(theme, { persist = true } = {}) {
    const root = document.documentElement;
    if (theme !== currentTheme()) {
        root.classList.add('theme-transition');
        setTimeout(() => root.classList.remove('theme-transition'), 250);
    }
    root.dataset['theme'] = theme;
    syncPage();
    if (persist) {
        try {
            localStorage.setItem(THEME_KEY, theme);
        }
        catch {
        }
    }
    document.dispatchEvent(new CustomEvent(THEME_EVENT, { detail: { theme } }));
}
function syncPage() {
    const theme = currentTheme();
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', THEME_COLORS[theme]);
    all('[data-theme-value]', HTMLButtonElement).forEach((option) => {
        const selected = option.dataset['themeValue'] === theme;
        option.setAttribute('aria-checked', String(selected));
        option.tabIndex = selected ? 0 : -1;
    });
}
export function initThemeToggles() {
    all('[data-theme-toggle]', HTMLElement).forEach((group) => {
        const options = all('[data-theme-value]', HTMLButtonElement, group);
        options.forEach((option, index) => {
            const choose = (target) => {
                const theme = target.dataset['themeValue'];
                if (isTheme(theme))
                    setTheme(theme);
            };
            option.addEventListener('click', () => choose(option));
            option.addEventListener('keydown', (event) => {
                const step = ARROW_STEPS[event.key];
                const next = step === undefined ? undefined : options[(index + step + options.length) % options.length];
                if (!next)
                    return;
                event.preventDefault();
                choose(next);
                next.focus();
            });
        });
    });
    syncPage();
}
window.addEventListener('storage', (event) => {
    if (event.key === THEME_KEY && isTheme(event.newValue))
        setTheme(event.newValue, { persist: false });
});
