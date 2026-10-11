// Voice for the assistant: recording a question with the microphone and reading an answer aloud.
// Both features are optional in browsers; each one says whether it is available and never throws for lack of it.

/** Containers browsers can record, in order of preference; the server accepts all of them. */
const RECORDING_TYPES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4'];

export type RecordingFailure = 'unsupported' | 'denied' | 'no-microphone' | 'empty' | 'failed';

export class RecordingError extends Error {
  readonly reason: RecordingFailure;

  constructor(reason: RecordingFailure) {
    super(reason);
    this.name = 'RecordingError';
    this.reason = reason;
  }
}

export const canRecord = (): boolean =>
  typeof MediaRecorder !== 'undefined' && typeof navigator.mediaDevices?.getUserMedia === 'function';

/** One recording at a time: start it, then stop it to get the audio, or cancel it to throw the audio away. */
export class VoiceRecorder {
  private recorder: MediaRecorder | null = null;
  private stream: MediaStream | null = null;
  private chunks: Blob[] = [];
  private limit: number | undefined;
  /** Changes on every cancel, so a start that was still asking for the microphone knows it is not wanted. */
  private generation = 0;

  get recording(): boolean {
    return this.recorder?.state === 'recording';
  }

  /**
   * Asks for the microphone and starts recording. `onLimit` runs when the recording reaches `maxMs`,
   * so the caller can stop and send it instead of letting it grow. Resolves with false, and with the
   * microphone released, when the recording was cancelled while the browser was asking for permission.
   */
  async start(maxMs: number, onLimit: () => void): Promise<boolean> {
    if (!canRecord()) throw new RecordingError('unsupported');
    this.cancel();
    const generation = this.generation;
    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (error) {
      const name = error instanceof DOMException ? error.name : '';
      if (name === 'NotAllowedError' || name === 'SecurityError') throw new RecordingError('denied');
      if (name === 'NotFoundError' || name === 'OverconstrainedError') throw new RecordingError('no-microphone');
      throw new RecordingError('failed');
    }
    if (generation !== this.generation) {
      stream.getTracks().forEach((track) => track.stop());
      return false;
    }
    try {
      const type = RECORDING_TYPES.find((candidate) => MediaRecorder.isTypeSupported(candidate));
      const recorder = new MediaRecorder(stream, type ? { mimeType: type } : {});
      this.chunks = [];
      recorder.addEventListener('dataavailable', (event) => {
        if (event.data.size > 0) this.chunks.push(event.data);
      });
      recorder.start();
      this.stream = stream;
      this.recorder = recorder;
      this.limit = window.setTimeout(onLimit, maxMs);
      return true;
    } catch {
      stream.getTracks().forEach((track) => track.stop());
      throw new RecordingError('failed');
    }
  }

  /** Ends the recording and resolves with the audio. The microphone is released either way. */
  stop(): Promise<Blob> {
    const recorder = this.recorder;
    if (!recorder || recorder.state === 'inactive') {
      this.release();
      return Promise.reject(new RecordingError('empty'));
    }
    return new Promise<Blob>((resolve, reject) => {
      recorder.addEventListener(
        'stop',
        () => {
          const audio = new Blob(this.chunks, { type: recorder.mimeType || 'audio/webm' });
          this.release();
          if (audio.size === 0) reject(new RecordingError('empty'));
          else resolve(audio);
        },
        { once: true },
      );
      recorder.addEventListener(
        'error',
        () => {
          this.release();
          reject(new RecordingError('failed'));
        },
        { once: true },
      );
      recorder.stop();
    });
  }

  /** Stops without keeping anything. Safe to call when nothing is being recorded. */
  cancel(): void {
    this.generation++;
    const recorder = this.recorder;
    if (recorder && recorder.state !== 'inactive') {
      try {
        recorder.stop();
      } catch {
        // Already stopping: the tracks are released below.
      }
    }
    this.release();
  }

  private release(): void {
    window.clearTimeout(this.limit);
    this.limit = undefined;
    this.stream?.getTracks().forEach((track) => track.stop());
    this.stream = null;
    this.recorder = null;
    this.chunks = [];
  }
}

export const canSpeak = (): boolean =>
  typeof window.speechSynthesis !== 'undefined' && typeof SpeechSynthesisUtterance !== 'undefined';

/** Stops whatever is being read aloud. */
export function stopSpeaking(): void {
  if (canSpeak()) window.speechSynthesis.cancel();
}

/** Reads the text aloud in Spanish, replacing anything still being read. Does nothing where unsupported. */
export function speak(text: string): void {
  if (!canSpeak() || text.trim() === '') return;
  const synthesis = window.speechSynthesis;
  synthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = 'es-ES';
  // Without a Spanish voice installed the browser's default reads it; the language tag still guides it.
  const voice = synthesis.getVoices().find((candidate) => candidate.lang.toLowerCase().startsWith('es'));
  if (voice) {
    utterance.voice = voice;
    utterance.lang = voice.lang;
  }
  synthesis.speak(utterance);
}
