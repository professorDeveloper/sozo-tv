'use strict';
// Loopback HLS relay for the webOS native player: same bytes, corrected content types.
var http = require('http');
var https = require('https');
var url = require('url');
var crypto = require('crypto');
var dns = require('dns');
var net = require('net');

var PLAYLIST = 'application/vnd.apple.mpegurl';
var MAX_PLAYLIST = 4 * 1024 * 1024;
var MAX_REDIRECTS = 5;
var TIMEOUT = 20000;
var UA = 'Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36';

function isUpstream(u) {
  return /^https?:\/\/[^\/\s]+/i.test(u || '');
}

// Media type from the first bytes, so a segment sent as text/html still reaches the player as video.
function sniff(head, upstreamType) {
  var b = head || [];
  var text = '';
  for (var i = 0; i < Math.min(b.length, 16); i++) text += String.fromCharCode(b[i]);
  if (/^\s*#EXTM3U/.test(text)) return PLAYLIST;
  if (b[0] === 0x47) return 'video/mp2t';
  if (text.slice(4, 8) === 'ftyp' || text.slice(4, 8) === 'styp' || text.slice(4, 8) === 'moof') return 'video/mp4';
  if (text.slice(0, 3) === 'ID3' || (b[0] === 0xff && (b[1] & 0xf6) === 0xf0)) return 'audio/aac';
  var t = String(upstreamType || '').split(';')[0].trim().toLowerCase();
  if (!t || t === 'text/html' || t === 'text/plain' || t === 'application/octet-stream') return 'application/octet-stream';
  return t;
}

// Every URI in a playlist is made absolute and sent back through the relay.
function rewritePlaylist(text, base, wrap) {
  return String(text).split(/\r?\n/).map(function (line) {
    var s = line.trim();
    if (!s) return line;
    if (s.charAt(0) !== '#') return wrap(url.resolve(base, s));
    return line.replace(/URI="([^"]*)"/g, function (m, uri) {
      return 'URI="' + wrap(url.resolve(base, uri)) + '"';
    });
  }).join('\n');
}

function wrapper(port, token) {
  return function (u) {
    return 'http://127.0.0.1:' + port + '/' + token + '/p?u=' + encodeURIComponent(u);
  };
}

// Public internet only: loopback, private, link-local, CGNAT, multicast and reserved ranges are refused.
function isPublicAddress(ip) {
  var v = String(ip || '').toLowerCase();
  var mapped = /^::ffff:(\d+\.\d+\.\d+\.\d+)$/.exec(v);
  if (mapped) v = mapped[1];
  if (net.isIPv4(v)) {
    var o = v.split('.').map(Number);
    if (o[0] === 0 || o[0] === 10 || o[0] === 127 || o[0] >= 224) return false;
    if (o[0] === 100 && o[1] >= 64 && o[1] <= 127) return false;
    if (o[0] === 169 && o[1] === 254) return false;
    if (o[0] === 172 && o[1] >= 16 && o[1] <= 31) return false;
    if (o[0] === 192 && (o[1] === 168 || (o[1] === 0 && (o[2] === 0 || o[2] === 2)))) return false;
    if (o[0] === 198 && (o[1] === 18 || o[1] === 19 || (o[1] === 51 && o[2] === 100))) return false;
    if (o[0] === 203 && o[1] === 0 && o[2] === 113) return false;
    return true;
  }
  if (net.isIPv6(v)) {
    if (v === '::' || v === '::1') return false;
    if (/^f[cd]/.test(v) || /^fe[89ab]/.test(v) || /^ff/.test(v)) return false;
    if (/^2001:0?db8:/.test(v) || /^64:ff9b:/.test(v) || /^::/.test(v)) return false;
    return true;
  }
  return false;
}

// dns.lookup that fails for any non-public answer; checked at connect time, so DNS rebinding cannot slip past.
function publicLookup(isPublic) {
  return function (hostname, options, cb) {
  if (typeof options === 'function') { cb = options; options = {}; }
  var o = typeof options === 'number' ? { family: options } : (options || {});
  dns.lookup(hostname, { family: o.family || 0, hints: o.hints || 0, all: true }, function (err, list, fam) {
    if (err) { cb(err); return; }
    if (typeof list === 'string') list = [{ address: list, family: fam }];
    for (var i = 0; i < list.length; i++) {
      if (!isPublic(list[i].address)) { cb(new Error('refused address ' + list[i].address)); return; }
    }
    if (!list.length) { cb(new Error('no address')); return; }
    if (o.all) cb(null, list);
    else cb(null, list[0].address, list[0].family);
  });
  };
}

