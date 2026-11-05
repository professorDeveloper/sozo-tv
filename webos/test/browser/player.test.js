'use strict';
// Real <video> in current Chromium, playing open-codec clips. This is not Chromium 53 and not a TV:
// nothing here proves H.264, native HLS, or the LG media pipeline.
const test = require('node:test');
const assert = require('node:assert');
const { openApp, closeBrowser, pressCode, press, back, waitFocus, shot, card, pagedSeries, savedEpisode, sleep, setHidden, keyEvent, cursorState } = require('./harness.js');

test.after(closeBrowser);

const KEY = { play: 415, pause: 19, stop: 413, rewind: 412, forward: 417, ok: 13, left: 37, right: 39, up: 38, down: 40 };
const SRC = (name, extra) => Object.assign({ quality: name, videoUrl: 'https://cdn.test/' + name + '.mp4', type: 'mp4' }, extra);

function filmRoutes(envelope) {
  return {
    home: () => ({ body: { provider: 'uzbeklar', banner: [], sections: [{ key: 'a', label: 'Row', items: [card(100)] }] } }),
    detail: (q) => ({ body: { provider: 'uzbeklar', contentUrl: q.get('url'), title: 'Film 100', thumbnail: 'https://img.test/p.jpg', year: 2024, isSerial: false } }),
    episodes: (q) => ({ body: Object.assign({ provider: 'uzbeklar', contentUrl: q.get('url'), isSerial: false, episodes: [{ episode: 1, label: 'Play', mediaRef: 'r' }], type: 'mp4', headers: {} }, envelope) })
  };
}

function seriesRoutes(media, count) {
  const episodes = [];
  for (let i = 1; i <= (count || 3); i++) episodes.push({ episode: i, label: 'Episode ' + i, mediaRef: 'ref' + i, season: 1 });
  return {
    home: () => ({ body: { provider: 'uzbeklar', banner: [], sections: [{ key: 'a', label: 'Row', items: [card(100)] }] } }),
    detail: (q) => ({ body: { provider: 'uzbeklar', contentUrl: q.get('url'), title: 'Series 100', year: 2024, isSerial: true } }),
    episodes: (q) => ({ body: { provider: 'uzbeklar', contentUrl: q.get('url'), isSerial: true, episodes, page: 1, totalPages: 1 } }),
    media
  };
}

async function openTitle(page) {
  await waitFocus(page, /Title 100/);
  await press(page, 'Enter');
  await waitFocus(page, /Play|Resume/);
  await press(page, 'Enter');
}

const video = (page, expr) => page.evaluate(new Function('return (function (v) { return ' + expr + '; })(document.querySelector("video"));'));
const waitPlaying = (page, min) => page.waitForFunction((m) => {
  const v = document.querySelector('video');
  return v && !v.paused && v.currentTime > m;
}, min || 0.3, { timeout: 12000 });
const visible = (page, sel) => page.evaluate((s) => {
  const e = document.querySelector(s);
  return !!e && e.offsetWidth > 0 && getComputedStyle(e).display !== 'none';
}, sel);
const appText = (page, text) => page.evaluate((t) => document.getElementById('app').textContent.includes(t), text);
const times = (page, t) => page.evaluate((v) => Object.assign(window.Sozo.Playback.TIMES, v), t);
const progress = (page, url) => page.evaluate((u) => window.Sozo.App.store.progress('uzbeklar', u), url);
const URL_100 = card(100).contentUrl;
const noErrors = (log) => assert.deepEqual(log.errors, []);

test('a film plays from its envelope, with no /media call, no crossorigin and no adult header', async () => {
  const { page, log, context } = await openApp({ routes: filmRoutes({ videoSources: [SRC('a')] }), media: () => 'clip' });
  await openTitle(page);
  await waitPlaying(page);
  assert.equal(await video(page, 'v.hasAttribute("crossorigin")'), false);
  assert.equal(await visible(page, '.pl-busy'), false);
  assert.equal(log.api.some((r) => r.url.includes('/media')), false);
  assert.equal(log.api.some((r) => r.headers['x-sozo-adult'] !== undefined), false);
  assert.ok(log.external.includes('https://cdn.test/a.mp4'));
  assert.equal(log.external.some((u) => /hls/.test(u)), false);
  assert.equal(await page.evaluate(() => !!window.Hls), false, 'hls.js stays unloaded for mp4');
  noErrors(log);
  await context.close();
});

test('remote: OK pauses and resumes, up/down show controls, and controls hide on their own', async () => {
  const { page, log, context } = await openApp({ routes: filmRoutes({ videoSources: [SRC('a')] }), media: () => 'clip' });
  await times(page, { hideControls: 1200 });
  await openTitle(page);
  await waitPlaying(page);
  assert.equal(await visible(page, '.pl-bottom'), false);
  await pressCode(page, KEY.up);
  assert.equal(await visible(page, '.pl-bottom'), true);
  assert.equal(await appText(page, 'Film 100'), true);
  await shot(page, 'p1-controls');
  await page.waitForFunction(() => document.querySelector('.pl-bottom').style.display === 'none', null, { timeout: 5000 });
  await pressCode(page, KEY.ok);
  assert.equal(await video(page, 'v.paused'), true);
  assert.equal(await visible(page, '.pl-bottom'), true, 'pausing shows controls');
  await page.waitForTimeout(2500);
  assert.equal(await visible(page, '.pl-bottom'), true, 'controls stay while paused');
  await pressCode(page, KEY.ok);
  assert.equal(await video(page, 'v.paused'), false);
  noErrors(log);
  await context.close();
});

test('seeking: steps accelerate within a burst, commit once, and the bar shows the target first', async () => {
  const { page, log, context } = await openApp({ routes: filmRoutes({ videoSources: [SRC('a')] }), media: () => 'clip' });
  await times(page, { seekCommit: 600, seekBurst: 2000 });
  await openTitle(page);
  await waitPlaying(page);
  const start = await video(page, 'v.currentTime');
  await pressCode(page, KEY.right);
  await pressCode(page, KEY.right);
  await pressCode(page, KEY.right);
  await pressCode(page, KEY.right);
  const before = await video(page, 'v.currentTime');
  assert.ok(before < start + 5, 'not committed while the key is still going');
  assert.equal(await page.evaluate(() => document.querySelector('.pl-time').textContent), '0:50');
  await page.waitForFunction((t) => document.querySelector('video').currentTime >= t, start + 48, { timeout: 4000 });
  await times(page, { seekCommit: 100, seekBurst: 1 });
  await page.waitForTimeout(100);
  await pressCode(page, KEY.left);
  await page.waitForTimeout(300);
  const after = await video(page, 'v.currentTime');
  assert.ok(after > start + 37 && after < start + 44, 'one left is 10 s back, got ' + after);
  noErrors(log);
  await context.close();
});

