'use strict';
// Drives the packaged app with dispatched webOS key codes only. Current Chromium, not a TV.
const path = require('path');
const fs = require('fs');
const { chromium } = require('playwright-core');

const INDEX = 'file://' + path.join(__dirname, '..', '..', 'index.html');
const SHOTS = process.env.SOZO_SHOTS || '';
const K = { left: 37, up: 38, right: 39, down: 40, ok: 13, back: 461, play: 415, pause: 19, stop: 413, rewind: 412, forward: 417 };

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// The full channel carries H.264 and AAC, which the headless shell does not.
async function launch() {
  return chromium.launch({ channel: 'chromium', headless: true, args: ['--autoplay-policy=no-user-gesture-required', '--enable-precise-memory-info', '--js-flags=--expose-gc'] });
}

function watch(page, context) {
  const log = { errors: [], consoleErrors: [], netFails: [], requests: [] };
  page.on('pageerror', (e) => log.errors.push(String(e)));
  page.on('console', (m) => {
    if (m.type() !== 'error') return;
    const text = m.text();
    // Chromium logs every refused request, including third-party hosts without CORS. The app handles those.
    if (/Failed to load resource|net::ERR_|blocked by CORS policy/.test(text)) log.netFails.push(text);
    else log.consoleErrors.push(text);
  });
  page.on('requestfailed', (r) => log.netFails.push(r.failure().errorText + ' ' + new URL(r.url()).host));
  context.on('request', (r) => log.requests.push({ url: r.url(), method: r.method(), adult: r.headers()['x-sozo-adult'] !== undefined }));
  return log;
}

async function key(page, code, gapMs) {
  await page.evaluate((c) => {
    for (const type of ['keydown', 'keyup']) {
      const e = new KeyboardEvent(type, { bubbles: true, cancelable: true });
      Object.defineProperty(e, 'keyCode', { get: () => c });
      document.dispatchEvent(e);
    }
  }, code);
  await sleep(gapMs === undefined ? 130 : gapMs);
}

const keys = async (page, code, n, gapMs) => { for (let i = 0; i < n; i++) await key(page, code, gapMs); };

const focusInfo = (page) => page.evaluate(() => {
  const n = window.Sozo && window.Sozo.Nav;
  const c = n && n.current();
  if (!c) return null;
  const r = c.getBoundingClientRect();
  return { text: c.textContent, fid: c._fid || null, cls: c.className, x: r.left, y: r.top, w: r.width, h: r.height };
});

async function waitFocus(page, re, timeout) {
  await page.waitForFunction((src) => {
    const c = window.Sozo && window.Sozo.Nav.current();
    return !!c && new RegExp(src, 'i').test(c.textContent);
  }, re.source, { timeout: timeout || 20000 });
}

async function waitFocusSel(page, sel, timeout) {
  await page.waitForFunction((s) => {
    const c = window.Sozo && window.Sozo.Nav.current();
    return !!c && c.matches(s);
  }, sel, { timeout: timeout || 20000 });
}

// Moves with arrows towards an element until it holds focus. Target is picked in the page.
async function travelTo(page, pick, maxMoves) {
  for (let i = 0; i < (maxMoves || 40); i++) {
    const step = await page.evaluate((src) => {
      const target = new Function('return (' + src + ')();')();
      const Nav = window.Sozo.Nav;
      const cur = Nav.current();
      if (!target) return { missing: true };
      if (cur === target) return { done: true };
      if (!cur) return { dir: 'down' };
      const a = Nav.logicalRect(cur);
      const b = Nav.logicalRect(target);
      const dy = (b.y + b.h / 2) - (a.y + a.h / 2);
      const dx = (b.x + b.w / 2) - (a.x + a.w / 2);
      if (Math.abs(dy) > a.h / 2) return { dir: dy > 0 ? 'down' : 'up' };
      return { dir: dx > 0 ? 'right' : 'left' };
    }, pick.toString());
    if (step.missing) throw new Error('travelTo: target missing: ' + pick);
    if (step.done) return i;
    await key(page, K[step.dir]);
  }
  throw new Error('travelTo: no route to ' + pick + ' from ' + JSON.stringify(await focusInfo(page)));
}

// Holds at any moment the app is idle on a screen.
async function invariants(page, opts) {
  const o = opts || {};
  const r = await page.evaluate(() => {
    const out = { problems: [] };
    const de = document.documentElement;
    if (de.scrollWidth > window.innerWidth || document.body.scrollWidth > window.innerWidth) out.problems.push('horizontal overflow ' + de.scrollWidth + '/' + document.body.scrollWidth);
    if (window.scrollX || window.scrollY || de.scrollLeft || de.scrollTop || document.body.scrollLeft || document.body.scrollTop) out.problems.push('page scrolled');
    const scr = document.getElementById('app');
    if (scr.scrollLeft || scr.scrollTop) out.problems.push('app scrolled');
    const c = window.Sozo.Nav.current();
    out.dialog = window.Sozo.Dialog.isOpen();
    // While video plays with controls hidden, the player owns the keys and nothing shows focus.
    const bottom = document.querySelector('.pl-bottom');
    if (window.Sozo.Player.isOpen() && bottom && bottom.style.display === 'none' && !document.querySelector('.pl-panel, .pl-pane, .pl-next, .dialog-scrim')) return out;
    if (!c) out.problems.push('no focus');
    else if (!document.documentElement.contains(c)) out.problems.push('focus detached');
    else if (!c.offsetWidth) out.problems.push('focus invisible: ' + c.className);
    else {
      // Target position: a row may still be sliding when this runs.
      const l = window.Sozo.Nav.logicalRect(c);
      const b = { left: l.x, top: l.y, right: l.x + l.w, bottom: l.y + l.h };
      if (b.right <= 0 || b.left >= 1920 || b.bottom <= 0 || b.top >= 1080) out.problems.push('focus offscreen ' + c.className + ' ' + [b.left, b.top, b.right, b.bottom].join(','));
      else if (b.left < -2 || b.right > 1922 || b.top < -2 || b.bottom > 1082) out.problems.push('focus clipped ' + c.className + ' ' + [b.left, b.top, b.right, b.bottom].join(','));
    }
    return out;
  });
  if (o.allowNoFocus) r.problems = r.problems.filter((p) => p !== 'no focus');
  return r.problems;
}

