'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { Spatial, Keys, Keyboard, Router } = require('./helper.js');

const box = (id, x, y, w = 100, h = 100) => ({ id, x, y, w, h });
const BOUNDS = { x: 0, y: 0, w: 1920, h: 1080 };

test('findNext picks the aligned neighbour', () => {
  const from = box('a', 100, 100);
  const list = [box('r', 300, 100), box('r2', 500, 100), box('d', 100, 300), box('dr', 700, 300)];
  assert.equal(Spatial.findNext(from, 'right', list, BOUNDS).id, 'r');
  assert.equal(Spatial.findNext(from, 'down', list, BOUNDS).id, 'd');
  assert.equal(Spatial.findNext(from, 'left', list, BOUNDS), null);
  assert.equal(Spatial.findNext(from, 'up', list, BOUNDS), null);
});

test('sideways never jumps to another row', () => {
  const from = box('a', 100, 100);
  assert.equal(Spatial.findNext(from, 'right', [box('below', 300, 400)], BOUNDS), null);
});

test('vertical moves reach a row that does not overlap', () => {
  const from = box('tab', 1500, 20);
  assert.equal(Spatial.findNext(from, 'down', [box('card', 80, 300)], BOUNDS).id, 'card');
});

test('off-screen cards on the cross axis are not candidates', () => {
  const from = box('a', 100, 100);
  assert.equal(Spatial.findNext(from, 'down', [box('off', 2400, 300)], BOUNDS), null);
  assert.equal(Spatial.findNext(box('b', 100, 600), 'right', [box('off', 300, 1200)], BOUNDS), null);
});

test('a row below the fold is still reachable', () => {
  assert.equal(Spatial.findNext(box('a', 100, 900), 'down', [box('next', 100, 1200)], BOUNDS).id, 'next');
});

test('nextIndex on a row', () => {
  assert.equal(Spatial.nextIndex(0, 'left', 0, 5), -1);
  assert.equal(Spatial.nextIndex(0, 'right', 0, 5), 1);
  assert.equal(Spatial.nextIndex(4, 'right', 0, 5), -1);
  assert.equal(Spatial.nextIndex(2, 'up', 0, 5), -1);
  assert.equal(Spatial.nextIndex(0, 'right', 0, 0), -1);
});

test('nextIndex on a grid, with a partial last row', () => {
  assert.equal(Spatial.nextIndex(0, 'down', 3, 7), 3);
  assert.equal(Spatial.nextIndex(5, 'down', 3, 7), 6);
  assert.equal(Spatial.nextIndex(6, 'down', 3, 7), -1);
  assert.equal(Spatial.nextIndex(3, 'up', 3, 7), 0);
  assert.equal(Spatial.nextIndex(1, 'up', 3, 7), -1);
  assert.equal(Spatial.nextIndex(2, 'right', 3, 7), -1);
  assert.equal(Spatial.nextIndex(6, 'right', 3, 7), -1);
  assert.equal(Spatial.nextIndex(3, 'left', 3, 7), -1);
});

test('windowRange limits a row and aligns a grid to rows', () => {
  assert.deepEqual(Spatial.windowRange(50, 0, 100, 7, 9), { start: 43, end: 60 });
  assert.deepEqual(Spatial.windowRange(0, 0, 100, 7, 9), { start: 0, end: 10 });
  assert.deepEqual(Spatial.windowRange(99, 0, 100, 7, 9), { start: 92, end: 100 });
  assert.deepEqual(Spatial.windowRange(20, 6, 100, 3, 4), { start: 0, end: 48 });
  assert.deepEqual(Spatial.windowRange(0, 0, 0, 7, 9), { start: 0, end: 0 });
});

test('minimalScroll keeps a cell as wide as the view whole, and a pad never pushes a cell out', () => {
  assert.equal(Spatial.minimalScroll(0, 1784, 1784, 1760, 8 * 1784, 1070), 1784);
  assert.equal(Spatial.minimalScroll(1784, 0, 1784, 1760, 8 * 1784, 1070), 0);
  assert.equal(Spatial.minimalScroll(0, 900, 600, 1000, 3000, 900), 700);
});

test('minimalScroll moves only as far as needed and clamps', () => {
  assert.equal(Spatial.minimalScroll(0, 100, 50, 1000, 3000, 20), 0);
  assert.equal(Spatial.minimalScroll(0, 1000, 100, 1000, 3000, 20), 120);
  assert.equal(Spatial.minimalScroll(500, 400, 100, 1000, 3000, 20), 380);
  assert.equal(Spatial.minimalScroll(0, 2900, 100, 1000, 3000, 20), 2000);
  assert.equal(Spatial.minimalScroll(100, 0, 100, 1000, 3000, 20), 0);
  assert.equal(Spatial.minimalScroll(0, 0, 100, 1000, 500, 20), 0);
});

test('key codes follow the LG remote', () => {
  assert.equal(Keys.action(461), 'back');
  assert.equal(Keys.action(13), 'ok');
  assert.equal(Keys.action(37), 'left');
  assert.equal(Keys.action(65), null);
  assert.equal(Keys.action(415), 'play');
});

test('throttle drops fast repeats and resets on release', () => {
  let now = 0;
  const t = Keys.throttle(90, () => now);
  assert.equal(t.allow('down'), true);
  now = 30;
  assert.equal(t.allow('down'), false);
  assert.equal(t.allow('up'), true);
  now = 100;
  assert.equal(t.allow('down'), true);
  now = 110;
  t.release('down');
  assert.equal(t.allow('down'), true);
});

