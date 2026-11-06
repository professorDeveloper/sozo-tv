'use strict';
// Every screen: layout audit, then screenshots for looking at by eye.
const test = require('node:test');
const assert = require('node:assert');
const { openApp, closeBrowser, card, range, shot, fixture } = require('../browser/harness.js');
const L = require('./lib.js');
const { K, key, keys, sleep, travelTo, invariants, waitFocus, focusInfo, auditLayout } = L;

test.after(closeBrowser);

const fidIs = (fid) => new Function('return function () { const l = document.querySelectorAll(".fc"); for (let i = 0; i < l.length; i++) if (l[i]._fid === ' + JSON.stringify(fid) + ' && l[i].offsetWidth) return l[i]; return null; };')();

async function check(page, name) {
  await sleep(350);
  assert.deepEqual(await invariants(page, { allowNoFocus: false }), [], name + ' invariants');
  assert.deepEqual(await auditLayout(page), [], name + ' layout');
  await shot(page, 's-' + name);
}

async function toTab(page, name) {
  for (let i = 0; i < 12; i++) {
    if (await page.evaluate(() => window.Sozo.App.topbar.el.contains(window.Sozo.Nav.current()))) break;
    await key(page, K.up, 150);
  }
  await travelTo(page, fidIs('tab-' + name));
  await key(page, K.ok, 200);
}

const LONG = 'A very long title that goes on and on and will never fit on one line of any card or header on this screen at all, really';

test('tour of every screen in English: nothing outside the safe area, nothing too small', { timeout: 120000 }, async () => {
  const ctx = await openApp({ media: () => 'clip' });
  const { page, log, context } = ctx;
  await waitFocus(page, /Title 5000/);
  await check(page, 'home-hero');
  await key(page, K.right, 500);
  await check(page, 'home-hero-2');
  const hero = await page.evaluate(() => { const r = window.Sozo.Nav.current().getBoundingClientRect(); return [r.left, r.right]; });
  assert.deepEqual(hero, [80, 1840], 'a hero slide sits whole inside the safe area');
  await key(page, K.down, 500);
  await check(page, 'home-row');
  const heroCut = await page.evaluate(() => {
    const r = document.querySelector('.hero-row').getBoundingClientRect();
    return r.bottom > 124 && r.top < 124;
  });
  assert.equal(heroCut, false, 'the hero is not left half cut by the top bar');
  await key(page, K.up, 500);
  await key(page, K.up, 300);
  await check(page, 'home-tab-focus');
  await travelTo(page, () => document.querySelector('.source-chip'));
  await check(page, 'home-source-focus');
  await key(page, K.ok);
  await page.waitForSelector('.source-card');
  await check(page, 'sources');
  await key(page, K.back, 300);
  await toTab(page, 'search');
  await page.waitForSelector('.key');
  await check(page, 'search-empty');
  for (const ch of 'ti') { await travelTo(page, fidIs('key-' + ch)); await key(page, K.ok); }
  await page.waitForSelector('.results .card', { timeout: 6000 });
  await check(page, 'search-results');
  await toTab(page, 'categories');
  await page.waitForSelector('.chip');
  await check(page, 'categories');
  await key(page, K.ok, 200);
  await page.waitForSelector('.listing .card');
  await check(page, 'listing');
  await keys(page, K.down, 2, 200);
  await check(page, 'listing-scrolled');
  await key(page, K.back, 300);
  await toTab(page, 'settings');
  await check(page, 'settings');
  await toTab(page, 'home');
  await page.waitForSelector('.home');
  await key(page, K.down, 300);
  await key(page, K.down, 300);
  await key(page, K.ok);
  await waitFocus(page, /Play/, 6000);
  await check(page, 'detail-film');
  await travelTo(page, fidIs('mylist'));
  await key(page, K.ok, 200);
  await check(page, 'detail-in-list');
  await key(page, K.back, 300);
  await toTab(page, 'mylist');
  await page.waitForSelector('.mylist .card');
  await check(page, 'mylist');
  await toTab(page, 'home');
  await key(page, K.back, 400);
  await check(page, 'exit-dialog');
  await key(page, K.back, 300);
  assert.deepEqual(log.errors, []);
  assert.deepEqual(log.consoleErrors, []);
  await context.close();
});

