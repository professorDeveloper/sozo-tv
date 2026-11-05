'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { Subtitles, I18n } = require('./helper.js');

const SRT = '1\r\n00:00:01,000 --> 00:00:03,500\r\nHello <i>there</i>\r\nsecond line\r\n\r\n2\r\n00:00:05,000 --> 00:00:06,000\r\n{\\an8}Top &amp; tail\r\n\r\n';
const VTT = 'WEBVTT\n\nNOTE a comment --> not a cue\n\nSTYLE\n::cue { color: red }\n\ncue-1\n00:01.000 --> 00:02.000 align:start line:0\n<v Bob>Hi</v>\n\n01:00:00.500 --> 01:00:01.000\nLate\n';

test('SRT parses, with times, line breaks and markup removed', () => {
  const cues = Subtitles.parse(SRT);
  assert.equal(cues.length, 2);
  assert.deepEqual(cues[0], { start: 1, end: 3.5, text: 'Hello there\nsecond line' });
  assert.equal(cues[1].text, 'Top & tail');
});

test('WebVTT parses, ignoring headers, comments, styles and cue settings', () => {
  const cues = Subtitles.parse(VTT);
  assert.equal(cues.length, 2);
  assert.deepEqual(cues[0], { start: 1, end: 2, text: 'Hi' });
  assert.equal(cues[1].start, 3600.5);
});

test('a BOM and short timestamps are fine; bad cues are skipped, not fatal', () => {
  const cues = Subtitles.parse('﻿1\n00:00:01,5 --> 00:00:02,0\nA\n\n2\nnot a time --> 00:00:09,000\nB\n\n3\n00:00:09,000 --> 00:00:08,000\nC\n');
  assert.equal(cues.length, 1);
  assert.equal(cues[0].start, 1.5);
});

test('web pages, empty and oversized bodies are rejected', () => {
  assert.equal(Subtitles.parse('<!DOCTYPE html><html><body>00:00:01,000 --> 00:00:02,000</body></html>'), null);
  assert.equal(Subtitles.parse('<html>x</html>'), null);
  assert.equal(Subtitles.parse(''), null);
  assert.equal(Subtitles.parse('WEBVTT\n\n'), null);
  assert.equal(Subtitles.parse(null), null);
  assert.equal(Subtitles.parse('x'.repeat(Subtitles.MAX_BYTES + 1)), null);
});

test('script-like cue text comes out as plain text with no tags', () => {
  const cues = Subtitles.parse('1\n00:00:01,000 --> 00:00:02,000\n<script>alert(1)</script><b onclick="x">Hi</b>\n');
  assert.equal(/[<>]/.test(cues[0].text), false);
});

test('textAt finds cues by time, joins overlaps, and is empty in gaps', () => {
  const cues = Subtitles.parse('1\n00:00:01,000 --> 00:00:05,000\nA\n\n2\n00:00:03,000 --> 00:00:04,000\nB\n\n3\n00:00:10,000 --> 00:00:11,000\nC\n');
  assert.equal(Subtitles.textAt(cues, 0), '');
  assert.equal(Subtitles.textAt(cues, 2), 'A');
  assert.equal(Subtitles.textAt(cues, 3.5), 'A\nB');
  assert.equal(Subtitles.textAt(cues, 7), '');
  assert.equal(Subtitles.textAt(cues, 10.5), 'C');
  assert.equal(Subtitles.textAt(null, 1), '');
});

const sub = (label, extra) => Object.assign({ label, file: 'https://subs.test/' + label }, extra);

test('tracks with headers, a cipher, an m3u8 or no usable url are skipped', () => {
  const list = Subtitles.eligible({ subtitles: [
    sub('A'), sub('B', { headers: { Referer: 'x' } }), sub('C', { cipher: 'k' }), { label: 'D', file: 'https://s.test/d.m3u8' },
    { label: 'E', file: 'file:///etc/passwd' }, { label: 'F' }, null, sub('G', { headers: {} })
  ] }, 'en');
  assert.deepEqual(list.map((t) => t.label), ['A', 'G']);
});

test('url, src, lang and language also work; codes become names; repeats are numbered and de-duplicated', () => {
  const list = Subtitles.eligible({ subtitles: [
    { url: 'https://s.test/1', lang: 'pob' }, { src: 'https://s.test/2', language: 'spa' },
    { file: 'https://s.test/3', label: 'Spanish' }, { file: 'https://s.test/3', label: 'Spanish again' }
  ] }, 'en');
  assert.deepEqual(list.map((t) => t.label), ['Portuguese (Brazil)', 'Spanish', 'Spanish 2']);
});

test('the viewer language sorts first', () => {
  const list = Subtitles.eligible({ subtitles: [sub('French'), sub('Russian'), sub('English')] }, 'ru');
  assert.equal(list[0].label, 'Russian');
});

test('off by default, unless a default track matches the UI language or the viewer chose before', () => {
  const tracks = Subtitles.eligible({ subtitles: [sub('French', { default: true }), sub('English')] }, 'en');
  assert.equal(Subtitles.pickDefault(tracks, 'en', null), null);
  assert.equal(Subtitles.pickDefault(tracks, 'fr', null), null);
  const def = Subtitles.eligible({ subtitles: [sub('English', { default: true })] }, 'en');
  assert.equal(Subtitles.pickDefault(def, 'en', null).label, 'English');
  assert.equal(Subtitles.pickDefault(def, 'en', 'off'), null);
  assert.equal(Subtitles.pickDefault(tracks, 'en', 'French').label, 'French');
  assert.equal(Subtitles.pickDefault(tracks, 'en', 'Klingon'), null);
});

test('the three UI languages name their stubless player strings', () => {
  for (const lang of I18n.LANGS) {
    assert.ok(I18n.STRINGS[lang]['player.noSource.t']);
    assert.equal(I18n.STRINGS[lang]['player.stubTitle'], undefined);
  }
});

test('tracks on the viewer own network or with user info are not offered', () => {
  const list = Subtitles.eligible({ subtitles: [
    { file: 'http://192.168.1.1/admin.vtt', label: 'a' }, { file: 'http://user:p@x.com/a.vtt', label: 'b' },
    { file: 'http://127.1/a.vtt', label: 'c' }, { file: 'https://subs.test/a.vtt', label: 'd' }
  ] }, 'en');
  assert.deepEqual(list.map((t) => t.label), ['d']);
});

test('a hostile cue just under the size cap parses at once', () => {
  const head = '00:00:01.000 --> 00:00:02.000\n';
  for (const body of ['<'.repeat(Subtitles.MAX_BYTES - 100), '{\\'.repeat((Subtitles.MAX_BYTES - 100) / 2)]) {
    const t = Date.now();
    Subtitles.parse(head + body);
    assert.ok(Date.now() - t < 1500, 'took ' + (Date.now() - t) + ' ms');
  }
});
