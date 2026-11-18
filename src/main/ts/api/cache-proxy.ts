import type { DailyWater, EmailAvailability, EstateMarker, IrrigationRequest, ReadingSeries } from '../types/api.js';
import type { ApiPort } from './facade.js';

export const READINGS_TTL_MS = 30_000;
export const LISTS_TTL_MS = 60_000;

const WATER = 'water';
const MARKERS = 'markers';

interface Entry {
  value: unknown;
  expiresAt: number;
}

// Same interface as the real API. Going back and forth between sensors or time ranges does not ask the backend
// again, which may be asleep, while the answer is still fresh. Only answers are kept: a failure or an aborted
// request is never cached. Writes always go through, and irrigating drops the water figures it just changed.
export class CachingApiProxy implements ApiPort {
  private readonly entries = new Map<string, Entry>();

  constructor(
    private readonly real: ApiPort,
    private readonly now: () => number = Date.now,
  ) {}

  readings(sensorId: string, hours: number, signal?: AbortSignal): Promise<ReadingSeries> {
    return this.cached(`readings:${sensorId}:${hours}`, READINGS_TTL_MS, () =>
      this.real.readings(sensorId, hours, signal),
    );
  }

  waterUsage(): Promise<DailyWater[]> {
    return this.cached(WATER, LISTS_TTL_MS, () => this.real.waterUsage());
  }

  estateMarkers(): Promise<EstateMarker[]> {
    return this.cached(MARKERS, LISTS_TTL_MS, () => this.real.estateMarkers());
  }

  async startIrrigation(irrigation: IrrigationRequest): Promise<Record<string, unknown>> {
    const answer = await this.real.startIrrigation(irrigation);
    this.entries.delete(WATER);
    return answer;
  }

  // Whether an address is free can change at any moment, so it is never cached.
  checkEmail(url: string, email: string, signal: AbortSignal): Promise<EmailAvailability> {
    return this.real.checkEmail(url, email, signal);
  }

  private async cached<T>(key: string, ttlMs: number, load: () => Promise<T>): Promise<T> {
    const entry = this.entries.get(key);
    if (entry && this.now() < entry.expiresAt) return entry.value as T;
    const value = await load();
    this.entries.set(key, { value, expiresAt: this.now() + ttlMs });
    return value;
  }
}
