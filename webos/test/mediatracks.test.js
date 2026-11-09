'use strict';
const test = require('node:test');
const assert = require('node:assert');
const MediaTracks = require('../js/lib/mediatracks.js');

test('langCode reads two- and three-letter codes and regions', () => {
  assert.equal(MediaTracks.langCode('en'), 'en');
  assert.equal(MediaTracks.langCode('eng'), 'en');
  assert.equal(MediaTracks.langCode('hin'), 'hi');
  assert.equal(MediaTracks.langCode('en-US'), 'en');
  assert.equal(MediaTracks.langCode('spa'), 'es');
  assert.equal(MediaTracks.langCode('ger'), 'de');
  for (const bad of ['', 'und1', 'x', null, undefined, 5]) assert.equal(MediaTracks.langCode(bad), '', String(bad));
});

test('pickAudio: saved language, then UI language, then English, else leave it', () => {
  const langs = ['hi', 'en', 'ta', 'ru'];
  assert.equal(MediaTracks.pickAudio(langs, 'ta', 'ru'), 2);
  assert.equal(MediaTracks.pickAudio(langs, null, 'ru'), 3);
  assert.equal(MediaTracks.pickAudio(langs, null, 'uz'), 1);
  assert.equal(MediaTracks.pickAudio(['hin', 'eng'], '', 'uz'), 1);
  assert.equal(MediaTracks.pickAudio(['hi', 'ta'], null, 'uz'), -1);
  assert.equal(MediaTracks.pickAudio([], 'en', 'en'), -1);
});

test('subtitleChoices labels by language and name, numbers repeats and skips junk', () => {
  const out = MediaTracks.subtitleChoices([
    { index: 0, language: 'eng', name: 'SDH', codec: 'S_TEXT/UTF8' },
    { index: 1, language: 'en', name: 'English (SDH)' },
    { index: 2, language: 'hin', name: 'Forced', forced: true },
    { index: 3, language: 'hi', name: '' },
    { index: 4, language: 'hi' },
    { index: 5, language: 'xx-YY', name: '<b>x</b>' },
    { index: 6 },
    { index: -1, language: 'en' }, { index: 1.5 }, { language: 'en' }, null
  ]);
  assert.deepEqual(out.map((t) => [t.index, t.label, t.base, t.forced]), [
    [0, 'English · SDH', 'embedded:en', false],
    [1, 'English (SDH)', 'embedded:en', false],
    [2, 'Hindi · Forced', 'embedded:hi', true],
    [3, 'Hindi', 'embedded:hi', false],
    [4, 'Hindi (2)', 'embedded:hi', false],
    [5, 'XX · bx/b', 'embedded:xx', false],
    [6, 'Track 7', 'embedded:und', false]
  ]);
  assert.deepEqual(MediaTracks.subtitleChoices(undefined), []);
});

test('pickSubtitle takes the remembered language, a full track before a forced one', () => {
  const list = MediaTracks.subtitleChoices([{ index: 0, language: 'hi', forced: true }, { index: 1, language: 'hi' }, { index: 2, language: 'en' }]);
  assert.equal(MediaTracks.pickSubtitle(list, 'embedded:hi').index, 1);
  assert.equal(MediaTracks.pickSubtitle(list, 'embedded:en').index, 2);
  assert.equal(MediaTracks.pickSubtitle(list.slice(0, 1), 'embedded:hi').index, 0);
  assert.equal(MediaTracks.pickSubtitle(list, 'off'), null);
  assert.equal(MediaTracks.pickSubtitle(list, null), null);
});

function fakeBridge(calls) {
  return function Bridge() {
    this.call = (uri, params) => { calls.push([uri.replace('luna://com.webos.media/', ''), JSON.parse(params)]); };
  };
}

function fakeXhr(reqs) {
  return function () {
    const x = { open: (m, u) => { x.url = u; }, send: () => reqs.push(x), abort: () => { x.aborted = true; } };
    return x;
  };
}

test('load reads the relay list once, then selectText drives the pipeline for that mediaId', () => {
  const calls = [];
  const reqs = [];
  const t = MediaTracks.create({ isWebOS: true, Bridge: fakeBridge(calls), xhr: fakeXhr(reqs) });
  const got = [];
  t.load('_m1', 'http://127.0.0.1:1/tok/t?u=x', (list) => got.push(list));
  assert.equal(reqs.length, 1);
  assert.equal(reqs[0].url, 'http://127.0.0.1:1/tok/t?u=x');
  reqs[0].status = 200;
  reqs[0].responseText = JSON.stringify({ audio: [], subtitles: [{ index: 0, language: 'en' }] });
  reqs[0].onload();
  reqs[0].onload && reqs[0].onload();
  assert.equal(got.length, 1);
  assert.equal(got[0][0].label, 'English');
  t.selectText(0);
  t.selectText(null);
  assert.deepEqual(calls, [
    ['selectTrack', { mediaId: '_m1', type: 'text', index: 0 }],
    ['setSubtitleEnable', { mediaId: '_m1', enable: true }],
    ['setSubtitleEnable', { mediaId: '_m1', enable: false }]
  ]);
  t.stop();
  t.selectText(0);
  assert.equal(calls.length, 3, 'nothing after stop');
});

test('a failed, bad or replaced list request gives [] or nothing, never throws', () => {
  const reqs = [];
  const t = MediaTracks.create({ isWebOS: true, Bridge: fakeBridge([]), xhr: fakeXhr(reqs) });
  const got = [];
  t.load('_m1', 'u1', (l) => got.push(['a', l]));
  t.load('_m2', 'u2', (l) => got.push(['b', l]));
  assert.equal(reqs[0].aborted, true, 'replaced request is aborted');
  reqs[1].status = 200;
  reqs[1].responseText = 'not json';
  reqs[1].onload();
  t.load('_m3', 'u3', (l) => got.push(['c', l]));
  reqs[2].onerror();
  assert.deepEqual(got, [['b', []], ['c', []]]);
});

test('off webOS, or without a mediaId or URL, nothing is requested or called', () => {
  const calls = [];
  const reqs = [];
  MediaTracks.create({ isWebOS: false, Bridge: fakeBridge(calls), xhr: fakeXhr(reqs) }).load('_m1', 'u', () => {});
  const t = MediaTracks.create({ isWebOS: true, Bridge: fakeBridge(calls), xhr: fakeXhr(reqs) });
  t.load('', 'u', () => {});
  t.load('_m1', null, () => {});
  t.selectText(1);
  assert.equal(reqs.length + calls.length, 0);
});
