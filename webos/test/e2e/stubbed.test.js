'use strict';
// Failure journeys with the API and streams stubbed, driven only by dispatched webOS key codes.
const test = require('node:test');
const assert = require('node:assert');
const { openApp, closeBrowser, card, range, shot } = require('../browser/harness.js');
const L = require('./lib.js');
const { K, key, keys, sleep, travelTo, invariants, waitFocus, waitFocusSel, focusInfo, monitorFocus, focusFaults } = L;

test.after(closeBrowser);

const fidIs = (fid) => new Function('return function () { const l = document.querySelectorAll(".fc"); for (let i = 0; i < l.length; i++) if (l[i]._fid === ' + JSON.stringify(fid) + ' && l[i].offsetWidth) return l[i]; return null; };')();
const delay = (ms, value) => new Promise((r) => setTimeout(() => r(value), ms));

async function start(opts) {
  const ctx = await openApp(opts);
  await ctx.page.waitForFunction(() => window.Sozo && window.Sozo.Nav.current(), null, { timeout: 30000 });
  await monitorFocus(ctx.page);
  await ctx.page.evaluate(() => { window.Sozo.Platform.exit = function () { window.__exited = (window.__exited || 0) + 1; }; });
  return ctx;
}

async function done(ctx, label) {
  const { page, log, context } = ctx;
  assert.deepEqual(await invariants(page), [], label + ': invariants');
  assert.deepEqual(await focusFaults(page), [], label + ': focus faults');
  assert.deepEqual(log.errors, [], label + ': page errors');
  assert.deepEqual(log.consoleErrors, [], label + ': console errors');
  assert.equal(log.api.some((r) => r.headers['x-sozo-adult'] !== undefined), false);
  await context.close();
}

const clean = async (page, label) => assert.deepEqual(await invariants(page), [], label);

async function toTab(page, name) {
  for (let i = 0; i < 12; i++) {
    if (await page.evaluate(() => window.Sozo.App.topbar.el.contains(window.Sozo.Nav.current()))) break;
    await key(page, K.up, 150);
  }
  await travelTo(page, fidIs('tab-' + name));
  await key(page, K.ok, 200);
}

const appText = (page) => page.evaluate(() => document.getElementById('app').textContent);
const videoT = (page) => page.evaluate(() => { const v = document.querySelector('video'); return v ? v.currentTime : -1; });
const playing = (page, min) => page.waitForFunction((m) => { const v = document.querySelector('video'); return v && !v.paused && v.currentTime > m; }, min || 0.3, { timeout: 15000 });

test('API failure at boot: friendly screen, Retry by remote recovers', async () => {
  let fail = true;
  const ctx = await openApp({ routes: { providers: () => (fail ? { status: 500, body: { message: 'SECRET backend text' } } : { body: require('../fixtures/providers.json') }) } });
  const { page } = ctx;
  await waitFocus(page, /Try again/, 15000);
  await monitorFocus(page);
  assert.equal((await appText(page)).includes('SECRET'), false);
  await clean(page, 'boot error');
  await shot(page, 'e2e-boot-error');
  fail = false;
  await key(page, K.ok);
  await page.waitForSelector('.home', { timeout: 10000 });
  await waitFocus(page, /Title 5000/);
  await done(ctx, 'boot');
});

test('a dropped connection at boot recovers by itself while the spinner shows', async () => {
  let calls = 0;
  const fb = require('../fixtures/providers.json');
  const ctx = await openApp({ routes: { providers: () => { calls++; return calls <= 4 ? { abort: true } : { body: fb }; } } });
  const { page } = ctx;
  await page.waitForSelector('.status-loading', { timeout: 5000 });
  await waitFocus(page, /Title 5000/, 20000);
  assert.equal(calls, 5);
  assert.equal(await page.evaluate(() => !!document.querySelector('.status-error')), false);
  await monitorFocus(page);
  await done(ctx, 'boot recovers');
});

