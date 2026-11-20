// Pure helpers about time and retries, shared by the widgets that refresh themselves. No DOM here, so node --test runs it.

const clockFormat = new Intl.DateTimeFormat('es-CO', { hour: '2-digit', minute: '2-digit', hour12: false });

/** "14:05": the time of day a widget last refreshed. */
export const clockTime = (when: Date): string => clockFormat.format(when);

/** "hace un momento", "hace 12 min", "hace 3 h", "hace 2 días": worded like SensorView.lastReadingAgo() on the server. */
export function agoText(when: Date, now: Date): string {
  const minutes = Math.max(0, Math.floor((now.getTime() - when.getTime()) / 60_000));
  if (minutes < 1) return 'hace un momento';
  if (minutes < 60) return `hace ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 48) return `hace ${hours} h`;
  return `hace ${Math.floor(hours / 24)} días`;
}

/** The next pause of an exponential backoff, never above the cap. */
export const nextBackoff = (waitMs: number, capMs: number): number => Math.min(Math.max(waitMs, 1) * 2, capMs);

/**
 * What a self-refreshing widget says about itself: after each success "Actualizado a las HH:MM"; after failures it
 * keeps the last good time and, from `warnAfter` failures in a row, says since when it is stale so the user can retry.
 */
export class RefreshTracker {
  private lastOk: Date | null;
  private failures = 0;

  constructor(
    lastOk: Date | null,
    private readonly warnAfter = 2,
  ) {
    this.lastOk = lastOk;
  }

  succeeded(at: Date): void {
    this.lastOk = at;
    this.failures = 0;
  }

  failed(): void {
    this.failures += 1;
  }

  get stale(): boolean {
    return this.failures >= this.warnAfter;
  }

  get failureCount(): number {
    return this.failures;
  }

  text(): string {
    if (this.stale) return this.lastOk ? `Sin actualizar desde las ${clockTime(this.lastOk)}` : 'Sin actualizar todavía';
    if (this.failures > 0) return 'No se pudo actualizar; se intentará de nuevo';
    return this.lastOk ? `Actualizado a las ${clockTime(this.lastOk)}` : '';
  }
}
