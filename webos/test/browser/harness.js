'use strict';
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright-core');

const INDEX = 'file://' + path.join(__dirname, '..', '..', 'index.html');
const FIX = path.join(__dirname, '..', 'fixtures');
const fixture = (n) => JSON.parse(fs.readFileSync(path.join(FIX, n + '.json'), 'utf8'));
const SHOTS = process.env.SOZO_SHOTS || '';

const MEDIA = path.join(FIX, 'media');
const CLIP = path.join(MEDIA, 'clip.webm');

function serveFile(route, file, contentType) {
  const buf = fs.readFileSync(file);
  const headers = { 'content-type': contentType, 'accept-ranges': 'bytes', 'access-control-allow-origin': '*' };
  const m = /bytes=(\d+)-(\d*)/.exec(route.request().headers().range || '');
  if (!m) return route.fulfill({ status: 200, headers, body: buf });
  const start = Number(m[1]);
  const end = m[2] ? Number(m[2]) : buf.length - 1;
  headers['content-range'] = 'bytes ' + start + '-' + end + '/' + buf.length;
  return route.fulfill({ status: 206, headers, body: buf.slice(start, end + 1) });
}

// A clip in open codecs: Playwright's Chromium has no H.264 or AAC, so the bytes differ from what
// the real sources serve. o.media(url) answers 'clip', {hls: file}, {text, status}, 'hang', or nothing to refuse.
async function answerMedia(route, answer) {
  if (answer === 'hang') return undefined;
  if (answer === 'clip') return serveFile(route, CLIP, 'video/webm');
  if (answer && answer.hls) {
    const type = answer.hls.endsWith('.m3u8') ? 'application/vnd.apple.mpegurl' : 'video/mp4';
    return serveFile(route, path.join(MEDIA, 'hls', answer.hls), type);
  }
  if (answer && answer.text !== undefined) {
    return route.fulfill({ status: answer.status || 200, headers: { 'access-control-allow-origin': '*', 'content-type': 'text/plain' }, body: answer.text });
  }
  return route.abort();
}

const CORS = { 'access-control-allow-origin': '*', 'content-type': 'application/json' };

function card(i, extra) {
  return Object.assign({
    provider: 'uzbeklar', externalId: String(i), title: 'Title ' + i, description: 'About title ' + i,
    slug: 't' + i, contentUrl: 'https://uzbeklar.biz/' + i + '-t.html', thumbnail: 'https://img.test/' + i + '.jpg',
    year: 2026, rating: 0, qualities: [], genres: [], category: null
  }, extra);
}

function range(from, n) {
  const out = [];
  for (let i = 0; i < n; i++) out.push(card(from + i));
  return out;
}

function homeBody() {
  const sections = [];
  for (let s = 0; s < 8; s++) {
    sections.push({ key: 'sec' + s, label: 'Row ' + s, viewAll: { type: 'category', slug: 'cat' + s }, items: range(s * 100, 30) });
  }
  sections[0].items.push(card(9001, { title: 'Bad XXX film' }));
  sections.push({ key: 'bad', label: 'Adult', viewAll: null, items: range(8000, 3) });
  return { provider: 'uzbeklar', banner: range(5000, 4).map((c) => Object.assign(c, { banner: 'https://img.test/b.jpg', description: 'A hero film' })), sections };
}

function episodesFor(n, season) {
  const out = [];
  for (let i = 1; i <= n; i++) out.push({ episode: i, label: 'Episode ' + i, mediaRef: 'uzbeklar:s' + (season || 1) + 'e' + i, image: 'https://img.test/e.jpg', season: season || 1 });
  return out;
}

