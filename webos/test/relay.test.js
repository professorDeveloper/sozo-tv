'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const http = require('http');
const path = require('path');
const acorn = require('acorn');
const { ROOT } = require('./helper.js');
const proxy = require('../service/proxy.js');
const Relabel = require('../js/lib/relabel.js');

const TS = Buffer.concat([Buffer.from([0x47, 0x40, 0x00, 0x10]), Buffer.alloc(184, 0xff)]);

test('sniff names media by its bytes, not by a text/html label', () => {
  assert.equal(proxy.sniff(Buffer.from('#EXTM3U\n'), 'text/html'), proxy.PLAYLIST);
  assert.equal(proxy.sniff(TS, 'text/html; charset=UTF-8'), 'video/mp2t');
  assert.equal(proxy.sniff(Buffer.from('\0\0\0\x18ftypisom'), 'text/html'), 'video/mp4');
  assert.equal(proxy.sniff(Buffer.from('ID3\x04'), 'text/html'), 'audio/aac');
  assert.equal(proxy.sniff(Buffer.from('<html>'), 'text/html'), 'application/octet-stream');
  assert.equal(proxy.sniff(Buffer.from('WEBVTT'), 'text/vtt'), 'text/vtt');
});

test('rewritePlaylist sends every URI, relative or absolute, back through the relay', () => {
  const wrap = (u) => 'R(' + u + ')';
  const text = '#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI="key.bin"\n#EXT-X-MAP:URI="/init.mp4"\n#EXTINF:6,\nseg0.html\n\nhttps://cdn.test/seg1.html\n#EXT-X-ENDLIST';
  const out = proxy.rewritePlaylist(text, 'https://a.test/x/pl/index.m3u8', wrap).split('\n');
  assert.equal(out[1], '#EXT-X-KEY:METHOD=AES-128,URI="R(https://a.test/x/pl/key.bin)"');
  assert.equal(out[2], '#EXT-X-MAP:URI="R(https://a.test/init.mp4)"');
  assert.equal(out[4], 'R(https://a.test/x/pl/seg0.html)');
  assert.equal(out[5], '');
  assert.equal(out[6], 'R(https://cdn.test/seg1.html)');
  assert.equal(out[7], '#EXT-X-ENDLIST');
});

test('isUpstream takes only http(s) URLs with a host', () => {
  assert.equal(proxy.isUpstream('https://a.test/x'), true);
  assert.equal(proxy.isUpstream('http://a.test'), true);
  assert.equal(proxy.isUpstream('file:///etc/passwd'), false);
  assert.equal(proxy.isUpstream('https:///x'), false);
  assert.equal(proxy.isUpstream(''), false);
});

function listen(server) {
  return new Promise((resolve) => server.listen(0, '127.0.0.1', () => resolve(server.address().port)));
}