async function shot(page, name) {
  if (!SHOTS) return;
  fs.mkdirSync(SHOTS, { recursive: true });
  await sleep(350);
  await page.screenshot({ path: path.join(SHOTS, name + '.png') });
}

const domCount = (page) => page.evaluate(() => document.getElementsByTagName('*').length);

async function heap(page, client) {
  await client.send('HeapProfiler.collectGarbage');
  await client.send('HeapProfiler.collectGarbage');
  const m = await client.send('Runtime.getHeapUsage');
  const p = await client.send('Performance.getMetrics');
  const metric = (n) => (p.metrics.find((x) => x.name === n) || {}).value;
  return { used: m.usedSize, nodes: metric('Nodes'), listeners: metric('JSEventListeners'), docs: metric('Documents') };
}

module.exports = { INDEX, K, sleep, launch, watch, key, keys, focusInfo, waitFocus, waitFocusSel, travelTo, invariants, shot, domCount, heap };

// Samples focus while a journey runs. Null focus only counts when nothing is loading.
async function monitorFocus(page) {
  await page.evaluate(() => {
    const faults = [];
    let nullFor = 0;
    window.__focusFaults = faults;
    window.__focusTimer = setInterval(() => {
      const c = window.Sozo.Nav.current();
      const bottom = document.querySelector('.pl-bottom');
      if (window.Sozo.Player.isOpen() && bottom && bottom.style.display === 'none' && !document.querySelector('.pl-panel, .pl-pane, .pl-next, .dialog-scrim')) { nullFor = 0; return; }
      const where = window.Sozo.App.router.names().join('>') + (window.Sozo.Player.isOpen() ? '+player' : '') + (window.Sozo.Dialog.isOpen() ? '+dialog' : '');
      if (c && !document.documentElement.contains(c)) { faults.push('detached ' + c.className + ' [' + c.textContent + '] on ' + where); return; }
      if (c && !c.offsetWidth) { faults.push('hidden ' + c.className + ' on ' + where); return; }
      if (c) { nullFor = 0; return; }
      const busy = document.querySelector('.status-loading, .detail-loading') || (document.querySelector('.pl-busy') && document.querySelector('.pl-busy').style.display !== 'none');
      if (busy) { nullFor = 0; return; }
      nullFor++;
      if (nullFor === 8) faults.push('no focus and nothing loading on ' + window.Sozo.App.router.names().join('>'));
    }, 100);
  });
}
const focusFaults = (page) => page.evaluate(() => window.__focusFaults.slice(0, 20));
module.exports.monitorFocus = monitorFocus;
module.exports.focusFaults = focusFaults;

// Horizontal safe area and minimum text size. Row content scrolls off on purpose, so tracks are skipped.
async function auditLayout(page) {
  return page.evaluate(() => {
    const out = [];
    const walker = document.createTreeWalker(document.getElementById('app'), NodeFilter.SHOW_TEXT);
    const seen = new Set();
    for (let n = walker.nextNode(); n; n = walker.nextNode()) {
      if (!n.textContent.trim()) continue;
      const el = n.parentElement;
      if (seen.has(el) || !el.offsetWidth) continue;
      seen.add(el);
      const cs = getComputedStyle(el);
      if (cs.visibility === 'hidden' || cs.display === 'none') continue;
      const name = el.className || el.tagName;
      if (parseFloat(cs.fontSize) < 24) out.push('small text ' + cs.fontSize + ' ' + name);
      if (el.closest('.col-row') || el.closest('.pl-subs')) continue;
      const r = document.createRange();
      r.selectNodeContents(el);
      const b = r.getBoundingClientRect();
      if (b.bottom < 0 || b.top > 1080) continue;
      // Ellipsis and line clamps clip the text to the element's own box.
      const own = el.getBoundingClientRect();
      const clip = cs.overflow === 'hidden';
      const left = clip ? Math.max(b.left, own.left + parseFloat(cs.paddingLeft)) : b.left;
      const right = clip ? Math.min(b.right, own.right - parseFloat(cs.paddingRight)) : b.right;
      if (left < 60 || right > 1860) out.push('outside safe area ' + name + ' ' + Math.round(left) + '..' + Math.round(right));
    }
    return Array.from(new Set(out));
  });
}
module.exports.auditLayout = auditLayout;
