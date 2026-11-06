(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});

  // Allowlist and evidence: contract section 8 and 9. Do not add an id without re-running the probes.
  var LIST = [
    { id: 'uzbeklar', kind: 'vod', search: true, viewAll: true, related: true,
      hosts: /^[a-z0-9]+\.uzbeklar\.biz$/, ignoreHeaders: ['referer', 'user-agent'], hlsjs: false },
    { id: 'idub', kind: 'vod', search: true, viewAll: true, related: true,
      hosts: /^v\.mover\.uz$/, ignoreHeaders: [], hlsjs: false },
    { id: 'vidapi', kind: 'vod', search: false, viewAll: false, related: false,
      hosts: null, ignoreHeaders: ['referer', 'origin', 'user-agent'], hlsjs: true },
    { id: 'anilibria', kind: 'vod', search: true, viewAll: true, related: true,
      hosts: /^cache\.libria\.fun$/, ignoreHeaders: ['user-agent'], hlsjs: true },
    { id: 'animevost', kind: 'vod', search: true, viewAll: true, related: true,
      hosts: /^video\.animetop\.info$/, ignoreHeaders: ['referer', 'user-agent'], hlsjs: false },
    { id: 'supercartoons', kind: 'vod', search: true, viewAll: true, related: true,
      hosts: /^ww\.supercartoons\.net$/, ignoreHeaders: ['referer', 'user-agent'], hlsjs: false },
    { id: 'worldlivetv', kind: 'live', search: true, viewAll: false, related: false,
      hosts: null, ignoreHeaders: ['user-agent'], hlsjs: true },
    { id: 'livetvindia', kind: 'live', search: true, viewAll: false, related: false,
      hosts: null, ignoreHeaders: ['user-agent'], hlsjs: true },
    { id: 'sportstv', kind: 'live', search: true, viewAll: false, related: false,
      hosts: null, ignoreHeaders: ['user-agent'], hlsjs: true }
  ];

  var DEFAULT_ID = 'uzbeklar';

  function byId(id) {
    for (var i = 0; i < LIST.length; i++) {
      if (LIST[i].id === id) return LIST[i];
    }
    return null;
  }

  function healthOf(id, health) {
    var sources = health && health.sources;
    var s = sources && Object.prototype.hasOwnProperty.call(sources, id) ? sources[id] : null;
    if (!s) return 'unknown';
    if (s.ok === false) return 'down';
    if (s.unchecked) return 'unknown';
    return s.ok === true ? 'ok' : 'unknown';
  }

  // A provider shows only if it is allowlisted and the live API still offers it.
  function visible(liveItems, health) {
    var out = [];
    if (!liveItems || !liveItems.length) return out;
    for (var i = 0; i < LIST.length; i++) {
      var p = LIST[i];
      for (var j = 0; j < liveItems.length; j++) {
        var live = liveItems[j];
        if (!live || live.id !== p.id) continue;
        if (live.mode !== 'server' && live.mode !== 'hybrid') break;
        if (live.browseOnly === true || live.nsfw) break;
        if (Sozo.Filter && Sozo.Filter.isAdultText(live.name)) break;
        out.push({
          id: p.id, name: live.name || p.id, kind: p.kind, lang: live.lang || '',
          logo: live.logo || live.image || '', search: p.search, viewAll: p.viewAll,
          related: p.related, health: healthOf(p.id, health)
        });
        break;
      }
    }
    var healthy = [];
    var down = [];
    for (var k = 0; k < out.length; k++) (out[k].health === 'down' ? down : healthy).push(out[k]);
    return healthy.concat(down);
  }

  function pick(visibleList, savedId) {
    var i;
    for (i = 0; i < visibleList.length; i++) if (visibleList[i].id === savedId) return visibleList[i];
    for (i = 0; i < visibleList.length; i++) if (visibleList[i].id === DEFAULT_ID) return visibleList[i];
    return visibleList[0] || null;
  }

  var api = { list: LIST, DEFAULT_ID: DEFAULT_ID, byId: byId, healthOf: healthOf, visible: visible, pick: pick };
  Sozo.Providers = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
}(typeof window !== 'undefined' ? window : global));
