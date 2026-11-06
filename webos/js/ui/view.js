(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});
  var Dom = Sozo.Dom;
  var h = Dom.h;
  var Nav = Sozo.Nav;
  var Model = Sozo.Model;

  function t(key, vars) { return Sozo.I18n.t(key, vars); }

  function button(label, onOk, opts) {
    var o = opts || {};
    var el = h('div', 'btn fc' + (o.primary ? ' btn-primary' : '') + (o.cls ? ' ' + o.cls : ''), [
      o.icon ? Dom.icon(o.icon) : null,
      h('span', 'btn-label', label)
    ]);
    el._onOk = onOk;
    if (o.fid) el._fid = o.fid;
    el.setAttribute('role', 'button');
    return el;
  }

  function setButtonLabel(el, label) {
    var span = el.querySelector('.btn-label');
    if (span) span.textContent = label;
  }

  function spinner(label) {
    return h('div', 'status status-loading', [
      h('div', 'spinner'),
      h('div', 'status-text', label || t('app.loading'))
    ]);
  }

  function message(text, actions) {
    return h('div', 'status', [
      h('div', 'status-text', text),
      actions && actions.length ? h('div', 'status-actions', actions) : null
    ]);
  }

  function errorMessage(err) {
    var key = Sozo.Api.errorKey(err);
    var body = t(key + '.m');
    if (key === 'err.rate' && err && err.retryAfter) body = t('err.rateWait.m', { s: err.retryAfter });
    return { title: t(key + '.t'), body: body };
  }

  // actions always starts with Retry when retry is given. extra: more buttons after it.
  function errorView(err, retry, extra) {
    var m = errorMessage(err);
    var actions = [];
    if (retry) actions.push(button(t('common.retry'), retry, { primary: true, fid: 'retry' }));
    if (extra) actions = actions.concat(extra);
    return h('div', 'status status-error', [
      h('div', 'status-title', m.title),
      h('div', 'status-text', m.body),
      actions.length ? h('div', 'status-actions', actions) : null
    ]);
  }

  function staticGroup(el, defaultEl) {
    var last = defaultEl || null;
    el._group = {
      restore: function () { return last && el.contains(last) ? last : null; },
      onFocus: function (cur) { last = cur; },
      set: function (cur) { last = cur; }
    };
    return el._group;
  }

  function requests() {
    var list = [];
    return {
      add: function (handle) { list.push(handle); return handle; },
      abortAll: function () {
        for (var i = 0; i < list.length; i++) {
          try { list[i].abort(); } catch (e) { continue; }
        }
        list = [];
      }
    };
  }

  function describeFocus() {
    var cur = Nav.current();
    if (!cur) return null;
    var group = Nav.groupOf(cur);
    return {
      fid: cur._fid || null,
      col: group && group.key ? group.key : null
    };
  }

  function restoreFocus(desc, rootEl, collections) {
    if (!desc) return false;
    if (desc.col && collections && collections[desc.col]) {
      var el = collections[desc.col].restore();
      if (el) return Nav.focus(el);
    }
    if (desc.fid) {
      var list = rootEl.querySelectorAll('.fc');
      for (var i = 0; i < list.length; i++) {
        if (list[i]._fid === desc.fid) return Nav.focus(list[i]);
      }
    }
    return false;
  }

  function withoutAnimation(el, fn) {
    el.classList.add('no-anim');
    fn();
    root.setTimeout(function () { el.classList.remove('no-anim'); }, 60);
  }

  // A focusable poster card. Under the image is a titled placeholder that stays if the image fails.
  function placeholder(title) {
    return h('div', 'card-ph', h('span', 'card-ph-title', title));
  }

  function cardTitle(card) {
    return Model.cleanText(card.title) || t('detail.untitled');
  }

  function posterCard(card, onOk, opts) {
    var o = opts || {};
    var title = cardTitle(card);
    var img = Dom.lazyImage(Model.posterUrl(card.thumbnail), 'card-img');
    var progress = o.progress > 0
      ? h('div', 'card-progress', (function () {
        var bar = h('div', 'card-progress-bar');
        bar.style.width = Math.round(o.progress * 100) + '%';
        return bar;
      }()))
      : null;
    var el = h('div', 'card fc', [
      h('div', 'card-frame', [placeholder(title), img, progress]),
      h('div', 'card-title', title),
      h('div', 'card-sub', Model.year(card.year))
    ]);
    el._onOk = onOk;
    return el;
  }

  // Channels and episodes: logos and stills are landscape.
  function wideCard(title, sub, imageUrl, onOk, opts) {
    var o = opts || {};
    var img = Dom.lazyImage(imageUrl, 'card-img' + (o.contain ? ' card-img-contain' : ''));
    var el = h('div', 'card card-wide fc', [
      h('div', 'card-frame', [placeholder(title), img]),
      h('div', 'card-title', title),
      sub ? h('div', 'card-sub', sub) : null
    ]);
    el._onOk = onOk;
    return el;
  }

  function moreCard(label, onOk, wide) {
    var el = h('div', 'card card-more fc' + (wide ? ' card-wide' : ''), [
      h('div', 'card-frame', h('div', 'card-more-body', [Dom.icon('chevron'), h('div', '', label)]))
    ]);
    el._onOk = onOk;
    return el;
  }

  function header(title, sub) {
    return h('div', 'screen-header', [
      h('div', 'screen-title', title),
      sub ? h('div', 'screen-sub', sub) : null
    ]);
  }

  // A labelled horizontal row, ready to add to a Page.
  function row(label, collection) {
    return h('div', 'row reveal-unit', [h('div', 'row-label', label), collection.root]);
  }

  // Room for a poster, a two-line title and the year, so grid rows never touch.
  var POSTER = { cellW: 292, cellH: 524 };
  var WIDE = { cellW: 424, cellH: 322 };

  Sozo.View = {
    t: t, button: button, setButtonLabel: setButtonLabel, spinner: spinner, message: message,
    errorView: errorView, errorMessage: errorMessage, staticGroup: staticGroup, requests: requests,
    describeFocus: describeFocus, restoreFocus: restoreFocus, withoutAnimation: withoutAnimation,
    posterCard: posterCard, wideCard: wideCard, moreCard: moreCard, cardTitle: cardTitle, header: header, row: row,
    POSTER: POSTER, WIDE: WIDE
  };
}(window));
