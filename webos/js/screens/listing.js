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

  var COLS = 6;
  var MAX_EMPTY_PAGES = 4;

  // View-all and category pages. params: {title, path} where path is from Model.listPath.
  Sozo.Screens.listing = function (app, params, state) {
    var provider = app.provider;
    var el = h('div', 'listing');
    var reqs = View.requests();
    var saved = state || {};
    var items = saved.items || [];
    var page = saved.page || 0;
    var totalPages = saved.totalPages || 1;
    var col = null;
    var footer = h('div', 'listing-footer');
    var fetching = false;

    function grid(index) {
      Dom.clear(el);
      var p = new Sozo.Page({ viewH: app.contentH(false) });
      col = new Sozo.Collection({
        key: 'grid', items: items, cols: COLS, cellW: View.POSTER.cellW, cellH: View.POSTER.cellH, index: index,
        render: function (card) { return View.posterCard(card, function () { app.openDetail(card, provider.id); }); },
        onNearEnd: fetchMore
      });
      p.add(View.header(params.title || ''));
      p.add(col.root, col);
      p.add(footer);
      el.appendChild(p.root);
    }

    function fetchMore() {
      if (fetching || !Model.hasMore(page, totalPages)) return;
      fetchPage(page + 1, 0);
    }

    function showFirstError(err) {
      Dom.clear(el);
      el.appendChild(View.errorView(err, function () { first(); }));
      Nav.ensure();
    }

    function showMoreError(err) {
      var retry = View.button(View.t('common.retry'), function () {
        var hadFocus = Nav.current() === retry;
        Dom.clear(footer);
        if (hadFocus && col) Nav.focus(col.restore());
        fetchMore();
      }, { fid: 'more-retry' });
      Dom.clear(footer);
      Dom.add(footer, [
        h('div', 'status-text', View.errorMessage(err).title + '. ' + View.t('list.moreError')),
        retry
      ]);
    }

    function fetchPage(p, emptyPages) {
      fetching = true;
      reqs.add(app.api.list(params.path, provider.id, p, function (err, data) {
        fetching = false;
        if (err) {
          if (p === 1) showFirstError(err);
          else showMoreError(err);
          return;
        }
        page = parseInt(data.page, 10) || p;
        totalPages = parseInt(data.totalPages, 10) || 1;
        var fresh = Filter.cards(data.items, provider.id);
        var merged = Model.mergeCards(items, fresh);
        var added = merged.slice(items.length);
        items = merged;
        // A page can filter down to nothing while later pages still have titles.
        if (!added.length && Model.hasMore(page, totalPages) && emptyPages < MAX_EMPTY_PAGES) {
          fetchPage(page + 1, emptyPages + 1);
          return;
        }
        if (!col) {
          if (!items.length) {
            Dom.clear(el);
            el.appendChild(View.header(params.title || ''));
            el.appendChild(View.message(View.t('list.empty'), [
              View.button(View.t('common.back'), function () { app.back(); }, { primary: true, fid: 'list-back' })
            ]));
            Nav.ensure();
            return;
          }
          grid(0);
          Nav.focus(col.restore());
        } else if (added.length) {
          col.addItems(added);
        }
      }));
    }

    function first() {
      Dom.clear(el);
      col = null;
      items = [];
      page = 0;
      el.appendChild(View.spinner());
      Nav.ensure();
      fetchPage(1, 0);
    }

    return {
      el: el,
      start: function () {
        if (!params.path) {
          showFirstError({ kind: 'http', status: 404 });
          return;
        }
        if (items.length) {
          grid(saved.index || 0);
          Nav.focus(col.restore());
        } else {
          first();
        }
      },
      capture: function () {
        return { items: items, page: page, totalPages: totalPages, index: col ? col.index : 0 };
      },
      onOnline: function () { if (!items.length) first(); },
      destroy: function () { reqs.abortAll(); }
    };
  };
}(window));
