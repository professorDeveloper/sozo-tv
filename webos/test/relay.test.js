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

function startRelay(options) {
  return new Promise((resolve, reject) => proxy.start((err, r) => (err ? reject(err) : resolve(r)), options));
}

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
  const relay = await startRelay({ allowPrivate: true });
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

test('isPublicAddress refuses loopback, private, link-local, CGNAT, reserved and multicast, v4 and v6', () => {
  for (const ip of ['127.0.0.1', '10.1.2.3', '172.16.0.1', '172.31.255.255', '192.168.1.120', '169.254.1.1', '100.64.0.1', '0.0.0.0',
    '224.0.0.1', '255.255.255.255', '203.0.113.250', '::1', '::', 'fe80::1', 'fd00::1', 'ff02::1', '2001:db8:1000::fa', '::ffff:192.168.1.1', 'not-an-ip',
    '0:0:0:0:0:0:0:1', '0:0:0:0:0:ffff:7f00:1', '0000:0000:0000:0000:0000:0000:0000:0000', '0:0:0:0:0:ffff:c0a8:178', '::ffff:127.0.0.1',
    'FE80::1%eth0', '2002:c0a8:0101::1', '2001:0:4136:e378::1', '64:ff9b::a00:1']) {
    assert.equal(proxy.isPublicAddress(ip), false, ip);
  }
  for (const ip of ['188.114.96.0', '104.17.88.107', '172.32.0.1', '2a06:98c1:3120::', '::ffff:8.8.8.8', '2606:4700:4700:0:0:0:0:1111', '0:0:0:0:0:ffff:808:808', '2002:0808:0808::1']) {
    assert.equal(proxy.isPublicAddress(ip), true, ip);
  }
});

test('relay: by default a LAN or loopback upstream is refused, by literal address and by name', async () => {
  let hits = 0;
  const lan = http.createServer((req, res) => { hits++; res.end('#EXTM3U\n'); });
  const port = await listen(lan);
  const relay = await startRelay();
  try {
    const wrap = proxy.wrapper(relay.port, relay.token);
    for (const host of ['127.0.0.1', 'localhost', '[::1]', '192.168.1.1', '10.0.0.1']) {
      assert.equal((await fetch(wrap('http://' + host + ':' + port + '/x'))).status, 403, host);
    }
    assert.equal(hits, 0, 'nothing reached the LAN server');
  } finally {
    relay.server.close();
    lan.close();
  }
});

test('relay: a redirect to a refused address is refused at that hop', async () => {
  let hits = 0;
  const inner = http.createServer((req, res) => { hits++; res.end('secret'); });
  const innerPort = await new Promise((r) => inner.listen(0, '127.0.0.2', () => r(inner.address().port)));
  const hop = http.createServer((req, res) => { res.writeHead(302, { Location: 'http://127.0.0.2:' + innerPort + '/secret' }); res.end(); });
  const hopPort = await listen(hop);
  // Only 127.0.0.1 counts as public here, so the first hop passes and the redirect target does not.
  const relay = await startRelay({ isPublic: (ip) => ip === '127.0.0.1' });
  try {
    assert.equal((await fetch(proxy.wrapper(relay.port, relay.token)('http://127.0.0.1:' + hopPort + '/'))).status, 403);
    assert.equal(hits, 0);
  } finally {
    relay.server.close();
    hop.close();
    inner.close();
  }
});

