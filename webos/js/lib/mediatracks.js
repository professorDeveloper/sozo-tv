(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});

  var MEDIA = 'luna://com.webos.media/';
  var TIMEOUT = 15000;
  var THREE = { spa: 'es', ger: 'de', deu: 'de', chi: 'zh', zho: 'zh', fre: 'fr', fra: 'fr', per: 'fa', fas: 'fa', dut: 'nl', nld: 'nl', gre: 'el', ell: 'el', cze: 'cs', ces: 'cs', rum: 'ro', ron: 'ro', slo: 'sk', slk: 'sk', ice: 'is', isl: 'is', nno: 'nn', nob: 'nb', bul: 'bg', est: 'et', swe: 'sv', dan: 'da', pol: 'pl', ukr: 'uk', hrv: 'hr', lit: 'lt', lav: 'lv', slv: 'sl', srp: 'sr', heb: 'he', ind: 'id', may: 'ms', msa: 'ms', vie: 'vi', kor: 'ko', mal: 'ml', kan: 'kn', ben: 'bn', mar: 'mr', urd: 'ur' };
  var NAMES = { en: 'English', hi: 'Hindi', es: 'Spanish', fr: 'French', de: 'German', pt: 'Portuguese', ru: 'Russian', uz: 'Uzbek', ta: 'Tamil', te: 'Telugu', ar: 'Arabic', ja: 'Japanese', zh: 'Chinese', it: 'Italian', ko: 'Korean', tr: 'Turkish', pl: 'Polish', nl: 'Dutch', sv: 'Swedish', da: 'Danish', fi: 'Finnish', nb: 'Norwegian', nn: 'Norwegian', cs: 'Czech', el: 'Greek', hu: 'Hungarian', ro: 'Romanian', uk: 'Ukrainian', th: 'Thai', vi: 'Vietnamese', id: 'Indonesian', ms: 'Malay', he: 'Hebrew', fa: 'Persian', bg: 'Bulgarian', hr: 'Croatian', sk: 'Slovak', sl: 'Slovenian', lt: 'Lithuanian', lv: 'Latvian', et: 'Estonian', is: 'Icelandic', sr: 'Serbian', ml: 'Malayalam', kn: 'Kannada', bn: 'Bengali', mr: 'Marathi', ur: 'Urdu' };

  // Two-letter language code from "en", "eng", "en-US" or "hin"; empty when unknown.
  function langCode(v) {
    var s = typeof v === 'string' ? v.toLowerCase().split(/[-_]/)[0] : '';
    if (!/^[a-z]{2,3}$/.test(s)) return '';
    return THREE[s] || s.slice(0, 2);
  }

  // Index of the audio track to enable: saved language, then UI language, then English; -1 to leave it.
  function pickAudio(langs, saved, ui) {
    var wants = [langCode(saved), langCode(ui), 'en'];
    for (var w = 0; w < wants.length; w++) {
      if (!wants[w]) continue;
      for (var i = 0; i < langs.length; i++) if (langCode(langs[i]) === wants[w]) return i;
    }
    return -1;
  }

  function clean(v) {
    return typeof v === 'string' ? v.replace(/[\u0000-\u001f<>]/g, '').replace(/\s+/g, ' ').replace(/^\s+|\s+$/g, '').slice(0, 60) : '';
  }

  // Subtitle choices from the relay's track list; the index is what the TV's selectTrack expects.
  function subtitleChoices(list) {
    var src = Array.isArray(list) ? list : [];
    var out = [];
    var seen = {};
    for (var i = 0; i < src.length; i++) {
      var t = src[i];
      if (!t || typeof t !== 'object' || typeof t.index !== 'number' || t.index < 0 || t.index % 1) continue;
      var code = langCode(t.language);
      var lang = NAMES[code] || (code ? code.toUpperCase() : '');
      var name = clean(t.name);
      var label = !name ? lang : !lang || name.toLowerCase().indexOf(lang.toLowerCase()) === 0 ? name : lang + ' · ' + name;
      if (!label) label = 'Track ' + (t.index + 1);
      seen[label] = (seen[label] || 0) + 1;
      if (seen[label] > 1) label += ' (' + seen[label] + ')';
      out.push({ index: t.index, label: label, base: 'embedded:' + (code || 'und'), forced: t.forced === true, embedded: true });
    }
    return out;
  }

  // The remembered embedded language, preferring a full track over a forced one; null when none matches.
  function pickSubtitle(choices, remembered) {
    var hit = null;
    for (var i = 0; i < choices.length; i++) {
      if (choices[i].base !== remembered) continue;
      if (!choices[i].forced) return choices[i];
      if (!hit) hit = choices[i];
    }
    return hit;
  }

  // webOS only: subtitles inside a Matroska file are listed by the relay and drawn by the TV's media pipeline.
  function create(options) {
    var o = options || {};
    var Bridge = o.Bridge || root.PalmServiceBridge;
    var newXhr = o.xhr || function () { return new root.XMLHttpRequest(); };
    var req = null;
    var mediaId = null;

    function call(method, params) {
      if (typeof Bridge !== 'function') return;
      try {
        var b = new Bridge();
        b.onservicecallback = function () {};
        b.call(MEDIA + method, JSON.stringify(params));
      } catch (e) { return; }
    }

    function stop() {
      if (req) {
        var x = req;
        req = null;
        x.onload = null;
        x.onerror = null;
        x.ontimeout = null;
        try { x.abort(); } catch (e) { mediaId = null; }
      }
      mediaId = null;
    }

    // cb(choices) once, with [] on any failure.
    function load(id, tracksUrl, cb) {
      stop();
      if (!o.isWebOS || typeof id !== 'string' || !id || !tracksUrl) return;
      mediaId = id;
      var x = newXhr();
      req = x;
      function end(list) {
        if (req !== x) return;
        req = null;
        cb(subtitleChoices(list));
      }
      x.onload = function () {
        var body = null;
        try { body = x.status === 200 ? JSON.parse(x.responseText) : null; } catch (e) { body = null; }
        end(body && body.subtitles);
      };
      x.onerror = function () { end([]); };
      x.ontimeout = function () { end([]); };
      try {
        x.open('GET', tracksUrl, true);
        x.timeout = TIMEOUT;
        x.send();
      } catch (e) {
        end([]);
      }
    }

    return {
      load: load,
      stop: stop,
      // index null turns the pipeline's subtitles off.
      selectText: function (index) {
        if (!mediaId) return;
        if (index === null) { call('setSubtitleEnable', { mediaId: mediaId, enable: false }); return; }
        call('selectTrack', { mediaId: mediaId, type: 'text', index: index });
        call('setSubtitleEnable', { mediaId: mediaId, enable: true });
      }
    };
  }

  var api = { create: create, langCode: langCode, pickAudio: pickAudio, subtitleChoices: subtitleChoices, pickSubtitle: pickSubtitle };
  Sozo.MediaTracks = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
}(typeof window !== 'undefined' ? window : global));
