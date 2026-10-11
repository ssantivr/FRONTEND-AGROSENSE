// Tools for the tables the server renders: search across the page's rows, and export of what is visible.
import { all, find } from './core/dom.js';
/**
 * The topbar search box filters everything marked data-search-row (table rows, cards, timeline items);
 * it stays hidden on pages without any.
 */
function initTableSearch() {
    const rows = all('[data-search-row]', HTMLElement);
    const box = find('tableSearch', HTMLElement);
    const input = find('tableSearchInput', HTMLInputElement);
    if (!box || !input || rows.length === 0)
        return;
    box.hidden = false;
    let timer;
    input.addEventListener('input', () => {
        window.clearTimeout(timer);
        timer = window.setTimeout(() => {
            const term = input.value.trim().toLocaleLowerCase('es');
            rows.forEach((row) => {
                row.hidden = term !== '' && !(row.textContent ?? '').toLocaleLowerCase('es').includes(term);
            });
        }, 120);
    });
}
const cellText = (cell) => (cell.textContent ?? '').replace(/\s+/g, ' ').trim();
/** Quotes a value when it needs it, and stops spreadsheets from reading it as a formula. */
function csvValue(text) {
    const safe = /^[=+\-@]/.test(text) && !/^-?[\d.,]+$/.test(text) ? `'${text}` : text;
    return /[";\n]/.test(safe) ? `"${safe.replaceAll('"', '""')}"` : safe;
}
/**
 * The table as CSV: the rows the search left visible, without the columns marked data-no-export.
 * Semicolons separate the values because the numbers use a decimal comma.
 */
export function tableToCsv(table) {
    const skipped = new Set();
    const header = all('thead th', HTMLTableCellElement, table).filter((cell, index) => {
        if ('noExport' in cell.dataset)
            skipped.add(index);
        return !skipped.has(index);
    });
    const lines = [header.map((cell) => csvValue(cellText(cell))).join(';')];
    all('tbody tr', HTMLTableRowElement, table)
        .filter((row) => !row.hidden)
        .forEach((row) => {
        const cells = [...row.cells].filter((_, index) => !skipped.has(index));
        lines.push(cells.map((cell) => csvValue(cellText(cell))).join(';'));
    });
    return lines.join('\r\n');
}
/** <button data-export-csv="tableId" data-filename="riegos"> downloads that table. */
function initCsvExport() {
    all('[data-export-csv]', HTMLButtonElement).forEach((button) => {
        const table = find(button.dataset['exportCsv'] ?? '', HTMLTableElement);
        if (!table) {
            button.hidden = true;
            return;
        }
        button.addEventListener('click', () => {
            // The byte-order mark makes spreadsheet programs read the accents as UTF-8.
            const url = URL.createObjectURL(new Blob(['﻿', tableToCsv(table)], { type: 'text/csv;charset=utf-8' }));
            const link = document.createElement('a');
            link.href = url;
            link.download = `${button.dataset['filename'] ?? 'agrosense'}-${new Date().toISOString().slice(0, 10)}.csv`;
            link.click();
            URL.revokeObjectURL(url);
        });
    });
}
/**
 * <div data-view-toggle="targetId"> with <button data-view="cards|table">: sets data-view on #targetId,
 * which CSS uses to show one panel, and remembers the choice in this browser under the target's id.
 */
function initViewToggles() {
    all('[data-view-toggle]', HTMLElement).forEach((group) => {
        const targetId = group.dataset['viewToggle'] ?? '';
        const target = find(targetId, HTMLElement);
        const options = all('button[data-view]', HTMLButtonElement, group);
        if (!target)
            return;
        const key = `agrosense.view.${targetId}`;
        const apply = (view) => {
            target.dataset['view'] = view;
            options.forEach((option) => option.setAttribute('aria-pressed', String(option.dataset['view'] === view)));
        };
        options.forEach((option) => option.addEventListener('click', () => {
            const view = option.dataset['view'] ?? '';
            apply(view);
            try {
                localStorage.setItem(key, view);
            }
            catch {
                // The preference is optional; ignore storage failures.
            }
        }));
        try {
            const saved = localStorage.getItem(key);
            if (saved && options.some((option) => option.dataset['view'] === saved))
                apply(saved);
        }
        catch {
            // Keep the view the server rendered.
        }
    });
}
/** A number as the tables print it ("1.750", "46,8 %"), or null when the text is not one. */
function printedNumber(text) {
    const match = /^-?[\d.]+(,\d+)?/.exec(text.trim());
    if (!match)
        return null;
    const value = Number(match[0].replaceAll('.', '').replace(',', '.'));
    return Number.isFinite(value) ? value : null;
}
/** What a cell sorts by: its data-sort attribute when it has one (dates), else its text. */
const sortKey = (cell) => (cell ? (cell.dataset['sort'] ?? cellText(cell)) : '');
/**
 * <table data-sortable>: a click on a header sorts by that column, a second click reverses it. The state
 * is announced with aria-sort. Columns marked data-no-export (actions) are not sortable.
 */
function initSortableTables() {
    all('table[data-sortable]', HTMLTableElement).forEach((table) => {
        const body = table.tBodies[0];
        if (!body)
            return;
        const headers = all('thead th', HTMLTableCellElement, table);
        headers.forEach((header, column) => {
            if ('noExport' in header.dataset)
                return;
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'table-sort';
            button.append(...header.childNodes);
            header.append(button);
            header.setAttribute('aria-sort', 'none');
            button.addEventListener('click', () => {
                const ascending = header.getAttribute('aria-sort') !== 'ascending';
                headers.forEach((other) => other.hasAttribute('aria-sort') && other.setAttribute('aria-sort', 'none'));
                header.setAttribute('aria-sort', ascending ? 'ascending' : 'descending');
                const rows = [...body.rows];
                const numeric = rows.every((row) => {
                    const key = sortKey(row.cells[column]);
                    return key === '' || key === '—' || printedNumber(key) !== null;
                });
                rows.sort((a, b) => {
                    const left = sortKey(a.cells[column]);
                    const right = sortKey(b.cells[column]);
                    const order = numeric
                        ? (printedNumber(left) ?? -Infinity) - (printedNumber(right) ?? -Infinity)
                        : left.localeCompare(right, 'es', { numeric: true, sensitivity: 'base' });
                    return ascending ? order : -order;
                });
                body.append(...rows);
            });
        });
    });
}
export function initTableTools() {
    initSortableTables();
    initViewToggles();
    initTableSearch();
    initCsvExport();
}
