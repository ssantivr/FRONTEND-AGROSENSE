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
