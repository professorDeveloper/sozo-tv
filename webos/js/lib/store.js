(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});

  var PREFIX = 'sozo.v1.';
  var MAX_LIST = 200;
  var MAX_CONTINUE = 60;
  var MIN_POSITION = 5;
  var FINISHED_AT = 0.95;
  var END_MARGIN = 30;

  function defaultStorage() {
    try { return root.localStorage || null; } catch (e) { return null; }
  }

  // Every read and write survives a missing, full or corrupt storage. Memory is the fallback.
  function create(storage) {
    var store = storage === undefined ? defaultStorage() : storage;
    var memory = {};

    function read(key, fallback) {
      if (Object.prototype.hasOwnProperty.call(memory, key)) return memory[key];
      var raw = null;
      try { raw = store ? store.getItem(PREFIX + key) : null; } catch (e) { raw = null; }
      if (raw === null || raw === undefined) return fallback;
      try {
        var parsed = JSON.parse(raw);
        return parsed === null || parsed === undefined ? fallback : parsed;
      } catch (e2) {
        return fallback;
      }
    }

    function tryWrite(key, value) {
      try {
        if (!store) return false;
        store.setItem(PREFIX + key, JSON.stringify(value));
        return true;
      } catch (e) {
        return false;
      }
    }

    function write(key, value) {
      if (tryWrite(key, value)) { delete memory[key]; return true; }
      // Full: shed the oldest history, then try once more.
      var history = key === 'continue' ? value : read('continue', []);
      if (Array.isArray(history) && history.length > 1) {
        var trimmed = history.slice(0, Math.ceil(history.length / 2));
        if (key === 'continue') value = trimmed;
        else tryWrite('continue', trimmed);
      }
      if (tryWrite(key, value)) { delete memory[key]; return true; }
      memory[key] = value;
      return false;
    }

    function remove(key) {
      delete memory[key];
      try { if (store) store.removeItem(PREFIX + key); } catch (e) { return; }
    }

    function validEntry(e) {
      if (!e || typeof e !== 'object') return false;
      if (typeof e.provider !== 'string' || typeof e.url !== 'string' || !e.url) return false;
      if (Sozo.Providers && !Sozo.Providers.byId(e.provider)) return false;
      if (Sozo.Filter && (Sozo.Filter.isAdultText(e.title) || Sozo.Filter.isAdultText(e.url))) return false;
      return true;
    }

    function readList(key) {
      var v = read(key, []);
      return Array.isArray(v) ? v.filter(validEntry) : [];
    }

    function indexOf(list, provider, url) {
      for (var i = 0; i < list.length; i++) {
        if (list[i].provider === provider && list[i].url === url) return i;
      }
      return -1;
    }

    function entryFrom(c) {
      return {
        provider: c.provider, url: c.url, title: c.title || '', thumbnail: c.thumbnail || '',
        year: c.year || '', isSerial: !!c.isSerial
      };
    }

    return {
      getSetting: function (name, fallback) {
        var s = read('settings', {});
        return s && typeof s === 'object' && Object.prototype.hasOwnProperty.call(s, name) ? s[name] : fallback;
      },
      setSetting: function (name, value) {
        var s = read('settings', {});
        if (!s || typeof s !== 'object' || Array.isArray(s)) s = {};
        s[name] = value;
        return write('settings', s);
      },

      myList: function () { return readList('mylist'); },
      inMyList: function (provider, url) { return indexOf(readList('mylist'), provider, url) >= 0; },
      toggleMyList: function (card) {
        var list = readList('mylist');
        var i = indexOf(list, card.provider, card.url);
        if (i >= 0) {
          list.splice(i, 1);
          write('mylist', list);
          return false;
        }
        list.unshift(entryFrom(card));
        if (list.length > MAX_LIST) list.length = MAX_LIST;
        write('mylist', list);
        return true;
      },
      clearMyList: function () { remove('mylist'); },

      continueList: function () {
        return readList('continue').filter(function (e) { return !e.finished || e.isSerial; });
      },
      progress: function (provider, url) {
        var list = readList('continue');
        var i = indexOf(list, provider, url);
        return i >= 0 ? list[i] : null;
      },
      // p: {provider, url, title, thumbnail, year, isSerial, position, duration, episode}
      saveProgress: function (p) {
        if (!p || !p.provider || !p.url) return false;
        var provider = Sozo.Providers && Sozo.Providers.byId(p.provider);
        if (provider && provider.kind === 'live') return false;
        var position = Number(p.position) || 0;
        var duration = Number(p.duration) || 0;
        // The same end rule as Playback.resumeAt, which starts such a position over.
        var finished = duration > 0 && (position / duration >= FINISHED_AT || (position >= MIN_POSITION && duration - position < END_MARGIN));
        var list = readList('continue');
        var i = indexOf(list, p.provider, p.url);
        if (i >= 0) list.splice(i, 1);
        if (finished && !p.isSerial) {
          write('continue', list);
          return true;
        }
        if (!finished && position < MIN_POSITION) {
          if (i < 0) return false;
          write('continue', list);
          return true;
        }
        var e = entryFrom(p);
        e.position = finished ? 0 : position;
        e.duration = duration;
        e.finished = finished;
        e.updatedAt = Date.now();
        if (p.episode) {
          e.episode = {
            mediaRef: p.episode.mediaRef || '', season: p.episode.season === undefined ? null : p.episode.season,
            episode: p.episode.episode === undefined ? null : p.episode.episode, label: p.episode.label || ''
          };
        }
        list.unshift(e);
        if (list.length > MAX_CONTINUE) list.length = MAX_CONTINUE;
        write('continue', list);
        return true;
      },
      removeProgress: function (provider, url) {
        var list = readList('continue');
        var i = indexOf(list, provider, url);
        if (i < 0) return;
        list.splice(i, 1);
        write('continue', list);
      },
      clearHistory: function () { remove('continue'); }
    };
  }

  var api = { create: create, PREFIX: PREFIX, MAX_LIST: MAX_LIST, MAX_CONTINUE: MAX_CONTINUE };
  Sozo.Store = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
}(typeof window !== 'undefined' ? window : global));
