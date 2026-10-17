import { isSidebarCollapsed, setSidebarCollapsed } from './app.js';
import { find } from './core/dom.js';

const sidebarPreference = find('prefSidebarCollapsed', HTMLInputElement);
if (sidebarPreference) {
  sidebarPreference.checked = isSidebarCollapsed();
  sidebarPreference.addEventListener('change', () => setSidebarCollapsed(sidebarPreference.checked));
  find('sidebarToggle', HTMLButtonElement)?.addEventListener('click', () => {
    sidebarPreference.checked = isSidebarCollapsed();
  });
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
