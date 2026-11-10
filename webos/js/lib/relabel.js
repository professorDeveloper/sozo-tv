(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});

  var SERVICE = 'luna://com.soplay.sozo.webos.service/relay';

  // webOS only: the bundled service relays HLS over loopback with corrected content types,
  // so the native player can play sources whose hosts label segments as text/html.
  function create(options) {
    var o = options || {};
    var Bridge = o.Bridge || root.PalmServiceBridge;
    var base = null;
    var bridge = null;
    var started = false;

    function start() {
      if (started || !o.isWebOS || typeof Bridge !== 'function') return;
      started = true;
      try {
        bridge = new Bridge();
        bridge.onservicecallback = function (text) {
          var m = null;
          try { m = JSON.parse(text); } catch (e) { m = null; }
          if (m && m.returnValue && m.port && /^[0-9a-f]+$/.test(m.token || '')) {
            base = 'http://127.0.0.1:' + m.port + '/' + m.token + '/p?u=';
          } else {
            base = null;
          }
        };
        bridge.call(SERVICE, JSON.stringify({ subscribe: true }));
      } catch (e) {
        bridge = null;
        base = null;
      }
    }

    return {
      start: start,
      ready: function () { return base !== null; },
      // URL of the relay's track list for a Matroska file, or null when the relay is not up.
      tracksUrl: function (u, headers) {
        if (base === null) return null;
        var h = headers && typeof headers === 'object' && Object.keys(headers).length ? '&h=' + encodeURIComponent(JSON.stringify(headers)) : '';
        return base.replace(/\/p\?u=$/, '/t?u=') + encodeURIComponent(u) + h;
      },
      // headers: optional {name: value} the relay sends upstream; it keeps only names it allows.
      wrap: function (u, headers) {
        if (base === null) return u;
        var h = headers && typeof headers === 'object' && Object.keys(headers).length ? '&h=' + encodeURIComponent(JSON.stringify(headers)) : '';
        return base + encodeURIComponent(u) + h;
      }
    };
  }

  var api = { create: create, SERVICE: SERVICE };
  Sozo.Relabel = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
}(typeof window !== 'undefined' ? window : global));