function defaultRoutes() {
  return {
    'providers': () => ({ body: fixture('providers') }),
    'providers/health': () => ({ body: { sources: { uzbeklar: { ok: true }, idub: { ok: true, unchecked: 'device' }, animevost: { ok: false, hint: 'x' } } } }),
    'home': (q) => {
      if (q.get('provider') === 'uzbeklar') return { body: homeBody() };
      return { body: Object.assign(fixture('home_vidapi'), { provider: q.get('provider') }) };
    },
    'categories': () => ({ body: fixture('categories_uzbeklar') }),
    'genres': () => ({ body: fixture('genres_uzbeklar') }),
    'latest': (q) => ({ body: { provider: 'uzbeklar', items: range(6000 + (q.get('page') - 1) * 24, 24), page: Number(q.get('page')), totalPages: 3 } }),
    'category': (q) => ({ body: { provider: 'uzbeklar', items: range(7000 + (q.get('page') - 1) * 24, 24), page: Number(q.get('page')), totalPages: 2 } }),
    'search': (q) => ({ body: { provider: 'uzbeklar', query: q.get('q'), items: range(3000, 9).concat(card(3999, { title: 'Hidden XXX' })), page: 1, totalPages: 1 } }),
    'detail': (q) => {
      const url = q.get('url');
      if (url.includes('8888')) return { body: { provider: 'uzbeklar', title: 'Secret', description: 'Erotic drama', contentUrl: url } };
      const serial = url.includes('5000') || url.includes('-t.html') && url.includes('/1-');
      return { body: { provider: 'uzbeklar', contentId: '1', contentUrl: url, title: 'Detail of ' + url.split('/').pop(), description: 'Long description', thumbnail: 'https://img.test/p.jpg', banner: 'https://img.test/b.jpg', year: 2024, duration: '102 min', country: null, director: 'A Director', genres: ['Drama', 'Comedy'], cast: [{ id: '1', name: 'Actor One' }, { id: '2', name: 'Actor Two' }], isSerial: serial, related: range(4000, 12) } };
    },
    'episodes': (q) => {
      const url = q.get('url');
      if (url.includes('5000') || url.includes('/1-')) {
        const list = episodesFor(6, 1).concat(episodesFor(4, 2));
        return { body: { provider: 'uzbeklar', contentUrl: url, isSerial: true, episodes: list, page: 1, size: 100, total: list.length, totalPages: 1, sort: 'asc' } };
      }
      return { body: { provider: 'uzbeklar', contentUrl: url, isSerial: false, episodes: [{ episode: 1, label: 'Play', mediaRef: 'r' }], videoSources: [{ quality: '720p', videoUrl: 'https://cdn.test/a.mp4', type: null, isDefault: true }], type: null, headers: {} } };
    }
  };
}

let browserPromise = null;
function browser() {
  if (!browserPromise) browserPromise = chromium.launch({ headless: true, executablePath: process.env.SOZO_CHROMIUM || undefined });
  return browserPromise;
}

async function closeBrowser() {
  if (browserPromise) await (await browserPromise).close();
  browserPromise = null;
}

