"use strict";
(() => {
    const root = document.documentElement;
    const colors = { light: '#ffffff', dark: '#141d18', mono: '#ffffff' };
    let stored = null;
    try {
        stored = localStorage.getItem('agrosense.theme');
    }
    catch {
    }
    const preference = stored === 'light' || stored === 'premium' ? 'light' : stored === 'dark' ? 'dark' : 'system';
    const theme = preference !== 'system'
        ? preference
        : window.matchMedia('(prefers-contrast: more)').matches
            ? 'mono'
            : window.matchMedia('(prefers-color-scheme: dark)').matches
                ? 'dark'
                : 'light';
    root.dataset['theme'] = theme;
    root.dataset['themePreference'] = preference;
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', colors[theme] ?? '#ffffff');
    root.classList.add('js');
})();