test('long titles and text stay in their boxes on Home, Detail and the player', async () => {
  const long = (i) => card(i, { title: LONG, description: LONG + ' ' + LONG + ' ' + LONG + ' ' + LONG });
  const ctx = await openApp({
    routes: {
      home: () => ({ body: { provider: 'uzbeklar', banner: [Object.assign(long(1), { banner: 'https://img.test/b.jpg' })], sections: [{ key: 'a', label: LONG + ' ' + LONG, items: [long(2), long(3)] }] } }),
      detail: (q) => ({ body: { provider: 'uzbeklar', contentUrl: q.get('url'), title: LONG, description: LONG + LONG + LONG + LONG + LONG + LONG + LONG + LONG, year: 2024, duration: '90', country: LONG, director: LONG, genres: ['One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten'], cast: Array.from({ length: 12 }, (_, i) => ({ name: 'A rather long actor name ' + i })), isSerial: false } }),
      episodes: (q) => ({ body: { provider: 'uzbeklar', contentUrl: q.get('url'), isSerial: false, episodes: [{ episode: 1, label: 'Play', mediaRef: 'r' }], videoSources: [{ quality: LONG, videoUrl: 'https://cdn.test/a.mp4', type: 'mp4' }, { quality: '480p', videoUrl: 'https://cdn.test/b.mp4', type: 'mp4' }], headers: {} } })
    },
    media: () => 'clip'
  });
  const { page, context, log } = ctx;
  await waitFocus(page, /very long/);
  await check(page, 'long-home');
  await key(page, K.down, 400);
  await check(page, 'long-home-row');
  await key(page, K.ok, 200);
  await waitFocus(page, /Play/, 6000);
  await check(page, 'long-detail');
  await key(page, K.ok);
  await page.waitForFunction(() => { const v = document.querySelector('video'); return v && v.currentTime > 0.2; }, null, { timeout: 15000 });
  await key(page, K.up, 300);
  await check(page, 'long-player');
  await travelTo(page, fidIs('quality'));
  await key(page, K.ok, 300);
  await check(page, 'long-quality');
  await key(page, K.back, 300);
  assert.deepEqual(log.errors, []);
  await context.close();
});

test('a poster hero sits flush with the frame', async () => {
  const noBanner = { provider: 'uzbeklar', banner: [card(1, { banner: null, description: 'A film' })], sections: [{ key: 'a', label: 'Row', items: [card(2)] }] };
  const ctx = await openApp({ routes: { home: () => ({ body: noBanner }) } });
  const { page, context } = ctx;
  await waitFocus(page, /Title 1/);
  await page.waitForSelector('.hero-poster');
  const r = await page.evaluate(() => [document.querySelector('.hero-poster').getBoundingClientRect().right, document.querySelector('.hero-frame').getBoundingClientRect().right]);
  assert.equal(r[0], r[1]);
  await context.close();
});

