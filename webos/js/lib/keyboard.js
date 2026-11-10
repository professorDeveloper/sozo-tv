(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});

  var MAX_LEN = 60;
  var MIN_SEARCH = 2;
  var COLS = 8;

  var CHARS = {
    latin: "abcdefghijklmnopqrstuvwxyz'-",
    cyrillic: 'абвгдеёжзийклмнопрстуфхцчшщъыьэюя',
    digits: '1234567890'
  };

  function chunk(text) {
    var rows = [];
    for (var i = 0; i < text.length; i += COLS) {
      var row = [];
      var part = text.slice(i, i + COLS);
      for (var j = 0; j < part.length; j++) row.push({ id: part.charAt(j), label: part.charAt(j), ch: part.charAt(j) });
      rows.push(row);
    }
    return rows;
  }

  function layout(mode) {
    var rows = chunk(CHARS[mode] || CHARS.latin);
    rows = rows.concat(chunk(CHARS.digits));
    rows.push([
      { id: 'mode', action: 'mode', wide: 2 },
      { id: 'space', action: 'space', wide: 3 },
      { id: 'backspace', action: 'backspace', wide: 3 }
    ]);
    return rows;
  }

  function nextMode(mode) {
    return mode === 'cyrillic' ? 'latin' : 'cyrillic';
  }

  function type(query, ch) {
    var q = query || '';
    if (q.length >= MAX_LEN) return q;
    if (ch === ' ' && (q === '' || q.charAt(q.length - 1) === ' ')) return q;
    return q + ch;
  }

  function backspace(query) {
    return (query || '').slice(0, -1);
  }

  function normalize(query) {
    return (query || '').replace(/\s+/g, ' ').replace(/^\s+|\s+$/g, '');
  }

  function shouldSearch(query) {
    return normalize(query).length >= MIN_SEARCH;
  }

  function debouncer(ms, setT, clearT) {
    var st = setT || function (f, t) { return setTimeout(f, t); };
    var ct = clearT || function (id) { clearTimeout(id); };
    var id = null;
    return {
      call: function (fn) {
        if (id !== null) ct(id);
        id = st(function () { id = null; fn(); }, ms);
      },
      cancel: function () {
        if (id !== null) ct(id);
        id = null;
      }
    };
  }

  var api = { MAX_LEN: MAX_LEN, MIN_SEARCH: MIN_SEARCH, COLS: COLS, layout: layout, nextMode: nextMode, type: type, backspace: backspace, normalize: normalize, shouldSearch: shouldSearch, debouncer: debouncer };
  Sozo.Keyboard = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
}(typeof window !== 'undefined' ? window : global));
