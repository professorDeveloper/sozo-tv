'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { Model, I18n, fixture } = require('./helper.js');

test('nulls render as absent, never as "null"', () => {
  assert.equal(Model.str(null), '');
  assert.equal(Model.str('null'), '');
  assert.equal(Model.str(undefined), '');
  assert.equal(Model.str(5), '5');
  assert.equal(Model.str(NaN), '');
  assert.equal(Model.cleanText(null), '');
});

test('cleanText strips markup and decodes entities', () => {
  assert.equal(Model.cleanText('<p>Hello &amp; <b>bye</b></p>'), 'Hello & bye');
  assert.equal(Model.cleanText('a\r\n\r\nb'), 'a\nb');
  assert.equal(Model.cleanText('&#39;x&#39;'), "'x'");
  assert.equal(Model.cleanText('<script>alert(1)</script>'), 'alert(1)');
});

test('year accepts number or string and rejects junk', () => {
  assert.equal(Model.year(2026), '2026');
  assert.equal(Model.year('2026'), '2026');
  assert.equal(Model.year(0), '');
  assert.equal(Model.year('null'), '');
  assert.equal(Model.year('abcd'), '');
});

test('rating hides zero', () => {
  assert.equal(Model.rating(0), '');
  assert.equal(Model.rating(8.34), '8.3');
  assert.equal(Model.rating(null), '');
});

test('images: only http(s), and TMDB is downsized', () => {
  assert.equal(Model.safeImage('javascript:alert(1)'), '');
  assert.equal(Model.safeImage('data:image/png;base64,xx'), '');
  assert.equal(Model.safeImage('//x.com/a.jpg'), 'https://x.com/a.jpg');
  assert.equal(Model.safeImage(null), '');
  assert.equal(Model.posterUrl('https://image.tmdb.org/t/p/w500/a.jpg'), 'https://image.tmdb.org/t/p/w342/a.jpg');
  assert.equal(Model.bannerUrl('https://image.tmdb.org/t/p/original/a.jpg'), 'https://image.tmdb.org/t/p/w1280/a.jpg');
});

test('toggleSlash works both ways and skips non-http refs', () => {
  assert.equal(Model.toggleSlash('https://a/b'), 'https://a/b/');
  assert.equal(Model.toggleSlash('https://a/b/'), 'https://a/b');
  assert.equal(Model.toggleSlash('worldlivetv:x'), null);
  assert.equal(Model.toggleSlash('https://a/b?x=1'), null);
});

test('listPath allows known types only and encodes slugs', () => {
  assert.equal(Model.listPath('latest'), 'latest');
  assert.equal(Model.listPath('category', 'popular'), 'category/popular');
  assert.equal(Model.listPath('year', 2026), 'year/2026');
  assert.equal(Model.listPath('genre', 'a/b'), 'genre/a%2Fb');
  assert.equal(Model.listPath('category'), null);
  assert.equal(Model.listPath('extractors', 'x'), null);
  assert.equal(Model.listPath('constructor', 'x'), null);
});

test('names read strings, objects and comma lists', () => {
  assert.deepEqual(Model.nameList(['A', { name: 'B' }, { label: 'C' }, null]), ['A', 'B', 'C']);
  assert.deepEqual(Model.nameList('A, B'), ['A', 'B']);
  assert.deepEqual(Model.nameList(null), []);
});

test('seasons group and sort, tolerating strings and missing values', () => {
  const g = Model.groupSeasons([{ episode: 1, season: 2 }, { episode: 1, season: '1' }, { episode: 2, seasonNumber: 1 }, { episode: 9 }]);
  assert.deepEqual(g.map((x) => x.season), [1, 2, null]);
  assert.equal(g[0].episodes.length, 2);
  assert.deepEqual(Model.groupSeasons(null), []);
  assert.equal(Model.groupSeasons(fixture('episodes_anilibria').episodes).length, 1);
  assert.equal(Model.episodeNumber({ episode: 12.5 }), '12.5');
});

test('film envelope detection follows the contract', () => {
  assert.equal(Model.isFilmEnvelope(fixture('episodes_vidapi_movie')), true);
  assert.equal(Model.isFilmEnvelope(fixture('episodes_anilibria')), false);
  assert.equal(Model.isFilmEnvelope({ isSerial: false, videoSources: [] }), false);
  assert.equal(Model.isFilmEnvelope(null), false);
});

test('paging helpers', () => {
  assert.equal(Model.hasMore(1, 3), true);
  assert.equal(Model.hasMore(3, 3), false);
  assert.equal(Model.hasMore(undefined, undefined), false);
  const merged = Model.mergeCards([{ contentUrl: 'a' }], [{ contentUrl: 'a' }, { contentUrl: 'b' }]);
  assert.equal(merged.length, 2);
});

