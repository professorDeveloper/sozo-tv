(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});

  function str(v) {
    if (v === null || v === undefined) return '';
    if (typeof v === 'string') {
      var s = v.trim();
      return s === 'null' || s === 'undefined' ? '' : s;
    }
    return typeof v === 'number' && isFinite(v) ? String(v) : '';
  }

  var ENTITIES = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ', laquo: '«', raquo: '»', mdash: '—', ndash: '–' };

  // Output goes into textContent only. This just removes markup that a source left in.
  function cleanText(v) {
    return str(v)
      .replace(/<[^<>]*>/g, ' ')
      .replace(/&(#\d+|[a-z]+);/gi, function (m, name) {
        if (name.charAt(0) === '#') {
          var code = parseInt(name.slice(1), 10);
          return code > 0 && code < 65536 ? String.fromCharCode(code) : ' ';
        }
        var r = ENTITIES[name.toLowerCase()];
        return r === undefined ? m : r;
      })
      .replace(/[ \t]+/g, ' ')
      .replace(/[ \t]*[\r\n]+\s*/g, '\n')
      .trim();
  }

  function cardUrl(card) {
    return card ? str(card.contentUrl) || str(card.slug) : '';
  }

  function cardKey(provider, url) {
    return provider + '|' + url;
  }

  function year(v) {
    var s = str(v);
    return /^\d{4}$/.test(s) && s !== '0000' ? s : '';
  }

  function rating(v) {
    var n = typeof v === 'number' ? v : parseFloat(v);
    return isFinite(n) && n > 0 ? n.toFixed(1) : '';
  }

  // The browser's reading of the host wins over the pattern's, as it decides where the request goes.
  function parseUrl(raw) {
    if (/[\\\t\r\n]/.test(raw)) return null;
    var m = /^(https?):\/\/(?:([^\/?#@]*)@)?(\[[^\]]*\]|[^\/?#:]*)(?::(\d*))?([^?#]*)/i.exec(raw);
    if (!m || !m[3]) return null;
    var host = m[3].toLowerCase();
    var userInfo = m[2];
    if (typeof root.URL === 'function') {
      try {
        var u = new root.URL(raw);
        host = u.hostname.toLowerCase();
        if (userInfo === undefined && (u.username || u.password)) userInfo = u.username;
      } catch (e) {
        return null;
      }
    }
    return { scheme: m[1].toLowerCase(), userInfo: userInfo, host: host, path: m[5] || '/' };
  }

  function isPrivateHost(host) {
    var name = host.replace(/\.+$/, '');
    if (!name || name === 'localhost' || /\.(localhost|local|internal)$/.test(name)) return true;
    if (name.charAt(0) === '[') return /^\[(::1?|f[cd]|fe[89ab]|::ffff:)/.test(name);
    // A host ending in a number or hex is read as IPv4 by browsers, as in 127.1, 2130706433, 0x7f.0.0.1 and 0177.0.0.1.
    if (!/(^|\.)(\d+|0x[0-9a-f]*)$/.test(name)) return false;
    var m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(name);
    if (!m || /(^|\.)0\d/.test(name) || +m[1] > 255 || +m[2] > 255 || +m[3] > 255 || +m[4] > 255) return true;
    var a = +m[1];
    var b = +m[2];
    return a === 0 || a === 10 || a === 127 || (a === 169 && b === 254) ||
      (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168);
  }

  function isPublicUrl(raw) {
    var p = parseUrl(raw);
    return !!p && p.userInfo === undefined && !isPrivateHost(p.host);
  }

  function safeImage(url) {
    var s = str(url);
    if (s.indexOf('//') === 0) s = 'https:' + s;
    return /^https?:\/\/[^\s]+$/i.test(s) && isPublicUrl(s) ? s : '';
  }

  // Smaller TMDB renditions save a lot of memory on old TVs.
  function posterUrl(url) {
    return safeImage(url).replace(/(image\.tmdb\.org\/t\/p\/)(w500|w780|original)\//, '$1w342/');
  }

  function bannerUrl(url) {
    return safeImage(url).replace(/(image\.tmdb\.org\/t\/p\/)original\//, '$1w1280/');
  }

  function toggleSlash(url) {
    var s = str(url);
    if (!/^https?:\/\//i.test(s) || /[?#]/.test(s)) return null;
    return s.charAt(s.length - 1) === '/' ? s.slice(0, -1) : s + '/';
  }

  var LIST_TYPES = { latest: true, category: true, year: true, genre: true };

  function listPath(type, slug) {
    if (!Object.prototype.hasOwnProperty.call(LIST_TYPES, type)) return null;
    var sl = str(slug);
    if (type === 'latest') return 'latest';
    return sl ? type + '/' + encodeURIComponent(sl) : null;
  }

  function nameList(list) {
    var out = [];
    if (typeof list === 'string') list = list.split(',');
    if (!Array.isArray(list)) return out;
    for (var i = 0; i < list.length; i++) {
      var it = list[i];
      var name = cleanText(typeof it === 'object' && it ? (it.name || it.label || it.title) : it);
      if (name) out.push(name);
    }
    return out;
  }

  function seasonOf(ep) {
    if (!ep) return null;
    var raw = ep.season !== undefined && ep.season !== null ? ep.season : ep.seasonNumber;
    var n = parseInt(raw, 10);
    return isFinite(n) ? n : null;
  }

  // Keeps server order inside a season. A missing season joins a single unnamed group.
  function groupSeasons(episodes) {
    var groups = [];
    var map = {};
    var list = Array.isArray(episodes) ? episodes : [];
    for (var i = 0; i < list.length; i++) {
      var s = seasonOf(list[i]);
      var key = s === null ? 'none' : 's' + s;
      if (!map[key]) {
        map[key] = { season: s, episodes: [] };
        groups.push(map[key]);
      }
      map[key].episodes.push(list[i]);
    }
    groups.sort(function (a, b) {
      if (a.season === null) return b.season === null ? 0 : 1;
      if (b.season === null) return -1;
      return a.season - b.season;
    });
    return groups;
  }

  function episodeNumber(ep) {
    return ep ? str(ep.episode) : '';
  }

  function isFilmEnvelope(resp) {
    return !!resp && resp.isSerial !== true && Array.isArray(resp.videoSources) && resp.videoSources.length > 0;
  }

  function hasMore(page, totalPages) {
    var p = parseInt(page, 10) || 1;
    var t = parseInt(totalPages, 10) || 1;
    return p < t;
  }

  function mergeCards(existing, incoming) {
    var seen = {};
    var out = existing.slice();
    var i;
    for (i = 0; i < out.length; i++) seen['$' + cardUrl(out[i])] = true;
    for (i = 0; i < incoming.length; i++) {
      var k = '$' + cardUrl(incoming[i]);
      if (!seen[k]) { seen[k] = true; out.push(incoming[i]); }
    }
    return out;
  }

  function sameEpisode(ep, saved) {
    if (!ep || !saved) return false;
    if (saved.mediaRef && ep.mediaRef) return ep.mediaRef === saved.mediaRef;
    return str(ep.episode) === str(saved.episode) && seasonOf(ep) === seasonOf(saved);
  }

  // What Play should do for a series, given a saved progress entry.
  // incomplete: later pages are still to come, so a saved episode that is missing, or a finished one
  // with nothing after it yet, is an open question. The answer is {pending: true}.
  function resumeTarget(episodes, entry, incomplete) {
    var list = Array.isArray(episodes) ? episodes : [];
    if (!list.length) return null;
    if (entry && entry.episode) {
      for (var i = 0; i < list.length; i++) {
        if (!sameEpisode(list[i], entry.episode)) continue;
        if (entry.finished) {
          if (i + 1 < list.length) return { episode: list[i + 1], index: i + 1, position: 0, resume: true };
          if (incomplete) return { pending: true, episode: null, position: 0, resume: false };
          return { episode: list[0], index: 0, position: 0, resume: false };
        }
        return { episode: list[i], index: i, position: entry.position || 0, resume: (entry.position || 0) > 0 };
      }
      if (incomplete) return { pending: true, episode: null, position: 0, resume: false };
    }
    return { episode: list[0], index: 0, position: 0, resume: false };
  }

  function seriesDone(episodes, entry) {
    var list = Array.isArray(episodes) ? episodes : [];
    return !!entry && !!entry.finished && list.length > 0 && sameEpisode(list[list.length - 1], entry.episode);
  }

  function clock(seconds) {
    var s = Math.max(0, Math.floor(Number(seconds) || 0));
    var h = Math.floor(s / 3600);
    var m = Math.floor((s % 3600) / 60);
    var r = s % 60;
    var mm = h > 0 && m < 10 ? '0' + m : String(m);
    return (h > 0 ? h + ':' : '') + mm + ':' + (r < 10 ? '0' + r : r);
  }

  function progressRatio(position, duration) {
    var p = Number(position) || 0;
    var d = Number(duration) || 0;
    return d > 0 ? Math.max(0, Math.min(1, p / d)) : 0;
  }

  var api = {
    str: str, cleanText: cleanText, cardUrl: cardUrl, cardKey: cardKey, year: year, rating: rating,
    parseUrl: parseUrl, isPrivateHost: isPrivateHost, isPublicUrl: isPublicUrl,
    safeImage: safeImage, posterUrl: posterUrl, bannerUrl: bannerUrl, toggleSlash: toggleSlash,
    listPath: listPath, nameList: nameList, seasonOf: seasonOf, groupSeasons: groupSeasons,
    episodeNumber: episodeNumber, isFilmEnvelope: isFilmEnvelope, hasMore: hasMore, mergeCards: mergeCards,
    sameEpisode: sameEpisode, resumeTarget: resumeTarget, seriesDone: seriesDone, clock: clock, progressRatio: progressRatio
  };
  Sozo.Model = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
}(typeof window !== 'undefined' ? window : global));
