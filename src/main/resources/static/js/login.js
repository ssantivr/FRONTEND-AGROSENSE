// Show/hide toggles for password fields: <button data-toggle-password="inputId">.
document.querySelectorAll('[data-toggle-password]').forEach((toggle) => {
  const input = document.getElementById(toggle.dataset.togglePassword);
  if (!input) return;
  toggle.addEventListener('click', () => {
    const reveal = input.type === 'password';
    input.type = reveal ? 'text' : 'password';
    toggle.setAttribute('aria-pressed', String(reveal));
    toggle.setAttribute('aria-label', reveal ? 'Ocultar contraseña' : 'Mostrar contraseña');
    toggle.querySelector('i').className = reveal ? 'bi bi-eye-slash' : 'bi bi-eye';
  });
});

// Live e-mail availability on the sign-up form: <input data-email-availability="url">.
const emailInput = document.querySelector('[data-email-availability]');
const emailStatus = document.getElementById('emailAvailability');
if (emailInput && emailStatus) {
  let timer;
  let request;

  const show = (text, modifier) => {
    emailStatus.textContent = text;
    emailStatus.className = modifier ? `field-hint field-hint--${modifier}` : 'field-hint';
    emailStatus.hidden = !text;
  };

  const check = async () => {
    const email = emailInput.value.trim();
    request = new AbortController();
    show('Comprobando…');
    try {
      const response = await fetch(`${emailInput.dataset.emailAvailability}?email=${encodeURIComponent(email)}`, {
        headers: { Accept: 'application/json' },
        signal: request.signal,
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const { available } = await response.json();
      show(available ? 'Correo disponible.' : 'Este correo ya está registrado.', available ? 'ok' : 'taken');
    } catch (error) {
      if (error.name !== 'AbortError') show('No pudimos comprobar el correo. Inténtalo de nuevo.', 'taken');
    }
  };

  emailInput.addEventListener('input', () => {
    clearTimeout(timer);
    request?.abort();
    show('');
    if (emailInput.value.trim() && emailInput.checkValidity()) timer = setTimeout(check, 400);
  });
}
