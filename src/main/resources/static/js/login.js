// Show/hide toggle for the password field.
const input = document.getElementById('password');
const toggle = document.getElementById('togglePassword');

toggle?.addEventListener('click', () => {
  const reveal = input.type === 'password';
  input.type = reveal ? 'text' : 'password';
  toggle.setAttribute('aria-pressed', String(reveal));
  toggle.setAttribute('aria-label', reveal ? 'Ocultar contraseña' : 'Mostrar contraseña');
  toggle.querySelector('i').className = reveal ? 'bi bi-eye-slash' : 'bi bi-eye';
});
