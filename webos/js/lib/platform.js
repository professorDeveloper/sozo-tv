(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});

  function isWebOS(userAgent) {
    return /Web0S|webOS/.test(userAgent || '');
  }

  // Returns the route taken, so tests and callers can tell a no-op from a real exit.
  function exit(env) {
    var w = env || root;
    try {
      if (w.webOS && typeof w.webOS.platformBack === 'function') { w.webOS.platformBack(); return 'webOS'; }
      if (w.PalmSystem && typeof w.PalmSystem.platformBack === 'function') { w.PalmSystem.platformBack(); return 'PalmSystem'; }
      if (typeof w.close === 'function') { w.close(); return 'close'; }
    } catch (e) {
      try { if (typeof w.close === 'function') { w.close(); return 'close'; } } catch (e2) { return 'none'; }
    }
    return 'none';
  }

  var api = { isWebOS: isWebOS, exit: exit };
  Sozo.Platform = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
}(typeof window !== 'undefined' ? window : global));