test('media keys: Pause, Play, Fast-forward, Rewind and Stop all work', async () => {
  const { page, log, context } = await openApp({ routes: filmRoutes({ videoSources: [SRC('a')] }), media: () => 'clip' });
  await times(page, { seekCommit: 50 });
  await openTitle(page);
  await waitPlaying(page);
  await pressCode(page, KEY.pause);
  assert.equal(await video(page, 'v.paused'), true);
  await pressCode(page, KEY.play);
  assert.equal(await video(page, 'v.paused'), false);
  const t = await video(page, 'v.currentTime');
  await pressCode(page, KEY.forward);
  await page.waitForTimeout(200);
  assert.ok(await video(page, 'v.currentTime') > t + 8);
  await pressCode(page, KEY.rewind);
  await page.waitForTimeout(200);
  assert.ok(await video(page, 'v.currentTime') < t + 6);
  await pressCode(page, KEY.stop);
  assert.equal(await page.evaluate(() => window.Sozo.Player.isOpen()), false);
  noErrors(log);
  await context.close();
});

test('Back leaves a film at once, tears everything down, and returns focus to the detail page', async () => {
  const { page, log, context } = await openApp({
    routes: filmRoutes({ videoSources: [SRC('a')] }), media: () => 'clip',
    init: () => {
      const si = window.setInterval.bind(window);
      const ci = window.clearInterval.bind(window);
      window.__live = new Set();
      window.setInterval = (f, t) => { const id = si(f, t); window.__live.add(id); return id; };
      window.clearInterval = (id) => { window.__live.delete(id); ci(id); };
    }
  });
  await waitFocus(page, /Title 100/);
  await press(page, 'Enter');
  await waitFocus(page, /Play/);
  const baseline = await page.evaluate(() => window.__live.size);
  for (let i = 0; i < 4; i++) {
    await press(page, 'Enter');
    await waitPlaying(page);
    await back(page);
    await waitFocus(page, /Play|Resume/);
    assert.equal(await page.evaluate(() => document.querySelectorAll('video,.player,source').length), 0);
    assert.equal(await page.evaluate(() => window.Sozo.Player.isOpen()), false);
    assert.equal(await page.evaluate(() => window.__live.size), baseline, 'no interval is left running');
  }
  await pressCode(page, KEY.down);
  await back(page);
  await waitFocus(page, /Title 100/);
  noErrors(log);
  await context.close();
});

test('holding Back leaves the player only, not the screen under it too', async () => {
  const { page, context } = await openApp({ routes: filmRoutes({ videoSources: [SRC('a')] }), media: () => 'clip' });
  await openTitle(page);
  await waitPlaying(page);
  await page.evaluate(() => {
    for (let i = 0; i < 4; i++) {
      const e = new KeyboardEvent('keydown', { bubbles: true, cancelable: true, repeat: i > 0 });
      Object.defineProperty(e, 'keyCode', { get: () => 461 });
      document.dispatchEvent(e);
    }
    const up = new KeyboardEvent('keyup', { bubbles: true, cancelable: true });
    Object.defineProperty(up, 'keyCode', { get: () => 461 });
    document.dispatchEvent(up);
  });
  await waitFocus(page, /Play|Resume/);
  assert.equal(await page.evaluate(() => window.Sozo.App.router.current().name), 'detail');
  await context.close();
});

test('resume: position saves periodically and on leaving, Detail offers it, and playback starts there', async () => {
  const { page, log, context } = await openApp({ routes: filmRoutes({ videoSources: [SRC('a')] }), media: () => 'clip' });
  await times(page, { saveEvery: 1000, seekCommit: 50 });
  await openTitle(page);
  await waitPlaying(page);
  await pressCode(page, KEY.right);
  await pressCode(page, KEY.right);
  await page.waitForFunction(() => document.querySelector('video').currentTime > 15, null, { timeout: 4000 });
  await page.waitForTimeout(1500);
  const mid = await progress(page, URL_100);
  assert.ok(mid && mid.position > 15 && mid.duration > 50, 'saved while still playing');
  await back(page);
  await waitFocus(page, /Resume from 0:\d\d/);
  const saved = (await progress(page, URL_100)).position;
  await press(page, 'Enter');
  await page.waitForFunction((t) => {
    const v = document.querySelector('video');
    return v && !v.paused && v.currentTime >= t - 1;
  }, saved, { timeout: 10000 });
  await back(page);
  await waitFocus(page, /Resume from/);
  await back(page);
  await page.waitForFunction(() => document.getElementById('app').textContent.includes('Continue watching'), null, { timeout: 5000 });
  noErrors(log);
  await context.close();
});

test('resume at the very end starts over, and a film played to the end leaves Continue Watching', async () => {
  const { page, log, context } = await openApp({ routes: filmRoutes({ videoSources: [SRC('a')] }), media: () => 'clip' });
  await times(page, { seekCommit: 50 });
  await waitFocus(page, /Title 100/);
  await page.evaluate((u) => window.Sozo.App.store.saveProgress({ provider: 'uzbeklar', url: u, title: 'Film 100', position: 45, duration: 60 }), URL_100);
  await page.evaluate((u) => {
    const s = window.Sozo.App.store;
    const e = s.progress('uzbeklar', u);
    window.__play = () => window.Sozo.Player.play({
      provider: 'uzbeklar', contentUrl: u, title: 'Film 100', isSerial: false,
      envelope: { videoSources: [{ quality: 'a', videoUrl: 'https://cdn.test/a.mp4', type: 'mp4' }] }, episodes: [], startPosition: 58
    });
    return e;
  }, URL_100);
  await page.evaluate(() => window.__play());
  await waitPlaying(page, 0.1);
  assert.ok(await video(page, 'v.currentTime') < 10, 'near the end restarts from zero');
  await page.evaluate(() => { document.querySelector('video').currentTime = 59.5; });
  await page.waitForFunction(() => !window.Sozo.Player.isOpen(), null, { timeout: 8000 });
  assert.equal(await progress(page, URL_100), null, 'a finished film is removed');
  noErrors(log);
  await context.close();
});

