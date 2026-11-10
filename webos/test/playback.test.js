'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { Playback, Store, Model, fixture } = require('./helper.js');

const eligible = (env, id) => Playback.eligible(env, id || 'vidapi');
const urls = (env, id) => eligible(env, id).sources.map((s) => s.url);
const reason = (src, env, id) => eligible(Object.assign({ videoSources: [src] }, env), id || 'uzbeklar').rejected[0].reason;
const mp4 = (n, extra) => Object.assign({ quality: '720p', videoUrl: 'https://cdn.test/' + n + '.mp4', type: 'mp4' }, extra);

test('real fixtures: vidapi film and anilibria episode yield their sources', () => {
  const film = eligible(fixture('episodes_vidapi_movie'), 'vidapi').sources;
  assert.ok(film.length >= 1);
  assert.ok(film.every((s) => s.isHls));
  const ep = eligible(fixture('media_anilibria'), 'anilibria').sources;
  assert.ok(ep.length >= 1);
  assert.ok(ep.every((s) => /^https:\/\/cache\.libria\.fun\//.test(s.url)));
});

test('magnet links, user info and other schemes are rejected', () => {
  assert.equal(reason({ videoUrl: 'magnet:?xt=urn:btih:abc', type: 'mp4' }), 'scheme');
  assert.equal(reason({ videoUrl: 'ftp://x.test/a.mp4' }), 'scheme');
  assert.equal(reason({ videoUrl: '//x.test/a.mp4' }), 'scheme');
  assert.equal(reason({ videoUrl: 'https://user:pw@x.test/a.mp4' }), 'scheme');
  assert.equal(reason({ videoUrl: 'javascript:alert(1)//a.mp4' }), 'scheme');
});

test('local proxies and private hosts are rejected, public IP hosts are not', () => {
  for (const host of ['localhost', '127.0.0.1', '10.1.2.3', '192.168.1.5', '172.16.0.1', '172.31.9.9', '169.254.1.1', '0.0.0.0', '[::1]', 'tv.local']) {
    assert.equal(reason({ videoUrl: 'http://' + host + ':8080/a.m3u8' }), 'private-host', host);
  }
  assert.equal(eligible({ videoSources: [{ videoUrl: 'http://172.32.0.1/a.m3u8' }, { videoUrl: 'http://45.1.2.3/b.m3u8' }] }, 'sportstv').sources.length, 2);
});

test('iframe, embed, dash, torrent and mkv are rejected by type or path', () => {
  for (const type of ['iframe', 'embed', 'html', 'dash', 'mpd', 'torrent', 'magnet', 'mkv', 'IFRAME']) {
    assert.equal(reason({ videoUrl: 'https://x.test/a.mp4', type }), 'type', type);
  }
  for (const p of ['a.mpd', 'a.html', 'a.htm', 'a.mkv', 'embed/1.mp4', 'e/abc']) {
    assert.equal(reason({ videoUrl: 'https://x.test/' + p, type: 'mp4' }), 'path', p);
  }
});

test('device-only directives are rejected, on a source or on the envelope', () => {
  for (const d of ['useLocalProxy', 'localProxy', 'requestTransform', 'signer', 'useWebViewSniff', 'drm']) {
    assert.equal(reason(mp4('a', { [d]: true })), 'directive', d);
  }
  assert.equal(reason(mp4('a', { audioTracks: [{ id: 1 }] })), 'directive');
  assert.equal(eligible({ videoSources: [mp4('a', { audioTracks: [], drm: null, signer: false })] }, 'uzbeklar').sources.length, 1);
  for (const d of ['signer', 'useWebViewSniff', 'drm']) {
    assert.equal(reason(mp4('a'), { [d]: { x: 1 } }), 'envelope', d);
  }
});

test('a source the backend marked inaccessible is rejected', () => {
  assert.equal(reason(mp4('a', { accessible: false })), 'inaccessible');
});

test('headers: only names this provider is known not to enforce, envelope and source merged', () => {
  const ua = { 'User-Agent': 'x' };
  assert.equal(eligible({ videoSources: [mp4('a', { headers: ua })] }, 'vidapi').sources.length, 1);
  assert.equal(reason(mp4('a', { headers: { Cookie: 'x' } }), {}, 'vidapi'), 'header');
  assert.equal(reason(mp4('a'), { headers: { Authorization: 'x' } }, 'vidapi'), 'header');
  assert.equal(reason(mp4('a', { headers: ua }), {}, 'idub'), 'header');
});

test('mkv: only providers marked mkv accept it, by type or by path, as a file', () => {
  const byType = { videoUrl: 'https://w.test/abc', type: 'mkv' };
  const byPath = { videoUrl: 'https://r2.test/hub/Film.2026.1080p.mkv', type: 'mp4' };
  for (const id of ['uhdmovies', 'cinestream']) {
    const out = eligible({ videoSources: [byType, byPath] }, id).sources;
    assert.equal(out.length, 2, id);
    assert.ok(out.every((s) => !s.isHls), id);
  }
  assert.equal(reason(byType, {}, 'anikage'), 'type');
  assert.equal(reason(byPath, {}, 'anikage'), 'path');
  assert.equal(reason({ videoUrl: 'https://w.test/a.mpd', type: 'mkv' }, {}, 'uhdmovies'), 'path');
  assert.equal(reason({ videoUrl: 'https://w.test/a', type: 'iframe' }, {}, 'uhdmovies'), 'type');
});

test('relayHeaders: relayable names pass and travel with the source, source overriding the envelope', () => {
  const env = { headers: { 'User-Agent': 'env', Referer: 'https://env.test/' }, videoSources: [mp4('a', { headers: { Referer: 'https://src.test/', Origin: 'https://src.test', 'Accept-Language': '*' } })] };
  const src = eligible(env, 'anikage').sources[0];
  assert.deepEqual(src.headers, { 'User-Agent': 'env', Referer: 'https://src.test/', Origin: 'https://src.test', 'Accept-Language': '*' });
  assert.equal(reason(mp4('a', { headers: { Cookie: 'x' } }), {}, 'anikage'), 'header');
  assert.equal(reason(mp4('a', { headers: { Authorization: 'x' } }), {}, 'tioanime'), 'header');
  assert.equal('headers' in eligible({ videoSources: [mp4('a', { headers: { 'User-Agent': 'x' } })] }, 'vidapi').sources[0], false, 'other providers are unchanged');
});

test('hosts: an unknown host that declares headers is rejected, as the evidence does not cover it', () => {
  const on = (u, h) => ({ videoUrl: u, type: 'mp4', headers: h });
  const referer = { Referer: 'x' };
  assert.equal(eligible({ videoSources: [on('https://s1.uzbeklar.biz/a.mp4', referer)] }, 'uzbeklar').sources.length, 1);
  assert.equal(reason(on('https://other.test/a.mp4', referer), {}, 'uzbeklar'), 'host');
  assert.equal(eligible({ videoSources: [on('https://other.test/a.mp4', {})] }, 'uzbeklar').sources.length, 1);
});

test('what is not recognisably media is rejected, and type null inherits, then falls to the extension', () => {
  assert.equal(reason({ videoUrl: 'https://x.test/watch?id=1' }), 'not-media');
  assert.equal(urls({ type: 'hls', videoSources: [{ videoUrl: 'https://x.test/stream', type: null }] }, 'uzbeklar').length, 1);
  const byExt = eligible({ videoSources: [{ videoUrl: 'https://x.test/a.mp4?t=1', type: null }, { videoUrl: 'https://x.test/b.m3u8', type: null }] }, 'uzbeklar').sources;
  assert.deepEqual(byExt.map((s) => s.isHls), [false, true]);
});

test('top-level videoUrl and playerSrc are appended and de-duplicated, keeping server order', () => {
  const list = urls({
    videoUrl: 'https://x.test/b.mp4', playerSrc: 'https://x.test/c.mp4', type: 'mp4',
    videoSources: [mp4('z'), { videoUrl: 'https://x.test/b.mp4' }]
  }, 'uzbeklar');
  assert.deepEqual(list, ['https://cdn.test/z.mp4', 'https://x.test/b.mp4', 'https://x.test/c.mp4']);
});

test('unknown providers and junk envelopes give nothing, and never throw', () => {
  assert.deepEqual(Playback.eligible({ videoSources: [mp4('a')] }, 'thenkiri').sources, []);
  for (const env of [null, undefined, 'x', {}, { videoSources: 'x' }, { videoSources: [null, 1, {}] }]) {
    assert.deepEqual(Playback.eligible(env, 'uzbeklar').sources, []);
  }
});

test('repeated labels stay distinct, so fallback can tell sources apart', () => {
  const list = eligible({ videoSources: [mp4('a'), mp4('b'), mp4('c', { quality: '' }), mp4('d', { quality: '' })] }, 'uzbeklar').sources;
  assert.equal(new Set(list.map((s) => s.key)).size, 4);
  assert.equal(list[1].label, '720p (2)');
  assert.equal(list[2].label, '');
  const same = eligible({ videoSources: [mp4('a', { quality: 'Server 1', host: 'Server 1' }), mp4('b', { quality: '1080p', host: 'Server 2' })] }, 'uzbeklar').sources;
  assert.deepEqual(same.map((s) => s.label), ['Server 1', '1080p · Server 2']);
});

test('start pick: remembered quality, then isDefault, then server order, never by height', () => {
  const list = eligible({ videoSources: [mp4('a', { quality: '360p' }), mp4('b', { quality: '1080p' }), mp4('c', { quality: '720p', isDefault: true })] }, 'uzbeklar').sources;
  assert.equal(Playback.pickStart(list, '1080p'), 1);
  assert.equal(Playback.pickStart(list, '4K'), 2);
  assert.equal(Playback.pickStart(list, null), 2);
  assert.equal(Playback.pickStart(list.slice(0, 2), null), 0);
});

test('engine: mp4 is always a file; webOS is native first; browsers use hls.js, then native, then nothing', () => {
  const hlsSrc = { isHls: true };
  assert.equal(Playback.engine({ isHls: false }, {}), 'file');
  assert.equal(Playback.engine(hlsSrc, { isWebOS: true, mse: true }), 'native');
  assert.equal(Playback.engine(hlsSrc, { mse: true, nativeHls: true }), 'hlsjs');
  assert.equal(Playback.engine(hlsSrc, { nativeHls: true }), 'native');
  assert.equal(Playback.engine(hlsSrc, {}), 'none');
});

function ladderFor(n, opts, type) {
  const list = [];
  for (let i = 0; i < n; i++) list.push(mp4('s' + i, { quality: 'q' + i, type: type || 'mp4' }));
  const sources = eligible({ videoSources: list }, 'uzbeklar').sources;
  return Playback.createLadder(sources, 0, Object.assign({ canReresolve: false, env: {} }, opts));
}

test('fallback order: next sources in server order, chooser after three failures even with more left', () => {
  const l = ladderFor(5);
  assert.deepEqual(l.fail({}), { action: 'next', index: 1 });
  assert.deepEqual(l.fail({}), { action: 'next', index: 2 });
  assert.deepEqual(l.fail({}), { action: 'chooser' });
});

test('fallback wraps from a remembered start, and stops when every source has failed', () => {
  const sources = eligible({ videoSources: [mp4('a', { quality: 'a' }), mp4('b', { quality: 'b' })] }, 'uzbeklar').sources;
  const l = Playback.createLadder(sources, 1, { env: {} });
  assert.deepEqual(l.fail({}), { action: 'next', index: 0 });
  assert.deepEqual(l.fail({}), { action: 'chooser' });
});

test('a single source goes straight to the chooser', () => {
  assert.deepEqual(ladderFor(1).fail({}), { action: 'chooser' });
});

test('re-resolve happens once, then moves on', () => {
  const l = ladderFor(3, { canReresolve: true });
  assert.deepEqual(l.fail({}), { action: 'reresolve' });
  assert.deepEqual(l.fail({}), { action: 'next', index: 1 });
});

test('a known dead link skips re-resolve, while an expired one and any mid-play failure use it', () => {
  assert.equal(ladderFor(3, { canReresolve: true }).fail({ status: 404 }).action, 'next');
  assert.equal(ladderFor(3, { canReresolve: true }).fail({ status: 403 }).action, 'reresolve');
  assert.equal(ladderFor(3, { canReresolve: true }).fail({ status: 404, started: true }).action, 'reresolve');
});

test('a dead live channel fails fast without re-resolving, but one that was playing re-resolves', () => {
  assert.equal(ladderFor(2, { canReresolve: true, isLive: true }).fail({}).action, 'next');
  assert.equal(ladderFor(2, { canReresolve: true, isLive: true }).fail({ started: true }).action, 'reresolve');
});

test('webOS: native HLS retries the same source with hls.js once, only where the provider allows', () => {
  const env = { isWebOS: true, mse: true };
  const l = ladderFor(2, { env, hlsjsAllowed: true }, 'hls');
  assert.equal(l.engine(), 'native');
  assert.deepEqual(l.fail({}), { action: 'hlsjs', index: 0 });
  assert.equal(l.engine(), 'hlsjs');
  assert.deepEqual(l.fail({}), { action: 'next', index: 1 });
  assert.equal(l.engine(), 'native');

  const denied = ladderFor(2, { env, hlsjsAllowed: false }, 'hls');
  assert.equal(denied.fail({}).action, 'next');
  const noMse = ladderFor(2, { env: { isWebOS: true }, hlsjsAllowed: true }, 'hls');
  assert.equal(noMse.fail({}).action, 'next');
  const files = ladderFor(2, { env, hlsjsAllowed: true }, 'mp4');
  assert.equal(files.fail({}).action, 'next');
});

test('adopt: fresh links keep the same quality; the same dead link moves on', () => {
  const src = (u, q) => eligible({ videoSources: [{ videoUrl: u, quality: q, type: 'mp4' }] }, 'uzbeklar').sources[0];
  const first = [src('https://x.test/1a.mp4', 'q1'), src('https://x.test/2a.mp4', 'q2')];

  const fresh = Playback.createLadder(first, 1, { canReresolve: true, env: {} });
  fresh.fail({});
  assert.deepEqual(fresh.adopt([src('https://x.test/1b.mp4', 'q1'), src('https://x.test/2b.mp4', 'q2')], {}), { action: 'retry', index: 1 });
  assert.equal(fresh.current().url, 'https://x.test/2b.mp4');

  const cached = Playback.createLadder(first, 0, { canReresolve: true, env: {} });
  cached.fail({});
  assert.deepEqual(cached.adopt(first, { started: false }), { action: 'next', index: 1 });

  const midPlay = Playback.createLadder(first, 0, { canReresolve: true, env: {} });
  midPlay.fail({ started: true });
  assert.deepEqual(midPlay.adopt(first, { started: true }), { action: 'retry', index: 0 });

  const gone = Playback.createLadder(first, 0, { canReresolve: true, env: {} });
  gone.fail({});
  assert.deepEqual(gone.adopt([src('https://x.test/2c.mp4', 'q2')], {}), { action: 'next', index: 0 });
  assert.equal(gone.current().url, 'https://x.test/2c.mp4');
});

test('a viewer choice or a long healthy stretch gives a fresh ladder', () => {
  const l = ladderFor(4, { canReresolve: true });
  l.fail({});
  l.fail({});
  l.fail({});
  l.select(3);
  assert.equal(l.current().url, 'https://cdn.test/s3.mp4');
  assert.equal(l.fail({}).action, 'reresolve');

  const h = ladderFor(4);
  h.fail({});
  h.fail({});
  h.healthy();
  assert.deepEqual(h.fail({}), { action: 'next', index: 3 });
  assert.deepEqual(h.fail({}), { action: 'next', index: 0 });
});

test('resume: small positions and the end of a title start over', () => {
  assert.equal(Playback.resumeAt(0, 1000), 0);
  assert.equal(Playback.resumeAt(4, 1000), 0);
  assert.equal(Playback.resumeAt(300, 1000), 300);
  assert.equal(Playback.resumeAt(960, 1000), 0, 'past 95%');
  assert.equal(Playback.resumeAt(5000, 5040), 0, 'fewer than 30 s left');
  assert.equal(Playback.resumeAt(300, 0), 300, 'duration unknown keeps the position');
  assert.equal(Playback.resumeAt('x', NaN), 0);
});

function memoryStore() {
  const data = {};
  return Store.create({ getItem: (k) => (k in data ? data[k] : null), setItem: (k, v) => { data[k] = v; }, removeItem: (k) => { delete data[k]; } });
}

test('save rules: a few seconds of another episode never wipes the place in a series', () => {
  const store = memoryStore();
  const base = { provider: 'uzbeklar', url: 'u', title: 'T', isSerial: true, duration: 1000 };
  store.saveProgress(Object.assign({}, base, { position: 500, episode: { mediaRef: 'e5', episode: 5 } }));
  const entry = store.progress('uzbeklar', 'u');
  assert.equal(Playback.shouldWrite(3, 1000, entry, true), false);
  assert.equal(Playback.shouldWrite(6, 1000, entry, true), true);
  assert.equal(Playback.shouldWrite(990, 1000, entry, true), true);
  assert.equal(store.progress('uzbeklar', 'u').position, 500);
});

test('save rules: a film rewound to the start clears its entry, and a new film needs five seconds', () => {
  assert.equal(Playback.shouldWrite(3, 1000, { position: 500 }, false), true);
  assert.equal(Playback.shouldWrite(3, 1000, null, false), false);
  assert.equal(Playback.shouldWrite(5, 1000, null, false), true);
  assert.equal(Playback.shouldWrite(3, 0, null, false), false);
});

test('saved progress resumes through the same rules the detail screen reads', () => {
  const store = memoryStore();
  const eps = [{ episode: 1, mediaRef: 'a', season: 1 }, { episode: 2, mediaRef: 'b', season: 1 }];
  const base = { provider: 'uzbeklar', url: 'u', title: 'T', isSerial: true, duration: 1000 };
  store.saveProgress(Object.assign({}, base, { position: 1000, episode: eps[0] }));
  assert.equal(Model.resumeTarget(eps, store.progress('uzbeklar', 'u')).episode.mediaRef, 'b');
  store.saveProgress(Object.assign({}, base, { position: 120, episode: eps[1] }));
  const t = Model.resumeTarget(eps, store.progress('uzbeklar', 'u'));
  assert.equal(t.episode.mediaRef, 'b');
  assert.equal(t.position, 120);
});

test('seeking accelerates with a held key and stays inside the title', () => {
  const steps = [1, 3, 4, 8, 9, 16, 17, 30, 31].map(Playback.seekStep);
  assert.deepEqual(steps, [10, 10, 20, 20, 30, 30, 60, 60, 120]);
  assert.equal(Playback.seekTarget(5, -10, 100), 0);
  assert.equal(Playback.seekTarget(95, 30, 100), 98);
  assert.equal(Playback.seekTarget(50, 10, 100), 60);
  assert.equal(Playback.seekTarget(1, 10, 2), 0);
});

test('next episode follows season order, skips ones without a reference, and ends cleanly', () => {
  const eps = [
    { episode: 1, season: 2, mediaRef: 's2e1' }, { episode: 1, season: 1, mediaRef: 's1e1' },
    { episode: 2, season: 1, mediaRef: 's1e2' }, { episode: 3, season: 1, mediaRef: '' }, { episode: 2, season: 2, mediaRef: 's2e2' }
  ];
  assert.equal(Playback.nextEpisode(eps, eps[1]).mediaRef, 's1e2');
  assert.equal(Playback.nextEpisode(eps, eps[2]).mediaRef, 's2e1');
  assert.equal(Playback.nextEpisode(eps, eps[4]), null);
  assert.equal(Playback.nextEpisode(eps, null), null);
  assert.equal(Playback.nextEpisode(eps, { mediaRef: 'nope' }), null);
});

test('local addresses in any spelling a browser accepts are rejected', () => {
  const urls = ['http://127.1/a.mp4', 'http://2130706433/a.mp4', 'http://0x7f.0.0.1/a.mp4', 'http://0177.0.0.1/a.mp4', 'http://localhost./a.mp4',
    'http://192.168.1.1\\foo.m3u8', 'http://127.0.\n0.1/a.mp4', 'http://[0:0:0:0:0:0:0:1]/a.mp4', 'http://%31%32%37.0.0.1/a.mp4', 'http://a@b@cdn.test/a.mp4'];
  for (const u of urls) {
    const r = Playback.eligible({ videoSources: [{ videoUrl: u, type: 'mp4' }] }, 'uzbeklar');
    assert.equal(r.sources.length, 0, u);
  }
  assert.equal(Playback.eligible({ videoSources: [{ videoUrl: 'https://8.8.8.8/a.mp4', type: 'mp4' }] }, 'uzbeklar').sources.length, 1);
  assert.equal(Playback.eligible({ videoSources: [{ videoUrl: 'https://1.example.com/a.mp4', type: 'mp4' }] }, 'uzbeklar').sources.length, 1);
});

test('hls.js gets limits sized for a TV, which the build would otherwise leave at desktop values', () => {
  const c = Playback.hlsConfig(-1);
  assert.equal(c.backBufferLength, 30);
  assert.equal(c.maxBufferSize, 30000000);
  assert.equal(c.maxMaxBufferLength, 60);
  assert.equal(c.enableWorker, false);
  assert.equal(c.abrEwmaDefaultEstimate, 5000000);
  assert.equal(c.startPosition, -1);
  assert.equal(Playback.hlsConfig(12).startPosition, 12);
});
