(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});

  // Contract section 10. Reduces risk only: an adult film with an innocent title gets through.
  var ADULT = /hentai|porn|\berot|\bxxx\b|(^|[^а-яё])ххх([^а-яё]|$)|\bsexy?\b|\bnude\b|\bnudity\b|\bnaked\b|\bmilf\b|\badults?\b|uncensored|softcore|playboy|playmen|\becchi\b|kattalar uchun|катталар учун|(^|[^0-9])18\s*(\+|лет|yosh)|\+\s*18([^0-9]|$)|\br-?18\b|хентай|порно|эрот|секс|извращ|для\s+взрослых|только\s+взрослым|взросл(ый|ое)\s+(контент|кино|видео)|(^|[^а-яё])этти([^а-яё]|$)/i;

  // Screens show text after Model.cleanText, so that form is tested too. So is one with tags removed
  // and no space left behind, for H<b>en</b>tai.
  function isAdultString(s) {
    if (ADULT.test(s)) return true;
    var clean = Sozo.Model.cleanText;
    if (ADULT.test(clean(s).replace(/\s+/g, ' '))) return true;
    return s.indexOf('<') >= 0 && ADULT.test(clean(s.replace(/<[^<>]*>/g, '')).replace(/\s+/g, ' '));
  }

  function isAdultText(value) {
    if (value === null || value === undefined) return false;
    if (typeof value === 'string') return isAdultString(value);
    if (typeof value === 'number' || typeof value === 'boolean') return false;
    if (Array.isArray(value)) {
      for (var i = 0; i < value.length; i++) if (isAdultText(value[i])) return true;
      return false;
    }
    if (typeof value === 'object') {
      return isAdultText(value.name) || isAdultText(value.label) || isAdultText(value.title) || isAdultText(value.slug);
    }
    return false;
  }

  function isAdultCard(c) {
    if (!c) return true;
    return isAdultText(c.title) || isAdultText(c.altTitles) || isAdultText(c.description) ||
      isAdultText(c.genres) || isAdultText(c.category) || isAdultText(c.slug) || isAdultText(c.contentUrl);
  }

  function cards(list, providerId) {
    var out = [];
    var seen = {};
    if (!Array.isArray(list)) return out;
    for (var i = 0; i < list.length; i++) {
      var c = list[i];
      if (!c || typeof c !== 'object') continue;
      if (c.provider && providerId && c.provider !== providerId) continue;
      var url = c.contentUrl || c.slug;
      if (!url || typeof url !== 'string') continue;
      if (seen['$' + url]) continue;
      if (isAdultCard(c)) continue;
      seen['$' + url] = true;
      out.push(c);
    }
    return out;
  }

  function sections(list, providerId) {
    var out = [];
    if (!Array.isArray(list)) return out;
    for (var i = 0; i < list.length; i++) {
      var s = list[i];
      if (!s || isAdultText(s.label) || isAdultText(s.key)) continue;
      var items = cards(s.items, providerId);
      if (!items.length) continue;
      out.push({ key: String(s.key || i), label: s.label || '', viewAll: s.viewAll || null, items: items });
    }
    return out;
  }

  function labels(list) {
    var out = [];
    if (!Array.isArray(list)) return out;
    for (var i = 0; i < list.length; i++) {
      var it = list[i];
      if (!it || !it.slug || isAdultText(it.label) || isAdultText(it.slug)) continue;
      out.push(it);
    }
    return out;
  }

  function isAdultDetail(d) {
    return isAdultCard(d);
  }

  function episodes(list) {
    var out = [];
    if (!Array.isArray(list)) return out;
    for (var i = 0; i < list.length; i++) {
      var e = list[i];
      if (!e || isAdultText(e.label) || isAdultText(e.title) || isAdultText(e.overview)) continue;
      out.push(e);
    }
    return out;
  }

  var api = { isAdultText: isAdultText, isAdultCard: isAdultCard, isAdultDetail: isAdultDetail, cards: cards, sections: sections, labels: labels, episodes: episodes };
  Sozo.Filter = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
}(typeof window !== 'undefined' ? window : global));
