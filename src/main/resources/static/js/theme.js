// Theme switching shared by every page. theme-init has already applied the saved theme before first paint.
import { all } from './core/dom.js';
const THEME_KEY = 'agrosense.theme';
/** Browser chrome colour per theme, for <meta name="theme-color">. */
const THEME_COLORS = { premium: '#10291f', dark: '#0b1410', mono: '#0a0a0a' };
/** In the order the switch shows them. */
const THEMES = ['premium', 'dark', 'mono'];
const ARROW_STEPS = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 };
export const THEME_EVENT = 'agrosense:theme';
const isTheme = (value) => THEMES.some((theme) => theme === value);
export function currentTheme() {
    const theme = document.documentElement.dataset['theme'];
    return isTheme(theme) ? theme : 'premium';
}
/** The theme after the current one, wrapping around. */
export const nextTheme = () => THEMES[(THEMES.indexOf(currentTheme()) + 1) % THEMES.length] ?? 'premium';
/** Applies a theme, remembers it in this browser and tells the page (charts and maps redraw on THEME_EVENT). */
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
            // The preference is optional; ignore storage failures.
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
/** Wires every <div data-theme-toggle role="radiogroup">: click, arrow keys and roving tabindex. */
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
// Another tab changed the theme: follow it without writing it back.
window.addEventListener('storage', (event) => {
    if (event.key === THEME_KEY && isTheme(event.newValue))
        setTheme(event.newValue, { persist: false });
});
