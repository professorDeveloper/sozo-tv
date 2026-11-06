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

// url.resolve throws on some URLs (e.g. http://[bad/x); null lets the caller drop or refuse it.
function resolve(base, ref) {
  try { return url.resolve(base, ref); } catch (e) { return null; }
}

// Every URI in a playlist is made absolute and sent back through the relay; a line with an unparsable URI is dropped.
function rewritePlaylist(text, base, wrap) {
  return String(text).split(/\r?\n/).map(function (line) {
    var s = line.trim();
    if (!s) return line;
    if (s.charAt(0) !== '#') { var u = resolve(base, s); return u === null ? null : wrap(u); }
    var bad = false;
    var out = line.replace(/URI="([^"]*)"/g, function (m, uri) {
      var r = resolve(base, uri);
      if (r === null) { bad = true; return m; }
      return 'URI="' + wrap(r) + '"';
    });
    return bad ? null : out;
  }).filter(function (line) { return line !== null; }).join('\n');
}

function wrapper(port, token) {
  return function (u) {
    return 'http://127.0.0.1:' + port + '/' + token + '/p?u=' + encodeURIComponent(u);
  };
}

// Eight 16-bit groups from any IPv6 spelling (zero runs, leading zeros, a dotted IPv4 tail, a zone id), or null.
function ipv6Groups(ip) {
  var v = String(ip).split('%')[0];
  var tail = /(\d+)\.(\d+)\.(\d+)\.(\d+)$/.exec(v);
  if (tail) {
    var q = tail.slice(1).map(Number);
    v = v.slice(0, tail.index) + ((q[0] << 8) | q[1]).toString(16) + ':' + ((q[2] << 8) | q[3]).toString(16);
  }
  var halves = v.split('::');
  if (halves.length > 2) return null;
  var head = halves[0] ? halves[0].split(':') : [];
  var rest = halves.length === 2 && halves[1] ? halves[1].split(':') : [];
  var fill = halves.length === 2 ? 8 - head.length - rest.length : 0;
  if (fill < 0 || (halves.length === 1 && head.length !== 8)) return null;
  var parts = head.concat(new Array(fill + 1).join('0').split(''), rest);
  if (parts.length !== 8) return null;
  var out = [];
  for (var i = 0; i < 8; i++) {
    if (!/^[0-9a-f]{1,4}$/i.test(parts[i])) return null;
    out.push(parseInt(parts[i], 16));
  }
  return out;
}

function isPublicV4(o) {
  if (o[0] === 0 || o[0] === 10 || o[0] === 127 || o[0] >= 224) return false;
  if (o[0] === 100 && o[1] >= 64 && o[1] <= 127) return false;
  if (o[0] === 169 && o[1] === 254) return false;
  if (o[0] === 172 && o[1] >= 16 && o[1] <= 31) return false;
  if (o[0] === 192 && (o[1] === 168 || (o[1] === 0 && (o[2] === 0 || o[2] === 2)))) return false;
  if (o[0] === 198 && (o[1] === 18 || o[1] === 19 || (o[1] === 51 && o[2] === 100))) return false;
  if (o[0] === 203 && o[1] === 0 && o[2] === 113) return false;
  return true;
}

function v4From(hi, lo) {
  return [hi >> 8, hi & 255, lo >> 8, lo & 255];
}

// Public internet only: loopback, private, link-local, CGNAT, multicast and reserved ranges are refused.
// IPv6 is normalised first, so expanded or IPv4-embedding spellings cannot slip past.
function isPublicAddress(ip) {
  var v = String(ip || '').toLowerCase();
  if (net.isIPv4(v)) return isPublicV4(v.split('.').map(Number));
  if (!net.isIPv6(v.split('%')[0])) return false;
  var h = ipv6Groups(v);
  if (!h) return false;
  if (!h[0] && !h[1] && !h[2] && !h[3] && !h[4]) return h[5] === 0xffff ? isPublicV4(v4From(h[6], h[7])) : false;
  if ((h[0] & 0xfe00) === 0xfc00 || (h[0] & 0xffc0) === 0xfe80 || (h[0] & 0xff00) === 0xff00) return false;
  if (h[0] === 0x2001 && (h[1] === 0x0db8 || h[1] === 0)) return false;
  if (h[0] === 0x64 && h[1] === 0xff9b) return false;
  if (h[0] === 0x2002) return isPublicV4(v4From(h[1], h[2]));
  return true;
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
  var opts;
  try { opts = url.parse(target); } catch (e) { cb(e); return; }
  if (policy.publicOnly) {
    var literal = String(opts.hostname || '').replace(/^\[|\]$/g, '');
    if (net.isIP(literal) && !policy.isPublic(literal)) { cb(new Error('refused address ' + literal)); return; }
    opts.lookup = publicLookup(policy.isPublic);
  }
  // http.get refuses unescaped non-ASCII, so a path like /файл.ts is percent-encoded; existing escapes stay.
  opts.path = String(opts.path || '/').replace(/[\ud800-\udbff][\udc00-\udfff]|[^\x21-\x7e]/g, function (c) { return encodeURIComponent(c); });
  opts.headers = { 'User-Agent': UA, 'Accept': '*/*' };
  if (range) opts.headers.Range = range;
  var done = false;
  var req;
  // http.get throws on some URLs (an odd scheme or host); that must become a 502, not an uncaught exception.
  try {
    req = lib.get(opts, onResponse);
  } catch (e) {
    cb(e);
    return;
  }
  function onResponse(res) {
    var loc = res.headers.location;
    if (res.statusCode >= 300 && res.statusCode < 400 && loc) {
      res.resume();
      req.abort();
      done = true;
      var next = resolve(target, loc);
      if (next === null || hops >= MAX_REDIRECTS || !isUpstream(next)) { cb(new Error('bad redirect')); return; }
      if (hold.aborted) return;
      fetchUpstream(next, range, hops + 1, policy, hold, cb);
      return;
    }
    done = true;
    cb(null, res, target);
  }
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