test('API failure on each screen: Retry works and Back still leaves', async () => {
  const failing = {};
  const failable = (name, ok) => (q) => (failing[name] ? { status: 502, body: { message: 'SECRET' } } : ok(q));
  const base = require('../browser/harness.js');
  const ctx = await start({
    routes: {
      home: failable('home', () => ({ body: { provider: 'uzbeklar', banner: [], sections: [{ key: 'a', label: 'Row', items: range(100, 10) }] } })),
      categories: failable('categories', () => ({ body: base.fixture('categories_uzbeklar') })),
      genres: failable('categories', () => ({ body: base.fixture('genres_uzbeklar') })),
      search: failable('search', (q) => ({ body: { provider: 'uzbeklar', query: q.get('q'), items: range(300, 6), page: 1, totalPages: 1 } })),
      detail: failable('detail', (q) => ({ body: { provider: 'uzbeklar', contentUrl: q.get('url'), title: 'Film', year: 2024, isSerial: false } })),
      episodes: failable('episodes', (q) => ({ body: { provider: 'uzbeklar', contentUrl: q.get('url'), isSerial: false, episodes: [{ episode: 1, label: 'Play', mediaRef: 'r' }], videoSources: [{ quality: '720p', videoUrl: 'https://cdn.test/a.mp4', type: 'mp4' }], headers: {} } }))
    },
    media: () => 'clip'
  });
  const { page } = ctx;
  await waitFocus(page, /Title 100/);

  failing.detail = true;
  await key(page, K.ok);
  await waitFocus(page, /Try again/, 15000);
  await clean(page, 'detail error');
  await shot(page, 'e2e-detail-error');
  failing.detail = false;
  await key(page, K.ok);
  await waitFocus(page, /Play/, 10000);
  await key(page, K.back, 300);
  await waitFocus(page, /Title 100/);

  failing.episodes = true;
  await key(page, K.ok);
  await waitFocus(page, /Add to My List|Try again/, 15000);
  await page.waitForFunction(() => /Try again/.test(document.querySelector('.detail-episodes').textContent), null, { timeout: 15000 });
  await clean(page, 'episodes error');
  await shot(page, 'e2e-episodes-error');
  failing.episodes = false;
  await travelTo(page, fidIs('episodes-retry'));
  await key(page, K.ok);
  await waitFocus(page, /Play/, 10000);
  await key(page, K.back, 300);
  await waitFocus(page, /Title 100/);

  failing.categories = true;
  await toTab(page, 'categories');
  await waitFocus(page, /Try again/, 15000);
  await clean(page, 'categories error');
  failing.categories = false;
  await key(page, K.ok);
  await page.waitForSelector('.chip', { timeout: 10000 });
  await sleep(200);
  await clean(page, 'categories ok');

  failing.search = true;
  await toTab(page, 'search');
  await page.waitForSelector('.key');
  for (const ch of 'ab') { await travelTo(page, fidIs('key-' + ch)); await key(page, K.ok); }
  await page.waitForFunction(() => /Try again/.test(document.querySelector('.results').textContent), null, { timeout: 15000 });
  await clean(page, 'search error');
  assert.deepEqual(await L.auditLayout(page), [], 'search error stays in the safe area');
  await shot(page, 'e2e-search-error');
  failing.search = false;
  await travelTo(page, fidIs('retry'));
  await key(page, K.ok);
  await page.waitForSelector('.results .card', { timeout: 10000 });
  await sleep(200);
  await clean(page, 'search recovered');

  await key(page, K.back, 300);
  await page.waitForSelector('.home');
  failing.home = true;
  await page.evaluate(() => window.Sozo.App.setProvider('uzbeklar'));
  await waitFocus(page, /Try again/, 15000);
  await clean(page, 'home error');
  await shot(page, 'e2e-home-error');
  failing.home = false;
  await key(page, K.ok);
  await waitFocus(page, /Title 100/, 10000);
  assert.equal((await appText(page)).includes('SECRET'), false);
  await done(ctx, 'each screen');
});

