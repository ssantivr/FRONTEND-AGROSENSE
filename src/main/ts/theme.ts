import { all } from './core/dom.js';

export type Theme = 'light' | 'dark' | 'mono';
export type ThemePreference = 'light' | 'dark' | 'system';

export interface ThemeChange {
  theme: Theme;
  preference: ThemePreference;
}

const THEME_KEY = 'agrosense.theme';
const THEME_COLORS: Record<Theme, string> = { light: '#ffffff', dark: '#141d18', mono: '#ffffff' };
const PREFERENCES: readonly ThemePreference[] = ['light', 'dark', 'system'];
const PREFERENCE_LABELS: Record<ThemePreference, string> = { light: 'Claro', dark: 'Oscuro', system: 'Sistema' };
const ARROW_STEPS: Readonly<Record<string, number>> = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 };
const DARK_QUERY = window.matchMedia('(prefers-color-scheme: dark)');
const CONTRAST_QUERY = window.matchMedia('(prefers-contrast: more)');
export const THEME_EVENT = 'agrosense:theme';

const isPreference = (value: unknown): value is ThemePreference => PREFERENCES.some((option) => option === value);

export function currentTheme(): Theme {
  const theme = document.documentElement.dataset['theme'];
  return theme === 'dark' || theme === 'mono' ? theme : 'light';
}

export function currentPreference(): ThemePreference {
  const preference = document.documentElement.dataset['themePreference'];
  return isPreference(preference) ? preference : 'system';
}

export const preferenceLabel = (preference: ThemePreference): string => PREFERENCE_LABELS[preference];

export const nextPreference = (): ThemePreference =>
  PREFERENCES[(PREFERENCES.indexOf(currentPreference()) + 1) % PREFERENCES.length] ?? 'system';

function resolve(preference: ThemePreference): Theme {
  if (preference !== 'system') return preference;
  if (CONTRAST_QUERY.matches) return 'mono';
  return DARK_QUERY.matches ? 'dark' : 'light';
}

function apply(preference: ThemePreference): void {
  const root = document.documentElement;
  const theme = resolve(preference);
  const changed = theme !== currentTheme();
  if (changed) {
    root.classList.add('theme-transition');
    window.setTimeout(() => root.classList.remove('theme-transition'), 260);
  }
  root.dataset['theme'] = theme;
  root.dataset['themePreference'] = preference;
  syncPage();
  if (changed) document.dispatchEvent(new CustomEvent<ThemeChange>(THEME_EVENT, { detail: { theme, preference } }));
}

export function setThemePreference(preference: ThemePreference, { persist = true }: { persist?: boolean } = {}): void {
  apply(preference);
  if (!persist) return;
  try {
    if (preference === 'system') localStorage.removeItem(THEME_KEY);
    else localStorage.setItem(THEME_KEY, preference);
  } catch {
  }
}

function syncPage(): void {
  const preference = currentPreference();
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', THEME_COLORS[currentTheme()]);
  all('[data-theme-value]', HTMLButtonElement).forEach((option) => {
    const selected = option.dataset['themeValue'] === preference;
    option.setAttribute('aria-checked', String(selected));
    option.tabIndex = selected ? 0 : -1;
  });
  all('input[name="themePreference"]', HTMLInputElement).forEach((input) => {
    input.checked = input.value === preference;
  });
}

export function initThemeToggles(): void {
  all('[data-theme-toggle]', HTMLElement).forEach((group) => {
    const options = all('[data-theme-value]', HTMLButtonElement, group);
    options.forEach((option, index) => {
      const choose = (target: HTMLButtonElement): void => {
        const preference = target.dataset['themeValue'];
        if (isPreference(preference)) setThemePreference(preference);
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
  all('input[name="themePreference"]', HTMLInputElement).forEach((input) =>
    input.addEventListener('change', () => {
      if (input.checked && isPreference(input.value)) setThemePreference(input.value);
    }),
  );
  syncPage();
}

const followSystem = (): void => {
  if (currentPreference() === 'system') apply('system');
};
DARK_QUERY.addEventListener('change', followSystem);
CONTRAST_QUERY.addEventListener('change', followSystem);

window.addEventListener('storage', (event) => {
  if (event.key !== THEME_KEY) return;
  setThemePreference(isPreference(event.newValue) ? event.newValue : 'system', { persist: false });
});

try {
  const stored = localStorage.getItem(THEME_KEY);
  if (stored !== null && stored !== 'light' && stored !== 'dark') {
    if (stored === 'premium') localStorage.setItem(THEME_KEY, 'light');
    else localStorage.removeItem(THEME_KEY);
  }
} catch {
}
