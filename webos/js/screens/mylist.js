(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});
  Sozo.Screens = Sozo.Screens || {};
  var Dom = Sozo.Dom;
  var h = Dom.h;
  var View = Sozo.View;
  var Nav = Sozo.Nav;
  var Model = Sozo.Model;

  var COLS = 6;

  function toCard(e) {
    return { provider: e.provider, contentUrl: e.url, title: e.title, thumbnail: e.thumbnail, year: e.year };
  }

  Sozo.Screens.mylist = function (app, params, state) {
    var el = h('div', 'mylist');
    var saved = state || {};
    var cols = {};

    function visible(list) {
      return list.filter(function (e) { return app.isVisible(e.provider); });
    }

    function open(e) {
      app.openDetail(toCard(e), e.provider);
    }

    return {
      el: el,
      start: function () {
        var startedAt = Nav.moves();
        var history = visible(app.store.continueList());
        var mine = visible(app.store.myList());
        var page = new Sozo.Page({ viewH: app.contentH(true) });

        if (history.length) {
          var row = new Sozo.Collection({
            key: 'continue', items: history, cellW: View.POSTER.cellW, cellH: View.POSTER.cellH, index: (saved.indices || {}).continue || 0,
            render: function (e) {
              return View.posterCard(toCard(e), function () { open(e); }, { progress: Model.progressRatio(e.position, e.duration) });
            }
          });
          cols['continue'] = row;
          page.add(View.row(View.t('home.continue'), row), row);
        }

        page.add(h('div', 'row-label mylist-label', View.t('mylist.title')));
        if (mine.length) {
          var grid = new Sozo.Collection({
            key: 'mine', items: mine, cols: COLS, cellW: View.POSTER.cellW, cellH: View.POSTER.cellH, index: (saved.indices || {}).mine || 0,
            render: function (e) { return View.posterCard(toCard(e), function () { open(e); }); }
          });
          cols.mine = grid;
          page.add(grid.root, grid);
        } else {
          page.add(View.message(View.t('mylist.empty')));
        }
        el.appendChild(page.root);
        page.updateActive();
        if (saved.focus && View.restoreFocus(saved.focus, el, cols)) return;
        var first = cols['continue'] || cols.mine;
        if (first) app.focusWhenIdle(startedAt, first.restore());
      },
      capture: function () {
        var idx = {};
        for (var k in cols) if (Object.prototype.hasOwnProperty.call(cols, k)) idx[k] = cols[k].index;
        return { indices: idx };
      },
      destroy: function () {}
    };
  };
}(window));