test('fallback: a failed source moves to the next by itself, after one re-resolve, in server order', async () => {
  let episodesCalls = 0;
  const routes = filmRoutes({ videoSources: [SRC('a', { isDefault: true }), SRC('b'), SRC('c')] });
  const original = routes.episodes;
  routes.episodes = (q) => { episodesCalls++; return original(q); };
  const { page, log, context } = await openApp({ routes, media: (u) => (u.includes('/c.mp4') ? 'clip' : null) });
  await openTitle(page);
  await waitPlaying(page);
  const order = log.external.filter((u) => u.includes('cdn.test')).map((u) => u.split('/').pop());
  const dedup = order.filter((u, i) => u !== order[i - 1]);
  assert.deepEqual(dedup, ['a.mp4', 'b.mp4', 'c.mp4']);
  assert.equal(episodesCalls, 2, 'one load for Detail and one re-resolve');
  assert.equal(log.api.some((r) => r.url.includes('/media')), false);
  noErrors(log);
  await context.close();
});

test('after three sources it stops, shows a chooser with a clear message, and a choice plays', async () => {
  const routes = filmRoutes({ videoSources: [SRC('a'), SRC('b'), SRC('c'), SRC('d')] });
  const { page, log, context } = await openApp({ routes, media: (u) => (u.includes('/d.mp4') ? 'clip' : null) });
  await openTitle(page);
  await waitFocus(page, /^a$/);
  assert.equal(await appText(page, 'This video did not play'), true);
  assert.equal(log.external.includes('https://cdn.test/d.mp4'), false, 'the limit is three sources');
  assert.equal(await visible(page, '.pl-busy'), false);
  await shot(page, 'p2-chooser');
  await pressCode(page, KEY.down);
  await pressCode(page, KEY.down);
  await pressCode(page, KEY.down);
  await waitFocus(page, /^d$/);
  await pressCode(page, KEY.ok);
  await waitPlaying(page);
  assert.equal(await page.evaluate(() => window.Sozo.App.store.getSetting('quality.uzbeklar', null)), 'd');
  noErrors(log);
  await context.close();
});

test('a stream that never answers trips the watchdog, then falls back', async () => {
  const routes = filmRoutes({ videoSources: [SRC('a'), SRC('b')] });
  const { page, log, context } = await openApp({ routes, media: (u) => (u.includes('/a.mp4') ? 'hang' : 'clip') });
  await times(page, { metadataFile: 1200 });
  await openTitle(page);
  assert.equal(await visible(page, '.pl-busy'), true, 'the spinner shows while waiting');
  await waitPlaying(page);
  assert.ok(log.external.includes('https://cdn.test/b.mp4'));
  await context.close();
});

const stallAt = (page) => page.evaluate(() => document.querySelector('video').pause());
const playingAtLeast = (page, t) => page.waitForFunction((m) => {
  const v = document.querySelector('video');
  return v && !v.paused && v.currentTime >= m;
}, t, { timeout: 8000 });

test('a stall mid-play keeps the position through a re-resolve and on to the next source', async () => {
  const routes = filmRoutes({ videoSources: [SRC('a'), SRC('b')] });
  const { page, log, context } = await openApp({ routes, media: () => 'clip' });
  await times(page, { stall: 1500, seekCommit: 50 });
  await openTitle(page);
  await waitPlaying(page);
  await pressCode(page, KEY.right);
  await playingAtLeast(page, 9);
  const before = await video(page, 'v.currentTime');
  const resolves = () => log.api.filter((r) => r.url.includes('/episodes')).length;
  const asked = resolves();
  await stallAt(page);
  // The stall re-resolves and retries the same link, from the same place.
  while (resolves() === asked) await sleep(100);
  await playingAtLeast(page, before - 1);
  assert.equal(await video(page, 'v.currentSrc.includes("/a.mp4")'), true);
  await stallAt(page);
  await page.waitForFunction(() => document.querySelector('video').currentSrc.includes('/b.mp4'), null, { timeout: 10000 });
  await playingAtLeast(page, before - 1);
  noErrors(log);
  await context.close();
});

test('nothing playable: a clear message and a route to another source, and no bad link is touched', async () => {
  const routes = filmRoutes({
    playerSrc: 'https://embed.test/embed/123',
    videoSources: [
      { quality: 'a', videoUrl: 'magnet:?xt=urn:btih:abc', type: 'mp4' },
      { quality: 'b', videoUrl: 'https://cdn.test/a.mkv', type: 'mkv' },
      { quality: 'c', videoUrl: 'http://127.0.0.1:8080/a.mp4', type: 'mp4' },
      { quality: 'd', videoUrl: 'https://cdn.test/s.mp4', type: 'mp4', signer: { url: 'x' } },
      { quality: 'e', videoUrl: 'https://cdn.test/f.mp4', type: 'iframe' }
    ]
  });
  const { page, log, context } = await openApp({ routes, media: () => 'clip' });
  await openTitle(page);
  await waitFocus(page, /Change source/);
  assert.equal(await appText(page, 'cannot play on this TV'), true);
  assert.equal(await visible(page, '.pl-busy'), false, 'never a spinner forever');
  assert.equal(log.external.filter((u) => /cdn\.test|embed\.test|127\.0\.0\.1/.test(u)).length, 0);
  await shot(page, 'p3-no-source');
  await pressCode(page, KEY.ok);
  await waitFocus(page, /IDUB|uzbeklar|Uzbeklar|VidAPI|vidapi/i);
  assert.equal(await page.evaluate(() => window.Sozo.App.router.current().name), 'sources');
  assert.equal(await page.evaluate(() => document.querySelectorAll('video').length), 0);
  noErrors(log);
  await context.close();
});

