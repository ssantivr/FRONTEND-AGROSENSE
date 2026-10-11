// The assistant's chat window, on every signed-in page: written questions, voice messages and answers read
// aloud. The conversation lives in sessionStorage, so it survives moving between pages and ends with the tab;
// it is kept under the account's name and erased on the sign-in page, so nobody who uses the tab next sees it.
import { ApiError, csrfHeaders, isRecord, messageOf } from '../api/client.js';
import { el, find, icon } from '../core/dom.js';
import { plainText, renderMarkdown } from './markdown.js';
import { CHAT_STORAGE_KEY } from './storage.js';
import { canRecord, canSpeak, RecordingError, speak, stopSpeaking, VoiceRecorder } from './voice.js';
import type { RecordingFailure } from './voice.js';

type Role = 'user' | 'assistant';

interface Message {
  role: Role;
  text: string;
  /** An answer the assistant gave on its own, from the farm's figures, while the model was unavailable. */
  local?: boolean;
}

interface AiReply {
  reply: string;
  transcript?: string;
  /** 'local' when the answer did not come from the language model. */
  source?: string;
}

const SPEAK_KEY = 'agrosense.ai.speak';
/** Messages kept in the tab, and how many of them travel with a question as context. */
const MAX_STORED = 40;
const MAX_CONTEXT = 8;
const MAX_QUESTION = 1000;
const REQUEST_TIMEOUT_MS = 45_000;
const MAX_RECORDING_MS = 45_000;
const NEAR_BOTTOM_PX = 80;
const VOICE_PLACEHOLDER = 'Mensaje de voz';
const LOCAL_NOTE = 'Respuesta básica con los datos de tu finca: el modelo de IA no está disponible ahora.';

const SUGGESTIONS = ['¿Cómo están mis cultivos hoy?', '¿Cuándo conviene regar?', '¿Qué hago con mis alertas?'];

const RECORDING_MESSAGES: Record<RecordingFailure, string> = {
  unsupported: 'Este navegador no puede grabar audio. Escribe tu pregunta.',
  denied: 'No hay permiso para usar el micrófono. Actívalo en el navegador o escribe tu pregunta.',
  'no-microphone': 'No se encontró ningún micrófono. Escribe tu pregunta.',
  empty: 'No se grabó nada. Mantén la grabación un momento más.',
  failed: 'No se pudo grabar el audio. Inténtalo de nuevo o escribe tu pregunta.',
};

const isAiReply = (value: unknown): value is AiReply =>
  isRecord(value) &&
  typeof value['reply'] === 'string' &&
  (value['transcript'] === undefined || value['transcript'] === null || typeof value['transcript'] === 'string') &&
  (value['source'] === undefined || value['source'] === null || typeof value['source'] === 'string');

const isMessage = (value: unknown): value is Message =>
  isRecord(value) &&
  (value['role'] === 'user' || value['role'] === 'assistant') &&
  typeof value['text'] === 'string' &&
  (value['local'] === undefined || typeof value['local'] === 'boolean');

function loadMessages(owner: string): Message[] {
  try {
    const stored: unknown = JSON.parse(sessionStorage.getItem(CHAT_STORAGE_KEY) ?? 'null');
    if (!isRecord(stored) || stored['owner'] !== owner || !Array.isArray(stored['messages'])) return [];
    return stored['messages'].filter(isMessage).slice(-MAX_STORED);
  } catch {
    return [];
  }
}

function saveMessages(owner: string, messages: Message[]): void {
  try {
    sessionStorage.setItem(CHAT_STORAGE_KEY, JSON.stringify({ owner, messages: messages.slice(-MAX_STORED) }));
  } catch {
    // Storage is full or disabled: the conversation simply lasts until the page is left.
  }
}

function readFlag(key: string): boolean {
  try {
    return localStorage.getItem(key) === 'true';
  } catch {
    return false;
  }
}

function writeFlag(key: string, value: boolean): void {
  try {
    localStorage.setItem(key, String(value));
  } catch {
    // The preference is kept for this page only.
  }
}

