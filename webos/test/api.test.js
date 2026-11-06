'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { Api, fixture } = require('./helper.js');

function harness(script) {
  const calls = [];
  const timers = [];
  let online = true;
  function FakeXhr() {
    this.headers = {};
    calls.push(this);
  }
  FakeXhr.prototype.open = function (m, url) { this.method = m; this.url = url; };
  FakeXhr.prototype.setRequestHeader = function (k, v) { this.headers[k] = v; };
  FakeXhr.prototype.abort = function () { this.aborted = true; };
  FakeXhr.prototype.getAllResponseHeaders = function () { return this.rawHeaders || ''; };
  FakeXhr.prototype.send = function () {
    const step = script[calls.length - 1];
    if (!step) return;
    if (step.hang) return;
    if (step.error) { this.onerror(); return; }
    this.status = step.status || 200;
    this.responseText = step.text !== undefined ? step.text : JSON.stringify(step.body || {});
    this.rawHeaders = step.retryAfter !== undefined ? 'content-type: x\r\nRetry-After: ' + step.retryAfter + '\r\n' : 'content-type: x\r\n';
    this.onload();
  };
  const api = Api.create({
    xhr: () => new FakeXhr(),
    setTimeout: (f, t) => { timers.push({ f, t }); return timers.length - 1; },
    clearTimeout: (id) => { if (timers[id]) timers[id].cleared = true; },
    isOnline: () => online
  });
  return {
    api, calls, timers,
    setOnline: (v) => { online = v; },
    flush: (limit = 10) => {
      for (let i = 0; i < limit; i++) {
        const t = timers.find((x) => !x.cleared && !x.ran && x.t < 30000);
        if (!t) return;
        t.ran = true;
        t.f();
      }
    },
    fireTimeout: () => {
      const t = timers.find((x) => !x.cleared && !x.ran && x.t >= 30000);
      if (t) { t.ran = true; t.f(); }
    }
  };
}

const get = (h) => new Promise((resolve) => h.api.home('uzbeklar', (err, data) => resolve({ err, data })));

test('builds GET urls under /contents with only safe headers', async () => {
  const h = harness([{ body: { ok: 1 } }]);
  const { data } = await get(h);
  assert.deepEqual(data, { ok: 1 });
  assert.equal(h.calls[0].method, 'GET');
  assert.equal(h.calls[0].url, 'https://apisozo.azamov.me/api/contents/home?provider=uzbeklar');
  assert.deepEqual(Object.keys(h.calls[0].headers).sort(), ['Accept', 'Accept-Language']);
});

test('the adult opt-in can never be sent', () => {
  const url = Api.buildUrl('b', 'home', { provider: 'p', nsfw: 1, NSFW: 1, adult: 1 });
  assert.equal(url, 'b/contents/home?provider=p');
  const src = require('fs').readFileSync(require('path').join(__dirname, '..', 'js', 'lib', 'api.js'), 'utf8');
  assert.equal(/x-sozo-adult/i.test(src), false);
});

test('query values are encoded', () => {
  assert.equal(Api.buildUrl('b', 'search', { q: 'a b&c=д', page: 1, none: null }), 'b/contents/search?q=a%20b%26c%3D%D0%B4&page=1');
});

test('Retry-After parsing', () => {
  assert.equal(Api.parseRetryAfter('3'), 3);
  assert.equal(Api.parseRetryAfter(' 10 '), 10);
  assert.equal(Api.parseRetryAfter(null), null);
  assert.equal(Api.parseRetryAfter('soon'), null);
  assert.equal(Api.parseRetryAfter('Thu, 05 Nov 2026 06:44:29 GMT', Date.parse('Thu, 05 Nov 2026 06:44:19 GMT')), 10);
});

