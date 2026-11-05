(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});
  Sozo.Screens = Sozo.Screens || {};
  var Dom = Sozo.Dom;
  var h = Dom.h;
  var View = Sozo.View;
  var Nav = Sozo.Nav;
  var Model = Sozo.Model;
  var Filter = Sozo.Filter;

  var HERO_MAX = 8;

  function heroCard(card, onOk) {
    var title = View.cardTitle(card);
    var meta = [Model.year(card.year), Model.rating(card.rating) ? '★ ' + Model.rating(card.rating) : '', Model.nameList(card.genres).slice(0, 3).join(', ')]
      .filter(function (x) { return !!x; }).join('  ·  ');
    // A poster stretched to 1760px looks bad, so without a real banner it stands to the right.
    var banner = Model.bannerUrl(card.banner);
    var image = banner
      ? Dom.lazyImage(banner, 'hero-img', 0)
      : Dom.lazyImage(Model.posterUrl(card.thumbnail), 'hero-poster', 0);
    var el = h('div', 'hero fc', [
      h('div', 'hero-frame', [
        image,
        h('div', 'hero-shade'),
        h('div', 'hero-text', [
          h('div', 'hero-title', title),
          meta ? h('div', 'hero-meta', meta) : null,
          h('div', 'hero-desc', Model.cleanText(card.description) === title ? '' : Model.cleanText(card.description))
        ])
      ])
    ]);
    el._onOk = onOk;
    return el;
  }

  Sozo.Screens.home = function (app, params, state) {
    var el = h('div', 'home');
    var reqs = View.requests();
    var saved = state || {};
    var data = saved.data || null;
    var indices = saved.indices || {};
    var cols = {};
    var page = null;
    var startedAt = 0;
    var provider = app.provider;
    var isLive = provider && provider.kind === 'live';

    function open(card) { app.openDetail(card, provider.id); }

    function continueCard(entry) {
      var card = { provider: entry.provider, contentUrl: entry.url, title: entry.title, thumbnail: entry.thumbnail, year: entry.year };
      return View.posterCard(card, function () { app.openDetail(card, entry.provider); }, {
        progress: Model.progressRatio(entry.position, entry.duration)
      });
    }

    function renderCard(section, wide) {
      return function (item) {
        if (item && item.__viewAll) {
          return View.moreCard(View.t('row.viewAll'), function () {
            app.push('listing', { title: section.label, path: Model.listPath(section.viewAll.type, section.viewAll.slug) });
          }, wide);
        }
        if (wide) {
          return View.wideCard(View.cardTitle(item), Model.year(item.year), Model.posterUrl(item.thumbnail), function () { open(item); }, { contain: true });
        }
        return View.posterCard(item, function () { open(item); });
      };
    }

    function addRow(key, label, items, render, layout) {
      var col = new Sozo.Collection({
        key: key, items: items, cellW: layout.cellW, cellH: layout.cellH, index: indices[key] || 0, render: render
      });
      cols[key] = col;
      page.add(View.row(label, col), col);
      return col;
    }

    function render() {
      Dom.clear(el);
      cols = {};
      page = new Sozo.Page({ viewH: app.contentH(true) });
      // Channel logos are too small for a hero.
      var banner = isLive ? [] : Filter.cards(data.banner, provider.id).slice(0, HERO_MAX);
      if (banner.length) {
        var hero = new Sozo.Collection({
          key: 'hero', items: banner, cellW: 1784, cellH: 520, before: 1, after: 2, index: indices.hero || 0,
          render: function (card) { return heroCard(card, function () { open(card); }); }
        });
        cols.hero = hero;
        var heroRow = h('div', 'hero-row reveal-unit', hero.root);
        page.add(heroRow, hero);
        page.snapNode = heroRow;
      }

      var history = app.store.continueList().filter(function (e) { return app.isVisible(e.provider); });
      if (history.length) {
        addRow('continue', View.t('home.continue'), history, continueCard, View.POSTER);
      }

      var sections = Filter.sections(data.sections, provider.id);
      for (var i = 0; i < sections.length; i++) {
        var s = sections[i];
        var items = s.items.slice();
        var path = s.viewAll && provider.viewAll ? Model.listPath(s.viewAll.type, s.viewAll.slug) : null;
        if (path) items.push({ __viewAll: true });
        addRow('s' + s.key, Model.cleanText(s.label), items, renderCard(s, isLive), isLive ? View.WIDE : View.POSTER);
      }

      if (!banner.length && !sections.length && !history.length) {
        el.appendChild(View.message(View.t('home.empty'), [
          View.button(View.t('common.changeSource'), function () { app.push('sources', {}); }, { primary: true })
        ]));
        app.focusWhenIdle(startedAt, el.querySelector('.fc'));
        return;
      }
      el.appendChild(page.root);
      page.updateActive();

      var last = saved.focus || (saved.lastKey ? { col: saved.lastKey } : null);
      saved.focus = null;
      if (last && View.restoreFocus(last, root.document.getElementById('screen'), cols)) return;
      var first = cols.hero || cols[Object.keys(cols)[0]];
      app.focusWhenIdle(startedAt, first ? first.restore() : null);
    }

    function load() {
      Dom.clear(el);
      el.appendChild(View.spinner());
      Nav.ensure();
      startedAt = Nav.moves();
      reqs.add(app.api.home(provider.id, function (err, body) {
        if (err) {
          Dom.clear(el);
          el.appendChild(View.errorView(err, load, [
            View.button(View.t('common.changeSource'), function () { app.push('sources', {}); })
          ]));
          app.focusWhenIdle(startedAt, el.querySelector('.fc'));
          return;
        }
        data = body;
        render();
      }));
    }

    return {
      el: el,
      start: function () {
        startedAt = Nav.moves();
        if (data) render();
        else load();
      },
      capture: function () {
        var idx = {};
        for (var k in cols) if (Object.prototype.hasOwnProperty.call(cols, k)) idx[k] = cols[k].index;
        return { data: data, indices: idx, lastKey: page ? page.lastKey : null };
      },
      onOnline: function () { if (!data) load(); },
      destroy: function () { reqs.abortAll(); }
    };
  };
}(window));
