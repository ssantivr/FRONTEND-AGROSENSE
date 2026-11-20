// Runs on the scripts tsc compiled: npm run build && npm test.
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
  FIRST_WAIT_MS,
  LONGEST_WAIT_MS,
  ReadingHistory,
  STATE_ICONS,
  STATE_LABELS,
  averageOf,
  badgeText,
  disabledState,
  isAccess,
  isLiveReading,
  parseLiveEvent,
  sourceOf,
  sparklinePoints,
  streamUrlWithToken,
  trendOf,
  trendOfPolyline,
} from '../../main/resources/static/js/events/live-model.js';
import { RefreshTracker, agoText, nextBackoff } from '../../main/resources/static/js/core/time.js';
import { retryAfterFailure } from '../../main/resources/static/js/connection.js';
import { isDataSources, originLabel, simulatorText, sourceCounts } from '../../main/resources/static/js/data-sources-model.js';
import { staleText } from '../../main/resources/static/js/esp32-humidity.js';

describe('live stream model', () => {
  it('A3: the reconnection wait doubles and never goes above 30 s', () => {
    assert.equal(LONGEST_WAIT_MS, 30_000);
    let wait = FIRST_WAIT_MS;
    const waits = [];
    for (let i = 0; i < 10; i++) {
      waits.push(wait);
      wait = nextBackoff(wait, LONGEST_WAIT_MS);
    }
    assert.deepEqual(waits.slice(0, 5), [1000, 2000, 4000, 8000, 16000]);
    assert.ok(waits.every((one) => one <= 30_000));
    assert.equal(waits.at(-1), 30_000);
  });

  it('A4: with live data off the badge says simulated in the demo and "Sin datos en vivo" otherwise', () => {
    assert.equal(disabledState(true), 'simulated');
    assert.equal(disabledState(false), 'unavailable');
    assert.equal(STATE_LABELS.unavailable, 'Sin datos en vivo');
    assert.equal(STATE_LABELS.simulated, 'Simulado');
  });

  it('B1: every badge state has its own label and its own icon', () => {
    const states = Object.keys(STATE_LABELS);
    assert.deepEqual(new Set(states), new Set(['live', 'reconnecting', 'offline', 'simulated', 'ai', 'unavailable']));
    assert.equal(new Set(states.map((state) => STATE_ICONS[state])).size, states.length);
    assert.equal(new Set(Object.values(STATE_LABELS)).size, states.length);
  });

  it('A5: only an http(s) stream address is used, and a broken one does not throw', () => {
    assert.equal(streamUrlWithToken('https://events.example/stream', 'a b'), 'https://events.example/stream?token=a+b');
    assert.equal(streamUrlWithToken('/stream', 't', 'https://app.example/x'), 'https://app.example/stream?token=t');
    assert.equal(streamUrlWithToken('not a url', 't'), null);
    assert.equal(streamUrlWithToken('javascript:alert(1)', 't'), null);
    assert.equal(streamUrlWithToken('ftp://events.example/stream', 't'), null);
  });

  it('A8: the badge text is capped at 99+ while the count itself is not', () => {
    assert.equal(badgeText(9), '9');
    assert.equal(badgeText(99), '99');
    assert.equal(badgeText(100), '99+');
    assert.equal(badgeText(-3), '0');
  });

  it('accepts the token answer only when it is complete or says live data is off', () => {
    assert.equal(isAccess({ enabled: false }), true);
    assert.equal(isAccess({ enabled: true, token: 't', streamUrl: 'https://e/stream', expiresInSeconds: 300 }), true);
    assert.equal(isAccess({ enabled: true, token: 't' }), false);
  });

  it('reads the optional source of a reading and ignores an unknown one without dropping the reading', () => {
    const base = { sensorCode: 'AS-1', value: 41.5, unit: '%', recordedAt: '2026-11-19T10:00:00' };
    assert.equal(isLiveReading(base), true);
    assert.equal(sourceOf({ ...base, source: 'SIMULATED' }), 'SIMULATED');
    assert.equal(sourceOf({ ...base, source: 'ESP32_TCP' }), 'ESP32_TCP');
    assert.equal(isLiveReading({ ...base, source: 'MARS' }), true);
    assert.equal(sourceOf({ ...base, source: 'MARS' }), null);
    assert.equal(isLiveReading({ ...base, value: Number.NaN }), false);
    assert.equal(isLiveReading({ ...base, value: '41' }), false);
  });

  it('parses an SSE message and returns null for anything malformed', () => {
    const event = parseLiveEvent(JSON.stringify({ readings: [{ sensorCode: 'AS-1', value: 3, unit: null, recordedAt: null, source: 'SEED' }], alerts: [] }));
    assert.equal(event?.readings[0]?.source, 'SEED');
    assert.equal(parseLiveEvent('{not json'), null);
    assert.equal(parseLiveEvent(JSON.stringify({ readings: 'x', alerts: [] })), null);
  });

  it('keeps the last 20 readings of each sensor apart', () => {
    const history = new ReadingHistory();
    for (let i = 1; i <= 25; i++) history.add('AS-1', i);
    history.add('AS-2', 7);
    assert.equal(history.of('AS-1').length, 20);
    assert.equal(history.of('AS-1')[0], 6);
    assert.deepEqual(history.of('AS-2'), [7]);
    assert.deepEqual(history.of('none'), []);
  });

  it('draws a sparkline inside the 100 x 28 box, flat in the middle when nothing changes', () => {
    assert.equal(sparklinePoints([5]), '');
    assert.equal(sparklinePoints([10, 20]), '0,26 100,2');
    assert.equal(sparklinePoints([3, 3, 3]), '0,14 50,14 100,14');
  });

  it('says the trend from the last two values, also from the server sparkline', () => {
    assert.equal(trendOf([1]), null);
    assert.equal(trendOf([10, 12]), 'up');
    assert.equal(trendOf([12, 10]), 'down');
    assert.equal(trendOf([10, 20, 20.1]), 'flat');
    assert.equal(trendOfPolyline('0,20 50,14 100,5'), 'up');
    assert.equal(trendOfPolyline('0,5 100,20'), 'down');
  });

  it('averages the members of a KPI card, skipping those without a value', () => {
    assert.equal(averageOf([40, 50, null]), 45);
    assert.equal(averageOf([null]), null);
  });
});