/** Sends JSON or a form and resolves with the assistant's answer; every failure is an ApiError with a message. */
async function ask(url: string, body: FormData | Record<string, unknown>): Promise<AiReply> {
  const controller = new AbortController();
  let timedOut = false;
  const timer = window.setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, REQUEST_TIMEOUT_MS);
  const form = body instanceof FormData;
  let response: Response;
  try {
    response = await fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      signal: controller.signal,
      // A form sets its own Content-Type, with the boundary of its parts.
      headers: { Accept: 'application/json', ...(form ? {} : { 'Content-Type': 'application/json' }), ...csrfHeaders() },
      body: form ? body : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(timedOut ? 'timeout' : 'network');
  } finally {
    window.clearTimeout(timer);
  }
  if (response.redirected && new URL(response.url).pathname.endsWith('/login')) {
    throw new ApiError('unauthorized', { status: 401 });
  }
  const answer: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    const status = response.status;
    const kind =
      status === 429 ? 'rate-limited' : status === 401 ? 'unauthorized' : status === 403 ? 'forbidden' : status >= 500 ? 'server' : 'validation';
    // The assistant words its own failures for the user, including the ones answered with 5xx.
    const message = isRecord(answer) && typeof answer['message'] === 'string' ? answer['message'] : undefined;
    throw new ApiError(kind, { status, ...(message ? { message } : {}) });
  }
  if (!isAiReply(answer) || answer.reply.trim() === '') throw new ApiError('unexpected', { status: response.status });
  return answer;
}

const isRetriable = (error: unknown): boolean =>
  error instanceof ApiError && ['network', 'timeout', 'server', 'rate-limited', 'unexpected'].includes(error.kind);

function init(): void {
  const root = find('aiChat', HTMLElement);
  const launcher = find('aiChatLauncher', HTMLButtonElement);
  const panel = find('aiChatPanel', HTMLElement);
  const log = find('aiChatLog', HTMLElement);
  const status = find('aiChatStatus', HTMLElement);
  const form = find('aiChatForm', HTMLFormElement);
  const input = find('aiChatInput', HTMLTextAreaElement);
  const send = find('aiChatSend', HTMLButtonElement);
  const mic = find('aiChatMic', HTMLButtonElement);
  const speakToggle = find('aiChatSpeak', HTMLButtonElement);
  const clear = find('aiChatClear', HTMLButtonElement);
  const close = find('aiChatClose', HTMLButtonElement);
  // The widget is only on signed-in pages; where any piece is missing it stays out of the way.
  if (!root || !launcher || !panel || !log || !status || !form || !input || !send || !clear || !close) return;
  const chatUrl = root.dataset['chatUrl'];
  const voiceUrl = root.dataset['voiceUrl'];
  if (!chatUrl || !voiceUrl) return;

  const owner = root.dataset['owner'] ?? '';
  mount({ root, launcher, panel, log, status, form, input, send, clear, close, mic, speakToggle, chatUrl, voiceUrl, owner });
}

interface Parts {
  root: HTMLElement;
  launcher: HTMLButtonElement;
  panel: HTMLElement;
  log: HTMLElement;
  status: HTMLElement;
  form: HTMLFormElement;
  input: HTMLTextAreaElement;
  send: HTMLButtonElement;
  clear: HTMLButtonElement;
  close: HTMLButtonElement;
  mic: HTMLButtonElement | null;
  speakToggle: HTMLButtonElement | null;
  chatUrl: string;
  voiceUrl: string;
  /** The account the conversation belongs to. */
  owner: string;
}

