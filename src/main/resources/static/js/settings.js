// Settings page: browser-level preferences and the account deletion dialog.
import { isSidebarCollapsed, setSidebarCollapsed } from './app.js';

const sidebarPreference = document.getElementById('prefSidebarCollapsed');
if (sidebarPreference) {
  sidebarPreference.checked = isSidebarCollapsed();
  sidebarPreference.addEventListener('change', () => setSidebarCollapsed(sidebarPreference.checked));
  // Keep the checkbox in step with the top bar button.
  document.getElementById('sidebarToggle')?.addEventListener('click', () => {
    sidebarPreference.checked = isSidebarCollapsed();
  });
}

const modal = document.getElementById('deleteAccountModal');
const openButton = document.getElementById('openDeleteAccount');
if (modal?.showModal && openButton) {
  openButton.addEventListener('click', () => {
    modal.showModal();
    document.getElementById('deleteAccountPassword').focus();
  });
  document.getElementById('cancelDeleteAccount').addEventListener('click', () => modal.close());
  modal.addEventListener('close', () => modal.querySelector('form').reset());
}