describe('refresh status', () => {
  const at = (hours, minutes) => new Date(2026, 10, 19, hours, minutes);

  it('A1: says when it last refreshed, and after two failures since when it is stale', () => {
    const tracker = new RefreshTracker(at(10, 0), 2);
    assert.equal(tracker.text(), 'Actualizado a las 10:00');
    tracker.failed();
    assert.equal(tracker.stale, false);
    tracker.failed();
    assert.equal(tracker.stale, true);
    assert.equal(tracker.text(), 'Sin actualizar desde las 10:00');
    tracker.succeeded(at(10, 5));
    assert.equal(tracker.stale, false);
    assert.equal(tracker.text(), 'Actualizado a las 10:05');
  });

  it('A2: a failed status check is retried with a growing pause and then stops', () => {
    const pauses = [1, 2, 3, 4, 5, 6].map(retryAfterFailure);
    assert.deepEqual(pauses, [4000, 8000, 15000, 30000, 30000, null]);
  });

  it('A9: a failed ESP32 refresh says at what time it failed', () => {
    assert.match(staleText(at(14, 7)), /^No se pudo actualizar a las 14:07/);
  });

  it('B4: the age of a reading is worded like the server', () => {
    const now = at(12, 0);
    assert.equal(agoText(new Date(2026, 10, 19, 11, 59, 30), now), 'hace un momento');
    assert.equal(agoText(at(11, 48), now), 'hace 12 min');
    assert.equal(agoText(at(9, 0), now), 'hace 3 h');
    assert.equal(agoText(new Date(2026, 10, 16, 12, 0), now), 'hace 3 días');
  });
});

