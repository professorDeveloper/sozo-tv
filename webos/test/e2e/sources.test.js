'use strict';
// One title per source against production. A source may fail for reasons outside the app, but the
// viewer must always end up watching or looking at a way forward, never at an endless spinner.
const test = require('node:test');
const assert = require('node:assert');
const L = require('./lib.js');
const { K, key, sleep, travelTo, invariants, shot } = L;

const IDS = (process.env.SOZO_SOURCES || 'uzbeklar,idub,vidapi,anilibria,animevost,supercartoons,worldlivetv,livetvindia,sportstv').split(',');

test('every offered source ends in playback or a clear choice', { skip: process.env.SOZO_LIVE ? false : 'set SOZO_LIVE=1', timeout: 900000 }, async (t) => {
  for (const id of IDS) {
    await t.test(id, { timeout: 240000 }, async (st) => {
      // A fresh browser each time: one long-lived instance started getting connection resets from the API.
      const browser = await L.launch();
      const context = await browser.newContext({ viewport: { width: 1920, height: 1080 }, locale: 'en-US' });
      const page = await context.newPage();
      const log = L.watch(page, context);
      try {
        await page.goto(L.INDEX);
        // A dropped connection shows Try again, so this does what a viewer would do.
        for (let i = 0; i < 5; i++) {
          const up = await page.waitForFunction(() => {
            if (window.Sozo.App.providers.length > 0 && window.Sozo.Nav.current()) return 'up';
            return document.querySelector('#screen .status-error') ? 'error' : false;
          }, null, { timeout: 70000 });
          if (await up.jsonValue() === 'up') break;
          st.diagnostic(id + ': boot failed, pressing Try again');
          await sleep(8000);
          await key(page, K.ok);
        }
        const offered = await page.evaluate((x) => !!window.Sozo.App.findProvider(x), id);
        if (!offered) { st.diagnostic(id + ': not offered right now'); return; }
        await page.evaluate((x) => window.Sozo.App.setProvider(x), id);
        await page.waitForFunction(() => document.querySelector('.home .row .card') || document.querySelector('.home .status:not(.status-loading)'), null, { timeout: 60000 });
        await sleep(500);
        assert.deepEqual(await invariants(page), [], 'home');
        if (!(await page.evaluate(() => !!document.querySelector('.home .row .card')))) {
          st.diagnostic(id + ': home has no titles, showing a message');
          await shot(page, 'src-' + id + '-home');
          return;
        }
        await travelTo(page, () => document.querySelector('.home .row .card'));
        const title = (await L.focusInfo(page)).text.slice(0, 40);
        await key(page, K.ok);
        await page.waitForFunction(() => document.querySelector('.detail-actions .btn-primary') || document.querySelector('.detail .status-error') || /Could not load/.test((document.querySelector('.detail-episodes') || {}).textContent || ''), null, { timeout: 100000 });
        if (!(await page.evaluate(() => !!document.querySelector('.detail-actions .btn-primary')))) {
          st.diagnostic(id + ': detail offers no Play for "' + title + '"');
          await shot(page, 'src-' + id + '-detail');
          return;
        }
        await page.evaluate(() => window.Sozo.Nav.focus(document.querySelector('.detail-actions .btn-primary')));
        await key(page, K.ok);
        const handle = await page.waitForFunction(() => {
          const v = document.querySelector('video');
          if (v && !v.paused && v.currentTime > 0.5 && v.videoWidth > 0) return 'playing';
          return document.querySelector('.pl-panel.is-wide, .pl-pane') ? 'clear failure' : false;
        }, null, { timeout: 180000, polling: 500 });
        const state = await handle.jsonValue();
        const info = await page.evaluate(() => {
          const v = document.querySelector('video');
          return v ? v.videoWidth + 'x' + v.videoHeight + (window.Hls ? ' hls.js' : ' direct') : '';
        });
        st.diagnostic(id + ': ' + state + ' ' + info + ' for "' + title + '"');
        await shot(page, 'src-' + id + '-' + state.replace(' ', '-'));
        assert.deepEqual(await invariants(page), [], 'player');
        assert.deepEqual(log.errors, []);
        assert.deepEqual(log.consoleErrors, []);
        assert.equal(log.requests.some((r) => r.adult), false);
      } finally {
        await browser.close();
        await sleep(1500);
      }
    });
  }
});
