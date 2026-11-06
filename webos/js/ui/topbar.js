(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});
  var Dom = Sozo.Dom;
  var h = Dom.h;
  var View = Sozo.View;

  var TABS = ['home', 'search', 'categories', 'mylist', 'settings'];

  function create(app) {
    var el = h('div', 'topbar');
    var group = View.staticGroup(el);
    var tabs = {};
    var sourceLabel = h('span', 'source-chip-name');

    function build() {
      Dom.clear(el);
      tabs = {};
      var tabEls = TABS.map(function (name) {
        var tab = h('div', 'tab fc', h('span', '', View.t('tab.' + name)));
        tab._onOk = function () { app.tab(name); };
        tab._fid = 'tab-' + name;
        tabs[name] = tab;
        return tab;
      });
      var chip = h('div', 'source-chip fc', [Dom.icon('source'), sourceLabel]);
      chip._onOk = function () { app.push('sources', {}); };
      chip._fid = 'tab-source';
      Dom.add(el, [
        h('img', 'topbar-logo'),
        h('div', 'tabs', tabEls),
        chip
      ]);
      el.querySelector('.topbar-logo').src = 'assets/logo.png';
      el.querySelector('.topbar-logo').alt = 'Sozo';
    }

    build();

    return {
      el: el,
      rebuild: build,
      setActive: function (name) {
        for (var k in tabs) {
          if (Object.prototype.hasOwnProperty.call(tabs, k)) tabs[k].classList.toggle('is-active', k === name);
        }
        if (tabs[name]) group.set(tabs[name]);
      },
      tabEl: function (name) { return tabs[name] || null; },
      setSource: function (name) { sourceLabel.textContent = name || ''; },
      show: function (visible) { el.style.display = visible ? '' : 'none'; }
    };
  }

  Sozo.Topbar = { create: create };
}(window));