test('series: /media gets the ref and language; an API failure offers Retry, and Retry plays', async () => {
  let calls = 0;
  const media = (q) => {
    calls++;
    if (calls === 1) return { status: 404, body: { message: 'Hech narsa topilmadi' } };
    return { body: { provider: 'uzbeklar', videoSources: [SRC('ok')], ref: q.get('ref') } };
  };
  const { page, log, context } = await openApp({ routes: seriesRoutes(media), media: () => 'clip' });
  await openTitle(page);
  await waitFocus(page, /Try again/);
  assert.equal(await appText(page, 'topilmadi'), false, 'backend text is never shown');
  await pressCode(page, KEY.ok);
  await waitPlaying(page);
  const url = log.api.filter((r) => r.url.includes('/media'))[0].url;
  assert.match(url, /ref=ref1/);
  assert.match(url, /lang=sub/);
  noErrors(log);
  await context.close();
});

test('availableLangs picks the language sent to /media', async () => {
  const routes = seriesRoutes(() => ({ body: { videoSources: [SRC('ok')] } }));
  routes.episodes = () => ({ body: { isSerial: true, episodes: [{ episode: 1, mediaRef: 'r', season: 1, availableLangs: ['dub', 'sub'] }], totalPages: 1 } });
  const { page, log, context } = await openApp({ routes, media: () => 'clip' });
  await openTitle(page);
  await waitPlaying(page);
  assert.match(log.api.filter((r) => r.url.includes('/media'))[0].url, /lang=dub/);
  await context.close();
});

test('next episode: auto-next counts down, plays on time, and marks the last one finished', async () => {
  const routes = seriesRoutes((q) => ({ body: { videoSources: [SRC(q.get('ref'))] } }));
  const { page, log, context } = await openApp({ routes, media: () => 'clip' });
  await times(page, { autoNext: 2 });
  await openTitle(page);
  await waitPlaying(page);
  await page.evaluate(() => { document.querySelector('video').currentTime = 59.6; });
  await waitFocus(page, /Play now/);
  assert.equal(await appText(page, 'Next episode in'), true);
  await shot(page, 'p4-next');
  const finished = await progress(page, URL_100);
  assert.equal(finished.finished, true);
  await page.waitForFunction(() => {
    const v = document.querySelector('video');
    return v && v.currentSrc.includes('ref2.mp4') && !v.paused && v.currentTime > 0.2;
  }, null, { timeout: 10000 });
  noErrors(log);
  await context.close();
});

test('next episode: Back cancels the countdown, the button still works, and the last episode exits', async () => {
  const routes = seriesRoutes((q) => ({ body: { videoSources: [SRC(q.get('ref'))] } }), 2);
  const { page, log, context } = await openApp({ routes, media: () => 'clip' });
  await times(page, { autoNext: 30, seekCommit: 50 });
  await openTitle(page);
  await waitPlaying(page);
  await page.evaluate(() => { document.querySelector('video').currentTime = 59.6; });
  await waitFocus(page, /Play now/);
  await back(page);
  await waitFocus(page, /Next episode/);
  await page.waitForTimeout(1200);
  assert.equal(await page.evaluate(() => document.querySelector('.pl-next')), null);
  assert.equal(await video(page, 'v.currentSrc.includes("ref1.mp4")'), true);
  await pressCode(page, KEY.ok);
  await page.waitForFunction(() => document.querySelector('video').currentSrc.includes('ref2.mp4'), null, { timeout: 8000 });
  await waitPlaying(page);
  await page.evaluate(() => { document.querySelector('video').currentTime = 59.6; });
  await page.waitForFunction(() => !window.Sozo.Player.isOpen(), null, { timeout: 8000 });
  noErrors(log);
  await context.close();
});

test('a few seconds of another episode leave the series place intact', async () => {
  const routes = seriesRoutes((q) => ({ body: { videoSources: [SRC(q.get('ref'))] } }));
  const { page, context } = await openApp({ routes, media: () => 'clip' });
  await waitFocus(page, /Title 100/);
  await page.evaluate((u) => window.Sozo.App.store.saveProgress({
    provider: 'uzbeklar', url: u, title: 'S', isSerial: true, position: 30, duration: 60, episode: { mediaRef: 'ref2', episode: 2, season: 1 }
  }), URL_100);
  await page.evaluate((u) => window.Sozo.Player.play({
    provider: 'uzbeklar', contentUrl: u, title: 'S', isSerial: true, episode: { mediaRef: 'ref1', episode: 1, season: 1 }, episodes: [], startPosition: 0
  }), URL_100);
  await waitPlaying(page, 0.2);
  await back(page);
  const e = await progress(page, URL_100);
  assert.equal(e.episode.mediaRef, 'ref2');
  assert.equal(e.position, 30);
  await context.close();
});

const SRT = '1\n00:00:00,000 --> 00:01:00,000\nHello <b>from</b> subtitles\n';

test('subtitles: choose a track by remote, cues render as text, the choice is kept, and Off works', async () => {
  const env = { videoSources: [SRC('a')], subtitles: [{ label: 'eng', file: 'https://subs.test/en' }, { label: 'pob', file: 'https://subs.test/pt' }, { label: 'Bad', file: 'https://subs.test/h', headers: { Referer: 'x' } }] };
  const { page, log, context } = await openApp({
    routes: filmRoutes(env),
    media: (u) => (u.includes('subs.test') ? { text: SRT } : 'clip')
  });
  await openTitle(page);
  await waitPlaying(page);
  assert.equal(await visible(page, '.pl-subs-text'), false, 'off by default');
  assert.equal(log.external.some((u) => u.includes('subs.test')), false, 'nothing is fetched until asked');
  await pressCode(page, KEY.up);
  await pressCode(page, KEY.down);
  await waitFocus(page, /Pause/);
  await pressCode(page, KEY.right);
  await waitFocus(page, /Subtitles/);
  await pressCode(page, KEY.ok);
  await waitFocus(page, /Off/);
  await shot(page, 'p5-subtitles-panel');
  assert.equal(await appText(page, 'Bad'), false, 'a track with headers is not offered');
  await pressCode(page, KEY.down);
  await waitFocus(page, /English/);
  await pressCode(page, KEY.ok);
  await page.waitForSelector('.pl-subs-text');
  assert.equal(await page.evaluate(() => document.querySelector('.pl-subs-text').textContent), 'Hello from subtitles');
  assert.equal(await page.evaluate(() => document.querySelector('.pl-subs-text b')), null);
  const size = await page.evaluate(() => parseInt(getComputedStyle(document.querySelector('.pl-subs-text')).fontSize, 10));
  assert.ok(size >= 48, 'readable from the sofa');
  await waitFocus(page, /Subtitles/);
  await shot(page, 'p6-subtitles-on');
  assert.equal(await page.evaluate(() => window.Sozo.App.store.getSetting('subtitle', null)), 'English');
  await pressCode(page, KEY.ok);
  await waitFocus(page, /English/);
  await pressCode(page, KEY.up);
  await pressCode(page, KEY.ok);
  await page.waitForFunction(() => !document.querySelector('.pl-subs-text'));
  noErrors(log);
  await context.close();
});