test('relay: a host that labels everything text/html comes out as a playlist and video, on loopback only', async () => {
  const upstream = http.createServer((req, res) => {
    if (req.url === '/moved') { res.writeHead(302, { Location: '/pl/master' }); res.end(); return; }
    if (req.url === '/pl/master') { res.writeHead(200, { 'Content-Type': 'text/html' }); res.end('#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nv/index\n'); return; }
    if (req.url === '/pl/v/index') { res.writeHead(200, { 'Content-Type': 'text/html' }); res.end('#EXTM3U\n#EXTINF:6,\nseg0.html\n#EXT-X-ENDLIST\n'); return; }
    if (req.url === '/pl/v/seg0.html') {
      if (req.headers.range === 'bytes=0-187') { res.writeHead(206, { 'Content-Type': 'text/html', 'Content-Range': 'bytes 0-187/188', 'Content-Length': '188' }); res.end(TS); return; }
      res.writeHead(200, { 'Content-Type': 'text/html', 'Content-Length': String(TS.length) }); res.end(TS); return;
    }
    res.writeHead(404); res.end();
  });
  const upPort = await listen(upstream);
  const relay = await new Promise((resolve, reject) => proxy.start((err, r) => (err ? reject(err) : resolve(r))));
  try {
    assert.equal(relay.server.address().address, '127.0.0.1');
    const wrap = proxy.wrapper(relay.port, relay.token);
    const master = await fetch(wrap('http://127.0.0.1:' + upPort + '/moved'));
    assert.equal(master.headers.get('content-type'), proxy.PLAYLIST);
    const variantUrl = (await master.text()).split('\n').find((l) => l.startsWith('http'));
    assert.equal(variantUrl, wrap('http://127.0.0.1:' + upPort + '/pl/v/index'), 'resolved against the redirect target');
    const segUrl = (await (await fetch(variantUrl)).text()).split('\n').find((l) => l.startsWith('http'));
    const seg = await fetch(segUrl);
    assert.equal(seg.headers.get('content-type'), 'video/mp2t');
    assert.deepEqual(Buffer.from(await seg.arrayBuffer()), TS);
    const part = await fetch(segUrl, { headers: { Range: 'bytes=0-187' } });
    assert.equal(part.status, 206);
    assert.equal(part.headers.get('content-range'), 'bytes 0-187/188');
    assert.equal((await fetch('http://127.0.0.1:' + relay.port + '/wrongtoken/p?u=' + encodeURIComponent('http://127.0.0.1:' + upPort + '/pl/master'))).status, 404);
    assert.equal((await fetch(wrap('file:///etc/passwd'))).status, 400);
    assert.equal((await fetch(wrap('http://127.0.0.1:' + upPort + '/nope'))).status, 404);
  } finally {
    relay.server.close();
    upstream.close();
  }
});

test('Relabel: off the TV it never calls the bridge and leaves URLs alone', () => {
  let made = 0;
  function Bridge() { made++; }
  const r = Relabel.create({ isWebOS: false, Bridge });
  r.start();
  assert.equal(made, 0);
  assert.equal(r.ready(), false);
  assert.equal(r.wrap('https://a.test/x'), 'https://a.test/x');
});

test('Relabel: on webOS it subscribes once and wraps URLs after the service answers', () => {
  const calls = [];
  let inst = null;
  function Bridge() { inst = this; this.call = (uri, params) => calls.push([uri, JSON.parse(params)]); }
  const r = Relabel.create({ isWebOS: true, Bridge });
  r.start();
  r.start();
  assert.deepEqual(calls, [[Relabel.SERVICE, { subscribe: true }]]);
  assert.equal(r.ready(), false);
  inst.onservicecallback(JSON.stringify({ returnValue: true, port: 4321, token: 'ab12' }));
  assert.equal(r.wrap('https://a.test/x?y=1'), 'http://127.0.0.1:4321/ab12/p?u=https%3A%2F%2Fa.test%2Fx%3Fy%3D1');
  inst.onservicecallback(JSON.stringify({ returnValue: false, errorText: 'gone' }));
  assert.equal(r.ready(), false, 'a failure falls back to the plain URL');
  inst.onservicecallback('not json');
  assert.equal(r.wrap('https://a.test/x'), 'https://a.test/x');
  inst.onservicecallback(JSON.stringify({ returnValue: true, port: 1, token: '../x' }));
  assert.equal(r.ready(), false, 'a token is hex only');
});

test('the relay service is ES5 for the oldest webOS Node, and its id matches the app', () => {
  for (const f of fs.readdirSync(path.join(ROOT, 'service')).filter((n) => n.endsWith('.js'))) {
    assert.doesNotThrow(() => acorn.parse(fs.readFileSync(path.join(ROOT, 'service', f), 'utf8'), { ecmaVersion: 5, sourceType: 'script' }), f);
  }
  const app = JSON.parse(fs.readFileSync(path.join(ROOT, 'appinfo.json'), 'utf8')).id;
  const svc = JSON.parse(fs.readFileSync(path.join(ROOT, 'service', 'services.json'), 'utf8'));
  assert.equal(svc.id, app + '.service');
  assert.equal(svc.services[0].name, svc.id);
  assert.equal(JSON.parse(fs.readFileSync(path.join(ROOT, 'service', 'package.json'), 'utf8')).name, svc.id);
  assert.equal(Relabel.SERVICE, 'luna://' + svc.id + '/relay');
});
