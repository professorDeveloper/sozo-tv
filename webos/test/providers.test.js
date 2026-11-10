'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { Providers, Platform, fixture } = require('./helper.js');

test('allowlist is exactly the sixteen approved ids', () => {
  assert.deepEqual(Providers.list.map((p) => p.id).sort(), ['anikage', 'anilibria', 'animeav1', 'animevost', 'cinestream', 'idub', 'livetvindia', 'rareanimes', 'sportstv', 'supercartoons', 'tioanime', 'uhdmovies', 'uzbeklar', 'vidapi', 'worldlivetv', 'yummyanime']);
});

test('excluded providers cannot appear even if the API returns them', () => {
  const items = ['thenkiri', 'internetarchive', 'peertube', 'wikimedia', 'musictv', 'hanime', 'uzbeklar'].map((id) => ({ id, name: id, mode: 'server' }));
  assert.deepEqual(Providers.visible(items, null).map((p) => p.id), ['uzbeklar']);
});

test('nsfw, browseOnly, client mode and missing providers stay hidden', () => {
  const v = Providers.visible([
    { id: 'uzbeklar', mode: 'server', nsfw: true },
    { id: 'idub', mode: 'hybrid', browseOnly: true },
    { id: 'anilibria', mode: 'client' },
    { id: 'animevost', mode: 'server', name: 'AnimeVost' }
  ], null);
  assert.deepEqual(v.map((p) => p.id), ['animevost']);
  assert.deepEqual(Providers.visible(null, null), []);
});

test('health: down sorts last but stays, unchecked and missing read as unknown', () => {
  const items = fixture('providers').items;
  const v = Providers.visible(items, { sources: { uzbeklar: { ok: false }, idub: { ok: true, unchecked: 'device' }, vidapi: { ok: true } } });
  assert.equal(v.length, 16);
  assert.equal(v[v.length - 1].id, 'uzbeklar');
  assert.equal(v[v.length - 1].health, 'down');
  assert.equal(v.find((p) => p.id === 'idub').health, 'unknown');
  assert.equal(v.find((p) => p.id === 'vidapi').health, 'ok');
  assert.equal(v.find((p) => p.id === 'anilibria').health, 'unknown');
});

test('pick prefers the saved provider, then the default, then the first', () => {
  const v = [{ id: 'vidapi' }, { id: 'uzbeklar' }];
  assert.equal(Providers.pick(v, 'vidapi').id, 'vidapi');
  assert.equal(Providers.pick(v, 'gone').id, 'uzbeklar');
  assert.equal(Providers.pick([{ id: 'idub' }], null).id, 'idub');
  assert.equal(Providers.pick([], null), null);
});

test('vidapi keeps search, view-all and related off', () => {
  const p = Providers.byId('vidapi');
  assert.deepEqual([p.search, p.viewAll, p.related], [false, false, false]);
});

test('exit uses the first available route and never throws', () => {
  let called = '';
  assert.equal(Platform.exit({ webOS: { platformBack: () => { called = 'w'; } } }), 'webOS');
  assert.equal(called, 'w');
  assert.equal(Platform.exit({ PalmSystem: { platformBack: () => {} } }), 'PalmSystem');
  assert.equal(Platform.exit({ close: () => {} }), 'close');
  assert.equal(Platform.exit({}), 'none');
  assert.equal(Platform.exit({ webOS: { platformBack: () => { throw new Error('x'); } }, close: () => {} }), 'close');
  assert.equal(Platform.isWebOS('Mozilla/5.0 (Web0S; Linux/SmartTV)'), true);
  assert.equal(Platform.isWebOS('Mozilla/5.0 Chrome/120'), false);
});

test('only relayHeaders providers go through the relay with headers, and never via hls.js', () => {
  const relayed = Providers.list.filter((p) => p.relayHeaders === true).map((p) => p.id).sort();
  assert.deepEqual(relayed, ['anikage', 'animeav1', 'cinestream', 'rareanimes', 'tioanime', 'uhdmovies', 'yummyanime']);
  assert.deepEqual(Providers.list.filter((p) => p.mkv === true).map((p) => p.id).sort(), ['cinestream', 'uhdmovies']);
  for (const id of relayed) {
    const p = Providers.byId(id);
    assert.equal(p.relabel, true, id);
    assert.equal(p.hlsjs, false, id);
  }
});
