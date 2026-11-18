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
    assert.deepEqual(deviceStatus(minutesAgo(2), now, 180_000), { state: 'online', label: 'ESP32 conectado' });
  });

  it('says since when there is no data once it is older, with the day only when it is not today', () => {
    const stale = deviceStatus(minutesAgo(25), now, 180_000);
    assert.equal(deviceStatus(minutesAgo(4), now, 180_000).state, 'stale');
    assert.equal(stale.state, 'stale');
    assert.match(stale.label, /^Sin datos desde 14:05$/);
    const yesterday = deviceStatus(minutesAgo(24 * 60), now, 180_000);
    assert.match(yesterday.label, /^Sin datos desde 9 (de )?nov/);
  });

  it('says there is no data yet without any reading, and newest() skips missing and broken times', () => {
    assert.equal(deviceStatus(null, now, 180_000).state, 'none');
    assert.equal(newest([null, 'not a date']), null);
    assert.equal(newest(['2026-11-10T14:00:00', null, '2026-11-10T14:20:00'])?.getMinutes(), 20);
  });
});

describe('esp32Humidity', async () => {
  const { alignSeries, isHumidityDay, isHumidityFeed, isNewer, newestBySensor, numberOf, shortMeasure, timeOf } = await import(
    '../../main/resources/static/js/esp32-humidity.js'
  );
  const row = {
    recordedAt: '2026-11-15T11:19:00', recordedAtText: '15 nov, 11:19', sensorCode: 'AS-001', measure: 'Humedad del suelo',
    valueText: '46,8 %', level: 'NORMAL', tone: 'success', icon: 'bi-check-circle', label: 'Normal',
    advice: 'La tierra tiene la humedad que el cultivo necesita.',
  };

  const listening = { silent: false, text: null };
  const quiet = { silent: true, text: 'Sin datos del ESP32 desde las 14:32 (hace 12 minutos).' };

  it('accepts only a well-formed feed', () => {
    assert.equal(isHumidityFeed({ readings: [row], newestAt: row.recordedAt, silence: listening }), true);
    assert.equal(isHumidityFeed({ readings: [], newestAt: null, silence: listening }), true);
    assert.equal(isHumidityFeed({ readings: [{ ...row, valueText: 46.8 }], newestAt: null, silence: listening }), false);
    assert.equal(isHumidityFeed({ readings: 'none', newestAt: null, silence: listening }), false);
  });

  it('accepts a silence only with the sentence that says since when', () => {
    assert.equal(isHumidityFeed({ readings: [row], newestAt: row.recordedAt, silence: quiet }), true);
    assert.equal(isHumidityFeed({ readings: [row], newestAt: row.recordedAt, silence: { silent: true, text: null } }), false);
    assert.equal(isHumidityFeed({ readings: [row], newestAt: row.recordedAt, silence: { silent: 'no', text: null } }), false);
    assert.equal(isHumidityFeed({ readings: [row], newestAt: row.recordedAt }), false);
  });

  it('repaints only when the server has a newer reading than the page shows', () => {
    assert.equal(isNewer('2026-11-15T11:19:00', '2026-11-15T11:20:00'), true);
    assert.equal(isNewer('2026-11-15T11:19:00', '2026-11-15T11:19:00'), false);
    assert.equal(isNewer('2026-11-15T11:19:00', '2026-11-15T10:00:00'), false);
    assert.equal(isNewer(null, '2026-11-15T11:19:00'), true);
    assert.equal(isNewer('2026-11-15T11:19:00', null), false);
    assert.equal(isNewer(null, 'not a date'), false);
  });

  it('takes the newest reading of each sensor for its card', () => {
    const older = { ...row, recordedAt: '2026-11-15T10:19:00', valueText: '43,8 %' };
    const air = { ...row, sensorCode: 'AS-007', valueText: '83,3 %' };
    const newest = newestBySensor([row, air, older]);
    assert.deepEqual([...newest.keys()], ['AS-001', 'AS-007']);
    assert.equal(newest.get('AS-001').valueText, '46,8 %');
  });

  it('shows the number apart from its unit and only the time in the list', () => {
    assert.equal(numberOf('46,8 %'), '46,8');
    assert.equal(numberOf('—'), '—');
    assert.equal(timeOf('15 nov, 11:19'), '11:19');
  });

  it('refuses a reading without its advice', () => {
    const { advice, ...withoutAdvice } = row;
    assert.equal(typeof advice, 'string');
    assert.equal(isHumidityFeed({ readings: [withoutAdvice], newestAt: null, silence: listening }), false);
  });

  const soil = { sensorCode: 'AS-001', measure: 'Humedad del suelo', min: 40, max: 80, points: [
    { at: '2026-11-15T10:00', value: 30.5 }, { at: '2026-11-15T10:30', value: 31 },
  ] };
  const air = { sensorCode: 'AS-007', measure: 'Humedad relativa', min: null, max: null, points: [
    { at: '2026-11-15T10:15', value: 45 }, { at: '2026-11-15T10:30', value: 46 },
  ] };

  it('accepts only a well-formed day chart', () => {
    assert.equal(isHumidityDay({ series: [soil, air], bucketMinutes: 15 }), true);
    assert.equal(isHumidityDay({ series: [], bucketMinutes: 15 }), true);
    assert.equal(isHumidityDay({ series: [{ ...soil, min: '40' }], bucketMinutes: 15 }), false);
    assert.equal(isHumidityDay({ series: [{ ...soil, points: [{ at: 1, value: 2 }] }], bucketMinutes: 15 }), false);
    assert.equal(isHumidityDay({ series: [soil] }), false);
  });

  it('puts both sensors on one time axis and leaves a gap where one has no value', () => {
    const { times, values } = alignSeries([soil, air]);
    assert.deepEqual(times, ['2026-11-15T10:00', '2026-11-15T10:15', '2026-11-15T10:30']);
    assert.deepEqual(values, [[30.5, null, 31], [null, 45, 46]]);
    assert.deepEqual(alignSeries([]), { times: [], values: [] });
  });

  it('names each range by what it measures', () => {
    assert.equal(shortMeasure('Humedad del suelo'), 'Suelo');
    assert.equal(shortMeasure('Humedad relativa'), 'Aire');
  });
});
