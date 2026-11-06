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
      wrap: function (u) { return base === null ? u : base + encodeURIComponent(u); }
    };
  }

  var api = { create: create, SERVICE: SERVICE };
  Sozo.Relabel = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
}(typeof window !== 'undefined' ? window : global));
