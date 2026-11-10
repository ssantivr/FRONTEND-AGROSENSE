// Runs on the scripts tsc compiled: npm run build && npm test.
import assert from 'node:assert/strict';
import { afterEach, describe, it } from 'node:test';

import { CachingApiProxy, LISTS_TTL_MS, READINGS_TTL_MS } from '../../main/resources/static/js/api/cache-proxy.js';
import { ApiFacade } from '../../main/resources/static/js/api/facade.js';
import { LatestStream } from '../../main/resources/static/js/events/latest-stream.js';
import { fillRanges } from '../../main/resources/static/js/crop-template.js';

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

  it('keeps nothing beyond the page: the next page, or the next user, starts empty', async () => {
    const first = bench();
    await first.proxy.waterUsage();

    const nextPage = new CachingApiProxy(first.real, () => first.clock.time);
    await nextPage.waterUsage();

    assert.equal(first.real.calls.length, 2);
  });

  it('never caches whether an email is free', async () => {
    const { real, proxy } = bench();
    const signal = new AbortController().signal;

    await proxy.checkEmail('/check', 'a@b.co', signal);
    await proxy.checkEmail('/check', 'a@b.co', signal);

    assert.equal(real.calls.length, 2);
  });
});

describe('crop template', () => {
  const fields = () => Object.fromEntries(['humidityMin', 'humidityMax', 'tempMin', 'tempMax', 'phMin', 'phMax'].map((name) => [name, { value: '1' }]));

  it('fills the ranges and leaves them editable', () => {
    const form = fields();
    const template = { humidityMin: '60', humidityMax: '80', tempMin: '18', tempMax: '27', phMin: '5.5', phMax: '6.8' };

    fillRanges(template, (name) => form[name]);
    assert.equal(form.humidityMin.value, '60');
    assert.equal(form.phMax.value, '6.8');

    form.humidityMin.value = '65';
    assert.equal(template.humidityMin, '60');
  });

  it('touches nothing with no template', () => {
    const form = fields();
    fillRanges({}, (name) => form[name]);
    assert.equal(form.tempMin.value, '1');
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

describe('LatestStream', () => {
  const later = () => {
    let answer;
    let fail;
    const promise = new Promise((resolve, reject) => {
      answer = resolve;
      fail = reject;
    });
    return { promise, answer, fail };
  };
  const source = () => ({ closed: false, close() { this.closed = true; } });

  it('a second connection while the first waits for its token leaves one stream open', async () => {
    const stream = new LatestStream();
    const first = later();
    const second = later();
    const opened = [];
    const create = (access) => {
      const created = { ...source(), access };
      opened.push(created);
      return created;
    };
    const slow = stream.open(() => first.promise, create);
    const fast = stream.open(() => second.promise, create);
    second.answer('new');
    await fast;
    first.answer('old');
    await slow;
    assert.deepEqual(opened.map((created) => created.access), ['new']);
  });

  it('closing while the token is on its way opens nothing', async () => {
    const stream = new LatestStream();
    const token = later();
    let created = 0;
    const pending = stream.open(() => token.promise, () => { created += 1; return source(); });
    stream.close();
    token.answer('late');
    assert.equal(await pending, null);
    assert.equal(created, 0);
  });

  it('opening again closes the stream that was open', async () => {
    const stream = new LatestStream();
    const first = await stream.open(async () => 1, source);
    await stream.open(async () => 2, source);
    assert.equal(first.closed, true);
  });

  it('only the failure of the attempt still wanted reaches the caller', async () => {
    const stream = new LatestStream();
    const old = later();
    const pending = stream.open(() => old.promise, source);
    await stream.open(async () => 1, source);
    old.fail(new Error('network'));
    assert.equal(await pending, null);
    await assert.rejects(stream.open(async () => { throw new Error('network'); }, source));
  });
});

describe('deviceStatus', async () => {
  const { deviceStatus, newest } = await import('../../main/resources/static/js/device-status.js');
  const now = new Date(2026, 10, 10, 14, 30, 0);
  const minutesAgo = (minutes) => new Date(now.getTime() - minutes * 60_000);

  it('says connected while the newest reading is recent', () => {
    assert.deepEqual(deviceStatus(minutesAgo(1), now, 120_000), { state: 'online', label: 'ESP32 conectado' });
  });

  it('says since when there is no data once it is older, with the day only when it is not today', () => {
    const stale = deviceStatus(minutesAgo(25), now, 120_000);
    assert.equal(stale.state, 'stale');
    assert.match(stale.label, /^ESP32 sin datos desde 14:05$/);
    const yesterday = deviceStatus(minutesAgo(24 * 60), now, 120_000);
    assert.match(yesterday.label, /^ESP32 sin datos desde 9 (de )?nov/);
  });

  it('says there is no data yet without any reading, and newest() skips missing and broken times', () => {
    assert.equal(deviceStatus(null, now, 120_000).state, 'none');
    assert.equal(newest([null, 'not a date']), null);
    assert.equal(newest(['2026-11-10T14:00:00', null, '2026-11-10T14:20:00'])?.getMinutes(), 20);
  });
});
