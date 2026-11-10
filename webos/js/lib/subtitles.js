(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});

  // Contract section 7: rendered by the app as an overlay, never as markup.

  var MAX_BYTES = 2 * 1024 * 1024;
  var MAX_CUES = 20000;

  var NAMES = {
    en: 'English', eng: 'English', ru: 'Russian', rus: 'Russian', uz: 'Uzbek', uzb: 'Uzbek',
    es: 'Spanish', spa: 'Spanish', fr: 'French', fre: 'French', fra: 'French', de: 'German', ger: 'German', deu: 'German',
    it: 'Italian', ita: 'Italian', pt: 'Portuguese', por: 'Portuguese', pob: 'Portuguese (Brazil)',
    tr: 'Turkish', tur: 'Turkish', ar: 'Arabic', ara: 'Arabic', hi: 'Hindi', hin: 'Hindi',
    ja: 'Japanese', jpn: 'Japanese', ko: 'Korean', kor: 'Korean', zh: 'Chinese', zho: 'Chinese', chi: 'Chinese',
    pl: 'Polish', pol: 'Polish', uk: 'Ukrainian', ukr: 'Ukrainian', nl: 'Dutch', dut: 'Dutch', nld: 'Dutch'
  };

  var LANGUAGE_WORDS = {
    en: /^(en|eng|english)\b/i,
    ru: /^(ru|rus|russian|русский)/i,
    uz: /^(uz|uzb|uzbek|o['ʻ’`]?zbek)/i
  };

  function str(v) {
    return typeof v === 'string' ? v.trim() : '';
  }

  function prettyLabel(raw) {
    var clean = Sozo.Model.cleanText(raw);
    var key = clean.toLowerCase();
    return Object.prototype.hasOwnProperty.call(NAMES, key) ? NAMES[key] : clean;
  }

  function matchesLanguage(label, lang) {
    var re = LANGUAGE_WORDS[lang];
    return !!re && re.test(label);
  }

  function emptyHeaders(h) {
    if (!h || typeof h !== 'object') return true;
    for (var k in h) if (Object.prototype.hasOwnProperty.call(h, k)) return false;
    return true;
  }

  // Skips what a web player cannot fetch, de-duplicates, and lists the viewer's language first.
  function eligible(envelope, uiLang) {
    var list = envelope && Array.isArray(envelope.subtitles) ? envelope.subtitles : [];
    var seen = {};
    var labels = {};
    var mine = [];
    var rest = [];
    for (var i = 0; i < list.length; i++) {
      var s = list[i];
      if (!s || typeof s !== 'object') continue;
      var file = str(s.file) || str(s.url) || str(s.src);
      if (!Sozo.Model.isPublicUrl(file) || /\.m3u8(\?|#|$)/i.test(file)) continue;
      if (!emptyHeaders(s.headers) || s.cipher || seen['$' + file]) continue;
      seen['$' + file] = 1;
      var label = prettyLabel(str(s.label) || str(s.lang) || str(s.language));
      if (Sozo.Filter && Sozo.Filter.isAdultText(label)) continue;
      var base = label || 'Subtitles';
      labels[base] = (labels[base] || 0) + 1;
      var track = {
        file: file, base: base, label: labels[base] > 1 ? base + ' ' + labels[base] : base,
        isDefault: s['default'] === true
      };
      (matchesLanguage(base, uiLang) ? mine : rest).push(track);
    }
    return mine.concat(rest);
  }

  // remembered is the viewer's own earlier choice: 'off', a label, or null when they never chose.
  function pickDefault(tracks, uiLang, remembered) {
    var i;
    if (remembered === 'off') return null;
    if (remembered) {
      for (i = 0; i < tracks.length; i++) if (tracks[i].base === remembered) return tracks[i];
    }
    for (i = 0; i < tracks.length; i++) {
      if (tracks[i].isDefault && matchesLanguage(tracks[i].base, uiLang)) return tracks[i];
    }
    return null;
  }

  function seconds(stamp) {
    var m = /^(?:(\d+):)?(\d{1,2}):(\d{1,2})[.,](\d{1,3})$/.exec(stamp);
    if (!m) return null;
    var ms = (m[4] + '00').slice(0, 3);
    return (m[1] ? parseInt(m[1], 10) * 3600 : 0) + parseInt(m[2], 10) * 60 + parseInt(m[3], 10) + parseInt(ms, 10) / 1000;
  }

  function cleanCue(lines) {
    var joined = lines.join('\n').replace(/\{\\[^{}]*\}/g, '');
    return Sozo.Model.cleanText(joined);
  }

  // WebVTT or SRT as plain text. Returns null for anything that is not subtitles, such as a web page.
  function parse(raw) {
    if (typeof raw !== 'string' || !raw || raw.length > MAX_BYTES) return null;
    var body = raw.replace(/^﻿/, '').replace(/\r\n?/g, '\n');
    if (/^\s*<(!doctype|html|\?xml|head|body)\b/i.test(body)) return null;
    var blocks = body.split(/\n\s*\n/);
    var cues = [];
    for (var b = 0; b < blocks.length && cues.length < MAX_CUES; b++) {
      var lines = blocks[b].replace(/^\n+/, '').split('\n');
      var at = -1;
      for (var l = 0; l < lines.length && l < 3; l++) {
        if (lines[l].indexOf('-->') >= 0) { at = l; break; }
      }
      if (at < 0) continue;
      var parts = lines[at].split('-->');
      var start = seconds(str(parts[0]));
      var end = seconds(str(parts[1]).split(/\s+/)[0]);
      if (start === null || end === null || end <= start) continue;
      var cueText = cleanCue(lines.slice(at + 1));
      if (cueText) cues.push({ start: start, end: end, text: cueText });
    }
    if (!cues.length) return null;
    cues.sort(function (x, y) { return x.start - y.start; });
    return cues;
  }

  // Text for time t. Overlapping cues are shown together, as a player would.
  function textAt(cues, t) {
    if (!cues || !cues.length) return '';
    var lo = 0;
    var hi = cues.length - 1;
    var last = -1;
    while (lo <= hi) {
      var mid = (lo + hi) >> 1;
      if (cues[mid].start <= t) { last = mid; lo = mid + 1; } else hi = mid - 1;
    }
    var out = [];
    for (var i = last; i >= 0 && i > last - 6; i--) {
      if (cues[i].end > t) out.unshift(cues[i].text);
    }
    return out.join('\n');
  }

  var api = { MAX_BYTES: MAX_BYTES, eligible: eligible, pickDefault: pickDefault, parse: parse, textAt: textAt, matchesLanguage: matchesLanguage };
  Sozo.Subtitles = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
}(typeof window !== 'undefined' ? window : global));
