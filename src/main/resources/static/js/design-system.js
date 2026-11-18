import { confirmAction, showToast } from './app.js';
import { all, find } from './core/dom.js';
const isToastType = (value) => value === 'success' || value === 'error' || value === 'info';
all('[data-demo-toast]', HTMLButtonElement).forEach((button) => {
    const type = button.dataset['demoToast'];
    button.addEventListener('click', () => showToast(button.dataset['demoMessage'] ?? '', isToastType(type) ? type : 'info'));
});
find('demoConfirm', HTMLButtonElement)?.addEventListener('click', () => {
    void confirmAction('¿Confirmar esta acción de ejemplo?').then((accepted) => showToast(accepted ? 'Acción confirmada.' : 'Acción cancelada.', accepted ? 'success' : 'info'));
});
const reduceMotion = find('demoReduceMotion', HTMLInputElement);
reduceMotion?.addEventListener('change', () => {
    document.documentElement.classList.toggle('reduce-motion', reduceMotion.checked);
});
find('demoReplay', HTMLButtonElement)?.addEventListener('click', () => {
    const sample = find('demoMotionSample', HTMLElement);
    sample?.replaceWith(sample.cloneNode(true));
});
