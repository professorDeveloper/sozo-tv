(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});
  Sozo.Screens = Sozo.Screens || {};
  var Dom = Sozo.Dom;
  var h = Dom.h;
  var View = Sozo.View;
  var I18n = Sozo.I18n;

  Sozo.Screens.settings = function (app, params, state) {
    var el = h('div', 'settings');
    var saved = state || {};

    function section(title, kids) {
      return h('div', 'settings-section', [h('div', 'row-label', title), h('div', 'settings-body', kids)]);
    }

    function langChoice(value, label, current) {
      var selected = value === current;
      var b = View.button(label, function () { app.setLanguage(value); }, {
        cls: selected ? 'is-selected' : '', icon: selected ? 'check' : null, fid: 'lang-' + value
      });
      return b;
    }

    function confirmClear(title, action, button) {
      Sozo.Dialog.show({
        title: title,
        message: View.t('settings.confirmHint'),
        actions: [
          { label: View.t('common.cancel'), primary: true, focus: true },
          {
            label: View.t('settings.clear'),
            onOk: function () {
              action();
              View.setButtonLabel(button, View.t('settings.cleared'));
            }
          }
        ]
      });
    }

    return {
      el: el,
      start: function () {
        var current = app.store.getSetting('language', 'auto');
        var page = new Sozo.Page({ viewH: app.contentH(true) });
        var langs = [langChoice('auto', View.t('settings.langAuto'), current)];
        for (var i = 0; i < I18n.LANGS.length; i++) {
          langs.push(langChoice(I18n.LANGS[i], I18n.NATIVE_NAMES[I18n.LANGS[i]], current));
        }
        var history = View.button(View.t('settings.clearHistory'), function () {
          confirmClear(View.t('settings.confirmHistory'), function () { app.store.clearHistory(); }, history);
        }, { fid: 'clear-history' });
        var mine = View.button(View.t('settings.clearList'), function () {
          confirmClear(View.t('settings.confirmList'), function () { app.store.clearMyList(); }, mine);
        }, { fid: 'clear-list' });
        var source = View.button(app.provider ? app.provider.name : '', function () { app.push('sources', {}); }, { icon: 'source', fid: 'source' });

        page.add(section(View.t('settings.language'), langs));
        page.add(section(View.t('settings.source'), source));
        page.add(section(View.t('settings.data'), [history, mine]));
        page.add(section(View.t('settings.about'), [
          h('div', 'settings-text', View.t('settings.version', { v: app.VERSION })),
          h('div', 'settings-text', View.t('settings.safety'))
        ]));
        el.appendChild(page.root);
        if (saved.focus) View.restoreFocus(saved.focus, el, {});
      },
      capture: function () { return {}; },
      destroy: function () {}
    };
  };
}(window));
