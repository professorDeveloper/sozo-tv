(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});

  // Pure playback rules: contract sections 5 and 6. The player in js/player.js only drives these.

  var TIMES = {
    metadataHls: 20000, metadataFile: 30000, stall: 20000, resolve: 100000, saveEvery: 15000,
    hideControls: 5000, seekCommit: 450, seekBurst: 700, healthy: 120000, autoNext: 10, subtitle: 15000
  };
  var MAX_SOURCES = 3;
  var MIN_RESUME = 5;
  var FINISHED_AT = 0.95;
  var END_MARGIN = 30;

  var BAD_TYPES = { iframe: 1, embed: 1, html: 1, dash: 1, mpd: 1, torrent: 1, magnet: 1, mkv: 1 };
  var HLS_TYPES = { hls: 1, m3u8: 1 };
  var FILE_TYPES = { mp4: 1, progressive: 1, video: 1 };
  var MEDIA_EXT = { m3u8: 1, mp4: 1, m4v: 1, mov: 1 };
  var SOURCE_DIRECTIVES = ['useLocalProxy', 'localProxy', 'requestTransform', 'signer', 'useWebViewSniff', 'drm', 'audioTracks'];
  var ENVELOPE_DIRECTIVES = ['signer', 'useWebViewSniff', 'drm'];

  function text(v) {
    if (typeof v === 'number' && isFinite(v)) return String(v);
    return typeof v === 'string' ? v.replace(/^\s+|\s+$/g, '') : '';
  }

  function extension(path) {
    var m = /\.([a-z0-9]+)$/i.exec(path);
    return m ? m[1].toLowerCase() : '';
  }

  function directive(v) {
    if (Array.isArray(v)) return v.length > 0;
    return !!v;
  }

  function hasDirective(o, names) {
    for (var i = 0; i < names.length; i++) if (directive(o[names[i]])) return true;
    return false;
  }

  function headerNames(a, b) {
    var seen = {};
    var out = [];
    [a, b].forEach(function (headers) {
      if (!headers || typeof headers !== 'object') return;
      for (var k in headers) {
        if (!Object.prototype.hasOwnProperty.call(headers, k)) continue;
        var name = k.toLowerCase();
        if (!seen[name]) { seen[name] = 1; out.push(name); }
      }
    });
    return out;
  }

  // Returns the reason a source is rejected, or null when a web player can try it.
  function rejectReason(raw, envelope, provider) {
    var url = text(raw.videoUrl);
    var parsed = Sozo.Model.parseUrl(url);
    if (!parsed || parsed.userInfo !== undefined) return 'scheme';
    if (Sozo.Model.isPrivateHost(parsed.host)) return 'private-host';
    var type = (text(raw.type) || text(envelope.type)).toLowerCase();
    var ext = extension(parsed.path);
    if (BAD_TYPES[type]) return 'type';
    if (ext === 'mpd' || ext === 'html' || ext === 'htm' || ext === 'mkv') return 'path';
    if (parsed.path.indexOf('/embed/') >= 0 || parsed.path.indexOf('/e/') === 0) return 'path';
    if (hasDirective(raw, SOURCE_DIRECTIVES)) return 'directive';
    if (raw.accessible === false) return 'inaccessible';
    var declared = headerNames(envelope.headers, raw.headers);
    for (var i = 0; i < declared.length; i++) {
      if (provider.ignoreHeaders.indexOf(declared[i]) < 0) return 'header';
    }
    if (provider.hosts && !provider.hosts.test(parsed.host) && declared.length) return 'host';
    if (!HLS_TYPES[type] && !FILE_TYPES[type] && !MEDIA_EXT[ext]) return 'not-media';
    return null;
  }

  function isHls(type, url) {
    var parsed = Sozo.Model.parseUrl(url);
    return !!HLS_TYPES[type] || (!!parsed && extension(parsed.path) === 'm3u8');
  }

  function candidates(envelope) {
    var list = Array.isArray(envelope.videoSources) ? envelope.videoSources.slice() : [];
    var seen = {};
    var out = [];
    list.push({ videoUrl: envelope.videoUrl, top: true });
    list.push({ videoUrl: envelope.playerSrc, top: true });
    for (var i = 0; i < list.length; i++) {
      var s = list[i];
      var url = s && text(s.videoUrl);
      if (!url || seen['$' + url]) continue;
      seen['$' + url] = 1;
      out.push(s);
    }
    return out;
  }

  // {sources, rejected}. Only what the rules allow ever reaches a video element.
  function eligible(envelope, providerId) {
    var provider = Sozo.Providers.byId(providerId);
    var result = { sources: [], rejected: [] };
    if (!provider || !envelope || typeof envelope !== 'object') return result;
    var all = candidates(envelope);
    var envelopeBad = hasDirective(envelope, ENVELOPE_DIRECTIVES);
    var labels = {};
    for (var i = 0; i < all.length; i++) {
      var raw = all[i];
      var url = text(raw.videoUrl);
      var why = envelopeBad ? 'envelope' : rejectReason(raw, envelope, provider);
      if (why) {
        result.rejected.push({ url: url, reason: why });
        continue;
      }
      var type = (text(raw.type) || text(envelope.type)).toLowerCase();
      var quality = Sozo.Model.cleanText(raw.quality);
      var server = Sozo.Model.cleanText(raw.host);
      var label = quality && server && quality !== server ? quality + ' · ' + server : quality || server;
      var key = label || '#';
      labels[key] = (labels[key] || 0) + 1;
      if (labels[key] > 1 && label) label += ' (' + labels[key] + ')';
      result.sources.push({
        url: url, type: type, isHls: isHls(type, url), quality: quality, label: label,
        key: key + '/' + labels[key], isDefault: raw.isDefault === true, order: result.sources.length + 1
      });
    }
    return result;
  }

  // Contract section 5: never by height, as labels overstate it.
  function pickStart(list, remembered) {
    var i;
    if (remembered) for (i = 0; i < list.length; i++) if (list[i].quality === remembered) return i;
    for (i = 0; i < list.length; i++) if (list[i].isDefault) return i;
    return 0;
  }

  function engine(source, env) {
    if (!source.isHls) return 'file';
    if (env.isWebOS) return 'native';
    if (env.mse) return 'hlsjs';
    return env.nativeHls ? 'native' : 'none';
  }

  // hls.js ships desktop buffer limits: unbounded back buffer, 60 MB ahead and up to 10 minutes.
  // These are sized for a TV by reasoning, not measured on one.
  function hlsConfig(startPosition) {
    return {
      enableWorker: false, lowLatencyMode: false, progressive: false, startPosition: startPosition,
      backBufferLength: 30, maxBufferSize: 30000000, maxMaxBufferLength: 60
    };
  }

  function expiredStatus(status) {
    return !status || status === 401 || status === 403 || status === 410;
  }

  // The failure ladder for one title. fail() names the next step and the player carries it out, so
  // the order and the limits can be tested without a video element.
  // options: {env, hlsjsAllowed, isLive, canReresolve}
  function createLadder(sources, startIndex, options) {
    var o = options || {};
    var env = o.env || {};
    var list = sources;
    var index = startIndex || 0;
    var failed = {};
    var hlsjs = {};
    var failures = 0;
    var reresolved = false;

    function current() { return list[index] || null; }

    function nextUntried() {
      for (var n = 1; n <= list.length; n++) {
        var i = (index + n) % list.length;
        if (!failed[list[i].key]) return i;
      }
      return -1;
    }

    function moveOn(key) {
      if (key) failed[key] = true;
      failures++;
      if (failures >= MAX_SOURCES) return { action: 'chooser' };
      var i = nextUntried();
      if (i < 0) return { action: 'chooser' };
      index = i;
      return { action: 'next', index: i };
    }

    return {
      current: current,
      list: function () { return list; },
      index: function () { return index; },
      engine: function () {
        var cur = current();
        if (!cur) return 'none';
        return hlsjs[cur.key] ? 'hlsjs' : engine(cur, env);
      },
      // info: {started, status}. status is unknown for native playback, which cannot read it.
      fail: function (info) {
        var i = info || {};
        var cur = current();
        if (cur && cur.isHls && !hlsjs[cur.key] && engine(cur, env) === 'native' && env.isWebOS && env.mse && o.hlsjsAllowed) {
          hlsjs[cur.key] = true;
          return { action: 'hlsjs', index: index };
        }
        var mayBeExpired = i.started || expiredStatus(i.status);
        var deadChannel = o.isLive && !i.started;
        if (!reresolved && o.canReresolve && mayBeExpired && !deadChannel) {
          reresolved = true;
          return { action: 'reresolve' };
        }
        return moveOn(cur && cur.key);
      },
      // Fresh links after a re-resolve: same quality if it is still offered, else the next source.
      adopt: function (fresh, info) {
        var i = info || {};
        var previous = current();
        list = fresh;
        index = -1;
        for (var k = 0; k < list.length; k++) {
          if (previous && list[k].key === previous.key) { index = k; break; }
        }
        if (index >= 0 && !failed[previous.key] && (list[index].url !== previous.url || i.started)) {
          return { action: 'retry', index: index };
        }
        var step = moveOn(previous && previous.key);
        if (index < 0) index = 0;
        return step;
      },
      // The viewer chose: they get a fresh ladder from that source.
      select: function (i) {
        index = i;
        failed = {};
        failures = 0;
        reresolved = false;
      },
      // A long healthy stretch: a failure now is a new problem, not part of the old one.
      healthy: function () {
        failed = {};
        failures = 0;
        reresolved = false;
      }
    };
  }

  function resumeAt(saved, duration) {
    var p = Number(saved) || 0;
    var d = Number(duration) || 0;
    if (p < MIN_RESUME) return 0;
    if (d > 0 && (p / d >= FINISHED_AT || d - p < END_MARGIN)) return 0;
    return p;
  }

  // Below the minimum, the store deletes the entry. A series entry also holds which episode the
  // viewer reached, so a few seconds of another episode must not wipe it.
  function shouldWrite(position, duration, entry, isSerial) {
    var p = Number(position) || 0;
    var d = Number(duration) || 0;
    if (d > 0 && p / d >= FINISHED_AT) return true;
    if (p >= MIN_RESUME) return true;
    return !isSerial && !!entry;
  }

  function seekStep(burst) {
    if (burst <= 3) return 10;
    if (burst <= 8) return 20;
    if (burst <= 16) return 30;
    if (burst <= 30) return 60;
    return 120;
  }

  function seekTarget(base, delta, duration) {
    var t = (Number(base) || 0) + delta;
    var d = Number(duration) || 0;
    if (d > 0 && t > d - 2) t = Math.max(0, d - 2);
    return t < 0 ? 0 : t;
  }

  function nextEpisode(episodes, current) {
    if (!current) return null;
    var groups = Sozo.Model.groupSeasons(episodes);
    var flat = [];
    for (var g = 0; g < groups.length; g++) flat = flat.concat(groups[g].episodes);
    for (var i = 0; i < flat.length; i++) {
      if (!Sozo.Model.sameEpisode(flat[i], current)) continue;
      for (var j = i + 1; j < flat.length; j++) if (flat[j].mediaRef) return flat[j];
      return null;
    }
    return null;
  }

  var api = {
    TIMES: TIMES, MAX_SOURCES: MAX_SOURCES, MIN_RESUME: MIN_RESUME,
    eligible: eligible, pickStart: pickStart, engine: engine, hlsConfig: hlsConfig,
    createLadder: createLadder, resumeAt: resumeAt, shouldWrite: shouldWrite,
    seekStep: seekStep, seekTarget: seekTarget, nextEpisode: nextEpisode
  };
  Sozo.Playback = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
}(typeof window !== 'undefined' ? window : global));
