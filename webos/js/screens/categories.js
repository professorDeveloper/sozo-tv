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

  Sozo.Screens.categories = function (app, params, state) {
    var provider = app.provider;
    var el = h('div', 'categories');
    var reqs = View.requests();
    var saved = state || {};
    var data = saved.data || null;
    var startedAt = 0;

    function chip(label, path) {
      var c = h('div', 'chip fc', Model.cleanText(label));
      c._fid = 'chip-' + path;
      c._onOk = function () { app.push('listing', { title: Model.cleanText(label), path: path }); };
      return c;
    }

    function section(title, chips) {
      return h('div', 'chip-section', [h('div', 'row-label', title), h('div', 'chips', chips)]);
    }

    function chipsFor(items, type) {
      var out = [];
      for (var i = 0; i < items.length; i++) {
        var path = Model.listPath(type, items[i].slug);
        var label = items[i].label || items[i].slug;
        if (path) out.push(chip(label, path));
      }
      return out;
    }

    function render() {
      Dom.clear(el);
      var page = new Sozo.Page({ viewH: app.contentH(true) });
      var cats = chipsFor(Filter.labels(data.categories), 'category');
      var genres = chipsFor(Filter.labels(data.genres), 'genre');
      cats.unshift(chip(View.t('cat.latest'), 'latest'));
      page.add(section(View.t('cat.categories'), cats));
      if (genres.length) page.add(section(View.t('cat.genres'), genres));
      el.appendChild(page.root);
      if (saved.focus && View.restoreFocus(saved.focus, el, {})) return;
      app.focusWhenIdle(startedAt, el.querySelector('.fc'));
    }

    function load() {
      Dom.clear(el);
      el.appendChild(View.spinner());
      Nav.ensure();
      startedAt = Nav.moves();
      var pending = 2;
      var result = { categories: [], genres: [] };
      var firstErr = null;
      var failures = 0;
      function done(key) {
        return function (err, body) {
          if (err) {
            failures++;
            firstErr = firstErr || err;
          } else {
            result[key] = (body && body.items) || [];
          }
          pending--;
          if (pending > 0) return;
          if (failures === 2) {
            Dom.clear(el);
            el.appendChild(View.errorView(firstErr, load));
            app.focusWhenIdle(startedAt, el.querySelector('.fc'));
            return;
          }
          data = result;
          render();
        };
      }
      reqs.add(app.api.categories(provider.id, done('categories')));
      reqs.add(app.api.genres(provider.id, done('genres')));
    }

    return {
      el: el,
      start: function () {
        startedAt = Nav.moves();
        if (!provider.viewAll) {
          el.appendChild(View.message(View.t('cat.off'), [
            View.button(View.t('common.changeSource'), function () { app.push('sources', {}); }, { primary: true })
          ]));
          return;
        }
        if (data) render();
        else load();
      },
      capture: function () { return { data: data }; },
      onOnline: function () { if (!data && provider.viewAll) load(); },
      destroy: function () { reqs.abortAll(); }
    };
  };
}(window));
