// Command palette (Ctrl/⌘ + K): jump to a module or run a quick action from the keyboard.
// The entries are the links and buttons the server rendered inside the dialog, so it only offers what exists.
import { all, find } from './core/dom.js';
import { nextTheme, setTheme } from './theme.js';
const isTypingTarget = (target) => target instanceof HTMLInputElement ||
    target instanceof HTMLTextAreaElement ||
    target instanceof HTMLSelectElement ||
    (target instanceof HTMLElement && target.isContentEditable);
export function initCommandPalette() {
    const dialog = find('commandPalette', HTMLDialogElement);
    const input = find('commandPaletteInput', HTMLInputElement);
    const empty = find('commandPaletteEmpty', HTMLElement);
    if (!dialog || !input || !empty)
        return;
    const entries = [
        ...all('[data-command]', HTMLAnchorElement, dialog),
        ...all('[data-command]', HTMLButtonElement, dialog),
    ].sort((a, b) => (a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1));
    let active = 0;
    const visible = () => entries.filter((entry) => !entry.parentElement?.hidden);
    const highlight = (index) => {
        const shown = visible();
        active = shown.length === 0 ? 0 : (index + shown.length) % shown.length;
        entries.forEach((entry) => entry.removeAttribute('aria-selected'));
        const current = shown[active];
        current?.setAttribute('aria-selected', 'true');
        current?.scrollIntoView({ block: 'nearest' });
        if (current)
            input.setAttribute('aria-activedescendant', current.id);
        else
            input.removeAttribute('aria-activedescendant');
    };
    const filter = () => {
        const term = input.value.trim().toLocaleLowerCase('es');
        entries.forEach((entry) => {
            const item = entry.parentElement;
            if (item)
                item.hidden = term !== '' && !(entry.textContent ?? '').toLocaleLowerCase('es').includes(term);
        });
        empty.hidden = visible().length > 0;
        highlight(0);
    };
    const open = () => {
        if (dialog.open)
            return;
        input.value = '';
        filter();
        dialog.showModal();
        input.focus();
    };
    entries.forEach((entry, index) => {
        entry.id = `command-${index}`;
        if (entry instanceof HTMLButtonElement && entry.dataset['command'] === 'theme') {
            entry.addEventListener('click', () => {
                setTheme(nextTheme());
                dialog.close();
            });
        }
    });
    input.addEventListener('input', filter);
    input.addEventListener('keydown', (event) => {
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
            event.preventDefault();
            highlight(active + (event.key === 'ArrowDown' ? 1 : -1));
        }
        else if (event.key === 'Enter') {
            event.preventDefault();
            visible()[active]?.click();
        }
    });
    // A click on the backdrop lands on the dialog element itself.
    dialog.addEventListener('click', (event) => {
        if (event.target === dialog)
            dialog.close();
    });
    all('[data-open-command-palette]', HTMLButtonElement).forEach((button) => button.addEventListener('click', open));
    document.addEventListener('keydown', (event) => {
        const combo = (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k';
        // "?" opens it too, unless the user is typing somewhere or another dialog is in front.
        const help = event.key === '?' && !isTypingTarget(event.target) && !document.querySelector('dialog[open]');
        if (!combo && !help)
            return;
        event.preventDefault();
        if (dialog.open && combo)
            dialog.close();
        else
            open();
    });
}
