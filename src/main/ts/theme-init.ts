(() => {
  const root = document.documentElement;
  let theme: string | null = null;
  try {
    theme = localStorage.getItem('agrosense.theme');
  } catch {
  }
  if (theme !== 'premium' && theme !== 'dark' && theme !== 'mono') {
    theme = window.matchMedia('(prefers-contrast: more)').matches
      ? 'mono'
      : window.matchMedia('(prefers-color-scheme: dark)').matches
        ? 'dark'
        : 'premium';
  }
  root.dataset['theme'] = theme;
  root.classList.add('js');
})();