test('one remote press giving key and click activates once, but typing fast passes', () => {
  let now = 0;
  const g = Keys.activationGuard(300, () => now);
  assert.equal(g.allow('key'), true);
  now = 20;
  assert.equal(g.allow('pointer'), false);
  now = 400;
  assert.equal(g.allow('pointer'), true);
  now = 410;
  assert.equal(g.allow('pointer'), true);
  now = 1000;
  assert.equal(g.allow('key'), true);
  now = 1050;
  assert.equal(g.allow('key'), true);
});

test('printable reads only single characters', () => {
  assert.equal(Keys.printable({ key: 'a' }), 'a');
  assert.equal(Keys.printable({ key: 'Enter' }), '');
  assert.equal(Keys.printable({ key: 'a', ctrlKey: true }), '');
  assert.equal(Keys.printable({}), '');
});

test('keyboard layouts have every letter and fit the row width', () => {
  for (const mode of ['latin', 'cyrillic']) {
    const rows = Keyboard.layout(mode);
    for (const r of rows) {
      const width = r.reduce((n, k) => n + (k.wide || 1), 0);
      assert.ok(width <= Keyboard.COLS, mode + ' row too wide: ' + width);
    }
  }
  const chars = Keyboard.layout('cyrillic').flat().map((k) => k.ch).join('');
  assert.ok(chars.includes('ё') && chars.includes('я') && chars.includes('0'));
  const latin = Keyboard.layout('latin').flat().map((k) => k.ch).join('');
  assert.ok(latin.includes("'"), 'Uzbek needs the apostrophe');
  assert.equal(Keyboard.nextMode('latin'), 'cyrillic');
  assert.equal(Keyboard.nextMode('cyrillic'), 'latin');
});

test('typing rules', () => {
  assert.equal(Keyboard.type('', ' '), '');
  assert.equal(Keyboard.type('a ', ' '), 'a ');
  assert.equal(Keyboard.type('ab', 'c'), 'abc');
  assert.equal(Keyboard.backspace('abc'), 'ab');
  assert.equal(Keyboard.backspace(''), '');
  assert.equal(Keyboard.type('x'.repeat(60), 'y').length, 60);
  assert.equal(Keyboard.normalize('  a   b '), 'a b');
  assert.equal(Keyboard.shouldSearch(' a '), false);
  assert.equal(Keyboard.shouldSearch('ab'), true);
});

test('debounce runs only the last call', () => {
  const pending = [];
  const d = Keyboard.debouncer(100, (f) => { pending.push(f); return pending.length - 1; }, (id) => { pending[id] = null; });
  const calls = [];
  d.call(() => calls.push(1));
  d.call(() => calls.push(2));
  pending.filter(Boolean).forEach((f) => f());
  assert.deepEqual(calls, [2]);
  d.call(() => calls.push(3));
  d.cancel();
  assert.equal(pending[pending.length - 1], null);
});

test('router: push and back, tabs sit on home, home is root', () => {
  const r = Router.create();
  r.reset('home');
  assert.equal(r.isRoot(), true);
  assert.equal(r.back(), null);
  r.push('detail', { a: 1 });
  r.saveState({ x: 1 });
  r.push('detail', { a: 2 });
  assert.deepEqual(r.names(), ['home', 'detail', 'detail']);
  assert.equal(r.back().state.x, 1);
  r.tab('search');
  assert.deepEqual(r.names(), ['home', 'search']);
  r.tab('settings');
  assert.deepEqual(r.names(), ['home', 'settings']);
  assert.equal(r.back().name, 'home');
});

test('router remembers tab state and home state', () => {
  const r = Router.create();
  r.reset('home');
  r.saveState({ h: 1 });
  r.tab('search');
  r.saveState({ q: 'abc' });
  r.tab('settings');
  assert.equal(r.tab('search').state.q, 'abc');
  assert.equal(r.tab('home').state.h, 1);
  assert.deepEqual(r.names(), ['home']);
});

test('router bounds depth and drops old state', () => {
  const r = Router.create();
  r.reset('home');
  for (let i = 0; i < 100; i++) {
    r.push('detail', { i });
    r.saveState({ big: i });
  }
  assert.ok(r.depth() <= Router.MAX_DEPTH);
  assert.equal(r.names()[0], 'home');
  assert.equal(r.current().params.i, 99);
  r.back();
  assert.ok(r.current().state);
  for (let i = 0; i < Router.KEEP_STATE + 2; i++) r.back();
  assert.equal(r.current().state, null);
});

test('activationGuard measured from release: a late click after a long press is dropped', () => {
  let now = 0;
  const g = Keys.activationGuard(300, () => now);
  assert.equal(g.allow('key'), true);
  now = 2000;
  g.release('key');
  now = 2100;
  assert.equal(g.allow('pointer'), false);
  now = 2500;
  assert.equal(g.allow('pointer'), true);
  g.release('key');
  now = 2510;
  assert.equal(g.allow('pointer'), true, 'release of the other input changes nothing');
});

test('pointerTracker: no move at the same place or while hidden, one answer per event', () => {
  const p = Keys.pointerTracker();
  const ev = (x, y) => ({ clientX: x, clientY: y });
  assert.equal(p.visible(), null);
  const a = ev(1, 1);
  assert.equal(p.moved(a), true);
  assert.equal(p.moved(a), true, 'a second listener gets the same answer');
  assert.equal(p.moved(ev(1, 1)), false);
  assert.equal(p.moved(ev(2, 1)), true);
  p.setVisible(false);
  assert.equal(p.moved(ev(3, 1)), false);
  p.setVisible(true);
  assert.equal(p.moved(ev(3, 1)), false, 'it did not move since');
  assert.equal(p.moved(ev(4, 1)), true);
});
