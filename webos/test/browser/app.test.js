'use strict';
// Runs the real page in current Chromium with a mocked API. This is not Chromium 53 and not a TV.
const test = require('node:test');
const assert = require('node:assert');
const { openApp, closeBrowser, focusText, press, pressCode, back, waitFocus, shot, range, card, pagedSeries, savedEpisode, sleep, keyEvent, cursorState } = require('./harness.js');

test.after(closeBrowser);

const onHome = (page) => waitFocus(page, /Title 5000/);
const count = (page, sel) => page.evaluate((s) => document.querySelectorAll(s).length, sel);
const hasText = (page, text) => page.evaluate((t) => document.getElementById('app').textContent.includes(t), text);
const noErrors = (log) => assert.deepEqual(log.errors, []);

test('boots to Home on the default source, with focus on the hero', async () => {
  const { page, log, context } = await openApp();
  await onHome(page);
  assert.equal(await page.evaluate(() => window.Sozo.App.provider.id), 'uzbeklar');
  assert.ok(await count(page, '.hero') >= 1);
  assert.ok(await count(page, '.row') >= 8);
  await shot(page, '01-home');
  noErrors(log);
  await context.close();
});

test('a hero without a banner shows its poster to the side, not stretched', async () => {
  const { page, context } = await openApp({
    routes: { home: () => ({ body: { provider: 'uzbeklar', banner: range(5000, 2), sections: [] } }) }
  });
  await onHome(page);
  await page.waitForSelector('.hero-poster');
  assert.equal(await count(page, '.hero-img'), 0);
  await context.close();
});

test('only GETs, only safe headers, never the adult opt-in, and no adult cards', async () => {
  const { page, log, context } = await openApp();
  await onHome(page);
  assert.ok(log.api.length >= 2);
  for (const r of log.api) {
    assert.equal(r.method, 'GET');
    assert.equal(r.headers['x-sozo-adult'], undefined);
    assert.equal(r.headers.authorization, undefined);
    assert.equal(/nsfw/i.test(r.url), false);
  }
  assert.equal(await hasText(page, 'XXX'), false);
  assert.equal(await hasText(page, 'Adult'), false);
  await context.close();
});

test('no input element exists anywhere, so the TV keyboard cannot open', async () => {
  const { page, context } = await openApp();
  await onHome(page);
  await page.keyboard.press('ArrowUp');
  await page.keyboard.press('ArrowRight');
  await page.keyboard.press('Enter');
  await page.waitForSelector('.key');
  assert.equal(await count(page, 'input,textarea,select,[contenteditable]'), 0);
  await context.close();
});

test('arrows move focus along a row and between rows, and rows remember their place', async () => {
  const { page, context } = await openApp();
  await onHome(page);
  await press(page, 'ArrowDown');
  await waitFocus(page, /Title 0/);
  await press(page, 'ArrowRight', 3);
  await waitFocus(page, /Title 3/);
  await press(page, 'ArrowDown');
  await waitFocus(page, /Title 100/);
  await press(page, 'ArrowUp');
  await waitFocus(page, /Title 3/);
  await press(page, 'ArrowUp');
  await waitFocus(page, /Title 5000/);
  await press(page, 'ArrowUp');
  await waitFocus(page, /Home/);
  await context.close();
});