test('subtitles: an HTML or failing body shows a short notice, and the film keeps playing', async () => {
  const env = { videoSources: [SRC('a')], subtitles: [{ label: 'English', file: 'https://subs.test/en', default: true }] };
  const { page, log, context } = await openApp({
    routes: filmRoutes(env),
    media: (u) => (u.includes('subs.test') ? { text: '<!doctype html><html>blocked</html>' } : 'clip')
  });
  await openTitle(page);
  await waitPlaying(page);
  await page.waitForFunction(() => document.querySelector('.pl-toast').style.display === '');
  assert.equal(await appText(page, 'could not be loaded'), true);
  assert.equal(await appText(page, 'blocked'), false);
  assert.equal(await video(page, 'v.paused'), false);
  noErrors(log);
  await context.close();
});

test('Back closes a panel first, and only then leaves', async () => {
  const { page, log, context } = await openApp({ routes: filmRoutes({ videoSources: [SRC('a'), SRC('b')] }), media: () => 'clip' });
  await openTitle(page);
  await waitPlaying(page);
  await pressCode(page, KEY.up);
  await pressCode(page, KEY.down);
  await pressCode(page, KEY.right);
  await waitFocus(page, /Quality and source/);
  await pressCode(page, KEY.ok);
  await waitFocus(page, /^a$/);
  await back(page);
  await waitFocus(page, /Quality and source/);
  assert.equal(await page.evaluate(() => window.Sozo.Player.isOpen()), true);
  await back(page);
  assert.equal(await page.evaluate(() => window.Sozo.Player.isOpen()), false);
  noErrors(log);
  await context.close();
});

test('the source panel switches source and keeps the position', async () => {
  const { page, log, context } = await openApp({ routes: filmRoutes({ videoSources: [SRC('a'), SRC('b')] }), media: () => 'clip' });
  await times(page, { seekCommit: 50 });
  await openTitle(page);
  await waitPlaying(page);
  await pressCode(page, KEY.right);
  await page.waitForFunction(() => document.querySelector('video').currentTime > 9, null, { timeout: 4000 });
  await pressCode(page, KEY.down);
  await pressCode(page, KEY.right);
  await waitFocus(page, /Quality and source/);
  await pressCode(page, KEY.ok);
  await pressCode(page, KEY.down);
  await waitFocus(page, /^b$/);
  await shot(page, 'p7-source-panel');
  await pressCode(page, KEY.ok);
  await page.waitForFunction(() => {
    const v = document.querySelector('video');
    return v.currentSrc.includes('/b.mp4') && !v.paused && v.currentTime > 9;
  }, null, { timeout: 8000 });
  noErrors(log);
  await context.close();
});

test('Magic Remote: moving shows controls, a bar click seeks, a click on the picture toggles once', async () => {
  const { page, log, context } = await openApp({ routes: filmRoutes({ videoSources: [SRC('a')] }), media: () => 'clip' });
  await openTitle(page);
  await waitPlaying(page);
  await page.mouse.move(900, 500);
  assert.equal(await visible(page, '.pl-bottom'), true);
  const box = await page.evaluate(() => {
    const r = document.querySelector('.pl-bar').getBoundingClientRect();
    return { x: r.left, y: r.top, w: r.width, h: r.height };
  });
  await page.mouse.click(box.x + box.w / 2, box.y + box.h / 2);
  await page.waitForFunction(() => Math.abs(document.querySelector('video').currentTime - 30) < 5, null, { timeout: 4000 });
  assert.equal(await video(page, 'v.paused'), false);
  await page.mouse.click(900, 400);
  await page.waitForTimeout(100);
  assert.equal(await video(page, 'v.paused'), true);
  await page.mouse.move(box.x + 300, 700);
  await page.waitForTimeout(400);
  await page.evaluate(() => {
    const e = new KeyboardEvent('keydown', { bubbles: true, cancelable: true });
    Object.defineProperty(e, 'keyCode', { get: () => 13 });
    document.dispatchEvent(e);
    const up = new KeyboardEvent('keyup', { bubbles: true, cancelable: true });
    Object.defineProperty(up, 'keyCode', { get: () => 13 });
    document.dispatchEvent(up);
    document.querySelector('.pl-video').click();
  });
  await page.waitForTimeout(100);
  assert.equal(await video(page, 'v.paused'), false, 'key and click from one press toggle once');
  noErrors(log);
  await context.close();
});

test('live: no seek bar or time, nothing saved, a dead channel fails fast, and Back asks first', async () => {
  const body = { videoSources: [{ quality: 'dead', videoUrl: 'https://live.test/dead.m3u8', type: 'hls' }, { quality: 'ok', videoUrl: 'https://cdn.test/ch.mp4', type: 'mp4' }] };
  let mediaCalls = 0;
  const { page, log, context } = await openApp({
    routes: { media: () => { mediaCalls++; return { body }; } },
    media: (u) => (u.includes('cdn.test') ? 'clip' : null)
  });
  await waitFocus(page, /Title 5000/);
  await page.evaluate(() => window.Sozo.Player.play({
    provider: 'worldlivetv', contentUrl: 'chan', title: 'Channel', isLive: true, isSerial: false,
    episode: { mediaRef: 'live1' }, episodes: [], startPosition: 0
  }));
  await waitPlaying(page);
  assert.equal(mediaCalls, 1, 'a dead channel moves on without re-resolving');
  assert.equal(await page.evaluate(() => document.querySelector('.pl-bar')), null);
  await pressCode(page, KEY.up);
  await waitFocus(page, /Pause/);
  assert.equal(await appText(page, 'LIVE'), true);
  await shot(page, 'p8-live');
  await back(page);
  await waitFocus(page, /Keep watching/);
  await back(page);
  assert.equal(await page.evaluate(() => window.Sozo.Player.isOpen()), true);
  await back(page);
  await waitFocus(page, /Keep watching/);
  await pressCode(page, KEY.right);
  await waitFocus(page, /^Leave$/);
  await pressCode(page, KEY.ok);
  assert.equal(await page.evaluate(() => window.Sozo.Player.isOpen()), false);
  assert.equal(await page.evaluate(() => window.Sozo.App.store.progress('worldlivetv', 'chan')), null);
  noErrors(log);
  await context.close();
});

