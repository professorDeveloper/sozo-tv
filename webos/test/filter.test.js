'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { Filter, fixture } = require('./helper.js');

test('blocks the adult titles measured on production', () => {
  for (const t of ['MILF Juggs 2 (2021) +18 XXX', 'Mommy XXX', 'X.X.X: Uncensored', 'Erotic Ghost Story 1990', 'Monica Sex', 'Music Box Sexy (CZ)', 'Хентай', 'эротика', 'секс', 'kattalar uchun', '18+ only']) {
    assert.equal(Filter.isAdultText(t), true, t);
  }
});

test('lets ordinary titles through, and does not match years', () => {
  for (const t of ['Muhabbat gipotezasi', "A Dog's Perfect Christmas", 'Аой Ашито 2', 'Blade Runner 2049', 'Top 18 Goals', 'Sussex Gardens', 'Essex']) {
    assert.equal(Filter.isAdultText(t), false, t);
  }
});

test('text that only reads as adult after cleaning is caught, in the form it would be shown', () => {
  for (const t of ['18&nbsp;+', 'P&#111;rn Star', '&#72;entai Girls', 'Эро&#1090;ика', '18<b>+</b>', 'Kattalar  uchun', 'kattalar\u00a0uchun', 'H<b>en</b>tai']) {
    assert.equal(Filter.isAdultText(t), true, t);
  }
  assert.deepEqual(Filter.cards([{ title: 'P&#111;rn Star', contentUrl: 'u' }], 'a'), []);
  assert.equal(Filter.isAdultDetail({ title: 'x', description: '&#72;entai' }), true);
});

test('Russian and Uzbek age wording and compounds are caught', () => {
  for (const t of ['Только для взрослых', 'Контент для  взрослых', 'только взрослым', 'взрослое кино', '18 лет', '18 yosh', '18 yoshdan', 'softporn', 'ХХХ', 'Фильм ххх 2', 'катталар учун']) {
    assert.equal(Filter.isAdultText(t), true, t);
  }
});

test('ordinary Russian about growing up stays: measured on live anilibria and animevost pages', () => {
  for (const t of ['Повзрослев, он покинул родные края', 'непростой путь взросления', 'готовят подростков ко взрослой жизни', 'Лестница во взрослую жизнь', 'меняются, взрослеют', 'Мне 2018 лет', 'Top 18 Goals', 'Хроники 2018 yosh']) {
    assert.equal(Filter.isAdultText(t), false, t);
  }
});

test('null and odd values are safe', () => {
  for (const v of [null, undefined, 0, 12, true, {}, []]) assert.equal(Filter.isAdultText(v), false);
});

test('cards drop adult, foreign and url-less cards and duplicates', () => {
  const list = [
    { provider: 'a', title: 'Good', contentUrl: 'u1' },
    { provider: 'a', title: 'Good again', contentUrl: 'u1' },
    { provider: 'a', title: 'Bad XXX', contentUrl: 'u2' },
    { provider: 'b', title: 'Foreign', contentUrl: 'u3' },
    { provider: 'a', title: 'No url' },
    { provider: 'a', slug: 'only-slug' },
    { provider: 'a', title: 'Clean', description: 'Erotic thriller', contentUrl: 'u4' },
    { provider: 'a', title: 'Genre', genres: ['Hentai'], contentUrl: 'u5' },
    { provider: 'a', title: 'Alt', altTitles: ['Porn thing'], contentUrl: 'u6' },
    { provider: 'a', title: 'Cat', category: 'adult', contentUrl: 'u7' },
    { provider: 'a', title: 'Url', contentUrl: 'https://x/watch/sexy-film' },
    null, 7
  ];
  const urls = Filter.cards(list, 'a').map((c) => c.contentUrl || c.slug);
  assert.deepEqual(urls, ['u1', 'only-slug']);
});

test('sections drop adult labels and empty sections', () => {
  const out = Filter.sections([
    { key: 'a', label: 'Fine', items: [{ title: 'x', contentUrl: 'u' }] },
    { key: 'b', label: 'Adult', items: [{ title: 'y', contentUrl: 'v' }] },
    { key: 'c', label: 'Empty after filter', items: [{ title: 'XXX', contentUrl: 'w' }] }
  ], 'p');
  assert.deepEqual(out.map((s) => s.key), ['a']);
});

test('category labels and episodes are filtered', () => {
  assert.deepEqual(Filter.labels([{ slug: 'erotika', label: 'Erotic' }, { slug: 'drama', label: 'Drama' }, { label: 'noslug' }]).map((x) => x.slug), ['drama']);
  assert.equal(Filter.episodes([{ label: 'Ep 1' }, { label: 'Uncensored cut' }, { overview: 'порно' }]).length, 1);
});

test('real fixtures survive the filter', () => {
  const home = fixture('home_vidapi');
  assert.ok(Filter.sections(home.sections, 'vidapi').length > 0);
  assert.ok(Filter.cards(fixture('latest_uzbeklar').items, 'uzbeklar').length > 0);
  assert.equal(Filter.isAdultDetail(fixture('detail_anilibria')), false);
});