test('an empty source: Home, Categories, Search and listing all explain and keep focus', async () => {
  const ctx = await start({
    routes: {
      home: () => ({ body: { provider: 'uzbeklar', banner: [], sections: [] } }),
      categories: () => ({ body: { items: [] } }),
      genres: () => ({ body: { items: [] } }),
      latest: () => ({ body: { provider: 'uzbeklar', items: [], page: 1, totalPages: 1 } }),
      search: () => ({ body: { provider: 'uzbeklar', items: [], page: 1, totalPages: 1 } })
    }
  });
  const { page } = ctx;
  await waitFocus(page, /Change source|Home/);
  await sleep(200);
  await clean(page, 'empty home');
  await shot(page, 'e2e-empty-home');
  await toTab(page, 'categories');
  await sleep(300);
  await clean(page, 'empty categories');
  await shot(page, 'e2e-empty-categories');
  await travelTo(page, () => document.querySelector('.chip'));
  await key(page, K.ok, 300);
  await page.waitForFunction(() => !document.querySelector('.status-loading'));
  await sleep(200);
  await clean(page, 'empty listing');
  await waitFocus(page, /Back/);
  await shot(page, 'e2e-empty-listing');
  await key(page, K.back, 300);
  await toTab(page, 'search');
  await page.waitForSelector('.key');
  for (const ch of 'zz') { await travelTo(page, fidIs('key-' + ch)); await key(page, K.ok); }
  await page.waitForFunction(() => /Nothing found/.test(document.querySelector('.results').textContent), null, { timeout: 8000 });
  await clean(page, 'empty search');
  await shot(page, 'e2e-empty-search');
  await toTab(page, 'mylist');
  await sleep(200);
  await clean(page, 'empty my list');
  await shot(page, 'e2e-empty-mylist');
  await done(ctx, 'empty');
});

test('slow responses: leaving early drops the answer, and a late answer never steals focus', async () => {
  const base = require('../browser/harness.js');
  const SLOW = 1800;
  const ctx = await start({
    routes: {
      detail: (q) => delay(SLOW, { body: { provider: 'uzbeklar', contentUrl: q.get('url'), title: 'Slow film', year: 2024, isSerial: false } }),
      episodes: (q) => delay(SLOW, { body: { provider: 'uzbeklar', contentUrl: q.get('url'), isSerial: false, episodes: [{ episode: 1, label: 'Play', mediaRef: 'r' }], videoSources: [{ quality: '720p', videoUrl: 'https://cdn.test/a.mp4', type: 'mp4' }], headers: {} } }),
      categories: () => delay(SLOW, { body: base.fixture('categories_uzbeklar') }),
      genres: () => delay(SLOW, { body: base.fixture('genres_uzbeklar') }),
      search: (q) => delay(SLOW, { body: { provider: 'uzbeklar', query: q.get('q'), items: range(300, 6), page: 1, totalPages: 1 } })
    },
    media: () => 'clip'
  });
  const { page } = ctx;
  await waitFocus(page, /Title 5000/);

  // Back out of a detail that has not answered.
  await key(page, K.ok);
  await page.waitForSelector('.detail .status-loading');
  await shot(page, 'e2e-slow-detail');
  await key(page, K.back, 300);
  await waitFocus(page, /Title 5000/);
  await sleep(SLOW + 400);
  await clean(page, 'after dropped detail');
  await waitFocus(page, /Title 5000/);

  // Play only appears once episodes answer, and moving meanwhile keeps the viewer's place.
  await key(page, K.ok);
  await page.waitForSelector('.detail-title', { timeout: 6000 });
  await sleep(100);
  await clean(page, 'detail before episodes');
  const fi = await focusInfo(page);
  assert.match(fi.text, /My List/);
  await waitFocus(page, /Play/, 6000);
  await key(page, K.ok);
  await playing(page);
  await key(page, K.back, 300);
  await page.waitForFunction(() => !document.querySelector('video'));
  await key(page, K.back, 300);
  await waitFocus(page, /Title 5000/);

  // Move along the tabs while Categories loads: the answer must not pull focus back.
  await toTab(page, 'categories');
  await page.waitForSelector('.status-loading');
  await key(page, K.right, 200);
  const moved = (await focusInfo(page)).fid;
  await page.waitForSelector('.chip', { timeout: 8000 });
  await sleep(200);
  assert.equal((await focusInfo(page)).fid, moved, 'focus stayed where the viewer moved it');
  await clean(page, 'late categories');

  // Type past a slow search: only the last query is shown, and tab switch cancels it.
  await toTab(page, 'search');
  await page.waitForSelector('.key');
  for (const ch of 'abc') { await travelTo(page, fidIs('key-' + ch)); await key(page, K.ok); }
  await sleep(800);
  await toTab(page, 'home');
  await page.waitForSelector('.home .card');
  await sleep(SLOW + 300);
  await clean(page, 'search dropped');
  assert.equal(await page.evaluate(() => !!document.querySelector('.results')), false);
  await done(ctx, 'slow');
});