const hlsMedia = (u) => {
  const m = /\/hls\/([a-z0-9.]+)/i.exec(u);
  return m ? { hls: m[1] } : null;
};

test('HLS in a desktop browser: hls.js loads only when needed, and plays with a seek', async () => {
  const env = { videoSources: [{ quality: 'auto', videoUrl: 'https://hls.test/hls/index.m3u8', type: 'hls' }] };
  const { page, log, context } = await openApp({
    routes: filmRoutes(env), media: hlsMedia,
    init: () => { delete Object.entries; delete Object.getOwnPropertyDescriptors; delete window.AbortController; }
  });
  await waitFocus(page, /Title 100/);
  assert.equal(await page.evaluate(() => !!window.Hls), false);
  await press(page, 'Enter');
  await waitFocus(page, /Play/);
  await press(page, 'Enter');
  await waitPlaying(page);
  assert.equal(await page.evaluate(() => !!window.Hls), true);
  assert.equal(await page.evaluate(() => typeof Object.entries), 'function', 'the polyfill is in place');
  assert.equal(await page.evaluate(() => document.querySelectorAll('script[src*="hls.min.js"]').length), 1);
  await back(page);
  assert.equal(await page.evaluate(() => document.querySelectorAll('video').length), 0);
  noErrors(log);
  await context.close();
});

test('webOS user agent: native HLS first through <source>, then hls.js where the provider allows it', async () => {
  let refused = 0;
  const media = (u) => {
    if (u.endsWith('index.m3u8') && refused === 0) { refused = 1; return null; }
    return hlsMedia(u);
  };
  const body = { videoSources: [{ quality: 'auto', videoUrl: 'https://cache.libria.fun/hls/index.m3u8', type: 'hls' }] };
  const { page, log, context } = await openApp({
    userAgent: 'Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.34 Safari/537.36 WebAppManager',
    routes: { media: () => ({ body }) },
    media
  });
  await waitFocus(page, /Title 5000/);
  await page.evaluate(() => {
    window.Sozo.Player.play({ provider: 'anilibria', contentUrl: 'x', title: 'A', isSerial: true, episode: { mediaRef: 'r', episode: 1, season: 1 }, episodes: [], startPosition: 0 });
  });
  await page.waitForFunction(() => !!window.Hls, null, { timeout: 8000 });
  await waitPlaying(page);
  assert.equal(await page.evaluate(() => document.querySelectorAll('video source').length), 0, 'the native attempt is gone');
  noErrors(log);
  await context.close();
});

test('webOS user agent: a provider without hls.js moves on, and hls.js is never loaded', async () => {
  const body = { videoSources: [
    { quality: 'x', videoUrl: 'https://rotating.site/x/master', type: 'hls', headers: { Referer: 'r' } },
    { quality: 'y', videoUrl: 'https://cdn.test/y.mp4', type: 'mp4' }
  ] };
  const { page, log, context } = await openApp({
    userAgent: 'Mozilla/5.0 (Web0S; Linux/SmartTV) Chrome/53.0 WebAppManager',
    routes: { media: () => ({ body }) },
    media: (u) => (u.includes('y.mp4') ? 'clip' : null)
  });
  await waitFocus(page, /Title 5000/);
  await page.evaluate(() => window.Sozo.Player.play({ provider: 'vidapi', contentUrl: 'x', title: 'A', isSerial: true, episode: { mediaRef: 'r', episode: 1, season: 1 }, episodes: [], startPosition: 0 }));
  await waitPlaying(page);
  assert.equal(await page.evaluate(() => !!window.Hls), false);
  noErrors(log);
  await context.close();
});

test('18+: an adult envelope title never reaches the video, and an adult request is refused', async () => {
  const routes = filmRoutes({ title: 'Bad XXX film', videoSources: [SRC('a')] });
  const { page, log, context } = await openApp({ routes, media: () => 'clip' });
  await openTitle(page);
  await waitFocus(page, /Change source/);
  assert.equal(await appText(page, 'XXX'), false);
  assert.equal(log.external.includes('https://cdn.test/a.mp4'), false);
  await back(page);
  await page.evaluate(() => window.Sozo.Player.play({ provider: 'uzbeklar', contentUrl: 'x', title: 'Erotic thing', episodes: [] }));
  assert.equal(await page.evaluate(() => document.querySelectorAll('video').length), 0);
  await back(page);
  await page.evaluate(() => window.Sozo.Player.play({ provider: 'thenkiri', contentUrl: 'x', title: 'Fine', episodes: [] }));
  assert.equal(await page.evaluate(() => document.querySelectorAll('video').length), 0);
  await context.close();
});

test('losing the network holds the ladder, and coming back reloads at the position', async () => {
  const { page, log, context } = await openApp({ routes: filmRoutes({ videoSources: [SRC('a'), SRC('b')] }), media: () => 'clip' });
  await times(page, { stall: 1000, seekCommit: 50 });
  await openTitle(page);
  await waitPlaying(page);
  await pressCode(page, KEY.right);
  await playingAtLeast(page, 9);
  const before = await video(page, 'v.currentTime');
  await context.setOffline(true);
  await stallAt(page);
  await waitFocus(page, /Try again/);
  assert.equal(await appText(page, 'No internet'), true);
  assert.equal(log.external.includes('https://cdn.test/b.mp4'), false, 'no source is burned while offline');
  await context.setOffline(false);
  await page.waitForFunction(() => !document.querySelector('.pl-pane'), null, { timeout: 5000 });
  await playingAtLeast(page, before - 1);
  assert.equal(await video(page, 'v.currentSrc.includes("/a.mp4")'), true);
  await context.close();
});

