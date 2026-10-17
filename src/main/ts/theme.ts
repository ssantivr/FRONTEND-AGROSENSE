import { all } from './core/dom.js';

export type Theme = 'premium' | 'dark' | 'mono';

export interface ThemeChange {
  theme: Theme;
}

const THEME_KEY = 'agrosense.theme';
const THEME_COLORS: Record<Theme, string> = { premium: '#10291f', dark: '#0b1410', mono: '#0a0a0a' };
const THEMES: readonly Theme[] = ['premium', 'dark', 'mono'];
const ARROW_STEPS: Readonly<Record<string, number>> = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 };
export const THEME_EVENT = 'agrosense:theme';

const isTheme = (value: unknown): value is Theme => THEMES.some((theme) => theme === value);

export function currentTheme(): Theme {
  const theme = document.documentElement.dataset['theme'];
  return isTheme(theme) ? theme : 'premium';
}

export const nextTheme = (): Theme => THEMES[(THEMES.indexOf(currentTheme()) + 1) % THEMES.length] ?? 'premium';

export function setTheme(theme: Theme, { persist = true }: { persist?: boolean } = {}): void {
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
    } catch {
    }
  }
  document.dispatchEvent(new CustomEvent<ThemeChange>(THEME_EVENT, { detail: { theme } }));
}

function syncPage(): void {
  const theme = currentTheme();
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', THEME_COLORS[theme]);
  all('[data-theme-value]', HTMLButtonElement).forEach((option) => {
    const selected = option.dataset['themeValue'] === theme;
    option.setAttribute('aria-checked', String(selected));
    option.tabIndex = selected ? 0 : -1;
  });
}

export function initThemeToggles(): void {
  all('[data-theme-toggle]', HTMLElement).forEach((group) => {
    const options = all('[data-theme-value]', HTMLButtonElement, group);
    options.forEach((option, index) => {
      const choose = (target: HTMLButtonElement): void => {
        const theme = target.dataset['themeValue'];
        if (isTheme(theme)) setTheme(theme);
      };
      option.addEventListener('click', () => choose(option));
      option.addEventListener('keydown', (event) => {
        const step = ARROW_STEPS[event.key];
        const next = step === undefined ? undefined : options[(index + step + options.length) % options.length];
        if (!next) return;
        event.preventDefault();
        choose(next);
        next.focus();
      });
    });
  });
  syncPage();
}

window.addEventListener('storage', (event) => {
  if (event.key === THEME_KEY && isTheme(event.newValue)) setTheme(event.newValue, { persist: false });
});
