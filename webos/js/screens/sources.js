(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});
  Sozo.Screens = Sozo.Screens || {};
  var Dom = Sozo.Dom;
  var h = Dom.h;
  var View = Sozo.View;
  var Nav = Sozo.Nav;

  Sozo.Screens.sources = function (app) {
    var el = h('div', 'sources');
    var reqs = View.requests();

    function langLabel(lang) {
      var key = 'lang.' + lang;
      var text = View.t(key);
      return text === key ? '' : text;
    }

    function healthLine(p) {
      return h('div', 'source-health health-' + p.health, [
        h('span', 'health-dot'),
        h('span', '', View.t('source.' + p.health))
      ]);
    }

    function card(p) {
      var selected = app.provider && app.provider.id === p.id;
      var row = h('div', 'source-card fc' + (selected ? ' is-selected' : '') + (p.health === 'down' ? ' is-down' : ''), [
        h('div', 'source-name', [
          p.name,
          selected ? h('span', 'source-selected', [Dom.icon('check'), View.t('source.current')]) : null
        ]),
        h('div', 'source-info', [View.t('kind.' + p.kind), langLabel(p.lang)].filter(function (x) { return !!x; }).join('  ·  ')),
        healthLine(p)
      ]);
      row._onOk = function () {
        app.setProvider(p.id);
      };
      return row;
    }

    function render() {
      Dom.clear(el);
      var list = app.providers;
      var body = h('div', 'source-grid');
      if (!list.length) {
        el.appendChild(View.message(View.t('source.none'), [
          View.button(View.t('common.retry'), load, { primary: true })
        ]));
        Nav.ensure();
        return;
      }
      var selected = null;
      for (var i = 0; i < list.length; i++) {
        var c = card(list[i]);
        body.appendChild(c);
        if (app.provider && list[i].id === app.provider.id) selected = c;
      }
      var page = new Sozo.Page({ viewH: app.contentH(false) });
      page.add(View.header(View.t('source.title'), View.t('source.hint')));
      page.add(body);
      el.appendChild(page.root);
      Nav.focus(selected || body.firstChild);
    }

    function load() {
      Dom.clear(el);
      el.appendChild(View.spinner());
      Nav.ensure();
      reqs.add(app.api.providers(function (err, data) {
        if (err) {
          Dom.clear(el);
          el.appendChild(View.errorView(err, load));
          Nav.ensure();
          return;
        }
        var items = data && data.items;
        app.liveProviders = items;
        // Health is optional: without it the status simply reads as unknown.
        reqs.add(app.api.health(function (herr, health) {
          if (!herr) app.health = health;
          app.setProviders(items, herr ? app.health : health);
          render();
        }));
      }));
    }

    return {
      el: el,
      start: load,
      capture: function () { return {}; },
      onOnline: load,
      destroy: function () { reqs.abortAll(); }
    };
  };
}(window));