test('leaving during a network hold keeps the Continue Watching place', async () => {
  const { page, context } = await openApp({ routes: filmRoutes({ videoSources: [SRC('a')] }), media: () => 'clip' });
  await times(page, { stall: 1000, seekCommit: 50 });
  await openTitle(page);
  await waitPlaying(page);
  await pressCode(page, KEY.right);
  await playingAtLeast(page, 9);
  await context.setOffline(true);
  await stallAt(page);
  await waitFocus(page, /Try again/);
  await back(page);
  await context.setOffline(false);
  assert.equal(await page.evaluate(() => window.Sozo.Player.isOpen()), false);
  const e = await progress(page, URL_100);
  assert.ok(e && e.position >= 9, 'the entry stays, with its place: ' + JSON.stringify(e));
  await context.close();
});

test('live: Back asks as soon as the picture shows, even before the first timeupdate', async () => {
  const body = { videoSources: [{ quality: 'ok', videoUrl: 'https://cdn.test/ch.mp4', type: 'mp4' }] };
  const { page, log, context } = await openApp({
    routes: { media: () => ({ body }) },
    media: () => 'clip'
  });
  await waitFocus(page, /Title 5000/);
  await page.evaluate(() => {
    window.addEventListener('timeupdate', (e) => e.stopImmediatePropagation(), true);
    window.Sozo.Player.play({
      provider: 'worldlivetv', contentUrl: 'chan', title: 'Channel', isLive: true, isSerial: false,
      episode: { mediaRef: 'live1' }, episodes: [], startPosition: 0
    });
  });
  await waitPlaying(page);
  await back(page);
  await waitFocus(page, /Keep watching/);
  assert.equal(await page.evaluate(() => window.Sozo.Player.isOpen()), true);
  noErrors(log);
  await context.close();
});

test('a live chooser or pane closes the Leave dialog first, and the remote works on what is shown', async () => {
  const body = { videoSources: [{ quality: 'a', videoUrl: 'https://cdn.test/a.mp4', type: 'mp4' }, { quality: 'b', videoUrl: 'https://cdn.test/b.mp4', type: 'mp4' }] };
  const { page, context } = await openApp({ routes: { media: () => ({ body }) }, media: () => 'clip' });
  await waitFocus(page, /Title 5000/);
  await times(page, { stall: 800 });
  await page.evaluate(() => window.Sozo.Player.play({
    provider: 'worldlivetv', contentUrl: 'chan', title: 'Channel', isLive: true, isSerial: false,
    episode: { mediaRef: 'live1' }, episodes: [], startPosition: 0
  }));
  await waitPlaying(page);
  await back(page);
  await waitFocus(page, /Keep watching/);
  // Every link now fails, while the dialog is up.
  await page.evaluate(() => { const v = document.querySelector('video'); v.pause(); setInterval(() => v.pause(), 50); });
  await page.waitForSelector('.pl-panel.is-wide', { timeout: 15000 });
  assert.equal(await page.evaluate(() => window.Sozo.Dialog.isOpen()), false);
  assert.equal(await page.evaluate(() => document.querySelector('.dialog')), null);
  assert.equal(await page.evaluate(() => document.querySelector('.pl-panel').contains(window.Sozo.Nav.current())), true);
  await shot(page, 'p-chooser-after-dialog');
  await back(page);
  assert.equal(await page.evaluate(() => window.Sozo.Player.isOpen()), false, 'one Back leaves');
  await context.close();
});

test('a resting or hidden pointer does not bring the controls back', async () => {
  const { page, context } = await openApp({ routes: filmRoutes({ videoSources: [SRC('a')] }), media: () => 'clip' });
  await times(page, { hideControls: 800 });
  await openTitle(page);
  await waitPlaying(page);
  await page.mouse.move(900, 500);
  assert.equal(await visible(page, '.pl-bottom'), true);
  await page.waitForFunction(() => document.querySelector('.pl-bottom').style.display === 'none', null, { timeout: 4000 });
  const fake = (x, y) => page.evaluate(([cx, cy]) => {
    document.querySelector('video').dispatchEvent(new MouseEvent('mousemove', { bubbles: true, clientX: cx, clientY: cy }));
  }, [x, y]);
  await fake(900, 500);
  assert.equal(await visible(page, '.pl-bottom'), false, 'same coordinates are no move');
  await cursorState(page, false);
  await fake(910, 510);
  assert.equal(await visible(page, '.pl-bottom'), false, 'a hidden pointer is not moving');
  await cursorState(page, true);
  await fake(920, 520);
  assert.equal(await visible(page, '.pl-bottom'), true, 'a real move still wakes them');
  await context.close();
});

test('Magic Remote pointer showing: OK on the bar seeks through the click, and never pauses', async () => {
  const { page, log, context } = await openApp({ routes: filmRoutes({ videoSources: [SRC('a')] }), media: () => 'clip' });
  await openTitle(page);
  await waitPlaying(page);
  await cursorState(page, true);
  await page.mouse.move(900, 500);
  await page.waitForFunction(() => window.Sozo.Nav.current() === document.querySelector('.pl-bar'));
  const box = await page.evaluate(() => {
    const r = document.querySelector('.pl-bar').getBoundingClientRect();
    return { x: r.left, y: r.top, w: r.width, h: r.height };
  });
  await keyEvent(page, 'keydown', KEY.ok);
  await page.mouse.click(box.x + box.w / 2, box.y + box.h / 2);
  await keyEvent(page, 'keyup', KEY.ok);
  await page.waitForFunction(() => Math.abs(document.querySelector('video').currentTime - 30) < 5, null, { timeout: 4000 });
  await sleep(600);
  assert.equal(await video(page, 'v.paused'), false, 'the key did not also toggle');
  noErrors(log);
  await context.close();
});

test('pointer reported as showing but no click comes: the key still acts, and keeps acting', async () => {
  const { page, context } = await openApp({ routes: filmRoutes({ videoSources: [SRC('a')] }), media: () => 'clip' });
  await openTitle(page);
  await waitPlaying(page);
  await cursorState(page, true);
  await pressCode(page, KEY.ok);
  assert.equal(await video(page, 'v.paused'), false, 'waits for a click first');
  await page.waitForFunction(() => document.querySelector('video').paused, null, { timeout: 2000 });
  await sleep(1600);
  await pressCode(page, KEY.ok);
  assert.equal(await video(page, 'v.paused'), false, 'at once from then on');
  await context.close();
});

