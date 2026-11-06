'use strict';
// Whole viewer journey against production. GET only and gentle. Runs with: npm run test:e2e:live
const test = require('node:test');
const assert = require('node:assert');
const L = require('./lib.js');
const { K, key, keys, sleep, travelTo, invariants, shot, waitFocus, waitFocusSel, focusInfo } = L;

const fidIs = (fid) => new Function('return function () { const l = document.querySelectorAll(".fc"); for (let i = 0; i < l.length; i++) if (l[i]._fid === ' + JSON.stringify(fid) + ' && l[i].offsetWidth) return l[i]; return null; };')();
const hasText = (sel, re) => new Function('return function () { const l = document.querySelectorAll(' + JSON.stringify(sel) + '); for (let i = 0; i < l.length; i++) if (' + re + '.test(l[i].textContent) && l[i].offsetWidth) return l[i]; return null; };')();

async function clean(page, label, opts) {
  const p = await invariants(page, opts);
  assert.deepEqual(p, [], label);
}

const video = (page) => page.evaluate(() => {
  const v = document.querySelector('video');
  if (!v) return null;
  return { t: v.currentTime, paused: v.paused, d: v.duration, w: v.videoWidth, h: v.videoHeight, err: v.error && v.error.code, src: v.currentSrc, ready: v.readyState };
});

async function advancing(page, timeout) {
  await page.waitForFunction(() => {
    const v = document.querySelector('video');
    return v && !v.paused && v.currentTime > 0.5 && v.videoWidth > 0;
  }, null, { timeout: timeout || 60000 }).catch(async (e) => {
    const state = await page.evaluate(() => {
      const v = document.querySelector('video');
      const pane = document.querySelector('.pl-panel, .pl-pane');
      return { video: !!v, src: v && String(v.currentSrc).slice(0, 60), paused: v && v.paused, time: v && v.currentTime, ready: v && v.readyState, net: v && v.networkState, error: v && v.error && v.error.code, pane: pane && pane.textContent.slice(0, 120) };
    });
    throw new Error('not playing: ' + JSON.stringify(state) + ' / ' + e.message.split('\n')[0]);
  });
  // A real stream may rebuffer for a few seconds, so one still sample is not a failure.
  const a = await video(page);
  let b = a;
  for (let i = 0; i < 8 && b.t <= a.t; i++) {
    await sleep(2500);
    b = await video(page);
  }
  assert.ok(b.t > a.t, 'currentTime advances: ' + a.t + ' -> ' + b.t);
  return b;
}

async function goToTab(page, name) {
  for (let i = 0; i < 12; i++) {
    const inBar = await page.evaluate(() => window.Sozo.App.topbar.el.contains(window.Sozo.Nav.current()));
    if (inBar) break;
    await key(page, K.up, 200);
  }
  await travelTo(page, fidIs('tab-' + name));
  await key(page, K.ok);
}

