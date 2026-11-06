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
  var Kbd = Sozo.Keyboard;
  var Keys = Sozo.Keys;

  var CELL = 96;
  var RESULT_COLS = 3;
  var MAX_EMPTY_PAGES = 4;

  // No input element exists on purpose: on webOS, focusing one opens the system keyboard, which
  // takes over the remote. The query lives in a div and this on-screen keyboard edits it.
  Sozo.Screens.search = function (app, params, state) {
    var saved = state || {};
    var provider = app.provider;
    var el = h('div', 'search');
    var reqs = View.requests();
    var debounce = Kbd.debouncer(650);
    var query = saved.query || '';
    var mode = saved.mode || (Sozo.I18n.get() === 'ru' ? 'cyrillic' : 'latin');
    var results = saved.results || [];
    var page = saved.page || 1;
    var totalPages = saved.totalPages || 1;
    var searched = saved.searched || '';
    var loading = false;
    var loadingMore = false;
    var handle = null;
    var col = null;
    var resultPage = null;
    var queryEl = h('div', 'query');
    var keysEl = h('div', 'keys');
    var resultsEl = h('div', 'results');
    var keyGroup = View.staticGroup(keysEl);
    var pendingModeFocus = false;

    function showQuery() {
      Dom.clear(queryEl);
      if (query) {
        queryEl.classList.remove('is-empty');
        queryEl.appendChild(root.document.createTextNode(query));
        queryEl.appendChild(h('span', 'caret'));
      } else {
        queryEl.classList.add('is-empty');
        queryEl.appendChild(root.document.createTextNode(View.t('search.placeholder')));
      }
    }

    function changed() {
      showQuery();
      debounce.cancel();
      var q = Kbd.normalize(query);
      if (q === searched && (results.length || !q)) return;
      if (!Kbd.shouldSearch(q)) {
        abort();
        searched = '';
        results = [];
        col = null;
        showStatus(q ? View.t('search.short') : View.t('search.prompt'));
        return;
      }
      debounce.call(function () { run(q); });
    }

    function abort() {
      if (handle) handle.abort();
      handle = null;
      loading = false;
      loadingMore = false;
    }

    function showStatus(text, actions) {
      var hadFocus = Nav.current() && resultsEl.contains(Nav.current());
      Dom.disposeTree(resultsEl);
      Dom.clear(resultsEl);
      col = null;
      resultPage = null;
      resultsEl.appendChild(View.message(text, actions));
      if (hadFocus) returnFocus();
    }

    function returnFocus() {
      var k = keyGroup.restore() || keysEl.querySelector('.fc');
      if (k) Nav.focus(k);
    }

    function showError(err, retry) {
      var hadFocus = Nav.current() && resultsEl.contains(Nav.current());
      Dom.disposeTree(resultsEl);
      Dom.clear(resultsEl);
      col = null;
      resultPage = null;
      resultsEl.appendChild(View.errorView(err, retry));
      if (hadFocus) returnFocus();
    }

    function showResults(index) {
      var hadFocus = Nav.current() && resultsEl.contains(Nav.current());
      Dom.disposeTree(resultsEl);
      Dom.clear(resultsEl);
      resultPage = new Sozo.Page({ viewH: app.contentH(true) });
      col = new Sozo.Collection({
        key: 'results', items: results, cols: RESULT_COLS, cellW: View.POSTER.cellW, cellH: View.POSTER.cellH, index: index || 0,
        render: function (card) { return View.posterCard(card, function () { app.openDetail(card, provider.id); }); },
        onNearEnd: loadMore
      });
      resultPage.add(h('div', 'results-label', View.t('search.results')));
      resultPage.add(col.root, col);
      resultsEl.appendChild(resultPage.root);
      if (hadFocus) returnFocus();
    }

    function run(q) {
      abort();
      searched = q;
      results = [];
      page = 1;
      totalPages = 1;
      loading = true;
      var hadFocus = Nav.current() && resultsEl.contains(Nav.current());
      Dom.disposeTree(resultsEl);
      Dom.clear(resultsEl);
      col = null;
      resultPage = null;
      resultsEl.appendChild(View.spinner());
      if (hadFocus) returnFocus();
      fetchPage(q, 1, 0);
    }

    function fetchPage(q, p, emptyPages) {
      handle = reqs.add(app.api.search(provider.id, q, p, function (err, data) {
        handle = null;
        if (err) {
          loading = false;
          loadingMore = false;
          if (p === 1) {
            searched = '';
            showError(err, function () { run(q); });
          }
          return;
        }
        page = parseInt(data.page, 10) || p;
        totalPages = parseInt(data.totalPages, 10) || 1;
        var fresh = Filter.cards(data.items, provider.id);
        // A page can filter down to nothing while later pages still have titles.
        if (!fresh.length && Model.hasMore(page, totalPages) && emptyPages < MAX_EMPTY_PAGES) {
          fetchPage(q, page + 1, emptyPages + 1);
          return;
        }
        loading = false;
        loadingMore = false;
        if (p === 1 || !col) {
          results = fresh;
          if (results.length) showResults(0);
          else showStatus(View.t('search.none'));
          return;
        }
        var merged = Model.mergeCards(results, fresh);
        var added = merged.slice(results.length);
        results = merged;
        if (added.length) col.addItems(added);
      }));
    }

    function loadMore() {
      if (loading || loadingMore || !Model.hasMore(page, totalPages)) return;
      loadingMore = true;
      fetchPage(searched, page + 1, 0);
    }

    function pressed(key) {
      if (key.action === 'mode') {
        mode = Kbd.nextMode(mode);
        pendingModeFocus = true;
        buildKeys();
        return;
      }
      if (key.action === 'space') query = Kbd.type(query, ' ');
      else if (key.action === 'backspace') query = Kbd.backspace(query);
      else query = Kbd.type(query, key.ch);
      changed();
    }

    function keyLabel(key) {
      if (key.action === 'mode') return View.t('key.mode.' + Kbd.nextMode(mode));
      if (key.action === 'space') return View.t('key.space');
      if (key.action === 'backspace') return Dom.icon('backspace');
      return key.label;
    }

    function buildKeys() {
      Dom.clear(keysEl);
      var rows = Kbd.layout(mode);
      var modeEl = null;
      var firstEl = null;
      for (var r = 0; r < rows.length; r++) {
        var rowEl = h('div', 'key-row');
        for (var c = 0; c < rows[r].length; c++) {
          var key = rows[r][c];
          var keyEl = h('div', 'key fc' + (key.action ? ' key-action' : ''), keyLabel(key));
          keyEl.style.width = ((key.wide || 1) * CELL - 8) + 'px';
          keyEl._fid = 'key-' + key.id;
          keyEl._onOk = (function (k) { return function () { pressed(k); }; }(key));
          if (key.action === 'mode') modeEl = keyEl;
          if (!firstEl) firstEl = keyEl;
          rowEl.appendChild(keyEl);
        }
        keysEl.appendChild(rowEl);
      }
      keyGroup.set(pendingModeFocus ? modeEl : firstEl);
      if (pendingModeFocus) {
        pendingModeFocus = false;
        Nav.focus(modeEl);
      }
    }

    // A hardware keyboard, if one is plugged in, types into the same query.
    function capture(action, e) {
      if (e.keyCode === 8) {
        query = Kbd.backspace(query);
        changed();
        return true;
      }
      if (action) return false;
      var ch = Keys.printable(e);
      if (!ch) return false;
      query = Kbd.type(query, ch.toLowerCase());
      changed();
      return true;
    }

    function buildOff() {
      el.appendChild(View.message(View.t('search.off'), [
        View.button(View.t('common.changeSource'), function () { app.push('sources', {}); }, { primary: true, fid: 'change' })
      ]));
    }

    return {
      el: el,
      start: function () {
        if (!provider.search) {
          buildOff();
          return;
        }
        Dom.add(el, [
          h('div', 'search-left', [
            h('div', 'search-source', View.t('search.in', { source: provider.name })),
            queryEl,
            keysEl
          ]),
          resultsEl
        ]);
        showQuery();
        buildKeys();
        var q = Kbd.normalize(query);
        if (results.length) showResults(saved.index || 0);
        else if (!Kbd.shouldSearch(q)) showStatus(q ? View.t('search.short') : View.t('search.prompt'));
        else if (saved.settled && searched === q) showStatus(View.t('search.none'));
        else run(q);
        Nav.capture(capture);
        var cols = col ? { results: col } : {};
        if (!saved.focus || !View.restoreFocus(saved.focus, el, cols)) {
          Nav.focus(keyGroup.restore() || keysEl.querySelector('.fc'));
        }
      },
      capture: function () {
        return {
          query: query, mode: mode, results: results, page: page, totalPages: totalPages,
          searched: searched, settled: !loading, index: col ? col.index : 0
        };
      },
      destroy: function () {
        debounce.cancel();
        reqs.abortAll();
        Nav.capture(null);
      }
    };
  };
}(window));