function mount(parts: Parts): void {
  const { root, launcher, panel, log, status, form, input, send, clear, close, mic, speakToggle, chatUrl, voiceUrl } = parts;
  const { owner } = parts;
  let messages = loadMessages(owner);
  let pending = false;
  /** True while the browser is asking for the microphone, before the recording begins. */
  let starting = false;
  let readAloud = canSpeak() && readFlag(SPEAK_KEY);
  let typing: HTMLElement | null = null;
  const recorder = new VoiceRecorder();

  const nearBottom = (): boolean => log.scrollHeight - log.scrollTop - log.clientHeight < NEAR_BOTTOM_PX;
  const scrollToEnd = (): void => {
    log.scrollTop = log.scrollHeight;
  };

  function bubble(message: Message): HTMLElement {
    const item = el('div', `ai-chat-message ai-chat-message--${message.role}`);
    if (message.role === 'assistant') item.append(renderMarkdown(message.text));
    else item.textContent = message.text;
    // Said under the answer itself, so nobody takes a summary of the readings for the model's advice.
    if (message.local) item.append(el('p', 'ai-chat-note', LOCAL_NOTE));
    return item;
  }

  function showEmptyState(): void {
    const empty = el('div', 'ai-chat-empty');
    empty.append(
      icon('bi-stars'),
      el('p', 'ai-chat-empty-title', 'Hola, soy el asistente de AgroSense'),
      el('p', '', 'Pregúntame por tus cultivos, el riego o tus sensores. También puedes hablarme.'),
    );
    const list = el('div', 'ai-chat-suggestions');
    for (const suggestion of SUGGESTIONS) {
      const option = el('button', 'ai-chat-suggestion', suggestion);
      option.type = 'button';
      option.addEventListener('click', () => void sendText(suggestion));
      list.append(option);
    }
    empty.append(list);
    log.append(empty);
  }

  function renderAll(): void {
    log.replaceChildren();
    typing = null;
    if (messages.length === 0) showEmptyState();
    else for (const message of messages) log.append(bubble(message));
    scrollToEnd();
  }

  /** Adds a node at the end, following the conversation only when the reader is already at its end. */
  function append(node: HTMLElement, follow: boolean): void {
    const stick = follow || nearBottom();
    log.querySelector('.ai-chat-empty')?.remove();
    log.append(node);
    if (stick) scrollToEnd();
  }

  function addMessage(message: Message): HTMLElement {
    messages = [...messages, message].slice(-MAX_STORED);
    saveMessages(owner, messages);
    const node = bubble(message);
    append(node, message.role === 'user');
    return node;
  }

  function setStatus(text: string): void {
    status.textContent = text;
    status.hidden = text === '';
  }

  function refreshControls(): void {
    const recording = recorder.recording;
    input.disabled = pending || recording || starting;
    send.disabled = pending || recording || starting || input.value.trim() === '';
    clear.disabled = pending || recording || starting;
    if (mic) {
      mic.disabled = pending || starting;
      mic.setAttribute('aria-pressed', String(recording));
      mic.setAttribute('aria-label', recording ? 'Detener y enviar la grabación' : 'Grabar un mensaje de voz');
      mic.classList.toggle('is-recording', recording);
      mic.replaceChildren(icon(recording ? 'bi-stop-fill' : 'bi-mic'));
    }
  }

  function setPending(value: boolean): void {
    pending = value;
    typing?.remove();
    typing = null;
    if (value) {
      typing = el('div', 'ai-chat-message ai-chat-message--assistant ai-chat-typing');
      typing.setAttribute('role', 'status');
      typing.setAttribute('aria-label', 'El asistente está escribiendo');
      typing.append(el('span'), el('span'), el('span'));
      append(typing, true);
    }
    log.setAttribute('aria-busy', String(value));
    refreshControls();
  }

  /** Shows why there is no answer and, when trying again can help, a button that repeats the question. */
  function showError(error: unknown, retry: () => void): void {
    const note = el('div', 'ai-chat-message ai-chat-error');
    note.setAttribute('role', 'alert');
    note.append(icon('bi-exclamation-triangle'), el('span', '', messageOf(error)));
    if (isRetriable(error)) {
      const again = el('button', 'ai-chat-retry', 'Reintentar');
      again.type = 'button';
      again.addEventListener('click', () => {
        // A recording in progress is sent when it ends; asking again now would leave it without an answer.
        if (pending || starting || recorder.recording) return;
        note.remove();
        retry();
      });
      note.append(again);
    }
    append(note, true);
  }

  function showAnswer(answer: AiReply, spoken: boolean): void {
    const reply = answer.reply;
    addMessage({ role: 'assistant', text: reply, ...(answer.source === 'local' ? { local: true } : {}) });
    if (readAloud || spoken) speak(plainText(reply));
  }

  /** The question is already in the conversation: asks it, taking the turns before it as context. */
  async function requestAnswer(question: string): Promise<void> {
    if (pending) return;
    // Only who said what travels: the rest of a stored message is this window's own business.
    const history = messages
      .slice(0, -1)
      .slice(-MAX_CONTEXT)
      .map(({ role, text }) => ({ role, text }));
    setPending(true);
    try {
      const answer = await ask(chatUrl, { message: question, history });
      setPending(false);
      showAnswer(answer, false);
    } catch (error) {
      setPending(false);
      showError(error, () => void requestAnswer(question));
    }
    if (!panel.hidden) input.focus();
  }

  async function sendText(text: string): Promise<void> {
    const question = text.trim().slice(0, MAX_QUESTION);
    // The button is disabled while an answer is on its way; this also stops Enter and double clicks.
    if (pending || starting || recorder.recording || question === '') return;
    stopSpeaking();
    setStatus('');
    input.value = '';
    resizeInput();
    addMessage({ role: 'user', text: question });
    await requestAnswer(question);
  }

  async function requestVoiceAnswer(audio: Blob, placeholder: HTMLElement): Promise<void> {
    if (pending) return;
    const body = new FormData();
    body.append('audio', audio, 'voice');
    setPending(true);
    try {
      const answer = await ask(voiceUrl, body);
      setPending(false);
      const heard = answer.transcript?.trim();
      if (heard) {
        // The placeholder becomes what was actually said, here and in the stored conversation.
        placeholder.textContent = heard;
        let index = messages.length - 1;
        while (index >= 0 && !(messages[index]?.role === 'user' && messages[index]?.text === VOICE_PLACEHOLDER)) index--;
        if (index >= 0) {
          messages = messages.map((message, position) => (position === index ? { role: 'user', text: heard } : message));
          saveMessages(owner, messages);
        }
      }
      showAnswer(answer, true);
    } catch (error) {
      setPending(false);
      showError(error, () => void requestVoiceAnswer(audio, placeholder));
    }
  }

  async function finishRecording(): Promise<void> {
    if (!recorder.recording) return;
    try {
      const audio = await recorder.stop();
      setStatus('');
      refreshControls();
      const placeholder = addMessage({ role: 'user', text: VOICE_PLACEHOLDER });
      await requestVoiceAnswer(audio, placeholder);
    } catch (error) {
      setStatus(RECORDING_MESSAGES[error instanceof RecordingError ? error.reason : 'failed']);
      refreshControls();
    }
  }

  async function toggleRecording(): Promise<void> {
    if (pending || starting) return;
    if (recorder.recording) {
      await finishRecording();
      return;
    }
    stopSpeaking();
    starting = true;
    refreshControls();
    try {
      // false: the window was closed while the browser asked for permission, and the microphone was released.
      if (await recorder.start(MAX_RECORDING_MS, () => void finishRecording())) {
        setStatus('Grabando… pulsa de nuevo para enviar.');
      }
    } catch (error) {
      if (!panel.hidden) setStatus(RECORDING_MESSAGES[error instanceof RecordingError ? error.reason : 'failed']);
    }
    starting = false;
    refreshControls();
  }

  function resizeInput(): void {
    input.style.height = 'auto';
    input.style.height = `${Math.min(input.scrollHeight, 120)}px`;
  }

  function open(): void {
    panel.hidden = false;
    root.classList.add('is-open');
    launcher.setAttribute('aria-expanded', 'true');
    scrollToEnd();
    input.focus();
  }

  function shut(): void {
    recorder.cancel();
    stopSpeaking();
    setStatus('');
    panel.hidden = true;
    root.classList.remove('is-open');
    launcher.setAttribute('aria-expanded', 'false');
    refreshControls();
    launcher.focus();
  }

  launcher.addEventListener('click', () => (panel.hidden ? open() : shut()));
  close.addEventListener('click', shut);
  panel.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      event.stopPropagation();
      shut();
    }
  });
  form.addEventListener('submit', (event) => {
    event.preventDefault();
    void sendText(input.value);
  });
  input.addEventListener('input', () => {
    resizeInput();
    refreshControls();
  });
  input.addEventListener('keydown', (event) => {
    // Enter sends and Shift+Enter starts a new line; Enter while composing an accent or emoji is left alone.
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      void sendText(input.value);
    }
  });
  clear.addEventListener('click', () => {
    if (pending || starting || recorder.recording) return;
    stopSpeaking();
    messages = [];
    saveMessages(owner, messages);
    setStatus('');
    renderAll();
    input.focus();
  });
  if (mic) {
    mic.hidden = !canRecord();
    mic.addEventListener('click', () => void toggleRecording());
  }
  if (speakToggle) {
    speakToggle.hidden = !canSpeak();
    const paint = (): void => {
      speakToggle.setAttribute('aria-pressed', String(readAloud));
      speakToggle.replaceChildren(icon(readAloud ? 'bi-volume-up' : 'bi-volume-mute'));
    };
    paint();
    speakToggle.addEventListener('click', () => {
      readAloud = !readAloud;
      writeFlag(SPEAK_KEY, readAloud);
      if (!readAloud) stopSpeaking();
      paint();
    });
  }
  // Leaving the page must not leave the microphone open or a voice talking.
  window.addEventListener('pagehide', () => {
    recorder.cancel();
    stopSpeaking();
  });

  renderAll();
  refreshControls();
}

init();