// Follows redirects; hold.req is always the live request, so the caller can abort whichever hop is open.
function fetchUpstream(target, range, hops, policy, hold, cb) {
  var lib = /^https:/i.test(target) ? https : http;
  var opts = url.parse(target);
  if (policy.publicOnly) {
    var literal = String(opts.hostname || '').replace(/^\[|\]$/g, '');
    if (net.isIP(literal) && !policy.isPublic(literal)) { cb(new Error('refused address ' + literal)); return; }
    opts.lookup = publicLookup(policy.isPublic);
  }
  opts.headers = { 'User-Agent': UA, 'Accept': '*/*' };
  if (range) opts.headers.Range = range;
  var done = false;
  var req = lib.get(opts, function (res) {
    var loc = res.headers.location;
    if (res.statusCode >= 300 && res.statusCode < 400 && loc) {
      res.resume();
      req.abort();
      done = true;
      var next = url.resolve(target, loc);
      if (hops >= MAX_REDIRECTS || !isUpstream(next)) { cb(new Error('bad redirect')); return; }
      if (hold.aborted) return;
      fetchUpstream(next, range, hops + 1, policy, hold, cb);
      return;
    }
    done = true;
    cb(null, res, target);
  });
  hold.req = req;
  req.setTimeout(TIMEOUT, function () { req.abort(); });
  req.on('error', function (e) { if (!done) { done = true; cb(e); } });
}

function sendPlaylist(up, res, finalUrl, wrap, first) {
  var chunks = [first];
  var size = first.length;
  var dead = false;
  up.on('data', function (chunk) {
    if (dead) return;
    size += chunk.length;
    if (size > MAX_PLAYLIST) { dead = true; up.destroy(); res.writeHead(502); res.end(); return; }
    chunks.push(chunk);
  });
  up.on('end', function () {
    if (dead) return;
    var body = rewritePlaylist(Buffer.concat(chunks).toString('utf8'), finalUrl, wrap);
    res.writeHead(200, { 'Content-Type': PLAYLIST, 'Access-Control-Allow-Origin': '*', 'Cache-Control': 'no-cache' });
    res.end(body);
  });
  up.resume();
}

// Media is piped, so a slow player throttles the upstream instead of filling memory.
function sendMedia(up, res, type, first) {
  var h = { 'Content-Type': type, 'Access-Control-Allow-Origin': '*' };
  ['content-length', 'content-range', 'accept-ranges'].forEach(function (k) { if (up.headers[k]) h[k] = up.headers[k]; });
  res.writeHead(up.statusCode, h);
  if (res.write(first) === false) {
    res.once('drain', function () { up.pipe(res); });
  } else {
    up.pipe(res);
  }
}

function handle(req, res, token, wrap, policy) {
  var parsed = url.parse(req.url, true);
  if (parsed.pathname !== '/' + token + '/p') { res.writeHead(404); res.end(); return; }
  var target = String(parsed.query.u || '');
  if (!isUpstream(target)) { res.writeHead(400); res.end(); return; }
  var hold = { req: null, aborted: false };
  res.on('close', function () {
    hold.aborted = true;
    if (hold.req) hold.req.abort();
  });
  fetchUpstream(target, req.headers.range, 0, policy, hold, function (err, up, finalUrl) {
    if (err) {
      if (!res.headersSent) res.writeHead(/^refused/.test(err.message) ? 403 : 502);
      res.end();
      return;
    }
    if (up.statusCode >= 400) { up.resume(); res.writeHead(up.statusCode); res.end(); return; }
    up.on('error', function () { if (!res.headersSent) res.writeHead(502); res.end(); });
    var got = false;
    up.once('data', function (first) {
      got = true;
      up.pause();
      var type = sniff(first, up.headers['content-type']);
      if (type === PLAYLIST) sendPlaylist(up, res, finalUrl, wrap, first);
      else sendMedia(up, res, type, first);
    });
    up.once('end', function () {
      if (!got) { res.writeHead(up.statusCode, { 'Content-Length': '0' }); res.end(); }
    });
  });
}

// webos-service puts the calling app's id (WAM's pid already split off) or service name in message.sender.
function callerAllowed(sender, appId) {
  return typeof sender === 'string' && sender === appId;
}

// Listens on loopback only, on a free port; every path carries a per-run token.
// options.allowPrivate and options.isPublic are for tests against a local upstream; the service sets neither.
function start(cb, options) {
  var o = options || {};
  var policy = { publicOnly: !o.allowPrivate, isPublic: o.isPublic || isPublicAddress };
  var token = crypto.randomBytes(12).toString('hex');
  var wrap = null;
  var server = http.createServer(function (req, res) {
    if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405); res.end(); return; }
    handle(req, res, token, wrap, policy);
  });
  server.on('error', function (e) { cb(e); });
  server.listen(0, '127.0.0.1', function () {
    var port = server.address().port;
    wrap = wrapper(port, token);
    cb(null, { port: port, token: token, server: server });
  });
}

module.exports = { sniff: sniff, rewritePlaylist: rewritePlaylist, wrapper: wrapper, isUpstream: isUpstream, isPublicAddress: isPublicAddress, callerAllowed: callerAllowed, start: start, PLAYLIST: PLAYLIST };
