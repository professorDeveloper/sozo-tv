(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});

  var BASE = 'https://apisozo.azamov.me/api';
  var TIMEOUT = 30000;
  var MEDIA_TIMEOUT = 75000;
  var FORBIDDEN_PARAMS = { nsfw: true, adult: true };

  function buildUrl(base, path, params) {
    var parts = [];
    if (params) {
      for (var k in params) {
        if (!Object.prototype.hasOwnProperty.call(params, k)) continue;
        if (FORBIDDEN_PARAMS[k.toLowerCase()]) continue;
        if (params[k] === null || params[k] === undefined || params[k] === '') continue;
        parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(params[k]));
      }
    }
    return base + '/contents/' + path + (parts.length ? '?' + parts.join('&') : '');
  }

  // Seconds or an HTTP date. null when absent or unreadable.
  function parseRetryAfter(value, nowMs) {
    if (value === null || value === undefined) return null;
    var s = String(value).replace(/^\s+|\s+$/g, '');
    if (!s) return null;
    if (/^\d+$/.test(s)) return parseInt(s, 10);
    var when = Date.parse(s);
    if (isNaN(when)) return null;
    return Math.max(0, Math.ceil((when - (nowMs === undefined ? Date.now() : nowMs)) / 1000));
  }

  function headerFrom(all, name) {
    var lines = String(all || '').split(/\r?\n/);
    var wanted = name.toLowerCase() + ':';
    for (var i = 0; i < lines.length; i++) {
      if (lines[i].toLowerCase().indexOf(wanted) === 0) return lines[i].slice(wanted.length);
    }
    return null;
  }

  function httpError(status, bodyText, retryAfter) {
    var code = null;
    try {
      var body = JSON.parse(bodyText);
      if (body && typeof body.code === 'string') code = body.code;
    } catch (e) { code = null; }
    return { kind: 'http', status: status, code: code, retryAfter: retryAfter === undefined ? null : retryAfter };
  }

  // Delay before the single automatic retry, or null for none. retryAfter is null when the header
  // is absent or CORS hides it: the API does not expose it, so a packaged app usually sees null.
  function retryDelay(err) {
    if (err.kind === 'offline' || err.kind === 'aborted') return null;
    if (err.kind !== 'http') return 800;
    var s = err.status;
    if (s === 429 || s === 503) {
      if (err.code) return null;
      if (err.retryAfter === null || err.retryAfter === undefined) return 3000;
      return err.retryAfter <= 5 ? Math.max(500, err.retryAfter * 1000) : null;
    }
    if (s === 500 || s === 502 || s === 504 || (s >= 520 && s <= 530)) return 1000;
    return null;
  }

  // i18n key prefix for the viewer's message. Backend text is never shown.
  function errorKey(err) {
    if (!err) return 'err.generic';
    if (err.kind === 'offline') return 'err.offline';
    if (err.kind === 'timeout') return 'err.timeout';
    if (err.kind === 'network') return 'err.network';
    if (err.kind === 'badData') return 'err.badData';
    if (err.kind === 'adult') return 'err.notAvailable';
    var s = err.status;
    if (err.code === 'SOURCE_REGION_LOCKED' || err.code === 'GEO_BLOCKED' || s === 451) return 'err.region';
    if (err.code === 'SOURCE_RUNS_ON_DEVICE' || err.code === 'APP_UPDATE_REQUIRED' || s === 409 || s === 426 || s === 428) return 'err.device';
    if (err.code === 'SOURCE_REMOVED' || err.code === 'SOURCE_DISABLED' || s === 410) return 'err.gone';
    if (s === 429) return 'err.rate';
    if (s === 404) return 'err.notFound';
    if (s === 400) return 'err.request';
    if (s >= 500) return 'err.unavailable';
    return 'err.generic';
  }

  function create(options) {
    var o = options || {};
    var base = o.base || BASE;
    var newXhr = o.xhr || function () { return new root.XMLHttpRequest(); };
    var setT = o.setTimeout || function (f, t) { return setTimeout(f, t); };
    var clearT = o.clearTimeout || function (id) { clearTimeout(id); };
    var online = o.isOnline || function () {
      return !(root.navigator && root.navigator.onLine === false);
    };
    var onStatus = o.onStatus || function () {};

    function once(url, timeoutMs, cb) {
      var xhr = newXhr();
      var settled = false;
      var timer = null;

      function finish(err, data) {
        if (settled) return;
        settled = true;
        if (timer !== null) clearT(timer);
        cb(err, data);
      }

      xhr.onload = function () {
        var status = xhr.status;
        if (status >= 200 && status < 300) {
          var parsed;
          try { parsed = JSON.parse(xhr.responseText); } catch (e) {
            finish({ kind: 'badData', status: status });
            return;
          }
          if (parsed === null || typeof parsed !== 'object') {
            finish({ kind: 'badData', status: status });
            return;
          }
          finish(null, parsed);
          return;
        }
        var header = null;
        try { header = headerFrom(xhr.getAllResponseHeaders(), 'retry-after'); } catch (e2) { header = null; }
        finish(httpError(status, xhr.responseText, parseRetryAfter(header)));
      };
      xhr.onerror = function () { finish({ kind: online() ? 'network' : 'offline' }); };
      xhr.ontimeout = function () { finish({ kind: 'timeout' }); };

      timer = setT(function () {
        try { xhr.abort(); } catch (e) { timer = null; }
        finish({ kind: 'timeout' });
      }, timeoutMs);

      try {
        xhr.open('GET', url, true);
        xhr.setRequestHeader('Accept', 'application/json');
        xhr.setRequestHeader('Accept-Language', 'en');
        xhr.send();
      } catch (e3) {
        finish({ kind: 'network' });
      }
      return { abort: function () { settled = true; if (timer !== null) clearT(timer); try { xhr.abort(); } catch (e) { return; } } };
    }

    // cb(err, data) is called once, never after abort().
    function get(path, params, opts, cb) {
      var aborted = false;
      var current = null;
      var waiter = null;
      var timeoutMs = (opts && opts.timeout) || TIMEOUT;
      var url = buildUrl(base, path, params);

      function attempt(n) {
        if (!online()) {
          onStatus(false);
          waiter = setT(function () { waiter = null; if (!aborted) cb({ kind: 'offline' }); }, 0);
          return;
        }
        current = once(url, timeoutMs, function (err, data) {
          current = null;
          if (aborted) return;
          if (!err) { onStatus(true); cb(null, data); return; }
          if (err.kind === 'network' || err.kind === 'timeout') onStatus(online());
          var delay = n === 0 ? retryDelay(err) : null;
          if (delay === null) { cb(err); return; }
          waiter = setT(function () { waiter = null; if (!aborted) attempt(n + 1); }, delay);
        });
      }
      attempt(0);
      return {
        abort: function () {
          aborted = true;
          if (current) current.abort();
          if (waiter !== null) clearT(waiter);
        }
      };
    }

    // A second attempt with the trailing slash toggled, per contract section 4. Network failures
    // are not retried here because get() has already tried twice.
    function withSlashRetry(path, provider, url, extra, isEmpty, cb) {
      var handle = { abort: function () {} };
      function params(u) {
        var p = { provider: provider, url: u };
        for (var k in extra) if (Object.prototype.hasOwnProperty.call(extra, k)) p[k] = extra[k];
        return p;
      }
      handle.inner = get(path, params(url), null, function (err, data) {
        var toggled = Sozo.Model.toggleSlash(url);
        var worthRetry = toggled && (err ? err.kind === 'http' && !err.code : isEmpty(data));
        if (!worthRetry) { cb(err, data); return; }
        handle.inner = get(path, params(toggled), null, function (err2, data2) {
          if (!err2 && !isEmpty(data2)) { cb(null, data2); return; }
          if (err) cb(err); else cb(null, data);
        });
      });
      handle.abort = function () { handle.inner.abort(); };
      return handle;
    }

    return {
      get: get,
      providers: function (cb) { return get('providers', null, null, cb); },
      health: function (cb) { return get('providers/health', null, null, cb); },
      home: function (provider, cb) { return get('home', { provider: provider }, null, cb); },
      // path comes from Model.listPath, for example "latest" or "category/popular".
      list: function (path, provider, page, cb) { return get(path, { provider: provider, page: page }, null, cb); },
      categories: function (provider, cb) { return get('categories', { provider: provider }, null, cb); },
      genres: function (provider, cb) { return get('genres', { provider: provider }, null, cb); },
      search: function (provider, q, page, cb) { return get('search', { provider: provider, q: q, page: page }, null, cb); },
      detail: function (provider, url, cb) {
        return withSlashRetry('detail', provider, url, {}, function (d) { return !d || !d.title; }, cb);
      },
      episodes: function (provider, url, page, cb) {
        return withSlashRetry('episodes', provider, url, { page: page, size: 100, sort: 'asc' }, function (d) { return !d; }, cb);
      },
      media: function (provider, ref, lang, cb) {
        return get('media', { provider: provider, ref: ref, lang: lang }, { timeout: MEDIA_TIMEOUT }, function (err, data) {
          if (!err && data && data.error && !(data.videoSources && data.videoSources.length)) {
            cb({ kind: 'http', status: 503, code: null, retryAfter: null });
            return;
          }
          cb(err, data);
        });
      }
    };
  }

  var api = { create: create, BASE: BASE, TIMEOUT: TIMEOUT, MEDIA_TIMEOUT: MEDIA_TIMEOUT, buildUrl: buildUrl, parseRetryAfter: parseRetryAfter, retryDelay: retryDelay, errorKey: errorKey, httpError: httpError };
  Sozo.Api = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
}(typeof window !== 'undefined' ? window : global));