test('a request that never answers ends in a friendly timeout, and leaving early also works', async () => {
  const fb = require('../browser/harness.js');
  let hang = true;
  const ctx = await start({ routes: { categories: () => (hang ? { hang: true } : { body: fb.fixture('categories_uzbeklar') }), genres: () => (hang ? { hang: true } : { body: fb.fixture('genres_uzbeklar') }) } });
  const { page } = ctx;
  await waitFocus(page, /Title 5000/);
  await toTab(page, 'categories');
  await page.waitForSelector('.status-loading');
  await key(page, K.back, 300);
  await page.waitForSelector('.home');
  await clean(page, 'left a hanging screen');

  await page.clock.install();
  await toTab(page, 'categories');
  await page.waitForSelector('.status-loading');
  const limit = await page.evaluate(() => window.Sozo.Api.TIMEOUT);
  assert.ok(limit > 0 && limit <= 35000, 'request timeout is bounded: ' + limit);
  // One automatic retry, so the viewer waits two timeouts at most.
  for (let i = 0; i < 4; i++) {
    await page.clock.fastForward(limit + 1000);
    await sleep(100);
  }
  await waitFocus(page, /Try again/, 5000);
  await page.clock.resume();
  assert.match(await appText(page), /Try again/);
  await clean(page, 'timeout message');
  await shot(page, 'e2e-timeout');
  hang = false;
  await key(page, K.ok, 300);
  await page.waitForSelector('.chip', { timeout: 8000 });
  await done(ctx, 'hang');
});

test('broken images keep titled placeholders everywhere', async () => {
  const ctx = await start({ brokenImages: true, media: () => 'clip' });
  const { page } = ctx;
  await waitFocus(page, /Title 5000/);
  await key(page, K.down, 300);
  await sleep(500);
  await clean(page, 'home');
  await shot(page, 'e2e-broken-home');
  const ph = await page.evaluate(() => {
    const f = document.querySelector('.row .card .card-frame');
    const p = f.querySelector('.card-ph');
    const i = f.querySelector('img');
    return { ph: !!p && p.offsetHeight > 0, img: i ? getComputedStyle(i).visibility + '/' + i.naturalWidth : 'none' };
  });
  assert.equal(ph.ph, true);
  await key(page, K.ok, 300);
  await waitFocus(page, /Play/, 8000);
  await sleep(300);
  await clean(page, 'detail');
  await shot(page, 'e2e-broken-detail');
  await done(ctx, 'broken images');
});

function filmWith(sources) {
  return {
    home: () => ({ body: { provider: 'uzbeklar', banner: [], sections: [{ key: 'a', label: 'Row', items: [card(100), card(101), card(102)] }] } }),
    detail: (q) => ({ body: { provider: 'uzbeklar', contentUrl: q.get('url'), title: 'Film 100', year: 2024, isSerial: false } }),
    episodes: (q) => ({ body: { provider: 'uzbeklar', contentUrl: q.get('url'), isSerial: false, episodes: [{ episode: 1, label: 'Play', mediaRef: 'r' }], videoSources: sources, type: 'mp4', headers: {} } })
  };
}
const SRC = (name) => ({ quality: name, videoUrl: 'https://cdn.test/' + name + '.mp4', type: 'mp4' });

test('a stream that errors falls back to the next source and keeps playing', async () => {
  const ctx = await start({
    routes: filmWith([SRC('bad'), SRC('good')]),
    media: (u) => (u.includes('good') ? 'clip' : null)
  });
  const { page } = ctx;
  await waitFocus(page, /Title 100/);
  await key(page, K.ok);
  await waitFocus(page, /Play/, 8000);
  await key(page, K.ok);
  await playing(page);
  assert.match(await page.evaluate(() => document.querySelector('video').currentSrc), /good/);
  await clean(page, 'fell back');
  await key(page, K.back, 300);
  await waitFocus(page, /Resume|Play/);
  await done(ctx, 'fallback');
});