test('hidden: the auto-next countdown freezes, and continues on return', async () => {
  const routes = seriesRoutes((q) => ({ body: { videoSources: [SRC(q.get('ref'))] } }));
  const { page, log, context } = await openApp({ routes, media: () => 'clip' });
  await times(page, { autoNext: 2 });
  await openTitle(page);
  await waitPlaying(page);
  await page.evaluate(() => { document.querySelector('video').currentTime = 59.6; });
  await waitFocus(page, /Play now/);
  await setHidden(page, true);
  await sleep(3500);
  assert.equal(log.api.filter((r) => r.url.includes('ref=ref2')).length, 0, 'no next episode behind another app');
  assert.equal(await video(page, 'v.currentSrc.includes("ref1.mp4")'), true);
  assert.equal(await visible(page, '.pl-next'), true);
  await setHidden(page, false);
  await page.waitForFunction(() => document.querySelector('video').currentSrc.includes('ref2.mp4') && !document.querySelector('video').paused, null, { timeout: 8000 });
  await context.close();
});

test('hidden: a stream that resolves in the background waits silently, and plays on return', async () => {
  const routes = seriesRoutes(async (q) => {
    await sleep(800);
    return { body: { videoSources: [SRC(q.get('ref'))] } };
  });
  const { page, context } = await openApp({ routes, media: () => 'clip' });
  await openTitle(page);
  await page.waitForSelector('.player');
  await setHidden(page, true);
  await page.waitForFunction(() => { const v = document.querySelector('video'); return v && v.currentSrc.includes('ref1.mp4'); }, null, { timeout: 8000 });
  await sleep(1500);
  assert.equal(await video(page, 'v.paused'), true);
  assert.equal(await video(page, 'v.currentTime'), 0);
  await setHidden(page, false);
  await waitPlaying(page, 0.2);
  await context.close();
});

test('a cut-short list is not the end: auto-next waits for a slow later page', async () => {
  const routes = pagedSeries(6, 3, async (p) => { if (p === 2) await sleep(2500); });
  const { page, context } = await openApp({ routes, media: () => 'clip' });
  await times(page, { autoNext: 30 });
  await waitFocus(page, /Title 100/);
  await press(page, 'Enter');
  await waitFocus(page, /Play episode 1/);
  await page.waitForTimeout(100);
  await page.evaluate(() => window.Sozo.Nav.focus(document.querySelectorAll('.col .fc')[2]));
  await press(page, 'Enter');
  await waitPlaying(page);
  await page.evaluate(() => { document.querySelector('video').currentTime = 59.6; });
  await waitFocus(page, /Play now/);
  assert.equal(await page.evaluate(() => window.Sozo.Player.isOpen()), true);
  assert.equal(await appText(page, 'Episode 4'), true);
  await context.close();
});

test('a cut-short list is not the end: a failed later page gets one retry, then Detail offers Retry', async () => {
  const seen = [];
  const routes = pagedSeries(6, 3, async (p) => {
    if (p === 2) { seen.push(p); return { status: 404, body: { message: 'x' } }; }
    return null;
  });
  const { page, context } = await openApp({ routes, media: () => 'clip' });
  await waitFocus(page, /Title 100/);
  await press(page, 'Enter');
  await page.waitForSelector('.detail-pages .btn');
  const oneTry = seen.length;
  await page.evaluate(() => window.Sozo.Nav.focus(document.querySelectorAll('.col .fc')[2]));
  await press(page, 'Enter');
  await waitPlaying(page);
  await page.evaluate(() => { document.querySelector('video').currentTime = 59.6; });
  await page.waitForFunction(() => !window.Sozo.Player.isOpen(), null, { timeout: 8000 });
  assert.equal(await page.evaluate(() => document.querySelector('.pl-next')), null);
  assert.equal(seen.length, oneTry * 2);
  assert.equal(await page.evaluate(() => document.querySelectorAll('.detail-pages .btn').length), 1);
  assert.ok(await progress(page, URL_100), 'the series is not treated as over');
  await context.close();
});

test('the last episode finished leaves Continue Watching, once the list is whole', async () => {
  const routes = seriesRoutes((q) => ({ body: { videoSources: [SRC(q.get('ref'))] } }), 2);
  const { page, context } = await openApp({ routes, media: () => 'clip' });
  await waitFocus(page, /Title 100/);
  await press(page, 'Enter');
  await waitFocus(page, /Play episode 1/);
  await page.evaluate(() => window.Sozo.Nav.focus(document.querySelectorAll('.col .fc')[1]));
  await press(page, 'Enter');
  await waitPlaying(page);
  await page.evaluate(() => { document.querySelector('video').currentTime = 59.6; });
  await page.waitForFunction(() => !window.Sozo.Player.isOpen(), null, { timeout: 8000 });
  assert.equal(await progress(page, URL_100), null);
  await waitFocus(page, /Episode 2/);
  await back(page);
  await waitFocus(page, /Title 100/);
  assert.equal(await appText(page, 'Continue watching'), false);
  await context.close();
});

test('Detail and the player agree near the end: no Resume label for a start from zero', async () => {
  const { page, context } = await openApp({
    routes: filmRoutes({ videoSources: [SRC('a')] }), media: () => 'clip',
    init: () => localStorage.setItem('sozo.v1.continue', JSON.stringify([{ provider: 'uzbeklar', url: 'https://uzbeklar.biz/100-t.html', title: 'Film 100', isSerial: false, position: 40, duration: 60 }]))
  });
  await page.waitForFunction(() => /Film 100|Title 100/.test(window.Sozo.Nav.current() && window.Sozo.Nav.current().textContent));
  await press(page, 'Enter');
  await waitFocus(page, /^Play$/);
  await press(page, 'Enter');
  await waitPlaying(page, 0.2);
  assert.ok(await video(page, 'v.currentTime') < 10);
  await context.close();
});

test('the player screens are translated', async () => {
  const { page, context } = await openApp({ locale: 'uz-UZ', routes: filmRoutes({ videoSources: [{ quality: 'a', videoUrl: 'magnet:?x', type: 'mp4' }] }) });
  await waitFocus(page, /Title 100/);
  await press(page, 'Enter');
  await page.waitForSelector('.detail-actions .fc');
  await press(page, 'Enter');
  await waitFocus(page, /Manbani/);
  assert.equal(await appText(page, "ko'rib bo'lmaydi"), true);
  await context.close();
});
