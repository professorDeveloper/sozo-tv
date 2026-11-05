(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});
  var doc = root.document;
  var Dom = Sozo.Dom;
  var Nav = Sozo.Nav;
  var View = Sozo.View;
  var I18n = Sozo.I18n;

  var VERSION = '1.0.0';
  var TOPBAR_H = 124;
  var BOOT_TRIES = 2;
  var BOOT_DELAY = 3000;

  var app = {
    VERSION: VERSION,
    store: Sozo.Store.create(),
    router: Sozo.Router.create(),
    api: null,
    topbar: null,
    providers: [],
    provider: null,
    screen: null,
    contentH: function (withTopbar) { return withTopbar ? 1080 - TOPBAR_H : 1080; }
  };

  var screenEl;
  var offlineEl;
  var splashEl;
  var wasOffline = false;
  var bootRetry = null;

  function isTabScreen(name) {
    return name === 'home' || name === 'search' || name === 'categories' || name === 'mylist' || name === 'settings';
  }

  function hideSplash() {
    if (splashEl) {
      Dom.remove(splashEl);
      splashEl = null;
    }
  }

  function saveCurrent() {
    if (!app.screen) return;
    var state = null;
    try {
      state = app.screen.capture ? app.screen.capture() : null;
    } catch (e) {
      state = null;
    }
    // A tab that was just pressed is not where the viewer was reading, so it is not remembered.
    var inTopbar = Nav.current() && app.topbar.el.contains(Nav.current());
    if (state) state.focus = inTopbar ? null : View.describeFocus();
    app.router.saveState(state);
  }

  function destroyCurrent() {
    if (!app.screen) return;
    Nav.clear();
    try { if (app.screen.destroy) app.screen.destroy(); } catch (e) { app.screen = null; }
    app.screen = null;
    Dom.disposeTree(screenEl);
    Dom.clear(screenEl);
  }

  function open(entry) {
    destroyCurrent();
    var factory = Sozo.Screens[entry.name];
    var tab = isTabScreen(entry.name);
    screenEl.appendChild(app.topbar.el);
    app.topbar.show(tab);
    if (tab) app.topbar.setActive(entry.name);
    var screen = factory(app, entry.params || {}, entry.state || null);
    var holder = Dom.h('div', tab ? 'screen-body with-topbar' : 'screen-body', screen.el);
    screenEl.appendChild(holder);
    app.screen = screen;
    hideSplash();
    View.withoutAnimation(holder, function () {
      if (screen.start) screen.start();
    });
    if (!Nav.current()) {
      if (tab) Nav.focus(app.topbar.tabEl(entry.name));
      else Nav.ensure();
    }
  }

  // Focus rests on the active tab while a tab screen loads. Hand it over only if the viewer has
  // not moved since, so content arriving late never steals focus.
  app.focusWhenIdle = function (movesAtStart, el) {
    if (!el) return;
    if (Nav.moves() === movesAtStart) Nav.focus(el);
    else Nav.ensure();
  };

  app.push = function (name, params) {
    saveCurrent();
    open(app.router.push(name, params));
  };

  app.tab = function (name) {
    var cur = app.router.current();
    if (cur && cur.name === name) return;
    saveCurrent();
    open(app.router.tab(name));
  };

  app.back = function () {
    if (Sozo.Dialog.dismissTop()) return;
    if (app.screen && app.screen.onBack && app.screen.onBack() === true) return;
    if (app.router.isRoot()) {
      askExit();
      return;
    }
    destroyCurrent();
    open(app.router.back());
  };

  function askExit() {
    Sozo.Dialog.show({
      title: View.t('exit.title'),
      message: View.t('exit.message'),
      actions: [
        { label: View.t('exit.stay'), primary: true, focus: true },
        { label: View.t('exit.confirm'), onOk: function () { Sozo.Platform.exit(); } }
      ]
    });
  }

  app.isVisible = function (id) {
    return !!app.findProvider(id);
  };

  app.findProvider = function (id) {
    for (var i = 0; i < app.providers.length; i++) {
      if (app.providers[i].id === id) return app.providers[i];
    }
    return null;
  };

  app.setProvider = function (id) {
    var p = app.findProvider(id);
    if (!p) return;
    app.provider = p;
    app.store.setSetting('provider', id);
    app.topbar.setSource(p.name);
    destroyCurrent();
    open(app.router.reset('home', {}));
  };

  app.setProviders = function (items, health) {
    app.providers = Sozo.Providers.visible(items, health);
    var saved = app.store.getSetting('provider', null);
    var gone = !!app.provider && !app.findProvider(app.provider.id);
    app.provider = (app.provider && app.findProvider(app.provider.id)) || Sozo.Providers.pick(app.providers, saved);
    if (app.provider) app.topbar.setSource(app.provider.name);
    // Saved screens hold the old source's cards. The current one stays open, with Home under it.
    if (gone && app.screen) {
      var cur = app.router.current();
      app.router.reset('home', {});
      if (cur.name !== 'home') app.router.push(cur.name, cur.params);
    }
  };

  app.setLanguage = function (override) {
    app.store.setSetting('language', override);
    applyLanguage();
    app.topbar.rebuild();
    if (app.provider) app.topbar.setSource(app.provider.name);
    saveCurrent();
    open(app.router.current());
  };

  function applyLanguage() {
    var override = app.store.getSetting('language', 'auto');
    var nav = root.navigator && (root.navigator.language || (root.navigator.languages && root.navigator.languages[0]));
    var lang = I18n.resolve(override, nav);
    I18n.set(lang);
    doc.documentElement.lang = lang;
  }

  app.openDetail = function (card, provider) {
    app.push('detail', { provider: provider || card.provider || (app.provider && app.provider.id), card: card });
  };

  function setOffline(offline) {
    if (!offlineEl) return;
    offlineEl.textContent = View.t('offline.banner');
    offlineEl.style.display = offline ? '' : 'none';
    if (wasOffline && !offline) {
      if (app.screen && app.screen.onOnline) app.screen.onOnline();
      else if (!app.screen && bootRetry) bootRetry();
    }
    wasOffline = offline;
  }

  function fit() {
    var w = root.innerWidth || 1920;
    var h = root.innerHeight || 1080;
    var scale = Math.min(w / 1920, h / 1080);
    var el = doc.getElementById('app');
    var v = scale === 1 ? '' : 'scale(' + scale + ')';
    el.style.webkitTransform = v;
    el.style.transform = v;
  }

  // Provider list comes first: without it nothing is shown, as the live answer keeps removed
  // sources out. Health is secondary and may fail quietly.
  function boot() {
    var bootEl = null;

    function showError(err) {
      hideSplash();
      if (bootEl) Dom.remove(bootEl);
      bootEl = View.errorView(err, function () { attempt(); });
      bootRetry = function () { attempt(); };
      screenEl.appendChild(bootEl);
      Nav.ensure();
    }

    // A TV waking up often has no route for a few seconds, so a dropped connection gets more tries
    // here than anywhere else. Nothing else can be shown until this answers.
    function attempt(left) {
      var tries = typeof left === 'number' ? left : BOOT_TRIES;
      bootRetry = null;
      if (bootEl) { Dom.remove(bootEl); bootEl = null; Nav.clear(); }
      var waitEl = View.spinner();
      screenEl.appendChild(waitEl);
      app.api.providers(function (err, data) {
        Dom.remove(waitEl);
        if (err && err.kind === 'network' && tries > 0) {
          var again = View.spinner();
          screenEl.appendChild(again);
          root.setTimeout(function () { Dom.remove(again); attempt(tries - 1); }, BOOT_DELAY);
          return;
        }
        if (err) { showError(err); return; }
        var items = data && data.items;
        app.setProviders(items, null);
        if (!app.providers.length) {
          showError({ kind: 'http', status: 503, code: null, retryAfter: null });
          return;
        }
        app.liveProviders = items;
        open(app.router.reset('home', {}));
        app.api.health(function (herr, health) {
          if (herr) return;
          app.health = health;
          app.providers = Sozo.Providers.visible(items, health);
        });
      });
    }
    attempt();
  }

  function start() {
    screenEl = doc.getElementById('screen');
    offlineEl = doc.getElementById('offline');
    splashEl = doc.getElementById('splash');
    fit();
    root.addEventListener('resize', fit, false);
    applyLanguage();
    app.api = Sozo.Api.create({ onStatus: function (ok) { setOffline(!ok); } });
    app.topbar = Sozo.Topbar.create(app);
    Nav.init(doc.getElementById('app'));
    Nav.onBack(function () { app.back(); });
    root.addEventListener('online', function () { setOffline(false); }, false);
    root.addEventListener('offline', function () { setOffline(true); }, false);
    if (root.navigator && root.navigator.onLine === false) setOffline(true);
    boot();
  }

  Sozo.App = app;
  if (doc.readyState === 'loading') doc.addEventListener('DOMContentLoaded', start, false);
  else start();
}(window));