test('relay: leaving mid-redirect aborts the next hop, and a large segment is piped intact', async () => {
  let secondClosed = null;
  const big = Buffer.alloc(6 * 1024 * 1024, 0x47);
  const up = http.createServer((req, res) => {
    if (req.url === '/hop') { res.writeHead(302, { Location: '/hang' }); res.end(); return; }
    if (req.url === '/hang') { secondClosed = new Promise((r) => req.on('close', r)); return; }
    if (req.url === '/big.ts') { res.writeHead(200, { 'Content-Type': 'text/html', 'Content-Length': String(big.length) }); res.end(big); return; }
    res.writeHead(404); res.end();
  });
  const upPort = await listen(up);
  const relay = await startRelay({ allowPrivate: true });
  try {
    const wrap = proxy.wrapper(relay.port, relay.token);
    const ctrl = new AbortController();
    const pending = fetch(wrap('http://127.0.0.1:' + upPort + '/hop'), { signal: ctrl.signal }).catch(() => null);
    while (!secondClosed) await new Promise((r) => setTimeout(r, 10));
    ctrl.abort();
    await pending;
    await secondClosed;
    const seg = await fetch(wrap('http://127.0.0.1:' + upPort + '/big.ts'));
    assert.equal(seg.headers.get('content-type'), 'video/mp2t');
    const body = Buffer.from(await seg.arrayBuffer());
    assert.equal(body.length, big.length);
    assert.ok(body.equals(big));
  } finally {
    relay.server.close();
    up.close();
  }
});

test('callerAllowed only lets the app itself have the token', () => {
  assert.equal(proxy.callerAllowed('com.soplay.sozo.webos', 'com.soplay.sozo.webos'), true);
  assert.equal(proxy.callerAllowed('com.other.app', 'com.soplay.sozo.webos'), false);
  assert.equal(proxy.callerAllowed('com.soplay.sozo.webos.evil', 'com.soplay.sozo.webos'), false);
  assert.equal(proxy.callerAllowed(undefined, 'com.soplay.sozo.webos'), false);
  assert.equal(proxy.callerAllowed('', 'com.soplay.sozo.webos'), false);
});

test('relay: a redirect to a non-http scheme or a hostless URL answers 502 and the service keeps running', async () => {
  const up = http.createServer((req, res) => {
    const to = { '/ftp': 'ftp://example.com/x.ts', '/js': 'javascript:alert(1)', '/file': 'file:///etc/passwd', '/nohost': 'http:///x' }[req.url];
    if (to) { res.writeHead(302, { Location: to }); res.end(); return; }
    res.writeHead(200, { 'Content-Type': 'text/html' }); res.end('#EXTM3U\n');
  });
  const upPort = await listen(up);
  const relay = await startRelay({ allowPrivate: true });
  try {
    const wrap = proxy.wrapper(relay.port, relay.token);
    for (const p of ['/ftp', '/js', '/file', '/nohost']) {
      assert.equal((await fetch(wrap('http://127.0.0.1:' + upPort + p))).status, 502, p);
    }
    const ok = await fetch(wrap('http://127.0.0.1:' + upPort + '/ok'));
    assert.equal(ok.headers.get('content-type'), proxy.PLAYLIST, 'still serving after the bad redirects');
  } finally {
    relay.server.close();
    up.close();
  }
});

test('relay: non-Latin playlist and segment paths are percent-encoded and play through', async () => {
  const seen = [];
  const up = http.createServer((req, res) => {
    const path = decodeURIComponent(req.url);
    seen.push(req.url);
    if (path === '/видео/плейлист.m3u8') { res.writeHead(200, { 'Content-Type': 'text/html' }); res.end('#EXTM3U\n#EXTINF:6,\nсегмент 0.ts\n'); return; }
    if (path === '/видео/сегмент 0.ts') { res.writeHead(200, { 'Content-Type': 'text/html' }); res.end(TS); return; }
    res.writeHead(404); res.end();
  });
  const upPort = await listen(up);
  const relay = await startRelay({ allowPrivate: true });
  try {
    const wrap = proxy.wrapper(relay.port, relay.token);
    const pl = await fetch(wrap('http://127.0.0.1:' + upPort + '/видео/плейлист.m3u8'));
    assert.equal(pl.status, 200);
    const segUrl = (await pl.text()).split('\n').find((l) => l.startsWith('http'));
    const seg = await fetch(segUrl);
    assert.equal(seg.headers.get('content-type'), 'video/mp2t');
    assert.deepEqual(Buffer.from(await seg.arrayBuffer()), TS);
    assert.ok(seen.every((u) => /^[\x21-\x7e]+$/.test(u)), 'upstream only ever saw escaped paths');
  } finally {
    relay.server.close();
    up.close();
  }
});
