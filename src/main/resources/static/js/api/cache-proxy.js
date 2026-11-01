export const READINGS_TTL_MS = 30_000;
export const LISTS_TTL_MS = 60_000;
const WATER = 'water';
const MARKERS = 'markers';
// Same interface as the real API. Going back and forth between sensors or time ranges does not ask the backend
// again, which may be asleep, while the answer is still fresh. Only answers are kept: a failure or an aborted
// request is never cached. Writes always go through, and irrigating drops the water figures it just changed.
export class CachingApiProxy {
    real;
    now;
    entries = new Map();
    constructor(real, now = Date.now) {
        this.real = real;
        this.now = now;
    }
    readings(sensorId, hours, signal) {
        return this.cached(`readings:${sensorId}:${hours}`, READINGS_TTL_MS, () => this.real.readings(sensorId, hours, signal));
    }
    waterUsage() {
        return this.cached(WATER, LISTS_TTL_MS, () => this.real.waterUsage());
    }
    estateMarkers() {
        return this.cached(MARKERS, LISTS_TTL_MS, () => this.real.estateMarkers());
    }
    async startIrrigation(irrigation) {
        const answer = await this.real.startIrrigation(irrigation);
        this.entries.delete(WATER);
        return answer;
    }
    // Whether an address is free can change at any moment, so it is never cached.
    checkEmail(url, email, signal) {
        return this.real.checkEmail(url, email, signal);
    }
    async cached(key, ttlMs, load) {
        const entry = this.entries.get(key);
        if (entry && this.now() < entry.expiresAt)
            return entry.value;
        const value = await load();
        this.entries.set(key, { value, expiresAt: this.now() + ttlMs });
        return value;
    }
}
