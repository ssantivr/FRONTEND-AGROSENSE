"use strict";
// Applies the saved theme before the first paint. Loaded as a blocking classic script from <head>:
// the Content-Security-Policy forbids inline scripts, and a module would run after the page is drawn.
// It has no imports or exports on purpose, so it is compiled as a plain script.
(() => {
    const root = document.documentElement;
    let theme = null;
    try {
        theme = localStorage.getItem('agrosense.theme');
    }
    catch {
        // Storage can be blocked; the default below still applies.
    }
    if (theme !== 'premium' && theme !== 'dark' && theme !== 'mono') {
        // No choice saved yet: follow the system, high contrast first.
        theme = window.matchMedia('(prefers-contrast: more)').matches
            ? 'mono'
            : window.matchMedia('(prefers-color-scheme: dark)').matches
                ? 'dark'
                : 'premium';
    }
    root.dataset['theme'] = theme;
    root.classList.add('js');
})();