test('retry policy', () => {
  const http = (status, code, retryAfter = null) => ({ kind: 'http', status, code: code || null, retryAfter });
  assert.equal(Api.retryDelay({ kind: 'offline' }), null);
  assert.equal(Api.retryDelay({ kind: 'network' }), 800);
  assert.equal(Api.retryDelay({ kind: 'timeout' }), 800);
  assert.equal(Api.retryDelay({ kind: 'badData' }), 800);
  assert.equal(Api.retryDelay(http(429, null, 0)), 500);
  assert.equal(Api.retryDelay(http(429, null, 3)), 3000);
  assert.equal(Api.retryDelay(http(429, null, 5)), 5000);
  assert.equal(Api.retryDelay(http(429, null, 6)), null);
  assert.equal(Api.retryDelay(http(503, null, 10)), null);
  assert.equal(Api.retryDelay(http(503)), 3000, 'header hidden by CORS');
  assert.equal(Api.retryDelay(http(503, 'SOURCE_REGION_LOCKED')), null);
  assert.equal(Api.retryDelay(http(503, 'ROUTE_TIMEOUT')), null);
  assert.equal(Api.retryDelay(http(502)), 1000);
  assert.equal(Api.retryDelay(http(404)), null);
  assert.equal(Api.retryDelay(http(400)), null);
  assert.equal(Api.retryDelay(http(409, 'SOURCE_RUNS_ON_DEVICE')), null);
});

test('error table from the contract', () => {
  const cases = [
    [{ kind: 'offline' }, 'err.offline'], [{ kind: 'timeout' }, 'err.timeout'], [{ kind: 'network' }, 'err.network'], [{ kind: 'badData' }, 'err.badData'],
    [{ kind: 'adult' }, 'err.notAvailable'],
    [{ kind: 'http', status: 400 }, 'err.request'], [{ kind: 'http', status: 404 }, 'err.notFound'],
    [{ kind: 'http', status: 409, code: 'SOURCE_RUNS_ON_DEVICE' }, 'err.device'], [{ kind: 'http', status: 426, code: 'APP_UPDATE_REQUIRED' }, 'err.device'],
    [{ kind: 'http', status: 428 }, 'err.device'], [{ kind: 'http', status: 410, code: 'SOURCE_REMOVED' }, 'err.gone'],
    [{ kind: 'http', status: 429 }, 'err.rate'], [{ kind: 'http', status: 451, code: 'GEO_BLOCKED' }, 'err.region'],
    [{ kind: 'http', status: 503, code: 'SOURCE_REGION_LOCKED' }, 'err.region'], [{ kind: 'http', status: 503 }, 'err.unavailable'],
    [{ kind: 'http', status: 418 }, 'err.generic'], [null, 'err.generic']
  ];
  for (const [err, key] of cases) assert.equal(Api.errorKey(err), key);
});

test('network failure retries once, then reports', async () => {
  const h = harness([{ error: true }, { error: true }, { body: {} }]);
  const p = get(h);
  h.flush();
  const { err } = await p;
  assert.equal(err.kind, 'network');
  assert.equal(h.calls.length, 2);
});

test('a retry can succeed', async () => {
  const h = harness([{ status: 502, text: '<html>' }, { body: { fine: true } }]);
  const p = get(h);
  h.flush();
  const { err, data } = await p;
  assert.equal(err, null);
  assert.equal(data.fine, true);
});

test('429 honours a readable Retry-After', async () => {
  const h = harness([{ status: 429, retryAfter: 2, body: { message: 'x' } }, { body: {} }]);
  const p = get(h);
  assert.ok(h.timers.some((t) => t.t === 2000));
  h.flush();
  await p;
  assert.equal(h.calls.length, 2);
});

test('long Retry-After stops and surfaces the wait', async () => {
  const h = harness([{ status: 503, retryAfter: 60, body: { message: 'x' } }]);
  const { err } = await get(h);
  assert.equal(err.retryAfter, 60);
  assert.equal(h.calls.length, 1);
});

