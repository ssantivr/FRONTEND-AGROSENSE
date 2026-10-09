// Behaviour shared by every authenticated page: sidebar, clock, toasts, confirmations and table search.

const SIDEBAR_KEY = 'agrosense.sidebarCollapsed';
const TOAST_MS = 5000;
const DESKTOP_QUERY = window.matchMedia('(min-width: 1024px)');

/** Headers that authorise a same-origin fetch that changes data. */
export function csrfHeaders() {
  const token = document.querySelector('meta[name="_csrf"]')?.content;
  const header = document.querySelector('meta[name="_csrf_header"]')?.content;
  return token && header ? { [header]: token } : {};
}

export function showToast(message, type = 'info') {
  const region = document.getElementById('toastRegion');
  if (!region) return;
  const toast = document.createElement('div');
  toast.className = `toast toast--${type}`;
  const icon = document.createElement('i');
  icon.className = `bi ${type === 'success' ? 'bi-check-circle' : 'bi-info-circle'}`;
  icon.setAttribute('aria-hidden', 'true');
  const text = document.createElement('span');
  text.textContent = message;
  toast.append(icon, text);
  region.append(toast);
  scheduleDismiss(toast);
}

/** Resolves to true when the user accepts the question in the shared modal. */
export function confirmAction(question) {
  const modal = document.getElementById('confirmModal');
  if (!modal?.showModal) return Promise.resolve(window.confirm(question));
  document.getElementById('confirmModalText').textContent = question;
  modal.returnValue = 'cancel';
  modal.showModal();
  return new Promise((resolve) => {
    modal.addEventListener('close', () => resolve(modal.returnValue === 'confirm'), { once: true });
  });
}

function scheduleDismiss(toast) {
  setTimeout(() => {
    toast.classList.add('is-leaving');
    setTimeout(() => toast.remove(), 300);
  }, TOAST_MS);
}

export function isSidebarCollapsed() {
  try {
    return localStorage.getItem(SIDEBAR_KEY) === 'true';
  } catch {
    return false;
  }
}

/** Applies and remembers the desktop sidebar preference for this browser. */
export function setSidebarCollapsed(collapsed) {
  document.getElementById('appShell')?.classList.toggle('is-collapsed', collapsed);
  try {
    localStorage.setItem(SIDEBAR_KEY, String(collapsed));
  } catch {
    // The preference is optional; ignore storage failures.
  }
}

function initSidebar() {
  const shell = document.getElementById('appShell');
  const toggle = document.getElementById('sidebarToggle');
  if (!shell || !toggle) return;

  shell.classList.toggle('is-collapsed', isSidebarCollapsed());
  const syncExpanded = () => {
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
    if (shell.classList.contains('is-open') && !event.target.closest('#sidebar, #sidebarToggle')) {
      shell.classList.remove('is-open');
      syncExpanded();
    }
  });
  DESKTOP_QUERY.addEventListener('change', syncExpanded);
  syncExpanded();
}

function initClock() {
  const clock = document.getElementById('clock');
  if (!clock) return;
  const format = new Intl.DateTimeFormat('es-CO', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
  const tick = () => {
    const now = new Date();
    clock.dateTime = now.toISOString();
    clock.textContent = format.format(now);
  };
  tick();
  setInterval(tick, 30_000);
}

function initConfirmForms() {
  document.querySelectorAll('form[data-confirm]').forEach((form) => {
    form.addEventListener('submit', async (event) => {
      if (form.dataset.confirmed === 'true') return;
      event.preventDefault();
      if (await confirmAction(form.dataset.confirm)) {
        form.dataset.confirmed = 'true';
        form.requestSubmit(event.submitter ?? undefined);
      }
    });
  });
}

function initTableSearch() {
  const rows = [...document.querySelectorAll('[data-search-row]')];
  const box = document.getElementById('tableSearch');
  const input = document.getElementById('tableSearchInput');
  if (!box || !input || rows.length === 0) return;
  box.hidden = false;
  input.addEventListener('input', () => {
    const term = input.value.trim().toLocaleLowerCase('es');
    rows.forEach((row) => {
      row.hidden = term !== '' && !row.textContent.toLocaleLowerCase('es').includes(term);
    });
  });
}

initSidebar();
initClock();
initConfirmForms();
initTableSearch();
document.querySelectorAll('#toastRegion .toast').forEach(scheduleDismiss);
