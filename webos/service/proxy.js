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
var RELAY_HEADERS = { 'user-agent': 'User-Agent', referer: 'Referer', origin: 'Origin', accept: 'Accept', 'accept-language': 'Accept-Language' };
var MAX_HEADER = 1024;

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
  if (b[0] === 0x1a && b[1] === 0x45 && b[2] === 0xdf && b[3] === 0xa3) return 'video/x-matroska';
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

// h is the JSON header set from parseHeaders, carried on so every playlist entry is fetched with it.
function wrapper(port, token) {
  return function (u, h) {
    return 'http://127.0.0.1:' + port + '/' + token + '/p?u=' + encodeURIComponent(u) + (h ? '&h=' + encodeURIComponent(h) : '');
  };
}

// Only allowlisted names with short printable values survive; Cookie, Host, Authorization and the rest are dropped.
function parseHeaders(raw) {
  var out = {};
  var o = null;
  if (typeof raw !== 'string' || !raw) return out;
  try { o = JSON.parse(raw); } catch (e) { return out; }
  if (!o || typeof o !== 'object' || Array.isArray(o)) return out;
  for (var k in o) {
    if (!Object.prototype.hasOwnProperty.call(o, k)) continue;
    var name = RELAY_HEADERS[String(k).toLowerCase()];
    var v = o[k];
    if (name && typeof v === 'string' && v.length <= MAX_HEADER && /^[\x20-\x7e]*$/.test(v)) out[name] = v;
  }
  return out;
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
function fetchUpstream(target, send, hops, policy, hold, cb) {
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
  for (var name in send.headers) if (Object.prototype.hasOwnProperty.call(send.headers, name)) opts.headers[name] = send.headers[name];
  if (send.range) opts.headers.Range = send.range;
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
      fetchUpstream(next, send, hops + 1, policy, hold, cb);
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

var HEAD_BYTES = 512 * 1024;
var TRACKS_BYTES = 256 * 1024;
var ID = { SEGMENT: 0x18538067, SEEKHEAD: 0x114D9B74, SEEK: 0x4DBB, SEEKID: 0x53AB, SEEKPOS: 0x53AC, TRACKS: 0x1654AE6B, ENTRY: 0xAE, CLUSTER: 0x1F43B675 };

// EBML variable-length integer at pos: element IDs keep their length marker, sizes drop it; null past the end.
function vint(buf, pos, isId) {
  if (pos >= buf.length) return null;
  var first = buf[pos];
  var len = 1;
  var mask = 0x80;
  while (len <= 8 && !(first & mask)) { len++; mask >>= 1; }
  if (len > 8 || pos + len > buf.length) return null;
  var value = isId ? first : first & (mask - 1);
  var ones = (first & (mask - 1)) === mask - 1;
  for (var i = 1; i < len; i++) {
    value = value * 256 + buf[pos + i];
    if (buf[pos + i] !== 0xff) ones = false;
  }
  return { len: len, value: value, unknown: !isId && ones };
}

// Children of [start, end) as {id, data, size}; stops at the buffer's end or a damaged element.
function children(buf, start, end) {
  var out = [];
  var pos = start;
  while (pos < end) {
    var id = vint(buf, pos, true);
    if (!id) break;
    var size = vint(buf, pos + id.len);
    if (!size) break;
    var data = pos + id.len + size.len;
    out.push({ id: id.value, data: data, size: size.unknown ? -1 : size.value });
    if (size.unknown) break;
    pos = data + size.value;
  }
  return out;
}

function uintAt(buf, el) {
  var v = 0;
  for (var i = 0; i < el.size && i < 8; i++) v = v * 256 + buf[el.data + i];
  return v;
}

function textAt(buf, el) {
  return buf.slice(el.data, Math.min(el.data + el.size, buf.length)).toString('utf8').replace(/\0+$/, '');
}

// Audio and text subtitle tracks from a Matroska Tracks element; the TV numbers text subtitles only (no PGS/VobSub).
function readTracks(buf, el) {
  var out = { audio: [], subtitles: [] };
  children(buf, el.data, Math.min(el.data + el.size, buf.length)).forEach(function (entry) {
    if (entry.id !== ID.ENTRY) return;
    var t = { type: 0, language: 'eng', name: '', codec: '', isDefault: true, forced: false };
    children(buf, entry.data, Math.min(entry.data + entry.size, buf.length)).forEach(function (f) {
      if (f.id === 0x83) t.type = uintAt(buf, f);
      else if (f.id === 0x22B59C) t.language = textAt(buf, f);
      else if (f.id === 0x22B59D) t.bcp47 = textAt(buf, f);
      else if (f.id === 0x536E) t.name = textAt(buf, f);
      else if (f.id === 0x86) t.codec = textAt(buf, f);
      else if (f.id === 0x88) t.isDefault = uintAt(buf, f) === 1;
      else if (f.id === 0x55AA) t.forced = uintAt(buf, f) === 1;
    });
    var item = { language: t.bcp47 || t.language, name: t.name, codec: t.codec, isDefault: t.isDefault, forced: t.forced };
    if (t.type === 2) out.audio.push(item);
    else if (t.type === 0x11 && /^S_TEXT\//.test(t.codec)) { item.index = out.subtitles.length; out.subtitles.push(item); }
  });
  return out;
}

// Finds Tracks in the file head: {tracks} when there, {at} when the SeekHead points past the head, or null.
function scanHead(buf) {
  var top = children(buf, 0, buf.length);
  for (var i = 0; i < top.length; i++) {
    if (top[i].id !== ID.SEGMENT) continue;
    var seg = top[i];
    var segEnd = seg.size < 0 ? buf.length : Math.min(seg.data + seg.size, buf.length);
    var at = -1;
    var kids = children(buf, seg.data, segEnd);
    for (var k = 0; k < kids.length; k++) {
      var el = kids[k];
      if (el.id === ID.TRACKS && el.data + el.size <= buf.length) return { tracks: readTracks(buf, el) };
      if (el.id === ID.CLUSTER) break;
      if (el.id === ID.SEEKHEAD) at = seekTo(buf, el, seg.data);
    }
    return at >= 0 ? { at: at } : null;
  }
  return null;
}

function seekTo(buf, head, segData) {
  var found = -1;
  children(buf, head.data, Math.min(head.data + head.size, buf.length)).forEach(function (seek) {
    if (seek.id !== ID.SEEK) return;
    var sid = -1;
    var spos = -1;
    children(buf, seek.data, Math.min(seek.data + seek.size, buf.length)).forEach(function (f) {
      if (f.id === ID.SEEKID) sid = uintAt(buf, f);
      else if (f.id === ID.SEEKPOS) spos = uintAt(buf, f);
    });
    if (sid === ID.TRACKS && spos >= 0) found = segData + spos;
  });
  return found;
}

// Up to max bytes of a ranged GET, then the request is dropped.
function fetchBytes(target, from, max, headers, policy, hold, cb) {
  fetchUpstream(target, { range: 'bytes=' + from + '-' + (from + max - 1), headers: headers }, 0, policy, hold, function (err, up) {
    if (err) { cb(err); return; }
    if (up.statusCode >= 400) { up.resume(); cb(new Error('status ' + up.statusCode)); return; }
    if (from > 0 && up.statusCode !== 206) { up.destroy(); cb(new Error('no range')); return; }
    var chunks = [];
    var size = 0;
    var done = false;
    function finish() {
      if (done) return;
      done = true;
      up.destroy();
      cb(null, Buffer.concat(chunks).slice(0, max));
    }
    up.on('data', function (c) { chunks.push(c); size += c.length; if (size >= max) finish(); });
    up.on('end', finish);
    up.on('error', function (e) { if (!done) { done = true; cb(e); } });
  });
}

// GET /<token>/t?u=&h=: the file's audio and subtitle tracks as JSON, read from its Matroska header.
function handleTracks(res, target, headers, policy, hold) {
  function reply(status, body) {
    if (res.headersSent) return;
    res.writeHead(status, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*', 'Cache-Control': 'no-cache' });
    res.end(JSON.stringify(body));
  }
  fetchBytes(target, 0, HEAD_BYTES, headers, policy, hold, function (err, head) {
    if (err) { reply(502, { error: 'fetch' }); return; }
    var found = scanHead(head);
    if (!found) { reply(200, { audio: [], subtitles: [] }); return; }
    if (found.tracks) { reply(200, found.tracks); return; }
    fetchBytes(target, found.at, TRACKS_BYTES, headers, policy, hold, function (err2, part) {
      if (err2) { reply(502, { error: 'fetch' }); return; }
      var el = children(part, 0, part.length)[0];
      reply(200, el && el.id === ID.TRACKS ? readTracks(part, el) : { audio: [], subtitles: [] });
    });
  });
}

function handle(req, res, token, wrap, policy) {
  var parsed = url.parse(req.url, true);
  var kind = parsed.pathname === '/' + token + '/p' ? 'p' : parsed.pathname === '/' + token + '/t' ? 't' : '';
  if (!kind) { res.writeHead(404); res.end(); return; }
  var target = String(parsed.query.u || '');
  if (!isUpstream(target)) { res.writeHead(400); res.end(); return; }
  var headers = parseHeaders(parsed.query.h);
  var carried = Object.keys(headers).length ? JSON.stringify(headers) : '';
  var wrapOn = function (u) { return wrap(u, carried); };
  var hold = { req: null, aborted: false };
  res.on('close', function () {
    hold.aborted = true;
    if (hold.req) hold.req.abort();
  });
  if (kind === 't') { handleTracks(res, target, headers, policy, hold); return; }
  fetchUpstream(target, { range: req.headers.range, headers: headers }, 0, policy, hold, function (err, up, finalUrl) {
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
      if (type === PLAYLIST) sendPlaylist(up, res, finalUrl, wrapOn, first);
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

module.exports = { sniff: sniff, rewritePlaylist: rewritePlaylist, wrapper: wrapper, parseHeaders: parseHeaders, scanHead: scanHead, isUpstream: isUpstream, isPublicAddress: isPublicAddress, callerAllowed: callerAllowed, start: start, PLAYLIST: PLAYLIST };
