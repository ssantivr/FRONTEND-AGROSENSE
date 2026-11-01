// Runs on the scripts tsc compiled: npm run build && npm test.
import assert from 'node:assert/strict';
import { afterEach, describe, it } from 'node:test';

import { CachingApiProxy, LISTS_TTL_MS, READINGS_TTL_MS } from '../../main/resources/static/js/api/cache-proxy.js';
import { ApiFacade } from '../../main/resources/static/js/api/facade.js';

const series = { sensorCode: 'AS-1', unit: '%', thresholdMin: null, thresholdMax: null, points: [], windowHours: 24, source: 'api', loadedAt: 'now' };

function fakeApi() {
  const calls = [];
  const record =
    (name, answer) =>
    async (...args) => {
      calls.push([name, ...args]);
      return typeof answer === 'function' ? answer() : answer;
    };
  return {
    calls,
    readings: record('readings', series),
    waterUsage: record('waterUsage', [{ date: '2026-11-01', liters: 10 }]),
    estateMarkers: record('estateMarkers', []),
    startIrrigation: record('startIrrigation', { ok: true }),
    checkEmail: record('checkEmail', { available: true }),
  };
}

function bench() {
  const clock = { time: 1000 };
  const real = fakeApi();
  return { clock, real, proxy: new CachingApiProxy(real, () => clock.time) };
}

describe('CachingApiProxy', () => {
  it('answers a repeated read from memory until it expires', async () => {
    const { clock, real, proxy } = bench();

    await proxy.readings('1', 24);
    assert.equal(await proxy.readings('1', 24), series);
    assert.equal(real.calls.length, 1);

    clock.time += READINGS_TTL_MS;
    await proxy.readings('1', 24);
    assert.equal(real.calls.length, 2);
  });

  it('keeps each sensor and time range apart', async () => {
    const { real, proxy } = bench();

    await proxy.readings('1', 24);
    await proxy.readings('1', 72);
    await proxy.readings('2', 24);

    assert.equal(real.calls.length, 3);
  });

  it('passes the abort signal on and never caches a failure', async () => {
    const { real, proxy } = bench();
    const signal = new AbortController().signal;
    await proxy.readings('1', 24, signal);
    assert.equal(real.calls[0][3], signal);

    let attempts = 0;
    real.waterUsage = async () => {
      attempts += 1;
      if (attempts === 1) throw new Error('backend dormido');
      return [];
    };
    await assert.rejects(proxy.waterUsage());
    assert.deepEqual(await proxy.waterUsage(), []);
    assert.equal(attempts, 2);
  });

  it('caches the lists for longer', async () => {
    const { clock, real, proxy } = bench();

    await proxy.estateMarkers();
    clock.time += LISTS_TTL_MS - 1;
    await proxy.estateMarkers();
    assert.equal(real.calls.length, 1);
    clock.time += 1;
    await proxy.estateMarkers();
    assert.equal(real.calls.length, 2);
  });

  it('always sends an irrigation and drops the water figures it changed', async () => {
    const { real, proxy } = bench();
    await proxy.waterUsage();
    await proxy.estateMarkers();

    const irrigation = { cropId: 1, durationMin: 10, waterLiters: 50 };
    await proxy.startIrrigation(irrigation);
    await proxy.startIrrigation(irrigation);
    await proxy.waterUsage();
    await proxy.estateMarkers();

    assert.deepEqual(real.calls.map(([name]) => name), ['waterUsage', 'estateMarkers', 'startIrrigation', 'startIrrigation', 'waterUsage']);
  });

  it('keeps the water figures when the irrigation fails', async () => {
    const { real, proxy } = bench();
    await proxy.waterUsage();
    real.startIrrigation = async () => {
      throw new Error('rechazado');
    };

    await assert.rejects(proxy.startIrrigation({ cropId: 1, durationMin: 1, waterLiters: 1 }));
    await proxy.waterUsage();

    assert.equal(real.calls.length, 1);
  });

  it('never caches whether an email is free', async () => {
    const { real, proxy } = bench();
    const signal = new AbortController().signal;

    await proxy.checkEmail('/check', 'a@b.co', signal);
    await proxy.checkEmail('/check', 'a@b.co', signal);

    assert.equal(real.calls.length, 2);
  });
});

describe('ApiFacade', () => {
  const realFetch = globalThis.fetch;
  afterEach(() => {
    globalThis.fetch = realFetch;
  });

  it('hides the route and the guard of each call', async () => {
    const requested = [];
    globalThis.fetch = async (url) => {
      requested.push(url);
      return new Response(JSON.stringify(series), { status: 200 });
    };

    assert.deepEqual(await new ApiFacade().readings('a b', 24), series);
    assert.deepEqual(requested, ['/api/ui/sensors/a%20b/readings?hours=24']);
  });

  it('refuses an answer that is not what the page expects', async () => {
    globalThis.fetch = async () => new Response('{"nada": true}', { status: 200 });

    await assert.rejects(new ApiFacade().waterUsage(), { name: 'ApiError', kind: 'unexpected' });
  });
});
