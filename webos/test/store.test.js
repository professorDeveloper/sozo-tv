'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { Store, Playback } = require('./helper.js');

function memoryStorage(limit) {
  const data = {};
  return {
    data,
    getItem: (k) => (Object.prototype.hasOwnProperty.call(data, k) ? data[k] : null),
    setItem: (k, v) => {
      const size = Object.keys(data).reduce((n, x) => (x === k ? n : n + data[x].length), 0) + v.length;
      if (limit !== undefined && size > limit) throw new Error('QuotaExceededError');
      data[k] = v;
    },
    removeItem: (k) => { delete data[k]; }
  };
}

const card = (id) => ({ provider: 'uzbeklar', url: 'u' + id, title: 'T' + id, thumbnail: '', year: '2026' });

test('my list toggles and persists', () => {
  const st = memoryStorage();
  const s = Store.create(st);
  assert.deepEqual(s.myList(), []);
  assert.equal(s.toggleMyList(card(1)), true);
  assert.equal(s.inMyList('uzbeklar', 'u1'), true);
  assert.equal(Store.create(st).myList().length, 1);
  assert.equal(s.toggleMyList(card(1)), false);
  assert.equal(s.inMyList('uzbeklar', 'u1'), false);
});

test('my list is capped', () => {
  const s = Store.create(memoryStorage());
  for (let i = 0; i < Store.MAX_LIST + 10; i++) s.toggleMyList(card(i));
  assert.equal(s.myList().length, Store.MAX_LIST);
});

test('progress saves, resumes and moves to the front', () => {
  const s = Store.create(memoryStorage());
  s.saveProgress({ ...card(1), position: 100, duration: 1000 });
  s.saveProgress({ ...card(2), position: 50, duration: 1000 });
  assert.equal(s.progress('uzbeklar', 'u1').position, 100);
  assert.equal(s.continueList()[0].url, 'u2');
  s.saveProgress({ ...card(1), position: 200, duration: 1000 });
  assert.equal(s.continueList()[0].url, 'u1');
  assert.equal(s.continueList().length, 2);
});

test('a finished film leaves history, a finished series keeps its place', () => {
  const s = Store.create(memoryStorage());
  s.saveProgress({ ...card(1), position: 100, duration: 1000 });
  s.saveProgress({ ...card(1), position: 990, duration: 1000 });
  assert.equal(s.progress('uzbeklar', 'u1'), null);
  s.saveProgress({ ...card(2), isSerial: true, position: 990, duration: 1000, episode: { mediaRef: 'r', episode: 1 } });
  const e = s.progress('uzbeklar', 'u2');
  assert.equal(e.finished, true);
  assert.equal(e.position, 0);
  assert.equal(e.episode.mediaRef, 'r');
  assert.equal(s.continueList().length, 1);
});

test('the first seconds do not create history', () => {
  const s = Store.create(memoryStorage());
  assert.equal(s.saveProgress({ ...card(1), position: 2, duration: 1000 }), false);
  assert.equal(s.continueList().length, 0);
});

test('live channels never save progress', () => {
  const s = Store.create(memoryStorage());
  assert.equal(s.saveProgress({ provider: 'worldlivetv', url: 'x', title: 'C', position: 500, duration: 0 }), false);
  assert.equal(s.continueList().length, 0);
});

test('history is capped and clears', () => {
  const s = Store.create(memoryStorage());
  for (let i = 0; i < Store.MAX_CONTINUE + 5; i++) s.saveProgress({ ...card(i), position: 100, duration: 1000 });
  assert.equal(s.continueList().length, Store.MAX_CONTINUE);
  s.clearHistory();
  assert.equal(s.continueList().length, 0);
});

test('entries from removed providers or with adult titles are dropped on read', () => {
  const st = memoryStorage();
  st.setItem(Store.PREFIX + 'mylist', JSON.stringify([
    card(1),
    { provider: 'thenkiri', url: 'x', title: 'Old' },
    { provider: 'uzbeklar', url: 'y', title: 'Bad XXX' },
    { provider: 'uzbeklar' },
    null, 5
  ]));
  assert.deepEqual(Store.create(st).myList().map((e) => e.url), ['u1']);
});

test('corrupt, wrong-typed or null data reads as empty', () => {
  const st = memoryStorage();
  st.setItem(Store.PREFIX + 'mylist', '{not json');
  st.setItem(Store.PREFIX + 'continue', '{"a":1}');
  st.setItem(Store.PREFIX + 'settings', 'null');
  const s = Store.create(st);
  assert.deepEqual(s.myList(), []);
  assert.deepEqual(s.continueList(), []);
  assert.equal(s.getSetting('language', 'auto'), 'auto');
  s.setSetting('language', 'ru');
  assert.equal(s.getSetting('language'), 'ru');
});

test('storage that throws on every call still works for the session', () => {
  const broken = {
    getItem() { throw new Error('denied'); },
    setItem() { throw new Error('denied'); },
    removeItem() { throw new Error('denied'); }
  };
  const s = Store.create(broken);
  assert.equal(s.toggleMyList(card(1)), true);
  assert.equal(s.inMyList('uzbeklar', 'u1'), true);
  s.setSetting('provider', 'idub');
  assert.equal(s.getSetting('provider'), 'idub');
  s.clearMyList();
  assert.deepEqual(s.myList(), []);
});

test('no storage at all still works', () => {
  const s = Store.create(null);
  s.setSetting('a', 1);
  assert.equal(s.getSetting('a'), 1);
});

test('full storage sheds history before giving up', () => {
  const st = memoryStorage(2500);
  const s = Store.create(st);
  for (let i = 0; i < 40; i++) s.saveProgress({ ...card(i), title: 'x'.repeat(20), position: 100, duration: 1000 });
  assert.ok(s.continueList().length > 0);
  assert.ok(s.continueList().length <= 40);
  assert.equal(s.continueList()[0].url, 'u39');
  assert.equal(s.toggleMyList({ ...card(99), title: 'y'.repeat(200) }), true);
  assert.equal(s.inMyList('uzbeklar', 'u99'), true);
  assert.ok(Object.values(st.data).join('').length <= 2500);
});

test('a place inside the end margin counts as finished, as Playback.resumeAt starts it over', () => {
  const s = Store.create(memoryStorage());
  assert.equal(Playback.resumeAt(40, 60), 0);
  s.saveProgress({ ...card(1), position: 40, duration: 60 });
  assert.equal(s.progress(card(1).provider, card(1).url), null);
  s.saveProgress({ ...card(2), isSerial: true, position: 40, duration: 60, episode: { mediaRef: 'r', episode: 1 } });
  assert.equal(s.progress(card(2).provider, card(2).url).finished, true);
  s.saveProgress({ ...card(3), position: 20, duration: 60 });
  assert.equal(s.progress(card(3).provider, card(3).url).position, 20);
});

test('removeProgress takes one entry and leaves the rest', () => {
  const s = Store.create(memoryStorage());
  s.saveProgress({ ...card(1), position: 100, duration: 1000 });
  s.saveProgress({ ...card(2), position: 100, duration: 1000 });
  s.removeProgress(card(1).provider, card(1).url);
  assert.equal(s.progress(card(1).provider, card(1).url), null);
  assert.ok(s.progress(card(2).provider, card(2).url));
  s.removeProgress('uzbeklar', 'nothing');
  assert.equal(s.continueList().length, 1);
});