test('live viewer journey', { skip: process.env.SOZO_LIVE ? false : 'set SOZO_LIVE=1', timeout: 600000 }, async (t) => {
  const browser = await L.launch();
  const context = await browser.newContext({ viewport: { width: 1920, height: 1080 }, locale: 'en-US' });
  const page = await context.newPage();
  const log = L.watch(page, context);
  const result = {};
  t.after(() => browser.close());

  await t.test('open and Home loads', async () => {
    await page.goto(L.INDEX);
    await page.waitForSelector('.home .card, .home .hero', { timeout: 45000 });
    await sleep(800);
    await clean(page, 'home');
    await shot(page, 'live-01-home');
  });

  await t.test('rows navigate in all directions', async () => {
    await page.waitForFunction(() => window.Sozo.Nav.current().closest('.home'), null, { timeout: 5000 });
    const seen = new Set();
    for (const dir of ['down', 'right', 'right', 'right', 'down', 'right', 'left', 'down', 'left', 'up', 'up', 'right', 'up', 'left']) {
      const before = await focusInfo(page);
      await key(page, K[dir], 350);
      const after = await focusInfo(page);
      seen.add(after.text);
      await clean(page, dir + ' from ' + before.text);
    }
    assert.ok(seen.size >= 6, 'focus really moved, saw ' + seen.size);
    await shot(page, 'live-02-rows');
    for (let i = 0; i < 40; i++) {
      await key(page, K.right, 110);
      await clean(page, 'held right ' + i);
    }
    for (let i = 0; i < 30; i++) await key(page, K.left, 110);
    await clean(page, 'held left');
  });

  await t.test('change source with the source picker', async () => {
    await goToTab(page, 'home');
    await travelTo(page, () => document.querySelector('.source-chip'));
    await key(page, K.ok);
    await page.waitForSelector('.source-card', { timeout: 30000 });
    await sleep(300);
    await clean(page, 'sources');
    await shot(page, 'live-03-sources');
    await travelTo(page, hasText('.source-card', '/^AniLibria/i'));
    await key(page, K.ok);
    await page.waitForSelector('.home .card', { timeout: 45000 });
    await sleep(600);
    assert.equal(await page.evaluate(() => window.Sozo.App.provider.id), 'anilibria');
    await clean(page, 'anilibria home');
    await shot(page, 'live-04-anilibria-home');
  });

  await t.test('search with the on-screen keyboard', async () => {
    await goToTab(page, 'search');
    await page.waitForSelector('.keys .key');
    for (const ch of 'naruto') {
      await travelTo(page, fidIs('key-' + ch));
      await key(page, K.ok);
    }
    assert.match(await page.evaluate(() => document.querySelector('.query').textContent), /naruto/);
    // Slow typing lets a prefix search through, whose cards vanish when the debounce fires for the whole word.
    await sleep(900);
    await page.waitForSelector('.results .card', { timeout: 30000 });
    await sleep(400);
    await clean(page, 'results');
    await shot(page, 'live-05-search');
    await travelTo(page, () => document.querySelector('.results .card'));
    await clean(page, 'results focus');
    await key(page, K.ok);
  });

  await t.test('open detail and choose an episode', async () => {
    await page.waitForSelector('.detail .detail-title', { timeout: 30000 });
    await waitFocusSel(page, '.detail-actions .fc', 45000);
    await sleep(500);
    await clean(page, 'detail');
    await shot(page, 'live-06-detail');
    await page.waitForSelector('.detail-episodes .card', { timeout: 30000 });
    await travelTo(page, () => document.querySelector('.detail-episodes .card'));
    await key(page, K.right, 400);
    await clean(page, 'episode row');
    await shot(page, 'live-07-episodes');
    result.episodeText = (await focusInfo(page)).text;
    await key(page, K.ok);
  });

  await t.test('playback starts', async () => {
    const v = await advancing(page, 70000);
    result.play = v;
    t.diagnostic('anilibria playing ' + v.w + 'x' + v.h + ' at ' + v.t + ' engine ' + await page.evaluate(() => (window.Hls ? 'hls.js' : 'native')));
    await shot(page, 'live-08-playing');
  });

  await t.test('seek', async () => {
    const a = await video(page);
    await keys(page, K.right, 3, 120);
    await shot(page, 'live-09-seek');
    await sleep(800);
    await page.waitForFunction((min) => document.querySelector('video').currentTime > min, a.t + 25, { timeout: 20000 });
    const b = await advancing(page, 40000);
    assert.ok(b.t >= a.t + 25 && b.t < a.t + 50, 'three quick presses move 30 s: ' + a.t + ' -> ' + b.t);
    await keys(page, K.left, 1);
    await sleep(800);
    await page.waitForFunction((max) => document.querySelector('video').currentTime < max, b.t, { timeout: 20000 });
    await advancing(page, 40000);
  });

  await t.test('change quality', async () => {
    const before = await video(page);
    await key(page, K.down, 300);
    await page.waitForFunction(() => document.querySelector('.pl-bottom').style.display !== 'none');
    await travelTo(page, fidIs('quality'));
    await key(page, K.ok, 400);
    await page.waitForSelector('.pl-panel .pl-item');
    await clean(page, 'quality panel');
    await shot(page, 'live-10-quality');
    const opts = await page.evaluate(() => Array.from(document.querySelectorAll('.pl-item')).map((e) => e.textContent + (e.classList.contains('is-selected') ? '*' : '')));
    t.diagnostic('quality options: ' + opts.join(', '));
    await keys(page, K.down, 1);
    await page.evaluate(() => 0);
    const target = await focusInfo(page);
    await key(page, K.ok, 500);
    assert.equal(await page.evaluate(() => !!document.querySelector('.pl-panel')), false);
    result.quality = target.text;
    await sleep(1000);
    const v = await advancing(page, 60000);
    assert.ok(Math.abs(v.t - before.t) < 25, 'position kept across the switch: ' + before.t + ' -> ' + v.t);
    await clean(page, 'after quality');
    await key(page, K.down, 300);
    await travelTo(page, fidIs('quality'));
    await key(page, K.ok, 400);
    const now = await page.evaluate(() => document.querySelector('.pl-item.is-selected').textContent);
    assert.equal(now, target.text, 'the chosen quality is the one selected now');
    await key(page, K.back, 300);
    await key(page, K.up, 200);
    result.left = (await video(page)).t;
  });

  await t.test('leave, Continue Watching shows it, Resume lands near the position', async () => {
    result.left = (await video(page)).t;
    await key(page, K.back, 500);
    await page.waitForFunction(() => !document.querySelector('video'));
    await waitFocusSel(page, '.detail .fc', 5000);
    await clean(page, 'back on detail');
    await shot(page, 'live-11-detail-after');
    await key(page, K.back, 500);
    await page.waitForSelector('.search .keys', { timeout: 5000 });
    await clean(page, 'back on search');
    await goToTab(page, 'home');
    await page.waitForSelector('.home .card', { timeout: 30000 });
    await sleep(500);
    await clean(page, 'home again');
    const row = await page.evaluate(() => document.querySelector('.row .row-label').textContent);
    assert.match(row, /Continue/);
    await travelTo(page, () => document.querySelector('.row .card'));
    await shot(page, 'live-12-continue');
    await key(page, K.ok);
    await page.waitForSelector('.detail .detail-title', { timeout: 30000 });
    await page.waitForFunction(() => /Resume/.test((document.querySelector('.detail-actions .btn-primary') || {}).textContent || ''), null, { timeout: 45000 });
    await shot(page, 'live-13-resume');
    await waitFocus(page, /Resume/);
    await key(page, K.ok);
    await page.waitForFunction(() => { const v = document.querySelector('video'); return v && !v.paused && v.currentTime > 1 && v.videoWidth > 0; }, null, { timeout: 70000 });
    const v = await video(page);
    t.diagnostic('left at ' + result.left + ', resumed at ' + v.t);
    assert.ok(Math.abs(v.t - result.left) < 25, 'resume near ' + result.left + ': got ' + v.t);
    await sleep(1500);
    await key(page, K.back, 500);
    await page.waitForFunction(() => !document.querySelector('video'));
  });

  await t.test('other screens on a real source: categories, listing, my list, settings', async () => {
    await page.waitForSelector('.detail');
    await sleep(300);
    await key(page, K.back, 500);
    await page.waitForSelector('.home');
    await goToTab(page, 'home');
    await sleep(200);
    await travelTo(page, () => document.querySelector('.source-chip'));
    await key(page, K.ok);
    await page.waitForSelector('.source-card', { timeout: 30000 });
    await sleep(300);
    await travelTo(page, hasText('.source-card', '/^Uzbeklar/i'));
    await key(page, K.ok);
    await page.waitForSelector('.home .card, .home .hero', { timeout: 45000 });
    await sleep(600);
    await clean(page, 'uzbeklar home');
    await goToTab(page, 'categories');
    await page.waitForSelector('.chip', { timeout: 30000 });
    await sleep(400);
    await clean(page, 'categories');
    assert.deepEqual(await L.auditLayout(page), []);
    await shot(page, 'live-14-categories');
    await key(page, K.down, 300);
    await key(page, K.ok);
    await page.waitForSelector('.listing .card', { timeout: 30000 });
    await sleep(500);
    await clean(page, 'listing');
    assert.deepEqual(await L.auditLayout(page), []);
    await shot(page, 'live-15-listing');
    await key(page, K.back, 500);
    await goToTab(page, 'mylist');
    await sleep(500);
    await clean(page, 'my list');
    await shot(page, 'live-16-mylist');
    await goToTab(page, 'settings');
    await sleep(300);
    await clean(page, 'settings');
    await goToTab(page, 'home');
    await page.waitForSelector('.home .hero', { timeout: 30000 });
    await sleep(400);
  });

  await t.test('a film from a real source plays and resumes', async () => {
    // The first row is Continue Watching from the journey above, so take a card from the source's own row.
    await travelTo(page, () => document.querySelectorAll('.home .row')[1].querySelector('.card'));
    await key(page, K.ok);
    await page.waitForSelector('.detail .detail-title', { timeout: 30000 });
    await page.waitForFunction(() => /Play|Resume/.test((document.querySelector('.detail-actions .btn-primary') || {}).textContent || ''), null, { timeout: 45000 });
    await sleep(300);
    await clean(page, 'film detail');
    await shot(page, 'live-17-film-detail');
    await page.evaluate(() => { const b = document.querySelector('.detail-actions .btn-primary'); window.Sozo.Nav.focus(b); });
    await key(page, K.ok);
    const v = await advancing(page, 70000);
    t.diagnostic('film: ' + v.w + 'x' + v.h + ' ' + v.src.slice(0, 40));
    await keys(page, K.right, 2, 120);
    await sleep(1000);
    await page.waitForFunction((m) => document.querySelector('video').currentTime > m, v.t + 15, { timeout: 30000 });
    await advancing(page, 40000);
    await key(page, K.ok, 300);
    await shot(page, 'live-18-film-paused');
    await clean(page, 'paused');
    const left = (await video(page)).t;
    await key(page, K.back, 500);
    await page.waitForFunction(() => !document.querySelector('video'));
    await page.waitForFunction(() => /Resume/.test((document.querySelector('.detail-actions .btn-primary') || {}).textContent || ''), null, { timeout: 5000 });
    await sleep(200);
    await key(page, K.ok);
    await page.waitForFunction(() => { const x = document.querySelector('video'); return x && !x.paused && x.currentTime > 1; }, null, { timeout: 70000 });
    const resumed = await video(page);
    assert.ok(Math.abs(resumed.t - left) < 10, 'film resume near ' + left + ': got ' + resumed.t);
    await sleep(800);
    await key(page, K.back, 500);
    await page.waitForFunction(() => !document.querySelector('video'));
    await key(page, K.back, 400);
  });

  await t.test('a live channel plays and asks before leaving', async () => {
    await page.waitForSelector('.home');
    await goToTab(page, 'home');
    await sleep(200);
    await travelTo(page, () => document.querySelector('.source-chip'));
    await key(page, K.ok);
    await page.waitForSelector('.source-card', { timeout: 30000 });
    await sleep(300);
    await travelTo(page, hasText('.source-card', '/^World Live/i'));
    await key(page, K.ok);
    await page.waitForSelector('.home .card-wide', { timeout: 45000 });
    await sleep(600);
    await clean(page, 'live home');
    await shot(page, 'live-19-channels');
    await travelTo(page, () => document.querySelector('.home .card-wide'));
    await key(page, K.ok);
    await page.waitForFunction(() => /live/i.test((document.querySelector('.detail-actions .btn-primary') || {}).textContent || ''), null, { timeout: 45000 });
    await sleep(300);
    await shot(page, 'live-20-channel-detail');
    await key(page, K.ok);
    const got = await page.waitForFunction(() => {
      const x = document.querySelector('video');
      if (x && !x.paused && x.currentTime > 0.5) return 'playing';
      return document.querySelector('.pl-panel.is-wide, .pl-pane') ? 'failed' : false;
    }, null, { timeout: 70000 });
    const state = await got.jsonValue();
    t.diagnostic('live channel: ' + state);
    await shot(page, 'live-21-channel-' + state);
    if (state === 'playing') {
      await key(page, K.back, 400);
      const after = await page.evaluate(() => {
        const x = document.querySelector('video');
        return {
          dialog: window.Sozo.Dialog.isOpen(),
          chooser: !!document.querySelector('.pl-panel.is-wide, .pl-pane'),
          video: !!x,
          time: x && x.currentTime,
          ready: x && x.readyState,
          error: x && x.error && x.error.code
        };
      });
      assert.equal(after.dialog, true, 'leave dialog after Back: ' + JSON.stringify(after));
      await shot(page, 'live-22-leave-dialog');
      await key(page, K.back, 400);
      assert.equal(await page.evaluate(() => window.Sozo.Dialog.isOpen()), false);
      assert.ok(await page.evaluate(() => !!document.querySelector('video')));
      await key(page, K.back, 400);
      await travelTo(page, () => window.Sozo.Nav.current().parentNode.querySelectorAll('.fc')[1]);
      await key(page, K.ok, 400);
    } else {
      await key(page, K.back, 400);
    }
    await page.waitForFunction(() => !document.querySelector('video'), null, { timeout: 5000 });
  });

  await t.test('no errors, no adult header, GET only', async () => {
    assert.deepEqual(log.errors, []);
    assert.deepEqual(log.consoleErrors, []);
    assert.equal(log.requests.some((r) => r.adult), false);
    const used = Array.from(new Set(log.requests.map((r) => { const m = /[?&]provider=([^&]+)/.exec(r.url); return m ? m[1] : null; }).filter(Boolean)));
    const unknown = await page.evaluate((ids) => ids.filter((i) => !window.Sozo.Providers.byId(i)), used);
    assert.deepEqual(unknown, [], 'only allowlisted providers were asked');
    assert.deepEqual(log.requests.filter((r) => r.url.includes('apisozo') && r.method !== 'GET').map((r) => r.url), []);
    const counts = {};
    for (const f of log.netFails) counts[f] = (counts[f] || 0) + 1;
    t.diagnostic('network failures logged by the browser: ' + JSON.stringify(counts));
  });
});