test('safeImage refuses hosts on the viewer own network and user info', () => {
  for (const u of ['http://192.168.1.1/a.png', 'http://127.1/a.png', 'http://user:p@x.com/a.png', 'http://localhost./a.png', 'http://192.168.1.1\\a.png']) {
    assert.equal(Model.safeImage(u), '', u);
  }
  assert.equal(Model.safeImage('https://img.test/a.png'), 'https://img.test/a.png');
});

test('hostile strings clean in linear time', () => {
  const n = 2 * 1024 * 1024;
  for (const s of ['<'.repeat(n), ' '.repeat(n) + 'x', '\f'.repeat(n) + 'x', '<a'.repeat(n / 2)]) {
    const t = Date.now();
    Model.cleanText(s);
    assert.ok(Date.now() - t < 1500, 'took ' + (Date.now() - t) + ' ms');
  }
});

test('resumeTarget with later pages to come: a missing or last-finished episode stays open', () => {
  const eps = [{ mediaRef: 'a', episode: 1 }, { mediaRef: 'b', episode: 2 }];
  assert.equal(Model.resumeTarget(eps, { episode: { mediaRef: 'z' }, position: 50 }, true).pending, true);
  assert.equal(Model.resumeTarget(eps, { episode: { mediaRef: 'b' }, finished: true }, true).pending, true);
  assert.equal(Model.resumeTarget(eps, { episode: { mediaRef: 'b' }, position: 50 }, true).index, 1);
  assert.equal(Model.resumeTarget(eps, { episode: { mediaRef: 'a' }, finished: true }, true).index, 1);
  assert.equal(Model.resumeTarget(eps, null, true).index, 0);
  assert.equal(Model.resumeTarget(eps, { episode: { mediaRef: 'z' }, position: 50 }).index, 0);
});

test('seriesDone: only a finished last episode', () => {
  const eps = [{ mediaRef: 'a' }, { mediaRef: 'b' }];
  assert.equal(Model.seriesDone(eps, { finished: true, episode: { mediaRef: 'b' } }), true);
  assert.equal(Model.seriesDone(eps, { finished: true, episode: { mediaRef: 'a' } }), false);
  assert.equal(Model.seriesDone(eps, { finished: false, episode: { mediaRef: 'b' } }), false);
  assert.equal(Model.seriesDone(eps, null), false);
  assert.equal(Model.seriesDone([], { finished: true, episode: { mediaRef: 'b' } }), false);
});

test('resumeTarget: start, resume, next after finishing, restart after the last', () => {
  const eps = [{ mediaRef: 'a', episode: 1 }, { mediaRef: 'b', episode: 2 }];
  assert.equal(Model.resumeTarget([], null), null);
  assert.equal(Model.resumeTarget(eps, null).index, 0);
  const r = Model.resumeTarget(eps, { episode: { mediaRef: 'b' }, position: 120 });
  assert.deepEqual([r.index, r.position, r.resume], [1, 120, true]);
  const n = Model.resumeTarget(eps, { episode: { mediaRef: 'a' }, finished: true });
  assert.deepEqual([n.index, n.position, n.resume], [1, 0, true]);
  const l = Model.resumeTarget(eps, { episode: { mediaRef: 'b' }, finished: true });
  assert.deepEqual([l.index, l.resume], [0, false]);
  assert.equal(Model.resumeTarget(eps, { episode: { mediaRef: 'gone' }, position: 5 }).index, 0);
});

test('clock and ratio', () => {
  assert.equal(Model.clock(65), '1:05');
  assert.equal(Model.clock(3725), '1:02:05');
  assert.equal(Model.clock(null), '0:00');
  assert.equal(Model.progressRatio(50, 100), 0.5);
  assert.equal(Model.progressRatio(50, 0), 0);
  assert.equal(Model.progressRatio(500, 100), 1);
});

test('every language has every key and the same placeholders', () => {
  const en = I18n.STRINGS.en;
  for (const lang of I18n.LANGS) {
    for (const k of Object.keys(en)) {
      assert.ok(I18n.STRINGS[lang][k], lang + ' misses ' + k);
      const vars = (s) => (s.match(/\{\w+\}/g) || []).sort().join();
      assert.equal(vars(I18n.STRINGS[lang][k]), vars(en[k]), lang + ' ' + k);
    }
    assert.equal(Object.keys(I18n.STRINGS[lang]).length, Object.keys(en).length);
  }
});

test('language comes from the TV with an override, and unknown falls back to English', () => {
  assert.equal(I18n.detect('uz-UZ'), 'uz');
  assert.equal(I18n.detect('ru'), 'ru');
  assert.equal(I18n.detect('ko-KR'), 'en');
  assert.equal(I18n.detect(undefined), 'en');
  assert.equal(I18n.resolve('ru', 'en-US'), 'ru');
  assert.equal(I18n.resolve('auto', 'uz'), 'uz');
  I18n.set('uz');
  assert.equal(I18n.t('tab.search'), 'Qidiruv');
  assert.equal(I18n.t('settings.version', { v: '1' }), 'Versiya 1');
  assert.equal(I18n.t('nope'), 'nope');
  I18n.set('en');
});
