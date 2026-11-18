import { isSidebarCollapsed, setSidebarCollapsed } from './app.js';
import { find, setReducedMotion } from './core/dom.js';

const sidebarPreference = find('prefSidebarCollapsed', HTMLInputElement);
if (sidebarPreference) {
  sidebarPreference.checked = isSidebarCollapsed();
  sidebarPreference.addEventListener('change', () => setSidebarCollapsed(sidebarPreference.checked));
  find('sidebarToggle', HTMLButtonElement)?.addEventListener('click', () => {
    sidebarPreference.checked = isSidebarCollapsed();
  });
}

const motionPreference = find('prefReduceMotion', HTMLInputElement);
if (motionPreference) {
  motionPreference.checked = document.documentElement.classList.contains('reduce-motion');
  motionPreference.addEventListener('change', () => setReducedMotion(motionPreference.checked));
}

const modal = find('deleteAccountModal', HTMLDialogElement);
const openButton = find('openDeleteAccount', HTMLButtonElement);
if (modal && openButton) {
  openButton.addEventListener('click', () => {
    modal.showModal();
    find('deleteAccountPassword', HTMLInputElement)?.focus();
  });
  find('cancelDeleteAccount', HTMLButtonElement)?.addEventListener('click', () => modal.close());
  modal.addEventListener('close', () => modal.querySelector('form')?.reset());
}