test('backend error code and status are parsed; 404 does not retry', async () => {
  const h = harness([{ status: 409, body: { message: 'yo', code: 'SOURCE_RUNS_ON_DEVICE' } }]);
  const { err } = await get(h);
  assert.equal(err.code, 'SOURCE_RUNS_ON_DEVICE');
  assert.equal(h.calls.length, 1);
  assert.equal(Api.httpError(404, JSON.stringify(fixture('err_detail'))).status, 404);
  assert.equal(Api.httpError(500, '<html>').code, null);
});

test('HTML on a 200 is bad data, not a crash', async () => {
  const h = harness([{ text: '<html>challenge</html>' }, { text: 'null' }]);
  const p = get(h);
  h.flush();
  const { err } = await p;
  assert.equal(err.kind, 'badData');
});

test('a hung request times out, aborts and retries once', async () => {
  const h = harness([{ hang: true }, { hang: true }]);
  const p = get(h);
  assert.ok(h.timers.some((t) => t.t === Api.TIMEOUT));
  h.fireTimeout();
  assert.equal(h.calls[0].aborted, true);
  h.flush();
  assert.equal(h.calls.length, 2);
  h.fireTimeout();
  const { err } = await p;
  assert.equal(err.kind, 'timeout');
});

test('media uses the long timeout', () => {
  const h = harness([{ hang: true }]);
  h.api.media('anilibria', 'ref', 'sub', () => {});
  assert.ok(h.timers.some((t) => t.t === Api.MEDIA_TIMEOUT));
  assert.match(h.calls[0].url, /\/contents\/media\?provider=anilibria&ref=ref&lang=sub$/);
});

test('media with an error string and no sources is a failure', async () => {
  const h = harness([{ body: { error: 'nope', videoSources: [] } }]);
  const { err } = await new Promise((r) => h.api.media('a', 'b', 'c', (err, data) => r({ err, data })));
  assert.equal(err.status, 503);
});

test('offline fails fast without touching the network', async () => {
  const h = harness([]);
  h.setOnline(false);
  const p = get(h);
  h.flush();
  const { err } = await p;
  assert.equal(err.kind, 'offline');
  assert.equal(h.calls.length, 0);
});

test('abort silences the callback and cancels a pending retry', () => {
  const h = harness([{ error: true }]);
  let called = false;
  const handle = h.api.home('x', () => { called = true; });
  handle.abort();
  h.flush();
  assert.equal(called, false);
  assert.equal(h.calls.length, 1);
});

test('detail retries with the slash toggled when empty, and keeps the good answer', async () => {
  const h = harness([{ body: { title: '' } }, { body: { title: 'Found' } }]);
  const { data } = await new Promise((r) => h.api.detail('p', 'https://a/b', (err, data) => r({ err, data })));
  assert.equal(data.title, 'Found');
  assert.match(h.calls[1].url, /url=https%3A%2F%2Fa%2Fb%2F/);
});

test('detail reports the first error when the toggled url also fails', async () => {
  const h = harness([{ status: 404, body: { message: 'x' } }, { status: 400, body: { message: 'y' } }]);
  const { err } = await new Promise((r) => h.api.detail('p', 'https://a/b', (err, data) => r({ err, data })));
  assert.equal(err.status, 404);
});

test('a coded failure does not toggle, and a live ref has nothing to toggle', async () => {
  const h = harness([{ status: 409, body: { code: 'SOURCE_RUNS_ON_DEVICE' } }]);
  await new Promise((r) => h.api.detail('p', 'https://a/b', r));
  assert.equal(h.calls.length, 1);
  const h2 = harness([{ status: 404, body: {} }]);
  await new Promise((r) => h2.api.detail('worldlivetv', 'worldlivetv:x', r));
  assert.equal(h2.calls.length, 1);
});

test('episodes ask for page, size and sort', () => {
  const h = harness([{ body: {} }]);
  h.api.episodes('p', 'worldlivetv:x', 2, () => {});
  assert.match(h.calls[0].url, /page=2&size=100&sort=asc/);
});
