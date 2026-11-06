'use strict';
// DOM nodes, listeners and heap must not grow across screen changes and player opens.
const test = require('node:test');
const assert = require('node:assert');
const { openApp, closeBrowser, card } = require('../browser/harness.js');
const L = require('./lib.js');
const { K, key, sleep, travelTo, invariants, waitFocus, heap, domCount } = L;

test.after(closeBrowser);

const fidIs = (fid) => new Function('return function () { const l = document.querySelectorAll(".fc"); for (let i = 0; i < l.length; i++) if (l[i]._fid === ' + JSON.stringify(fid) + ' && l[i].offsetWidth) return l[i]; return null; };')();

// waitForSelector would hold an element handle, which keeps a whole old screen alive and reads as a leak.
const has = (page, sel) => page.waitForFunction((x) => !!document.querySelector(x), sel);

async function toTab(page, name) {
  for (let i = 0; i < 12; i++) {
    if (await page.evaluate(() => window.Sozo.App.topbar.el.contains(window.Sozo.Nav.current()))) break;
    await key(page, K.up, 110);
  }
  await travelTo(page, fidIs('tab-' + name));
  await key(page, K.ok, 150);
}

// Twenty screen changes, ending back on Home.
async function screens(page) {
  for (const name of ['search', 'categories', 'mylist', 'settings', 'home', 'categories', 'search', 'settings', 'mylist', 'home']) {
    await toTab(page, name);
    await sleep(80);
    assert.deepEqual(await invariants(page), [], 'tab ' + name);
  }
  for (let i = 0; i < 5; i++) {
    await has(page, '.home .card');
    await key(page, K.down, 130);
    await key(page, K.ok, 150);
    await has(page, '.detail-title');
    await sleep(120);
    await key(page, K.back, 200);
    await has(page, '.home .card');
  }
}

async function playerCycle(page) {
  await waitFocus(page, /Title 100/);
  await key(page, K.ok);
  await waitFocus(page, /Play|Resume/, 6000);
  await key(page, K.ok);
  await page.waitForFunction(() => { const v = document.querySelector('video'); return v && v.currentTime > 0.2; }, null, { timeout: 12000 });
  await key(page, K.up, 150);
  await key(page, K.back, 250);
  await page.waitForFunction(() => !document.querySelector('video'));
  await waitFocus(page, /Play|Resume/);
  await key(page, K.back, 250);
}

test('20 screen changes and 5 player opens leave no growth', { timeout: 240000 }, async (t) => {
  const { page, context, log } = await openApp({
    routes: {
      home: () => ({ body: { provider: 'uzbeklar', banner: [], sections: [{ key: 'a', label: 'Row', items: [card(100), card(101), card(102), card(103)] }, { key: 'b', label: 'Second', items: [card(200), card(201)] }] } }),
      detail: (q) => ({ body: { provider: 'uzbeklar', contentUrl: q.get('url'), title: 'Film', year: 2024, isSerial: false } }),
      episodes: (q) => ({ body: { provider: 'uzbeklar', contentUrl: q.get('url'), isSerial: false, episodes: [{ episode: 1, label: 'Play', mediaRef: 'r' }], videoSources: [{ quality: '720p', videoUrl: 'https://cdn.test/a.mp4', type: 'mp4' }], headers: {} } })
    },
    media: () => 'clip'
  });
  const client = await context.newCDPSession(page);
  await client.send('Performance.enable');
  await client.send('HeapProfiler.enable');
  await waitFocus(page, /Title 100/);

  // One warm round, so caches and first-use allocations do not count as growth.
  await screens(page);
  await page.evaluate(() => window.Sozo.Nav.current());
  await toTab(page, 'home');
  await key(page, K.down, 150);
  await key(page, K.up, 150);
  await sleep(300);
  const base = await heap(page, client);
  const baseDom = await domCount(page);

  for (let i = 0; i < 2; i++) {
    await screens(page);
  }
  await sleep(300);
  await key(page, K.up, 150);
  await sleep(300);
  const afterScreens = await heap(page, client);
  const afterScreensDom = await domCount(page);

  await page.evaluate(() => { const c = document.querySelector('.home .card'); if (c) window.Sozo.Nav.focus(c); });
  await sleep(100);
  for (let i = 0; i < 2; i++) await playerCycle(page);
  await page.evaluate(() => { const c = document.querySelector('.home .card'); if (c) window.Sozo.Nav.focus(c); });
  await sleep(300);
  const beforePlayers = await heap(page, client);
  const beforePlayersDom = await domCount(page);
  for (let i = 0; i < 5; i++) {
    await page.evaluate(() => { const c = document.querySelector('.home .card'); if (c) window.Sozo.Nav.focus(c); });
    await playerCycle(page);
  }
  await sleep(500);
  const afterPlayers = await heap(page, client);
  const afterPlayersDom = await domCount(page);

  t.diagnostic('screens: ' + JSON.stringify({ base, afterScreens, baseDom, afterScreensDom }));
  t.diagnostic('players: ' + JSON.stringify({ beforePlayers, afterPlayers, beforePlayersDom, afterPlayersDom }));

  assert.ok(afterScreens.nodes - base.nodes < 400, 'nodes after screens ' + base.nodes + ' -> ' + afterScreens.nodes);
  assert.ok(afterScreens.listeners - base.listeners < 30, 'listeners after screens ' + base.listeners + ' -> ' + afterScreens.listeners);
  assert.ok(afterScreens.used - base.used < 1.5e6, 'heap after screens ' + base.used + ' -> ' + afterScreens.used);
  assert.ok(afterPlayers.nodes - beforePlayers.nodes < 400, 'nodes after players ' + beforePlayers.nodes + ' -> ' + afterPlayers.nodes);
  assert.ok(afterPlayers.listeners - beforePlayers.listeners < 30, 'listeners after players ' + beforePlayers.listeners + ' -> ' + afterPlayers.listeners);
  assert.ok(afterPlayers.used - beforePlayers.used < 1.5e6, 'heap after players ' + beforePlayers.used + ' -> ' + afterPlayers.used);
  assert.ok(Math.abs(afterPlayersDom - beforePlayersDom) < 60, 'dom ' + beforePlayersDom + ' -> ' + afterPlayersDom);
  assert.deepEqual(log.errors, []);
  await context.close();
});
