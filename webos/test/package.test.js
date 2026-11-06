'use strict';
const test = require('node:test');
const assert = require('node:assert');
const crypto = require('crypto');
const { verify, readAr, readTar } = require('../tools/package.js');

const ID = 'com.example.app';
const PREFIX = 'usr/palm/applications/' + ID + '/';
const body = Buffer.from('var a = 1;\n');
const hash = crypto.createHash('sha256').update(body).digest('hex');

function tarEntry(name, data) {
  const h = Buffer.alloc(512);
  h.write(name, 0);
  h.write(data.length.toString(8).padStart(11, '0'), 124);
  h[156] = 48;
  const pad = Buffer.alloc((512 - (data.length % 512)) % 512);
  return Buffer.concat([h, data, pad]);
}

test('verify accepts exactly the shipped files and packageinfo.json', () => {
  const files = { [PREFIX + 'a.js']: body, ['usr/palm/packages/' + ID + '/packageinfo.json']: Buffer.from('{}') };
  assert.deepStrictEqual(verify(files, ID, { 'a.js': hash }), []);
});

test('verify rejects dev files, stray files, missing files and changed bytes', () => {
  const cases = [
    [{ [PREFIX + 'a.js']: body, [PREFIX + 'test/x.test.js']: body }, /forbidden file: test\/x\.test\.js/],
    [{ [PREFIX + 'a.js']: body, [PREFIX + 'node_modules/x/i.js']: body }, /forbidden file: node_modules/],
    [{ [PREFIX + 'a.js']: body, [PREFIX + 'package.json']: body }, /forbidden file: package\.json/],
    [{ [PREFIX + 'a.js']: body, [PREFIX + 'b.js']: body }, /not a shipped file: b\.js/],
    [{ [PREFIX + 'a.js']: body, 'etc/x': body }, /unexpected path: etc\/x/],
    [{}, /missing: a\.js/],
    [{ [PREFIX + 'a.js']: Buffer.from('var a=1;') }, /content changed: a\.js/]
  ];
  for (const [files, want] of cases) assert.match(verify(files, ID, { 'a.js': hash }).join('\n'), want);
});

test('verify checks a packaged service byte for byte, and only the services it was told about', () => {
  const SVC = 'usr/palm/services/' + ID + '.service/';
  const svc = { [ID + '.service']: { 'service.js': hash } };
  const base = { [PREFIX + 'a.js']: body };
  assert.deepStrictEqual(verify({ ...base, [SVC + 'service.js']: body }, ID, { 'a.js': hash }, svc), []);
  assert.match(verify(base, ID, { 'a.js': hash }, svc).join('\n'), /missing: com\.example\.app\.service\/service\.js/);
  assert.match(verify({ ...base, [SVC + 'service.js']: Buffer.from('x') }, ID, { 'a.js': hash }, svc).join('\n'), /content changed: .*service\.js/);
  assert.match(verify({ ...base, [SVC + 'service.js']: body, [SVC + 'extra.js']: body }, ID, { 'a.js': hash }, svc).join('\n'), /not a shipped service file: .*extra\.js/);
  assert.match(verify({ ...base, [SVC + 'service.js']: body }, ID, { 'a.js': hash }).join('\n'), /unexpected path: usr\/palm\/services/);
});

test('readTar and readAr read names and bytes back', () => {
  const tar = Buffer.concat([tarEntry('./usr/a.js', body), tarEntry('./usr/b.txt', Buffer.alloc(513, 97)), Buffer.alloc(1024)]);
  const files = readTar(tar);
  assert.deepStrictEqual(Object.keys(files), ['usr/a.js', 'usr/b.txt']);
  assert.ok(files['usr/a.js'].equals(body));
  assert.strictEqual(files['usr/b.txt'].length, 513);

  const member = (name, data) => {
    const h = Buffer.alloc(60, 32);
    h.write(name, 0);
    h.write(String(data.length), 48);
    return Buffer.concat([h, data, Buffer.alloc(data.length % 2, 10)]);
  };
  const ar = readAr(Buffer.concat([Buffer.from('!<arch>\n'), member('one/', Buffer.from('abc')), member('two', Buffer.from('de'))]));
  assert.strictEqual(ar.one.toString(), 'abc');
  assert.strictEqual(ar.two.toString(), 'de');
  assert.throws(() => readAr(Buffer.from('nope')), /not an ar archive/);
});
