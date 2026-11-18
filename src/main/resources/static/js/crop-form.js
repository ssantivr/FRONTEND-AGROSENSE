import { fillRanges } from './crop-template.js';
const select = document.getElementById('cropTemplate');
if (select instanceof HTMLSelectElement) {
    select.addEventListener('change', () => {
        const chosen = select.selectedOptions[0];
        if (!chosen)
            return;
        fillRanges(chosen.dataset, (name) => {
            const field = document.getElementById(name);
            return field instanceof HTMLInputElement ? field : null;
        });
    });
}
