import { all, el, find, icon } from './core/dom.js';
import { initFeedback } from './feedback.js';
import { initCommandPalette } from './palette.js';
import { initTableTools } from './tables.js';
import { initThemeToggles } from './theme.js';

export type ToastType = 'success' | 'error' | 'info';

const SIDEBAR_KEY = 'agrosense.sidebarCollapsed';
const TOAST_ICONS: Record<ToastType, string> = {
  success: 'bi-check-circle',
  error: 'bi-exclamation-octagon',
  info: 'bi-info-circle',
};
const DESKTOP_QUERY = window.matchMedia('(min-width: 1024px)');

export function showToast(message: string, type: ToastType = 'info'): void {
  const region = find('toastRegion', HTMLElement);
  if (!region) return;
  const toast = el('div', `toast toast--${type}`);
  toast.setAttribute('role', type === 'error' ? 'alert' : 'status');
  const close = el('button', 'icon-button toast-close');
  close.type = 'button';
  close.setAttribute('aria-label', 'Cerrar aviso');
  close.append(icon('bi-x-lg'));
  const timer = el('span', 'toast-timer');
  timer.setAttribute('aria-hidden', 'true');
  toast.append(icon(TOAST_ICONS[type]), el('span', '', message), close, timer);
  region.append(toast);
  scheduleDismiss(toast);
}

export function confirmAction(question: string): Promise<boolean> {
  const modal = find('confirmModal', HTMLDialogElement);
  const text = find('confirmModalText', HTMLElement);
  if (!modal || !text) return Promise.resolve(window.confirm(question));
  text.textContent = question;
  modal.returnValue = 'cancel';
  modal.showModal();
  return new Promise((resolve) => {
    modal.addEventListener('close', () => resolve(modal.returnValue === 'confirm'), { once: true });
  });
}

function scheduleDismiss(toast: HTMLElement): void {
  const dismiss = (): void => {
    toast.classList.add('is-leaving');
    setTimeout(() => toast.remove(), 300);
  };
  toast.querySelector('.toast-timer')?.addEventListener('animationend', dismiss, { once: true });
  toast.querySelector('.toast-close')?.addEventListener('click', dismiss, { once: true });
}

export function isSidebarCollapsed(): boolean {
  try {
    return localStorage.getItem(SIDEBAR_KEY) === 'true';
  } catch {
    return false;
  }
}

export function setSidebarCollapsed(collapsed: boolean): void {
  find('appShell', HTMLElement)?.classList.toggle('is-collapsed', collapsed);
  try {
    localStorage.setItem(SIDEBAR_KEY, String(collapsed));
  } catch {
  }
}

function initSidebar(): void {
  const shell = find('appShell', HTMLElement);
  const toggle = find('sidebarToggle', HTMLButtonElement);
  if (!shell || !toggle) return;

  shell.classList.toggle('is-collapsed', isSidebarCollapsed());
  const syncExpanded = (): void => {
    const expanded = DESKTOP_QUERY.matches
      ? !shell.classList.contains('is-collapsed')
      : shell.classList.contains('is-open');
    toggle.setAttribute('aria-expanded', String(expanded));
  };

  toggle.addEventListener('click', () => {
    if (DESKTOP_QUERY.matches) {
      setSidebarCollapsed(!shell.classList.contains('is-collapsed'));
    } else {
      shell.classList.toggle('is-open');
    }
    syncExpanded();
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && shell.classList.contains('is-open')) {
      shell.classList.remove('is-open');
      syncExpanded();
      toggle.focus();
    }
  });
  document.addEventListener('click', (event) => {
    const outside =
      event.target instanceof Element && !event.target.closest('#sidebar, #sidebarToggle, [data-open-menu]');
    if (shell.classList.contains('is-open') && outside) {
      shell.classList.remove('is-open');
      syncExpanded();
    }
  });
  all('[data-open-menu]', HTMLButtonElement).forEach((button) =>
    button.addEventListener('click', () => {
      shell.classList.add('is-open');
      syncExpanded();
      shell.querySelector<HTMLElement>('.sidebar-link')?.focus();
    }),
  );
  DESKTOP_QUERY.addEventListener('change', syncExpanded);
  syncExpanded();
}

function initClock(): void {
  const clock = find('clock', HTMLTimeElement);
  if (!clock) return;
  const format = new Intl.DateTimeFormat('es-CO', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
  const tick = (): void => {
    const now = new Date();
    clock.dateTime = now.toISOString();
    clock.textContent = format.format(now);
  };
  tick();
  setInterval(tick, 30_000);
}

function initConfirmForms(): void {
  all('form[data-confirm]', HTMLFormElement).forEach((form) => {
    form.addEventListener('submit', (event) => {
      if (form.dataset['confirmed'] === 'true') return;
      event.preventDefault();
      const submitter = event.submitter instanceof HTMLElement ? event.submitter : null;
      void confirmAction(form.dataset['confirm'] ?? '').then((accepted) => {
        if (!accepted) return;
        form.dataset['confirmed'] = 'true';
        form.requestSubmit(submitter);
      });
    });
  });
}

initThemeToggles();
initSidebar();
initClock();
initConfirmForms();
initTableTools();
initCommandPalette();
initFeedback();
all('#toastRegion .toast', HTMLElement).forEach(scheduleDismiss);