test('every stream failing shows a chooser that works by remote, and Back leaves', async () => {
  const ctx = await start({ routes: filmWith([SRC('a'), SRC('b')]), media: () => null });
  const { page } = ctx;
  await waitFocus(page, /Title 100/);
  await key(page, K.ok);
  await waitFocus(page, /Play/, 8000);
  await key(page, K.ok);
  await page.waitForSelector('.pl-panel.is-wide', { timeout: 15000 });
  await sleep(200);
  await clean(page, 'chooser');
  await shot(page, 'e2e-chooser');
  await keys(page, K.down, 2);
  assert.ok(await page.evaluate(() => window.Sozo.Nav.current().closest('.pl-panel')));
  await keys(page, K.left, 2);
  await keys(page, K.right, 2);
  await key(page, K.back, 300);
  await page.waitForFunction(() => !document.querySelector('video'));
  await waitFocus(page, /Play/);
  await done(ctx, 'chooser');
});

test('localStorage throwing on every access: the whole journey still works in memory', async () => {
  const init = `
    Object.defineProperty(window, 'localStorage', { get() { throw new Error('denied'); } });
  `;
  const ctx = await start({ init, routes: filmWith([SRC('a')]), media: () => 'clip' });
  const { page } = ctx;
  await waitFocus(page, /Title 100/);
  await key(page, K.ok);
  await waitFocus(page, /Play/, 8000);
  await travelTo(page, fidIs('mylist'));
  await key(page, K.ok);
  await waitFocus(page, /In My List/);
  await travelTo(page, fidIs('play'));
  await key(page, K.ok);
  await page.waitForFunction(() => { const v = document.querySelector('video'); return v && v.currentTime > 0.3; }, null, { timeout: 15000 });
  await page.evaluate(() => { document.querySelector('video').currentTime = 1; });
  await sleep(200);
  await key(page, K.back, 300);
  await waitFocus(page, /In My List|Resume|Play/);
  await key(page, K.back, 300);
  await toTab(page, 'mylist');
  await page.waitForSelector('.mylist .card');
  await clean(page, 'my list from memory');
  await toTab(page, 'settings');
  await sleep(200);
  await travelTo(page, fidIs('lang-ru'));
  await key(page, K.ok, 300);
  await clean(page, 'language without storage');
  assert.equal(await page.evaluate(() => document.documentElement.lang), 'ru');
  await done(ctx, 'storage');
});

test('localStorage full and throwing only on write: settings still take effect', async () => {
  const init = `Storage.prototype.setItem = function () { throw new DOMException('full', 'QuotaExceededError'); };`;
  const ctx = await start({ init, routes: filmWith([SRC('a')]), media: () => 'clip' });
  const { page } = ctx;
  await waitFocus(page, /Title 100/);
  await toTab(page, 'settings');
  await sleep(200);
  await travelTo(page, fidIs('lang-uz'));
  await key(page, K.ok, 300);
  assert.equal(await page.evaluate(() => document.documentElement.lang), 'uz');
  await clean(page, 'lang');
  await done(ctx, 'quota');
});