describe('data sources', () => {
  const item = {
    key: 'readings.esp32', label: 'Lecturas del ESP32', origin: 'SENSOR_FISICO', producer: 'Esp32TcpIngestionService',
    storage: 'sensor_readings.reading_value', endpoint: 'GET /api/sensors/{id}/readings', file: 'backend/x.java',
    frontendComponent: 'sensors.html',
  };

  it('accepts the answer of /api/ui/data-sources, with or without the backend', () => {
    assert.equal(isDataSources({ fallback: false, simulatorEnabled: true, readingCountsBySource: { SIMULATED: 4 }, items: [item] }), true);
    assert.equal(isDataSources({ fallback: true, simulatorEnabled: null, readingCountsBySource: {}, items: [{ ...item, storage: null }] }), true);
    assert.equal(isDataSources({ fallback: true, simulatorEnabled: null, readingCountsBySource: { X: '4' }, items: [] }), false);
    assert.equal(isDataSources({ fallback: 'no', simulatorEnabled: null, readingCountsBySource: {}, items: [] }), false);
    assert.equal(isDataSources({ fallback: false, simulatorEnabled: true, readingCountsBySource: {}, items: [{ key: 1 }] }), false);
  });

  it('words the origins and the counts like the reading chips, in a fixed order', () => {
    assert.equal(originLabel('IA'), 'Inteligencia artificial');
    assert.equal(originLabel('OTRO'), 'OTRO');
    assert.deepEqual(
      sourceCounts({ SIMULATED: 3, ESP32_TCP: 10, UNKNOWN: 1 }).map(({ label, count }) => `${label}=${count}`),
      ['ESP32=10', 'Simulado=3'],
    );
    assert.match(simulatorText(true), /activo/);
    assert.match(simulatorText(null), /No se pudo/);
  });
});

describe('polling fallback', async () => {
  const { POLL_MS, isPolledReadings, latestUrl, nextSince, pollDelay, pollState } = await import(
    '../../main/resources/static/js/events/live-model.js'
  );
  const reading = (recordedAt, source) => ({ sensorCode: 'AS-1', value: 40, unit: '%', recordedAt, source });

  it('polls every 10 s and backs off up to 30 s after errors', () => {
    assert.equal(POLL_MS, 10_000);
    assert.deepEqual([0, 1, 2, 3, 8].map(pollDelay), [10_000, 20_000, 30_000, 30_000, 30_000]);
  });

  it('moves since to the newest reading and never back', () => {
    const start = '2026-11-19T10:00:00';
    assert.equal(nextSince(start, []), start);
    assert.equal(nextSince(start, [reading('2026-11-19T10:00:05'), reading('2026-11-19T10:00:20'), reading('2026-11-19T10:00:10')]), '2026-11-19T10:00:20');
    assert.equal(nextSince(start, [reading('2026-11-19T09:59:00'), reading('not a date')]), start);
    assert.equal(latestUrl('/api/ui/readings/latest', '2026-11-19T10:00:00.000Z'), '/api/ui/readings/latest?since=2026-11-19T10%3A00%3A00.000Z');
  });

  it('says live for real readings, simulated when the newest is simulated, reconnecting after an error', () => {
    assert.equal(pollState(true, [reading('t', 'ESP32_TCP')], 'reconnecting'), 'live');
    assert.equal(pollState(true, [reading('t', 'ESP32_TCP'), reading('t', 'SIMULATED')], 'live'), 'simulated');
    assert.equal(pollState(true, [], 'simulated'), 'simulated');
    assert.equal(pollState(true, [], 'reconnecting'), 'live');
    assert.equal(pollState(false, [], 'live'), 'reconnecting');
  });

  it('does not claim live data in demo mode when a round brings nothing new', () => {
    // Demo: the token fails, the badge goes through "reconnecting" and an empty round must not end in "live".
    assert.equal(pollState(true, [], 'reconnecting', 'simulated'), 'simulated');
    assert.equal(pollState(true, [reading('t', 'ESP32_TCP')], 'reconnecting', 'simulated'), 'live');
  });

  it('accepts only readings that say when they were recorded', () => {
    assert.equal(isPolledReadings([reading('2026-11-19T10:00:00', 'SEED')]), true);
    assert.equal(isPolledReadings([{ sensorCode: 'AS-1', value: 1, unit: null, recordedAt: null }]), false);
    assert.equal(isPolledReadings({}), false);
  });
});
