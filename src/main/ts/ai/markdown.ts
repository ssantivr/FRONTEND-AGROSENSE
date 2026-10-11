// The small part of Markdown the assistant uses, turned into DOM nodes. Nothing is ever parsed as HTML:
// every piece of text goes through textContent, so an answer cannot inject markup or scripts.
import { el } from '../core/dom.js';

const INLINE = /(\*\*([^*\n]+)\*\*|__([^_\n]+)__|\*([^*\n]+)\*|`([^`\n]+)`|\[([^\]\n]+)\]\((https?:\/\/[^\s)]+)\))/g;
const BULLET = /^\s*[-*•]\s+(.*)$/;
const NUMBERED = /^\s*\d+[.)]\s+(.*)$/;
const HEADING = /^\s*#{1,6}\s+(.*)$/;
const FENCE = /^\s*```/;

/** Bold, italics, inline code and http(s) links inside one line. */
function inline(parent: HTMLElement, text: string): void {
  let last = 0;
  for (const match of text.matchAll(INLINE)) {
    const start = match.index ?? 0;
    if (start > last) parent.append(text.slice(last, start));
    const [whole, , bold, boldAlt, italic, code, label, url] = match;
    if (bold !== undefined || boldAlt !== undefined) parent.append(el('strong', '', bold ?? boldAlt ?? ''));
    else if (italic !== undefined) parent.append(el('em', '', italic));
    else if (code !== undefined) parent.append(el('code', '', code));
    else if (label !== undefined && url !== undefined) {
      const link = el('a', '', label);
      link.href = url;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      parent.append(link);
    } else parent.append(whole);
    last = start + whole.length;
  }
  if (last < text.length) parent.append(text.slice(last));
}

/** Paragraphs, headings, bulleted and numbered lists and fenced code, in the order they appear. */
export function renderMarkdown(source: string): DocumentFragment {
  const fragment = document.createDocumentFragment();
  const lines = source.replace(/\r\n?/g, '\n').split('\n');
  let paragraph: string[] = [];
  let list: HTMLUListElement | HTMLOListElement | null = null;
  let code: string[] | null = null;

  const closeParagraph = (): void => {
    if (paragraph.length === 0) return;
    const block = el('p');
    paragraph.forEach((line, index) => {
      if (index > 0) block.append(el('br'));
      inline(block, line);
    });
    fragment.append(block);
    paragraph = [];
  };
  const closeList = (): void => {
    list = null;
  };

  for (const line of lines) {
    if (code !== null) {
      if (FENCE.test(line)) {
        const block = el('pre');
        block.append(el('code', '', code.join('\n')));
        fragment.append(block);
        code = null;
      } else code.push(line);
      continue;
    }
    if (FENCE.test(line)) {
      closeParagraph();
      closeList();
      code = [];
      continue;
    }
    const bullet = BULLET.exec(line);
    const numbered = bullet ? null : NUMBERED.exec(line);
    const item = bullet?.[1] ?? numbered?.[1];
    if (item !== undefined) {
      closeParagraph();
      const tag = bullet ? 'ul' : 'ol';
      if (list === null || list.tagName.toLowerCase() !== tag) {
        list = el(tag);
        fragment.append(list);
      }
      const entry = el('li');
      inline(entry, item);
      list.append(entry);
      continue;
    }
    closeList();
    const heading = HEADING.exec(line);
    if (heading?.[1] !== undefined) {
      closeParagraph();
      const block = el('p', 'ai-chat-heading');
      inline(block, heading[1]);
      fragment.append(block);
    } else if (line.trim() === '') closeParagraph();
    else paragraph.push(line.trim());
  }
  // An answer cut short may leave a fence open: what was inside is still shown.
  if (code !== null && code.length > 0) {
    const block = el('pre');
    block.append(el('code', '', code.join('\n')));
    fragment.append(block);
  }
  closeParagraph();
  return fragment;
}

/** The same text without its marks, for reading aloud. */
export function plainText(source: string): string {
  return source
    .replace(/```/g, ' ')
    .replace(/\[([^\]\n]+)\]\((https?:\/\/[^\s)]+)\)/g, '$1')
    .replace(/(\*\*|__|\*|`)/g, '')
    .replace(/^\s*#{1,6}\s+/gm, '')
    .replace(/^\s*[-*•]\s+/gm, '')
    .replace(/\s+/g, ' ')
    .trim();
}