test('the player subtitle does not say the episode number twice, and keeps a real name', async () => {
  const eps = [{ episode: 3, label: 'Серия 3', mediaRef: 'r3', season: 1 }, { episode: 4, label: 'Pilot', mediaRef: 'r4', season: 1 }];
  const ctx = await openApp({
    routes: {
      home: () => ({ body: { provider: 'uzbeklar', banner: [], sections: [{ key: 'a', label: 'Row', items: [card(100)] }] } }),
      detail: (q) => ({ body: { provider: 'uzbeklar', contentUrl: q.get('url'), title: 'Series', year: 2024, isSerial: true } }),
      episodes: (q) => ({ body: { provider: 'uzbeklar', contentUrl: q.get('url'), isSerial: true, episodes: eps, page: 1, totalPages: 1 } }),
      media: () => ({ body: { videoSources: [{ quality: '720p', videoUrl: 'https://cdn.test/a.mp4', type: 'mp4' }], type: 'mp4', headers: {} } })
    },
    media: () => 'clip'
  });
  const { page, context } = ctx;
  await waitFocus(page, /Title 100/);
  await key(page, K.ok);
  await waitFocus(page, /Play/, 6000);
  await key(page, K.ok);
  await page.waitForFunction(() => { const v = document.querySelector('video'); return v && v.currentTime > 0.2; }, null, { timeout: 12000 });
  const first = await page.evaluate(() => document.querySelector('.pl-subtitle').textContent);
  assert.ok(!first.includes('Серия'), first);
  assert.match(first, /Episode 3/);
  await key(page, K.back, 300);
  await waitFocus(page, /Play|Resume/);
  await travelTo(page, () => document.querySelectorAll('.detail-episodes .card')[1]);
  await key(page, K.ok);
  await page.waitForFunction(() => { const v = document.querySelector('video'); return v && v.currentTime > 0.2; }, null, { timeout: 12000 });
  assert.match(await page.evaluate(() => document.querySelector('.pl-subtitle').textContent), /Pilot/);
  await context.close();
});

test('two-line titles in a grid do not run into the row below', async () => {
  const ctx = await openApp({ routes: { latest: () => ({ body: { provider: 'uzbeklar', items: Array.from({ length: 24 }, (_, i) => card(6000 + i, { title: LONG + i })), page: 1, totalPages: 1 } }) } });
  const { page, context } = ctx;
  await waitFocus(page, /Title 5000/);
  await toTab(page, 'categories');
  await page.waitForSelector('.chip');
  await sleep(200);
  await key(page, K.ok, 300);
  await page.waitForSelector('.listing .card');
  await sleep(300);
  const r = await page.evaluate(() => {
    const cards = Array.from(document.querySelectorAll('.listing .card')).sort((a, b) => a._idx - b._idx);
    const sub = cards[0].querySelector('.card-sub').getBoundingClientRect();
    const below = cards[6].querySelector('.card-frame').getBoundingClientRect();
    return { subBottom: sub.bottom, nextTop: below.top };
  });
  assert.ok(r.subBottom + 10 < r.nextTop, 'year ends at ' + r.subBottom + ', next poster starts at ' + r.nextTop);
  await shot(page, 's-grid-long');
  await context.close();
});

test('Uzbek and Russian: labels fit too', async () => {
  for (const lang of ['uz', 'ru']) {
    const ctx = await openApp({ locale: lang === 'uz' ? 'uz-UZ' : 'ru-RU', media: () => 'clip' });
    const { page, context } = ctx;
    await waitFocus(page, /Title 5000/);
    await check(page, lang + '-home');
    await toTab(page, 'settings');
    await check(page, lang + '-settings');
    await toTab(page, 'search');
    await page.waitForSelector('.key');
    await check(page, lang + '-search');
    await toTab(page, 'home');
    await key(page, K.back, 300);
    await check(page, lang + '-exit');
    await context.close();
  }
});

test('series detail and season chips', async () => {
  const ctx = await openApp({ media: () => 'clip' });
  const { page, context } = ctx;
  await waitFocus(page, /Title 5000/);
  await key(page, K.ok);
  await page.waitForSelector('.detail-episodes .card', { timeout: 6000 });
  await check(page, 'detail-series');
  await travelTo(page, () => document.querySelector('.detail-episodes .card'));
  await key(page, K.up, 300);
  await check(page, 'detail-seasons');
  const cut = await page.evaluate(() => {
    const r = document.querySelector('.detail-head').getBoundingClientRect();
    return r.top < 0 && r.bottom > 0;
  });
  assert.equal(cut, false, 'the title block is shown whole or scrolled away whole');
  await key(page, K.up, 400);
  await check(page, 'detail-back-up');
  assert.equal(await page.evaluate(() => document.querySelector('.detail-head').getBoundingClientRect().top >= 0), true);
  await context.close();
});