test('rows scroll with transform, and the DOM stays bounded on a long walk', async () => {
  const { page, log, context } = await openApp();
  await onHome(page);
  await press(page, 'ArrowDown');
  await press(page, 'ArrowRight', 12);
  const t = await page.evaluate(() => {
    const track = window.Sozo.Nav.current().parentNode;
    return track.style.transform;
  });
  assert.match(t, /translate3d\(-\d+px/);
  assert.equal(await page.evaluate(() => document.scrollingElement.scrollTop + document.getElementById('app').scrollTop), 0);
  for (let i = 0; i < 7; i++) await press(page, 'ArrowDown');
  assert.ok(await count(page, '.card') < 120, 'too many cards in the DOM: ' + await count(page, '.card'));
  await page.waitForTimeout(300);
  const r = await page.evaluate(() => {
    const c = window.Sozo.Nav.current().getBoundingClientRect();
    return { top: c.top, bottom: c.bottom };
  });
  assert.ok(r.top >= 124 && r.bottom <= 1080, 'focused card clears the top bar and the screen edge: ' + JSON.stringify(r));
  noErrors(log);
  await context.close();
});

test('fast key repeat never loses focus', async () => {
  const { page, log, context } = await openApp();
  await onHome(page);
  const seen = await page.evaluate(async () => {
    const codes = [40, 39, 39, 39, 40, 40, 37, 38, 39, 39, 40, 40, 40, 40, 39, 38, 38, 38, 38, 38, 38, 37, 37, 37, 37, 40, 40, 40];
    let lost = 0;
    for (let round = 0; round < 12; round++) {
      for (const c of codes) {
        const e = new KeyboardEvent('keydown', { bubbles: true, cancelable: true });
        Object.defineProperty(e, 'keyCode', { get: () => c });
        document.dispatchEvent(e);
        const cur = window.Sozo.Nav.current();
        if (!cur || !document.documentElement.contains(cur) || !cur.classList.contains('is-focus')) lost++;
        await new Promise((r) => setTimeout(r, 12));
      }
    }
    return { lost, focused: document.querySelectorAll('.is-focus').length };
  });
  assert.equal(seen.lost, 0);
  assert.equal(seen.focused, 1, 'exactly one focused element');
  noErrors(log);
  await context.close();
});

test('End of a row stops, it does not jump to another row', async () => {
  const { page, context } = await openApp();
  await onHome(page);
  await press(page, 'ArrowLeft');
  await waitFocus(page, /Title 5000/);
  await press(page, 'ArrowRight', 3);
  await waitFocus(page, /Title 5003/);
  await press(page, 'ArrowRight');
  await waitFocus(page, /Title 5003/);
  await context.close();
});

test('Back on Home asks to exit; Stay keeps focus and Exit calls the platform', async () => {
  const { page, context } = await openApp({
    init: () => { window.webOS = { platformBack: () => { window.__exited = true; } }; }
  });
  await onHome(page);
  await back(page);
  assert.equal(await count(page, '.dialog'), 1);
  await waitFocus(page, /Stay/);
  await shot(page, '02-exit');
  await press(page, 'Enter');
  assert.equal(await count(page, '.dialog'), 0);
  await waitFocus(page, /Title 5000/);
  assert.equal(await page.evaluate(() => !!window.__exited), false);
  await back(page);
  await back(page);
  assert.equal(await count(page, '.dialog'), 0, 'Back closes the dialog');
  await back(page);
  await press(page, 'ArrowRight');
  await waitFocus(page, /Exit/);
  await press(page, 'Enter');
  assert.equal(await page.evaluate(() => window.__exited), true);
  await context.close();
});

test('a dialog traps focus and the pointer', async () => {
  const { page, context } = await openApp();
  await onHome(page);
  await back(page);
  await press(page, 'ArrowDown');
  await press(page, 'ArrowUp');
  await waitFocus(page, /Stay/);
  await page.mouse.move(200, 900);
  await page.mouse.click(200, 900);
  assert.equal(await count(page, '.dialog'), 1);
  await context.close();
});

test('detail shows the facts and Back returns to the same card', async () => {
  const { page, log, context } = await openApp();
  await onHome(page);
  await press(page, 'ArrowDown');
  await press(page, 'ArrowRight', 2);
  await waitFocus(page, /Title 2/);
  await press(page, 'Enter');
  await waitFocus(page, /Play/);
  for (const text of ['Detail of 2-t.html', 'Long description', 'A Director', 'Actor One', 'Drama, Comedy', '2024', '102 min']) {
    assert.ok(await hasText(page, text), text);
  }
  assert.equal(await hasText(page, 'null'), false);
  assert.ok(await count(page, '.fc') > 3);
  await shot(page, '03-detail-film');
  await back(page);
  await waitFocus(page, /Title 2/);
  noErrors(log);
  await context.close();
});

test('series: seasons switch episodes, and Play reaches the player with a clear request', async () => {
  const { page, log, context } = await openApp({
    init: () => {
      window.__captured = [];
      document.addEventListener('DOMContentLoaded', () => {
        window.Sozo.Player.play = (r) => { window.__captured.push(r); };
      });
    }
  });
  await onHome(page);
  await press(page, 'Enter');
  await waitFocus(page, /Play episode 1/);
  await shot(page, '04-detail-series');
  await press(page, 'ArrowDown');
  await waitFocus(page, /Season 1/);
  await press(page, 'ArrowRight');
  await waitFocus(page, /Season 2/);
  await press(page, 'Enter');
  await press(page, 'ArrowDown');
  await waitFocus(page, /Episode 1/);
  assert.equal(await page.evaluate(() => window.Sozo.Nav.current().parentNode.childNodes.length), 4, 'season 2 has 4 episodes');
  await press(page, 'ArrowRight');
  await press(page, 'Enter');
  const req = await page.evaluate(() => window.__captured[0]);
  assert.equal(req.provider, 'uzbeklar');
  assert.equal(req.episode.mediaRef, 'uzbeklar:s2e2');
  assert.equal(req.envelope, null);
  assert.equal(req.isSerial, true);
  await waitFocus(page, /Episode 2/);
  noErrors(log);
  await context.close();
});

test('a film passes its envelope to the player and skips /media', async () => {
  const { page, log, context } = await openApp({
    init: () => {
      window.__captured = [];
      document.addEventListener('DOMContentLoaded', () => {
        window.Sozo.Player.play = (r) => { window.__captured.push(r); };
      });
    }
  });
  await onHome(page);
  await press(page, 'ArrowDown');
  await press(page, 'Enter');
  await waitFocus(page, /Play/);
  await press(page, 'Enter');
  const req = await page.evaluate(() => window.__captured[0]);
  assert.equal(req.isSerial, false);
  assert.equal(req.envelope.videoSources[0].videoUrl, 'https://cdn.test/a.mp4');
  assert.equal(req.episode, null);
  assert.equal(log.api.some((r) => r.url.includes('/media')), false);
  await context.close();
});

test('an adult detail page shows Not available and offers no Play', async () => {
  const { page, context } = await openApp({
    routes: { home: () => ({ body: { provider: 'uzbeklar', banner: [], sections: [{ key: 'a', label: 'A', items: [card(8888, { contentUrl: 'https://uzbeklar.biz/8888-t.html' })] }] } }) }
  });
  await waitFocus(page, /Title 8888/);
  await press(page, 'Enter');
  await waitFocus(page, /Back/);
  assert.ok(await hasText(page, 'Not available'));
  assert.equal(await hasText(page, 'Secret'), false);
  assert.equal(await hasText(page, 'Play'), false);
  await press(page, 'Enter');
  await waitFocus(page, /Title 8888/);
  await context.close();
});

test('My List: add from detail, shows on the tab, and survives reload', async () => {
  const { page, context } = await openApp();
  await onHome(page);
  await press(page, 'ArrowDown');
  await press(page, 'Enter');
  await waitFocus(page, /Play/);
  await press(page, 'ArrowRight');
  await waitFocus(page, /Add to My List/);
  await press(page, 'Enter');
  await waitFocus(page, /In My List/);
  await back(page);
  await page.waitForTimeout(100);
  await press(page, 'ArrowUp', 2);
  await waitFocus(page, /Home/);
  await press(page, 'ArrowRight', 3);
  await waitFocus(page, /My List/);
  await press(page, 'Enter');
  await waitFocus(page, /Detail of 0-t.html/);
  await shot(page, '05-mylist');
  await page.reload();
  await onHome(page);
  assert.equal(await page.evaluate(() => window.Sozo.App.store.myList().length), 1);
  await context.close();
});

test('My List empty state does not trap focus', async () => {
  const { page, context } = await openApp();
  await onHome(page);
  await press(page, 'ArrowUp');
  await press(page, 'ArrowRight', 3);
  await press(page, 'Enter');
  await page.waitForSelector('.mylist');
  await waitFocus(page, /My List/);
  assert.ok(await hasText(page, 'Your list is empty'));
  await press(page, 'ArrowLeft');
  await waitFocus(page, /Categories/);
  await back(page);
  await onHome(page);
  await context.close();
});

test('Continue Watching appears from saved progress and resumes', async () => {
  const { page, context } = await openApp({
    init: () => {
      localStorage.setItem('sozo.v1.continue', JSON.stringify([{ provider: 'uzbeklar', url: 'https://uzbeklar.biz/77-t.html', title: 'Half watched', thumbnail: '', year: '2020', isSerial: false, position: 600, duration: 1200 }]));
    }
  });
  await onHome(page);
  await press(page, 'ArrowDown');
  await waitFocus(page, /Half watched/);
  assert.equal(await count(page, '.card-progress'), 1);
  await press(page, 'Enter');
  await waitFocus(page, /Resume from 10:00/);
  await context.close();
});

test('corrupt localStorage does not stop the app', async () => {
  const { page, log, context } = await openApp({
    init: () => {
      localStorage.setItem('sozo.v1.continue', '{oops');
      localStorage.setItem('sozo.v1.mylist', '[1,2');
      localStorage.setItem('sozo.v1.settings', 'nope');
    }
  });
  await onHome(page);
  noErrors(log);
  await context.close();
});

test('blocked localStorage does not stop the app', async () => {
  const { page, log, context } = await openApp({
    init: () => { Object.defineProperty(window, 'localStorage', { get() { throw new Error('denied'); } }); }
  });
  await onHome(page);
  noErrors(log);
  await context.close();
});

test('source picker lists only allowed sources, marks health, remembers the choice', async () => {
  const { page, context } = await openApp();
  await onHome(page);
  await press(page, 'ArrowUp');
  await page.evaluate(() => window.Sozo.Nav.focus(window.Sozo.App.topbar.el.querySelector('.source-chip')));
  await press(page, 'Enter');
  await page.waitForSelector('.source-card');
  await waitFocus(page, /Uzbeklar/);
  const names = await page.evaluate(() => Array.from(document.querySelectorAll('.source-card .source-name')).map((n) => n.textContent));
  assert.equal(names.length, 9);
  assert.equal(names.some((n) => /thenkiri|Peertube|Internet Archive|nyaa/i.test(n)), false);
  assert.ok(/AnimeVost/.test(names[names.length - 1]), 'a down source sorts last');
  assert.ok(await hasText(page, 'May be unavailable'));
  assert.ok(await hasText(page, 'Status unknown'));
  assert.ok(await hasText(page, 'Working'));
  await shot(page, '06-sources');
  await press(page, 'ArrowRight');
  await waitFocus(page, /IDUB/);
  await press(page, 'Enter');
  await page.waitForFunction(() => window.Sozo.App.provider.id === 'idub');
  await page.reload();
  await page.waitForFunction(() => window.Sozo.App && window.Sozo.App.provider && window.Sozo.App.provider.id === 'idub');
  await context.close();
});

test('a provider the API no longer offers falls back to the default', async () => {
  const { page, context } = await openApp({
    init: () => { localStorage.setItem('sozo.v1.settings', JSON.stringify({ provider: 'thenkiri' })); }
  });
  await onHome(page);
  assert.equal(await page.evaluate(() => window.Sozo.App.provider.id), 'uzbeklar');
  await context.close();
});

test('content arriving under the exit dialog waits, and takes focus when it closes', async () => {
  const { page, context } = await openApp({
    routes: { home: async () => { await sleep(1200); return { body: { provider: 'uzbeklar', banner: [], sections: [{ key: 'a', label: 'Row', items: range(1, 5) }] } }; } }
  });
  await page.waitForSelector('.tab, .topbar');
  await back(page);
  await waitFocus(page, /Stay/);
  await page.waitForSelector('.row .card', { timeout: 5000 });
  await sleep(200);
  assert.equal(await focusText(page), 'Stay');
  assert.equal(await count(page, '.dialog .is-focus'), 1);
  await shot(page, '02b-exit-over-late-home');
  await press(page, 'Enter');
  assert.equal(await count(page, '.dialog'), 0);
  await waitFocus(page, /Title 1/);
  await context.close();
});

test('Back during boot: the dialog keeps focus after providers and Home arrive', async () => {
  const { page, context } = await openApp({
    routes: { providers: async () => { await sleep(1000); return { body: require('./harness.js').fixture('providers') }; } }
  });
  await back(page);
  await waitFocus(page, /Stay/);
  await page.waitForSelector('.row .card', { timeout: 6000 });
  await sleep(200);
  assert.equal(await count(page, '.dialog .is-focus'), 1);
  assert.equal(await focusText(page), 'Stay');
  await press(page, 'ArrowRight');
  await waitFocus(page, /Exit/);
  await context.close();
});

const openSeries = async (page) => {
  await waitFocus(page, /Title 100|Saved series/);
  await press(page, 'Enter');
};
const screenEpisodes = (page) => page.evaluate(() => window.Sozo.App.screen.capture().episodes.length);

test('Play waits for a saved episode on a later page, then resumes it, and the place survives', async () => {
  const routes = pagedSeries(250, 100, async (p) => { if (p > 1) await sleep(900); });
  const { page, log, context } = await openApp({
    routes, media: () => 'clip',
    init: new Function("localStorage.setItem('sozo.v1.continue', " + JSON.stringify(savedEpisode(250, 20)) + ')')
  });
  await openSeries(page);
  await waitFocus(page, /Loading/);
  await shot(page, '05b-play-loading');
  await press(page, 'Enter');
  assert.equal(log.api.some((r) => r.url.includes('/media')), false, 'no fallback to episode 1');
  await page.waitForFunction(() => window.Sozo.Player.isOpen(), null, { timeout: 8000 });
  await page.waitForFunction(() => { const v = document.querySelector('video'); return v && v.currentSrc.includes('ref250.mp4'); }, null, { timeout: 8000 });
  await page.waitForFunction(() => document.querySelector('video').currentTime >= 19, null, { timeout: 8000 });
  await back(page);
  const e = await page.evaluate(() => window.Sozo.App.store.continueList()[0]);
  assert.equal(e.episode.mediaRef, 'ref250');
  await waitFocus(page, /Resume episode 250/);
  noErrors(log);
  await context.close();
});

test('a failed page with the saved episode on it never starts episode 1', async () => {
  let down = true;
  const routes = pagedSeries(250, 100, async (p) => (p === 2 && down ? { status: 404, body: { message: 'x' } } : null));
  const { page, log, context } = await openApp({
    routes, media: () => 'clip',
    init: new Function("localStorage.setItem('sozo.v1.continue', " + JSON.stringify(savedEpisode(150, 20)) + ')')
  });
  await openSeries(page);
  await page.waitForSelector('.detail-pages .btn');
  await waitFocus(page, /Try again/);
  await shot(page, '05c-play-retry');
  await press(page, 'Enter');
  await page.waitForSelector('.detail-pages .btn');
  assert.equal(await page.evaluate(() => window.Sozo.Player.isOpen()), false);
  assert.equal(log.api.some((r) => r.url.includes('/media')), false);
  down = false;
  await waitFocus(page, /Try again/);
  await press(page, 'Enter');
  await page.waitForFunction(() => window.Sozo.Player.isOpen(), null, { timeout: 8000 });
  await page.waitForFunction(() => { const v = document.querySelector('video'); return v && v.currentSrc.includes('ref150.mp4'); }, null, { timeout: 8000 });
  await context.close();
});

test('a finished episode at the end of a loaded page waits for the next page', async () => {
  const routes = pagedSeries(150, 100, async (p) => { if (p > 1) await sleep(500); });
  const { page, context } = await openApp({
    routes, init: new Function("localStorage.setItem('sozo.v1.continue', " + JSON.stringify(savedEpisode(100, 0, true)) + ')')
  });
  await openSeries(page);
  await waitFocus(page, /Loading/);
  await waitFocus(page, /Resume episode 101/);
  await context.close();
});

test('with nothing saved, Play is ready at once while later pages load', async () => {
  const routes = pagedSeries(250, 100, async (p) => { if (p > 1) await sleep(1500); });
  const { page, context } = await openApp({ routes });
  await openSeries(page);
  await waitFocus(page, /Play episode 1/);
  await context.close();
});

test('leaving a series mid-load picks the remaining pages up on return', async () => {
  const seen = [];
  const routes = pagedSeries(250, 100, async (p) => { seen.push(p); if (p === 2) await sleep(700); });
  const { page, log, context } = await openApp({ routes });
  await openSeries(page);
  await waitFocus(page, /Play episode 1/);
  await page.evaluate(() => window.Sozo.App.openDetail({ provider: 'uzbeklar', title: 'Other', contentUrl: 'https://uzbeklar.biz/200-t.html', slug: 't200' }, 'uzbeklar'));
  await waitFocus(page, /Play/);
  await back(page);
  await waitFocus(page, /Play episode 1/);
  await page.waitForFunction(() => window.Sozo.App.screen.capture().episodes.length === 250, null, { timeout: 6000 });
  assert.ok(seen.includes(3));
  noErrors(log);
  await context.close();
});

test('a later page that fails shows Retry after the list, and Retry carries on from there', async () => {
  let down = true;
  const seen = [];
  const routes = pagedSeries(250, 100, async (p) => {
    seen.push(p);
    return p === 2 && down ? { status: 404, body: { message: 'x' } } : null;
  });
  const { page, log, context } = await openApp({ routes });
  await openSeries(page);
  await page.waitForSelector('.detail-pages .btn');
  assert.equal(await hasText(page, 'Could not load the episodes'), true);
  assert.equal(await screenEpisodes(page), 100);
  assert.equal(seen.includes(3), false);
  await waitFocus(page, /Play episode 1/);
  await press(page, 'ArrowDown');
  await waitFocus(page, /Episode 1/);
  await press(page, 'ArrowDown');
  await waitFocus(page, /Try again/);
  await shot(page, '05d-pages-retry');
  down = false;
  await press(page, 'Enter');
  await waitFocus(page, /Episode 1/);
  await page.waitForFunction(() => window.Sozo.App.screen.capture().episodes.length === 250, null, { timeout: 6000 });
  assert.equal(await count(page, '.detail-pages .btn'), 0);
  noErrors(log);
  await context.close();
});

test('a later page bringing a new season keeps focus where the viewer is', async () => {
  const ep = (i, season) => ({ episode: i, label: 'Episode ' + i, mediaRef: 'ref' + i, season });
  const routes = pagedSeries(1, 1, async (p) => {
    if (p === 2) await sleep(1200);
    const list = [];
    for (let i = 1; i <= 20; i++) list.push(ep(i + (p - 1) * 20, p));
    return { body: { provider: 'uzbeklar', isSerial: true, episodes: list, page: p, totalPages: 2 } };
  });
  const { page, context } = await openApp({ routes });
  await openSeries(page);
  await waitFocus(page, /Play episode 1/);
  await press(page, 'ArrowDown');
  await waitFocus(page, /Episode 1/);
  await press(page, 'ArrowRight', 3);
  await waitFocus(page, /Episode 4/);
  await page.waitForSelector('.season-chips');
  await waitFocus(page, /Episode 4/);
  assert.equal(await count(page, '.season-chips .is-selected'), 1);
  assert.match(await page.textContent('.season-chips .is-selected'), /Season 1/);
  await context.close();
});

test('coming back to Search: nothing found stays nothing found, and an unfinished search runs again', async () => {
  let hang = false;
  const { page, log, context } = await openApp({
    routes: { search: async (q) => {
      if (hang) await sleep(30000);
      return { body: { provider: 'uzbeklar', query: q.get('q'), items: [], page: 1, totalPages: 1 } };
    } }
  });
  await onHome(page);
  await openSearch(page);
  await press(page, 'Enter');
  await press(page, 'ArrowRight');
  await press(page, 'Enter');
  await page.waitForFunction(() => /Nothing found/.test(document.querySelector('.results').textContent), null, { timeout: 5000 });
  const leave = async () => {
    await press(page, 'ArrowUp');
    await waitFocus(page, /Search/);
    await press(page, 'ArrowLeft');
    await press(page, 'Enter');
    await onHome(page);
    await openSearch(page);
  };
  await leave();
  assert.equal(await page.textContent('.query'), 'ab');
  assert.match(await page.textContent('.results'), /Nothing found/);
  const before = log.api.filter((r) => r.url.includes('/search')).length;
  assert.equal(before, 1, 'no repeat request');
  hang = true;
  await page.evaluate(() => window.Sozo.Nav.focus(document.querySelectorAll('.key')[2]));
  await press(page, 'Enter');
  await page.waitForFunction(() => /\babc?\b/.test(document.querySelector('.query').textContent));
  await sleep(800);
  hang = false;
  await leave();
  await page.waitForFunction(() => /Nothing found/.test(document.querySelector('.results').textContent), null, { timeout: 5000 });
  assert.equal(await hasText(page, 'Type at least'), false);
  await context.close();
});

test('the current source leaving the live list drops every screen that holds its cards', async () => {
  let gone = false;
  const fixtureProviders = require('./harness.js').fixture('providers');
  const { page, log, context } = await openApp({
    routes: {
      providers: () => {
        if (!gone) return { body: fixtureProviders };
        return { body: Object.assign({}, fixtureProviders, { items: fixtureProviders.items.filter((p) => p.id !== 'uzbeklar') }) };
      }
    }
  });
  await onHome(page);
  await openSearch(page);
  await press(page, 'Enter');
  await press(page, 'ArrowRight');
  await press(page, 'Enter');
  await page.waitForSelector('.results .card');
  gone = true;
  await page.evaluate(() => window.Sozo.App.push('sources', {}));
  await page.waitForSelector('.source-card');
  const now = await page.evaluate(() => window.Sozo.App.provider.id);
  assert.notEqual(now, 'uzbeklar');
  assert.deepEqual(await page.evaluate(() => window.Sozo.App.router.names()), ['home', 'sources'], 'the viewer stays on the list');
  await back(page);
  await page.waitForFunction(() => window.Sozo.App.router.names().join() === 'home');
  await page.evaluate(() => window.Sozo.App.tab('search'));
  await page.waitForSelector('.key, .search');
  assert.equal(await count(page, '.results .card'), 0, 'no card of the old source');
  noErrors(log);
  await context.close();
});

const openSearch = async (page) => {
  await press(page, 'ArrowUp');
  await press(page, 'ArrowRight');
  await waitFocus(page, /Search/);
  await press(page, 'Enter');
  await page.waitForSelector('.key');
};

test('search types by remote, debounces, filters, and returns focus', async () => {
  const { page, log, context } = await openApp();
  await onHome(page);
  await openSearch(page);
  await waitFocus(page, /^a$/);
  await press(page, 'Enter');
  assert.equal(await page.textContent('.query'), 'a');
  await press(page, 'ArrowRight');
  await waitFocus(page, /^b$/);
  await press(page, 'Enter');
  await press(page, 'Enter');
  assert.equal(log.api.filter((r) => r.url.includes('/search')).length, 0, 'still debounced');
  await page.waitForSelector('.results .card');
  assert.equal(log.api.filter((r) => r.url.includes('/search')).length, 1);
  assert.match(log.api.find((r) => r.url.includes('/search')).url, /q=abb/);
  assert.equal(await hasText(page, 'Hidden'), false);
  await shot(page, '07-search');
  for (let i = 0; i < 7; i++) await press(page, 'ArrowRight');
  await waitFocus(page, /Title 3000/);
  await press(page, 'ArrowLeft');
  assert.ok((await focusText(page)).length <= 2, 'back on the keyboard');
  noErrors(log);
  await context.close();
});

test('keyboard switches to Cyrillic and back, backspace edits the query', async () => {
  const { page, context } = await openApp();
  await onHome(page);
  await openSearch(page);
  for (let i = 0; i < 6; i++) await press(page, 'ArrowDown');
  await waitFocus(page, /АБВ/);
  await press(page, 'Enter');
  await waitFocus(page, /ABC/);
  assert.ok(await hasText(page, 'ж'));
  await shot(page, '08-search-cyrillic');
  await press(page, 'ArrowUp', 7);
  await page.evaluate(() => { const k = window.Sozo.Nav.current(); return k; });
  await press(page, 'ArrowUp');
  const lastKeys = await page.evaluate(() => {
    const keys = document.querySelectorAll('.key');
    window.Sozo.Nav.focus(keys[0]);
    return keys.length;
  });
  assert.ok(lastKeys > 40);
  await press(page, 'Enter');
  assert.equal(await page.textContent('.query'), 'а');
  await page.evaluate(() => {
    const keys = document.querySelectorAll('.key');
    window.Sozo.Nav.focus(keys[keys.length - 1]);
  });
  await press(page, 'Enter');
  assert.equal(await page.textContent('.query'), 'Type to search');
  await context.close();
});

test('a source without search says so and offers another source', async () => {
  const { page, context } = await openApp({
    init: () => { localStorage.setItem('sozo.v1.settings', JSON.stringify({ provider: 'vidapi' })); }
  });
  await page.waitForFunction(() => window.Sozo.App && window.Sozo.App.provider);
  await page.waitForSelector('.row');
  await page.waitForTimeout(100);
  await press(page, 'ArrowUp');
  await waitFocus(page, /Home/);
  await press(page, 'ArrowRight');
  await press(page, 'Enter');
  await page.waitForSelector('.status');
  assert.ok(await hasText(page, 'Search is not available'));
  await press(page, 'ArrowDown');
  await waitFocus(page, /Change source/);
  assert.equal(await count(page, '.key'), 0);
  await context.close();
});

test('vidapi shows no related row and no view-all card', async () => {
  const { page, context } = await openApp({
    init: () => { localStorage.setItem('sozo.v1.settings', JSON.stringify({ provider: 'vidapi' })); }
  });
  await page.waitForSelector('.row');
  assert.equal(await count(page, '.card-more'), 0);
  await context.close();
});

test('view all pages through the grid and loads more near the end', async () => {
  const { page, log, context } = await openApp();
  await onHome(page);
  await press(page, 'ArrowDown');
  await page.evaluate(() => {
    const col = window.Sozo.Nav.groupOf(window.Sozo.Nav.current());
    col.index = col.items.length - 1;
    col._window();
    window.Sozo.Nav.focus(col.nodes[col.index]);
  });
  await waitFocus(page, /View all/);
  await press(page, 'Enter');
  await waitFocus(page, /Title 7000/);
  assert.ok(await hasText(page, 'Row 0'));
  await shot(page, '09-listing');
  for (let i = 0; i < 3; i++) await press(page, 'ArrowDown');
  await page.waitForFunction(() => document.querySelectorAll('.col-grid').length === 1 && window.Sozo.Nav.groupOf(window.Sozo.Nav.current()).items.length === 48);
  assert.ok(log.api.some((r) => /category\/cat0\?provider=uzbeklar&page=2/.test(r.url)));
  noErrors(log);
  await context.close();
});

test('a failed next page keeps the grid and offers Retry that works', async () => {
  let fail = true;
  const { page, context } = await openApp({
    routes: {
      latest: (q) => {
        const p = Number(q.get('page'));
        if (p === 2 && fail) return { status: 404, body: { message: 'x' } };
        return { body: { provider: 'uzbeklar', items: range(6000 + (p - 1) * 24, 24), page: p, totalPages: 2 } };
      }
    }
  });
  await onHome(page);
  await press(page, 'ArrowUp');
  await press(page, 'ArrowRight', 2);
  await press(page, 'Enter');
  await waitFocus(page, /Categories|Latest/);
  await page.waitForSelector('.chip');
  await page.evaluate(() => {
    const chip = document.querySelector('.chip');
    window.Sozo.Nav.focus(chip);
  });
  await shot(page, '10-categories');
  await press(page, 'Enter');
  await waitFocus(page, /Title 6000/);
  for (let i = 0; i < 3; i++) await press(page, 'ArrowDown');
  await page.waitForSelector('.listing-footer .btn');
  assert.equal(await count(page, '.col-grid .card') > 0, true);
  fail = false;
  await page.evaluate(() => window.Sozo.Nav.focus(document.querySelector('.listing-footer .btn')));
  await press(page, 'Enter');
  await page.waitForFunction(() => window.Sozo.Nav.groupOf(window.Sozo.Nav.current()).items.length === 48);
  assert.equal(await count(page, '.listing-footer .btn'), 0);
  await context.close();
});

test('Home failure shows a friendly screen, never the backend text, and Retry works', async () => {
  let fail = true;
  const { page, context } = await openApp({
    routes: {
      home: () => (fail
        ? { status: 503, body: { message: 'Video topilmadi', code: 'SOURCE_REGION_LOCKED' } }
        : { body: { provider: 'uzbeklar', banner: range(5000, 2), sections: [] } })
    }
  });
  await waitFocus(page, /Try again/);
  assert.ok(await hasText(page, 'Not available in this region'));
  assert.equal(await hasText(page, 'topilmadi'), false);
  await shot(page, '11-error');
  fail = false;
  await press(page, 'Enter');
  await waitFocus(page, /Title 5000/);
  await context.close();
});

test('an error screen still lets the viewer leave', async () => {
  const { page, context } = await openApp({
    routes: { home: () => ({ status: 409, body: { code: 'SOURCE_RUNS_ON_DEVICE' } }) }
  });
  await waitFocus(page, /Try again/);
  assert.ok(await hasText(page, 'Not available on this TV'));
  await press(page, 'ArrowRight');
  await waitFocus(page, /Change source/);
  await press(page, 'ArrowUp');
  await waitFocus(page, /Home/);
  await context.close();
});

test('503 without a readable Retry-After retries once on its own', async () => {
  let calls = 0;
  const { page, context } = await openApp({
    routes: {
      home: () => {
        calls++;
        if (calls === 1) return { status: 503, headers: { 'retry-after': '3' }, body: { message: 'busy' } };
        return { body: { provider: 'uzbeklar', banner: range(5000, 2), sections: [] } };
      }
    }
  });
  await waitFocus(page, /Title 5000/);
  assert.equal(calls, 2);
  await context.close();
});

test('boot failure shows Retry, and nothing is shown from the shipped list alone', async () => {
  let fail = true;
  const { fixture } = require('./harness.js');
  const { page, context } = await openApp({
    routes: { providers: () => (fail ? { status: 400, body: { message: 'no' } } : { body: fixture('providers') }) }
  });
  await waitFocus(page, /Try again/);
  assert.equal(await page.evaluate(() => window.Sozo.App.providers.length), 0);
  fail = false;
  await press(page, 'Enter');
  await waitFocus(page, /Title 5000/);
  await context.close();
});

test('going offline shows a banner and a working retry', async () => {
  let down = false;
  const { page, context } = await openApp({
    routes: { detail: () => (down ? { abort: true } : { body: { provider: 'uzbeklar', title: 'Fine', contentUrl: 'x', isSerial: false } }) }
  });
  await onHome(page);
  down = true;
  await context.setOffline(true);
  await page.evaluate(() => window.dispatchEvent(new Event('offline')));
  assert.equal(await page.evaluate(() => document.getElementById('offline').style.display), '');
  await press(page, 'ArrowDown');
  await press(page, 'Enter');
  await waitFocus(page, /Try again/);
  assert.ok(await hasText(page, 'No internet connection'));
  down = false;
  await context.setOffline(false);
  await page.evaluate(() => window.dispatchEvent(new Event('online')));
  await page.waitForFunction(() => document.getElementById('offline').style.display === 'none');
  await waitFocus(page, /Play|Add to My List/);
  await context.close();
});

test('Magic Remote: hover moves focus, click activates, wheel scrolls', async () => {
  const { page, context } = await openApp();
  await onHome(page);
  const box = await page.evaluate(() => {
    const r = document.querySelector('.row .card').getBoundingClientRect();
    return { x: r.left + r.width / 2, y: r.top + r.height / 2, title: document.querySelector('.row .card').textContent };
  });
  await page.mouse.move(box.x, box.y);
  await page.waitForTimeout(80);
  assert.equal(await focusText(page), box.title);
  assert.equal(await count(page, '.is-focus'), 1);
  await page.mouse.wheel(0, 100);
  await page.waitForTimeout(250);
  assert.notEqual(await focusText(page), box.title);
  await page.mouse.wheel(0, -100);
  await page.waitForTimeout(250);
  assert.equal(await focusText(page), box.title);
  await page.mouse.move(box.x + 1, box.y);
  await page.mouse.down();
  await page.mouse.up();
  await waitFocus(page, /Play|Add to My List/);
  await context.close();
});

test('one press that delivers key and click opens one screen, not two', async () => {
  const { page, context } = await openApp();
  await onHome(page);
  await page.evaluate(() => {
    const el = window.Sozo.Nav.current();
    const e = new KeyboardEvent('keydown', { bubbles: true, cancelable: true });
    Object.defineProperty(e, 'keyCode', { get: () => 13 });
    document.dispatchEvent(e);
    el.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  });
  await page.waitForFunction(() => window.Sozo.App.router.depth() >= 2);
  assert.deepEqual(await page.evaluate(() => window.Sozo.App.router.names()), ['home', 'detail']);
  await context.close();
});

test('a mousemove that did not move, or from a hidden pointer, leaves focus alone', async () => {
  const { page, context } = await openApp();
  await onHome(page);
  const box = await page.evaluate(() => {
    const r = document.querySelector('.row .card').getBoundingClientRect();
    return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
  });
  await page.mouse.move(box.x, box.y);
  await page.waitForTimeout(80);
  const first = await focusText(page);
  await press(page, 'ArrowRight');
  const second = await focusText(page);
  assert.notEqual(second, first);
  const fake = (x, y) => page.evaluate(([cx, cy]) => {
    document.elementFromPoint(cx, cy).dispatchEvent(new MouseEvent('mousemove', { bubbles: true, clientX: cx, clientY: cy }));
  }, [x, y]);
  await fake(box.x, box.y);
  assert.equal(await focusText(page), second, 'same coordinates');
  await cursorState(page, false);
  await fake(box.x + 2, box.y + 2);
  assert.equal(await focusText(page), second, 'hidden pointer');
  await cursorState(page, true);
  await fake(box.x + 4, box.y + 4);
  assert.notEqual(await focusText(page), second, 'a real move still takes focus');
  await context.close();
});

test('pointer showing: a press acts where the pointer is, not on the focus', async () => {
  const { page, context } = await openApp({ routes: pagedSeries(2, 2) });
  await waitFocus(page, /Title 100/);
  await cursorState(page, true);
  await keyEvent(page, 'keydown', 13);
  await page.mouse.click(1000, 1060);
  await keyEvent(page, 'keyup', 13);
  await sleep(700);
  assert.deepEqual(await page.evaluate(() => window.Sozo.App.router.names()), ['home'], 'a click on nothing does nothing');
  await context.close();
});

test('state unknown: a long press acts once on the key, and its late click is dropped', async () => {
  const { page, context } = await openApp();
  await onHome(page);
  await keyEvent(page, 'keydown', 13);
  await page.waitForFunction(() => window.Sozo.App.router.depth() === 2);
  await waitFocus(page, /Play|Add to My List/);
  await sleep(500);
  await keyEvent(page, 'keyup', 13);
  await page.evaluate(() => window.Sozo.Nav.current().click());
  await sleep(200);
  assert.equal(await page.evaluate(() => window.Sozo.Player.isOpen()), false);
  assert.deepEqual(await page.evaluate(() => window.Sozo.App.router.names()), ['home', 'detail']);
  await context.close();
});

test('cursorStateChange hides and shows the pointer state', async () => {
  const { page, context } = await openApp();
  await onHome(page);
  await page.evaluate(() => document.dispatchEvent(new CustomEvent('cursorStateChange', { detail: { visibility: false } })));
  assert.equal(await count(page, '#app.pointer-hidden'), 1);
  await page.evaluate(() => document.dispatchEvent(new CustomEvent('cursorStateChange', { detail: { visibility: true } })));
  assert.equal(await count(page, '#app.pointer-hidden'), 0);
  await context.close();
});

test('broken images leave the titled placeholder, and no error escapes', async () => {
  const { page, log, context } = await openApp({ brokenImages: true });
  await onHome(page);
  await page.waitForTimeout(400);
  assert.equal(await count(page, '.card-frame img'), 0, 'failed images are removed');
  assert.ok(await count(page, '.card-ph-title') > 0);
  await shot(page, '12-broken-images');
  noErrors(log);
  await context.close();
});

test('nothing but the mocked API and image hosts is contacted', async () => {
  const { page, log, context } = await openApp();
  await onHome(page);
  await page.waitForTimeout(300);
  for (const u of log.external) assert.match(u, /^https:\/\/img\.test\//);
  await context.close();
});

test('Settings changes the language at once and keeps it; TV language is the default', async () => {
  const { page, context } = await openApp({ locale: 'uz-UZ' });
  await waitFocus(page, /Title 5000/);
  assert.ok(await hasText(page, 'Bosh sahifa'));
  await press(page, 'ArrowUp');
  await press(page, 'ArrowRight', 4);
  await waitFocus(page, /Sozlamalar/);
  await press(page, 'Enter');
  await press(page, 'ArrowDown');
  await shot(page, '13-settings');
  assert.match(await focusText(page), /Televizor tili|English|O'zbekcha|Русский/);
  await press(page, 'ArrowRight', 3);
  await waitFocus(page, /Русский/);
  await press(page, 'Enter');
  await waitFocus(page, /Русский/);
  assert.ok(await hasText(page, 'Настройки'));
  await page.reload();
  await waitFocus(page, /Title 5000/);
  assert.ok(await hasText(page, 'Главная'));
  await context.close();
});

test('Clear history asks first, then empties it', async () => {
  const { page, context } = await openApp({
    init: () => {
      if (!sessionStorage.getItem('seeded')) {
        sessionStorage.setItem('seeded', '1');
        localStorage.setItem('sozo.v1.continue', JSON.stringify([{ provider: 'uzbeklar', url: 'u', title: 'Half', position: 60, duration: 100 }]));
      }
    }
  });
  await onHome(page);
  await page.evaluate(() => window.Sozo.App.tab('settings'));
  await page.waitForSelector('.settings');
  await page.evaluate(() => {
    const list = document.querySelectorAll('.btn');
    for (let i = 0; i < list.length; i++) if (list[i]._fid === 'clear-history') window.Sozo.Nav.focus(list[i]);
  });
  await press(page, 'Enter');
  await waitFocus(page, /Cancel/);
  await press(page, 'Enter');
  assert.equal(await page.evaluate(() => window.Sozo.App.store.continueList().length), 1);
  await waitFocus(page, /Clear watch history/);
  await press(page, 'Enter');
  await waitFocus(page, /Cancel/);
  await press(page, 'ArrowRight');
  await press(page, 'Enter');
  assert.equal(await page.evaluate(() => window.Sozo.App.store.continueList().length), 0);
  await waitFocus(page, /Done/);
  await context.close();
});

test('tab state is kept when coming back', async () => {
  const { page, context } = await openApp();
  await onHome(page);
  await openSearch(page);
  await press(page, 'Enter');
  await press(page, 'ArrowRight');
  await press(page, 'Enter');
  await page.waitForSelector('.results .card');
  await press(page, 'ArrowUp');
  await waitFocus(page, /Search/);
  await press(page, 'ArrowLeft');
  await press(page, 'Enter');
  await onHome(page);
  await openSearch(page);
  assert.equal(await page.textContent('.query'), 'ab');
  assert.ok(await count(page, '.results .card') > 0);
  await context.close();
});

test('live source renders channels and opens without a poster row', async () => {
  const live = { provider: 'worldlivetv', banner: [{ provider: 'worldlivetv', title: 'Channel A', contentUrl: 'worldlivetv:a', thumbnail: 'https://img.test/a.png' }], sections: [{ key: 'row-0', label: 'News', viewAll: { type: 'category', slug: 'row-0' }, items: [{ provider: 'worldlivetv', title: 'Channel A', contentUrl: 'worldlivetv:a', thumbnail: 'https://img.test/a.png' }] }] };
  const { page, log, context } = await openApp({
    init: () => { localStorage.setItem('sozo.v1.settings', JSON.stringify({ provider: 'worldlivetv' })); },
    routes: {
      home: () => ({ body: live }),
      detail: () => ({ body: { provider: 'worldlivetv', title: 'Channel A', contentUrl: 'worldlivetv:a', isSerial: false } }),
      episodes: () => ({ body: { provider: 'worldlivetv', isSerial: false, episodes: [{ episode: 1, mediaRef: 'm', label: 'Live' }], videoSources: [{ videoUrl: 'https://l.test/a.m3u8', type: 'hls' }] } })
    }
  });
  await waitFocus(page, /Channel A/);
  assert.equal(await count(page, '.card-wide'), 1);
  assert.equal(await count(page, '.card-more'), 0, 'live has no view-all');
  assert.equal(await count(page, '.hero'), 0, 'logos are too small for a hero');
  await shot(page, '14-live');
  await press(page, 'Enter');
  await waitFocus(page, /Watch live/);
  assert.equal(await hasText(page, 'Add to My List'), false);
  noErrors(log);
  await context.close();
});

test('a smaller window scales the 1920 canvas and stays navigable', async () => {
  const { page, context } = await openApp();
  await page.setViewportSize({ width: 1280, height: 720 });
  await onHome(page);
  assert.match(await page.evaluate(() => document.getElementById('app').style.transform), /scale\(0\.66/);
  await press(page, 'ArrowDown');
  await waitFocus(page, /Title 0/);
  await pressCode(page, 39);
  await waitFocus(page, /Title 1/);
  await context.close();
});