function mulberry(seed) {
  let a = seed;
  return () => {
    a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function hostileRoutes(seed) {
  const r = mulberry(seed * 7);
  const wrap = (name, ok) => async (q) => {
    await delay(Math.floor(r() * 400));
    if (r() < 0.2) return { status: 503, body: { code: 'ROUTE_TIMEOUT' } };
    return ok(q);
  };
  const fb = require('../browser/harness.js');
  return {
    detail: wrap('detail', (q) => ({ body: { provider: 'uzbeklar', contentUrl: q.get('url'), title: 'Film', year: 2024, isSerial: q.get('url').includes('/1-'), related: range(4000, 12) } })),
    episodes: wrap('episodes', (q) => (q.get('url').includes('/1-')
      ? { body: { provider: 'uzbeklar', contentUrl: q.get('url'), isSerial: true, episodes: [{ episode: 1, label: 'E1', mediaRef: 'r1', season: 1 }, { episode: 2, label: 'E2', mediaRef: 'r2', season: 2 }], page: 1, totalPages: 1 } }
      : { body: { provider: 'uzbeklar', contentUrl: q.get('url'), isSerial: false, episodes: [{ episode: 1, label: 'Play', mediaRef: 'r' }], videoSources: [SRC('a'), SRC('b')], type: 'mp4', headers: {} } })),
    media: wrap('media', () => ({ body: { videoSources: [SRC('a')], type: 'mp4', headers: {} } })),
    categories: wrap('categories', () => ({ body: fb.fixture('categories_uzbeklar') })),
    genres: wrap('genres', () => ({ body: fb.fixture('genres_uzbeklar') })),
    search: wrap('search', () => ({ body: { provider: 'uzbeklar', items: range(300, 6), page: 1, totalPages: 1 } })),
    latest: wrap('latest', () => ({ body: { provider: 'uzbeklar', items: range(600, 24), page: 1, totalPages: 1 } }))
  };
}

const SEEDS = (process.env.SOZO_MASH_SEEDS || '11,22,33').split(',').map(Number);
for (const seed of SEEDS) {
  test('fast key mashing against a flaky API leaves a working app, seed ' + seed, { timeout: 120000 }, async () => {
    const ctx = await start({ routes: seed % 2 ? hostileRoutes(seed) : undefined, media: (u) => (u.includes('/b.') || seed % 3 ? 'clip' : null) });
    const { page } = ctx;
    await waitFocus(page, /Title 5000/);
    const r = mulberry(seed);
    const pick = () => {
      const x = r();
      if (x < 0.62) return [K.left, K.right, K.up, K.down][Math.floor(r() * 4)];
      if (x < 0.85) return K.ok;
      if (x < 0.96) return K.back;
      return [K.play, K.pause, K.rewind, K.forward, K.stop][Math.floor(r() * 5)];
    };
    for (let i = 0; i < 700; i++) {
      await key(page, pick(), Math.floor(r() * 18));
      if (i % 100 === 99) {
        await page.evaluate((burst) => {
          for (const c of burst) {
            for (const type of ['keydown', 'keyup']) {
              const e = new KeyboardEvent(type, { bubbles: true, cancelable: true });
              Object.defineProperty(e, 'keyCode', { get: () => c });
              document.dispatchEvent(e);
            }
          }
        }, Array.from({ length: 40 }, pick));
      }
    }
    await sleep(500);
    // The app must still respond: dialogs close, then the arrows move something.
    for (let i = 0; i < 8; i++) {
      if (!(await page.evaluate(() => window.Sozo.Dialog.isOpen() || window.Sozo.Player.isOpen() || !window.Sozo.App.router.isRoot()))) break;
      await key(page, K.back, 300);
    }
    await page.waitForFunction(() => window.Sozo.Nav.current(), null, { timeout: 8000 });
    await sleep(300);
    const before = await focusInfo(page);
    assert.ok(before);
    await key(page, K.down, 200);
    await key(page, K.up, 200);
    await key(page, K.right, 200);
    await key(page, K.left, 200);
    assert.deepEqual(await invariants(page), [], 'after mash');
    await done(ctx, 'mash ' + seed);
  });
}

test('focus heals by itself when the focused node disappears', async () => {
  const ctx = await start({ media: () => 'clip' });
  const { page } = ctx;
  await waitFocus(page, /Title 5000/);
  await key(page, K.down, 300);
  await page.evaluate(() => { const c = window.Sozo.Nav.current(); c.parentNode.removeChild(c); });
  await page.waitForFunction(() => { const c = window.Sozo.Nav.current(); return c && document.documentElement.contains(c); }, null, { timeout: 2000 });
  await sleep(100);
  assert.deepEqual(await invariants(page), []);
  await page.evaluate(() => window.__focusFaults.length = 0);
  await done(ctx, 'heal');
});

test('a held key sends keydown with no keyup and opens only one screen', async () => {
  const ctx = await start({ media: () => 'clip' });
  const { page } = ctx;
  await waitFocus(page, /Title 5000/);
  await page.evaluate((ok) => {
    for (let i = 0; i < 30; i++) {
      const e = new KeyboardEvent('keydown', { bubbles: true, cancelable: true });
      Object.defineProperty(e, 'keyCode', { get: () => ok });
      document.dispatchEvent(e);
    }
    const up = new KeyboardEvent('keyup', { bubbles: true, cancelable: true });
    Object.defineProperty(up, 'keyCode', { get: () => ok });
    document.dispatchEvent(up);
  }, K.ok);
  await sleep(300);
  assert.deepEqual(await page.evaluate(() => window.Sozo.App.router.names()), ['home', 'detail']);
  await done(ctx, 'held');
});