// Opens the app from file://, like a packaged TV app, with every remote request intercepted.
async function openApp(opts) {
  const o = opts || {};
  const b = await browser();
  const context = await b.newContext({ viewport: { width: 1920, height: 1080 }, locale: o.locale || 'en-US', userAgent: o.userAgent || undefined });
  const page = await context.newPage();
  const routes = Object.assign(defaultRoutes(), o.routes || {});
  const log = { api: [], external: [], errors: [], consoleErrors: [] };
  page.on('pageerror', (e) => log.errors.push(String(e)));
  page.on('console', (m) => {
    if (m.type() === 'error' && !/Failed to load resource|net::ERR_/.test(m.text())) log.consoleErrors.push(m.text());
  });
  await context.route(/^https?:\/\//, async (route) => {
    const req = route.request();
    const url = new URL(req.url());
    if (url.hostname !== 'apisozo.azamov.me') {
      log.external.push(req.url());
      if (req.resourceType() === 'image') {
        if (o.brokenImages) return route.abort();
        return route.fulfill({ status: 200, contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="10" height="15"><rect width="10" height="15" fill="#355"/></svg>' });
      }
      return answerMedia(route, o.media ? o.media(req.url()) : null);
    }
    log.api.push({ url: req.url(), method: req.method(), headers: req.headers() });
    const name = url.pathname.replace('/api/contents/', '');
    const key = routes[name] ? name : name.split('/')[0];
    const handler = routes[key];
    if (!handler) return route.fulfill({ status: 404, headers: CORS, body: JSON.stringify({ message: 'x' }) });
    const res = await handler(url.searchParams, log);
    if (res && res.hang) return undefined;
    // The request can be gone by the time a slow handler answers.
    if (res && res.abort) return route.abort().catch(() => {});
    return route.fulfill({ status: res.status || 200, headers: Object.assign({}, CORS, res.headers), body: res.text !== undefined ? res.text : JSON.stringify(res.body) }).catch(() => {});
  });
  if (o.init) await context.addInitScript(o.init);
  await page.goto(INDEX);
  return { page, context, log, routes };
}

const focusText = (page) => page.evaluate(() => {
  const c = window.Sozo.Nav.current();
  return c ? c.textContent : null;
});

const press = async (page, key, times) => {
  for (let i = 0; i < (times || 1); i++) {
    await page.keyboard.press(key);
    await page.waitForTimeout(100);
  }
};

const LG_BACK = 461;
async function pressCode(page, code) {
  await page.evaluate((c) => {
    for (const type of ['keydown', 'keyup']) {
      const e = new KeyboardEvent(type, { bubbles: true, cancelable: true });
      Object.defineProperty(e, 'keyCode', { get: () => c });
      document.dispatchEvent(e);
    }
  }, code);
  await page.waitForTimeout(120);
}
const back = (page) => pressCode(page, LG_BACK);

async function waitFocus(page, re) {
  await page.waitForFunction((src) => {
    const c = window.Sozo && window.Sozo.Nav.current();
    return c && new RegExp(src).test(c.textContent);
  }, re.source, { timeout: 8000 });
}

async function shot(page, name) {
  if (!SHOTS) return;
  fs.mkdirSync(SHOTS, { recursive: true });
  await page.waitForTimeout(350);
  await page.screenshot({ path: path.join(SHOTS, name + '.png') });
}

// A series over several pages. behave(page) may answer a page itself, or wait first and return nothing.
function pagedSeries(total, size, behave, extra) {
  const url = card(100).contentUrl;
  const pages = Math.ceil(total / size);
  return Object.assign({
    home: () => ({ body: { provider: 'uzbeklar', banner: [], sections: [{ key: 'a', label: 'Row', items: [card(100)] }] } }),
    detail: (q) => ({ body: { provider: 'uzbeklar', contentUrl: q.get('url'), title: 'Series 100', year: 2024, isSerial: true } }),
    episodes: async (q) => {
      const p = Number(q.get('page'));
      const own = behave ? await behave(p) : null;
      if (own) return own;
      const episodes = [];
      for (let i = (p - 1) * size + 1; i <= Math.min(total, p * size); i++) episodes.push({ episode: i, label: 'Episode ' + i, mediaRef: 'ref' + i, season: 1 });
      return { body: { provider: 'uzbeklar', contentUrl: url, isSerial: true, episodes, page: p, totalPages: pages } };
    },
    media: (q) => ({ body: { videoSources: [{ quality: q.get('ref'), videoUrl: 'https://cdn.test/' + q.get('ref') + '.mp4', type: 'mp4' }] } })
  }, extra);
}

function savedEpisode(n, position, finished) {
  return JSON.stringify([{
    provider: 'uzbeklar', url: card(100).contentUrl, title: 'Saved series', thumbnail: '', year: '', isSerial: true,
    position: position, duration: 1500, finished: !!finished, updatedAt: 1, episode: { mediaRef: 'ref' + n, season: 1, episode: n, label: 'Episode ' + n }
  }]);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// document.hidden cannot be switched from a test any other way.
const setHidden = (page, hidden) => page.evaluate((h) => {
  Object.defineProperty(document, 'hidden', { configurable: true, get: () => h });
  document.dispatchEvent(new Event('visibilitychange'));
}, hidden);

const keyEvent = (page, type, code) => page.evaluate(([t, c]) => {
  const e = new KeyboardEvent(t, { bubbles: true, cancelable: true });
  Object.defineProperty(e, 'keyCode', { get: () => c });
  document.dispatchEvent(e);
}, [type, code]);

const cursorState = (page, visibility) => page.evaluate((v) => {
  document.dispatchEvent(new CustomEvent('cursorStateChange', { detail: { visibility: v } }));
}, visibility);

module.exports = { pagedSeries, savedEpisode, sleep, setHidden, keyEvent, cursorState, openApp, closeBrowser, focusText, press, pressCode, back, waitFocus, shot, card, range, fixture, CORS };
