(() => {
  const root = document.documentElement;
  const colors: Record<string, string> = { light: '#ffffff', dark: '#0b1a15', mono: '#ffffff' };
  let stored: string | null = null;
  try {
    stored = localStorage.getItem('agrosense.theme');
    if (localStorage.getItem('agrosense.reduceMotion') === '1') root.classList.add('reduce-motion');
    // The brand intro plays once per browser session, on the first page after signing in (only that layout
    // has it); app.ts records that it played.
    if (
      sessionStorage.getItem('agrosense.introShown') === null &&
      !root.classList.contains('reduce-motion') &&
      !window.matchMedia('(prefers-reduced-motion: reduce)').matches
    ) {
      root.classList.add('intro-pending');
    }
  } catch {
  }
  const preference = stored === 'light' || stored === 'premium' ? 'light' : stored === 'dark' ? 'dark' : 'system';
  const theme =
    preference !== 'system'
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
