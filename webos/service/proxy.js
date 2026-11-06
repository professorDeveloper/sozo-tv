'use strict';
// Loopback HLS relay for the webOS native player: same bytes, corrected content types.
var http = require('http');
var https = require('https');
var url = require('url');
var crypto = require('crypto');

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

function fetchUpstream(target, range, hops, cb) {
  var lib = /^https:/i.test(target) ? https : http;
  var opts = url.parse(target);
  opts.headers = { 'User-Agent': UA, 'Accept': '*/*' };
  if (range) opts.headers.Range = range;
  var done = false;
  var req = lib.get(opts, function (res) {
    var loc = res.headers.location;
    if (res.statusCode >= 300 && res.statusCode < 400 && loc) {
      res.resume();
      if (hops >= MAX_REDIRECTS) { done = true; cb(new Error('too many redirects')); return; }
      done = true;
      fetchUpstream(url.resolve(target, loc), range, hops + 1, cb);
      return;
    }
    done = true;
    cb(null, res, target);
  });
  req.setTimeout(TIMEOUT, function () { req.abort(); });
  req.on('error', function (e) { if (!done) { done = true; cb(e); } });
  return req;
}

function handle(req, res, token, wrap) {
  var parsed = url.parse(req.url, true);
  if (parsed.pathname !== '/' + token + '/p') { res.writeHead(404); res.end(); return; }
  var target = String(parsed.query.u || '');
  if (!isUpstream(target)) { res.writeHead(400); res.end(); return; }
  var upstream = fetchUpstream(target, req.headers.range, 0, function (err, up, finalUrl) {
    if (err) { if (!res.headersSent) res.writeHead(502); res.end(); return; }
    if (up.statusCode >= 400) { up.resume(); res.writeHead(up.statusCode); res.end(); return; }
    var first = true;
    var dead = false;
    var playlist = false;
    var chunks = [];
    var size = 0;
    up.on('data', function (chunk) {
      if (dead) return;
      if (first) {
        first = false;
        var type = sniff(chunk, up.headers['content-type']);
        playlist = type === PLAYLIST;
        if (!playlist) {
          var h = { 'Content-Type': type, 'Access-Control-Allow-Origin': '*' };
          ['content-length', 'content-range', 'accept-ranges'].forEach(function (k) { if (up.headers[k]) h[k] = up.headers[k]; });
          res.writeHead(up.statusCode, h);
        }
      }
      if (playlist) {
        size += chunk.length;
        if (size > MAX_PLAYLIST) { dead = true; up.destroy(); res.writeHead(502); res.end(); return; }
        chunks.push(chunk);
      } else {
        res.write(chunk);
      }
    });
    up.on('end', function () {
      if (dead) return;
      if (first) { res.writeHead(up.statusCode, { 'Content-Length': '0' }); res.end(); return; }
      if (!playlist) { res.end(); return; }
      var body = rewritePlaylist(Buffer.concat(chunks).toString('utf8'), finalUrl, wrap);
      res.writeHead(200, { 'Content-Type': PLAYLIST, 'Access-Control-Allow-Origin': '*', 'Cache-Control': 'no-cache' });
      res.end(body);
    });
    up.on('error', function () { if (dead) return; dead = true; if (!res.headersSent) res.writeHead(502); res.end(); });
  });
  req.on('close', function () { if (upstream) upstream.abort(); });
}

// Listens on loopback only, on a free port; every path carries a per-run token.
function start(cb) {
  var token = crypto.randomBytes(12).toString('hex');
  var wrap = null;
  var server = http.createServer(function (req, res) {
    if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405); res.end(); return; }
    handle(req, res, token, wrap);
  });
  server.on('error', function (e) { cb(e); });
  server.listen(0, '127.0.0.1', function () {
    var port = server.address().port;
    wrap = wrapper(port, token);
    cb(null, { port: port, token: token, server: server });
  });
}

module.exports = { sniff: sniff, rewritePlaylist: rewritePlaylist, wrapper: wrapper, isUpstream: isUpstream, start: start, PLAYLIST: PLAYLIST };
