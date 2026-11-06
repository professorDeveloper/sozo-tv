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
  var Playback = Sozo.Playback;

  var MAX_EPISODE_PAGES = 30;

  Sozo.Screens.detail = function (app, params, state) {
    var saved = state || {};
    var card = params.card || {};
    var providerId = params.provider;
    var provider = app.findProvider(providerId);
    var isLive = !!provider && provider.kind === 'live';
    var url = Model.cardUrl(card);
    var el = h('div', 'detail');
    var reqs = View.requests();

    var detail = saved.detail || null;
    var envelope = saved.envelope || null;
    var episodes = saved.episodes || [];
    var isSerial = saved.isSerial || false;
    var episodesState = saved.episodesState || 'loading';
    var seasonIndex = saved.seasonIndex || 0;
    var pageNext = saved.pageNext || 0;
    var pageTotal = saved.pageTotal || 0;
    var pagesLoading = false;
    var pagesFailed = false;
    var pageWaiters = [];
    var playOnSettle = null;

    var cols = {};
    var page = null;
    var playBtn = null;
    var listBtn = null;
    var actionsEl = h('div', 'detail-actions');
    var episodesSlot = h('div', 'detail-episodes');
    var pagesEl = h('div', 'detail-pages');
    var seasons = [];
    var episodeCol = null;
    var movesAtStart = 0;

    function title() {
      return Model.cleanText(detail && detail.title) || View.cardTitle(card);
    }

    function poster() {
      return Model.safeImage((detail && detail.thumbnail) || card.thumbnail);
    }

    function storeEntry() {
      return {
        provider: providerId, url: url, title: title(), thumbnail: poster(),
        year: Model.year((detail && detail.year) || card.year), isSerial: isSerial
      };
    }

    function playable() {
      return !!envelope || episodes.length > 0;
    }

    function pagesLeft() {
      return pageNext > 0 && pageNext <= pageTotal && pageNext <= MAX_EPISODE_PAGES;
    }

    function resumeInfo() {
      var entry = isLive ? null : app.store.progress(providerId, url);
      if (envelope) {
        var at = entry ? Playback.resumeAt(entry.position, entry.duration) : 0;
        return { episode: null, position: at, resume: at > 0 };
      }
      var target = Model.resumeTarget(episodes, entry, pagesLeft());
      if (!target) return { episode: null, position: 0, resume: false };
      if (target.position) {
        target.position = Playback.resumeAt(target.position, entry.duration);
        target.resume = target.position > 0;
      }
      return target;
    }

    function playLabel() {
      if (isLive) return View.t('detail.watchLive');
      var info = resumeInfo();
      if (info.pending) return View.t(pagesFailed ? 'common.retry' : 'app.loading');
      if (info.episode && isSerial) {
        var n = Model.episodeNumber(info.episode);
        if (n) return View.t(info.resume ? 'detail.resumeEpisode' : 'detail.playEpisode', { n: n });
      }
      if (info.resume && info.position > 0) return View.t('detail.resumeAt', { time: Model.clock(info.position) });
      return info.resume ? View.t('detail.resume') : View.t('detail.play');
    }

    function refreshPlay() {
      if (playBtn) View.setButtonLabel(playBtn, playLabel());
    }

    // Nothing is left to continue once the last episode is done. Only a whole list can tell.
    function dropFinishedSeries() {
      if (isLive || envelope || !isSerial || pagesLeft()) return;
      if (Model.seriesDone(episodes, app.store.progress(providerId, url))) app.store.removeProgress(providerId, url);
    }

    function afterPlayer() {
      dropFinishedSeries();
      refreshPlay();
    }

    // The one way into the player. Adult detail pages never get this far.
    function play(episode, position) {
      Sozo.Player.play({
        provider: providerId,
        contentUrl: url,
        title: title(),
        poster: poster(),
        year: Model.year((detail && detail.year) || card.year),
        isSerial: isSerial,
        isLive: isLive,
        envelope: envelope,
        episode: envelope ? null : episode,
        episodes: episodes,
        allEpisodes: function () { return episodes; },
        loadMoreEpisodes: loadMoreEpisodes,
        startPosition: position || 0,
        onExit: afterPlayer
      });
    }

    // Play never falls back to the first episode while the saved one may still be on a later page,
    // as the first save from there would replace the viewer's place.
    function onPlay() {
      var info = resumeInfo();
      if (info.pending) {
        playOnSettle = Nav.moves();
        if (pagesFailed) retryPages();
        return;
      }
      play(info.episode, info.position);
    }

    function renderActions() {
      var cur = Nav.current();
      var keepFid = cur && actionsEl.contains(cur) ? cur._fid : null;
      Dom.clear(actionsEl);
      playBtn = null;
      if (playable() && episodesState !== 'loading') {
        playBtn = View.button(playLabel(), onPlay, { primary: true, icon: 'play', fid: 'play' });
        actionsEl.appendChild(playBtn);
      }
      if (!isLive) {
        var inList = app.store.inMyList(providerId, url);
        listBtn = View.button(View.t(inList ? 'detail.remove' : 'detail.add'), function () {
          var added = app.store.toggleMyList(storeEntry());
          View.setButtonLabel(listBtn, View.t(added ? 'detail.remove' : 'detail.add'));
          var svg = listBtn.querySelector('svg');
          if (svg) listBtn.replaceChild(Dom.icon(added ? 'check' : 'plus'), svg);
        }, { icon: inList ? 'check' : 'plus', fid: 'mylist' });
        actionsEl.appendChild(listBtn);
      }
      if (keepFid) {
        var keep = keepFid === 'play' ? playBtn : listBtn;
        var target = keep || actionsEl.querySelector('.fc');
        if (target) Nav.focus(target, { pointer: true });
      }
    }

    function episodeCard(ep) {
      var n = Model.episodeNumber(ep);
      var label = Model.cleanText(ep.label) || (n ? View.t('detail.episodeN', { n: n }) : '');
      var sub = [Model.cleanText(ep.runtime), Model.cleanText(ep.airdate)].filter(function (x) { return !!x; }).join('  ·  ');
      return View.wideCard(label, sub, Model.safeImage(ep.image) || Model.safeImage(card.thumbnail), function () {
        var entry = app.store.progress(providerId, url);
        var same = entry && !entry.finished && Model.sameEpisode(ep, entry.episode);
        play(ep, same ? entry.position : 0);
      });
    }

    function seasonChips() {
      if (seasons.length < 2) return null;
      var chips = seasons.map(function (s, i) {
        var label = s.season === null ? View.t('detail.episodes') : View.t('detail.season', { n: s.season });
        var chip = h('div', 'chip fc' + (i === seasonIndex ? ' is-selected' : ''), label);
        chip._fid = 'season-' + i;
        chip._onOk = function () {
          if (seasonIndex === i) return;
          seasonIndex = i;
          var all = chip.parentNode.querySelectorAll('.chip');
          for (var j = 0; j < all.length; j++) all[j].classList.toggle('is-selected', all[j] === chip);
          episodeCol.setItems(seasons[i].episodes, 0);
        };
        return chip;
      });
      var row = h('div', 'chips season-chips', chips);
      row._group = { restore: function () { return chips[seasonIndex]; } };
      return row;
    }

    function renderEpisodes(index) {
      renderEpisodeList(index);
      episodesSlot.appendChild(pagesEl);
    }

    function renderEpisodeList(index) {
      Dom.disposeTree(episodesSlot);
      Dom.clear(episodesSlot);
      episodeCol = null;
      delete cols.episodes;
      if (envelope) return;

      if (episodesState === 'error') {
        episodesSlot.appendChild(h('div', 'detail-inline', [
          h('div', 'status-text', View.t('detail.episodesError')),
          View.button(View.t('common.retry'), function () {
            Nav.focus(actionsEl.querySelector('.fc'));
            movesAtStart = Nav.moves();
            loadEpisodes();
          }, { fid: 'episodes-retry' })
        ]));
        return;
      }
      if (episodesState === 'loading') {
        episodesSlot.appendChild(h('div', 'detail-inline detail-loading', h('div', 'status-text', View.t('app.loading'))));
        return;
      }
      if (!episodes.length) {
        episodesSlot.appendChild(h('div', 'detail-inline', h('div', 'status-text', View.t('detail.noEpisodes'))));
        return;
      }
      // A film whose only stream reference is one episode needs no list.
      if (!isSerial && episodes.length === 1) return;

      seasons = Model.groupSeasons(episodes);
      if (seasonIndex >= seasons.length) seasonIndex = 0;
      episodeCol = new Sozo.Collection({
        key: 'episodes', items: seasons[seasonIndex].episodes, cellW: View.WIDE.cellW, cellH: View.WIDE.cellH,
        index: index !== undefined ? index : saved.indices && saved.indices.episodes || 0, render: episodeCard
      });
      cols.episodes = episodeCol;
      Dom.add(episodesSlot, [
        h('div', 'row-label', View.t('detail.episodes')),
        seasonChips(),
        h('div', 'reveal-unit', episodeCol.root)
      ]);
    }

    function appendEpisodes(fresh) {
      var before = episodes.length;
      episodes = episodes.concat(Filter.episodes(fresh));
      if (episodes.length === before) return;
      if (!episodeCol) {
        renderEpisodes();
        renderActions();
        Nav.ensure();
        return;
      }
      var grouped = Model.groupSeasons(episodes);
      if (grouped.length !== seasons.length) {
        var focused = Nav.current();
        var focusInSlot = focused && episodesSlot.contains(focused);
        var onChip = focusInSlot && !episodeCol.root.contains(focused);
        var season = seasons[seasonIndex].season;
        for (var i = 0; i < grouped.length; i++) if (grouped[i].season === season) seasonIndex = i;
        renderEpisodes(episodeCol.index);
        if (focusInSlot) Nav.focus(onChip ? episodesSlot.querySelector('.chip.is-selected') : episodeCol.restore());
        return;
      }
      seasons = grouped;
      var cur = seasons[seasonIndex].episodes;
      var added = cur.slice(episodeCol.items.length);
      if (added.length) episodeCol.addItems(added);
    }

    function renderPagesFailed() {
      Dom.clear(pagesEl);
      if (!pagesFailed) return;
      Dom.add(pagesEl, h('div', 'detail-inline', [
        h('div', 'status-text', View.t('detail.episodesError')),
        View.button(View.t('common.retry'), retryPages, { fid: 'pages-retry' })
      ]));
    }

    function retryPages() {
      var hadFocus = Nav.current() && pagesEl.contains(Nav.current());
      pagesFailed = false;
      renderPagesFailed();
      if (hadFocus) Nav.focus((episodeCol && episodeCol.restore()) || actionsEl.querySelector('.fc'));
      refreshPlay();
      loadRemainingEpisodes();
    }

    function afterPage(failed) {
      if (!failed) dropFinishedSeries();
      refreshPlay();
      var waiting = pageWaiters;
      pageWaiters = [];
      waiting.forEach(function (cb) { cb(failed); });
      if (playOnSettle === null) return;
      if (failed || Nav.moves() !== playOnSettle || Sozo.Player.isOpen()) {
        playOnSettle = null;
      } else if (!resumeInfo().pending) {
        playOnSettle = null;
        onPlay();
      }
    }

    function loadRemainingEpisodes() {
      if (pagesLoading || !pagesLeft()) return;
      pagesLoading = true;
      pagesFailed = false;
      renderPagesFailed();
      reqs.add(app.api.episodes(providerId, url, pageNext, function (err, body) {
        pagesLoading = false;
        if (err || !body) {
          pagesFailed = true;
          renderPagesFailed();
          afterPage(true);
          return;
        }
        pageNext++;
        appendEpisodes(body.episodes);
        afterPage(false);
        loadRemainingEpisodes();
      }));
    }

    // For the player, when an episode ends with nothing after it in the list so far.
    function loadMoreEpisodes(cb) {
      if (!pagesLeft()) return false;
      pageWaiters.push(cb);
      loadRemainingEpisodes();
      return true;
    }

    function loadEpisodes() {
      episodesState = 'loading';
      renderEpisodes();
      renderActions();
      reqs.add(app.api.episodes(providerId, url, 1, function (err, body) {
        if (err) {
          episodesState = 'error';
          renderEpisodes();
          renderActions();
          Nav.ensure();
          return;
        }
        isSerial = body.isSerial === true;
        episodes = Filter.episodes(body.episodes);
        envelope = Model.isFilmEnvelope(body) ? body : null;
        episodesState = 'ready';
        pageTotal = envelope ? 0 : parseInt(body.totalPages, 10) || 1;
        pageNext = pageTotal > 1 ? 2 : 0;
        renderActions();
        renderEpisodes();
        focusPrimary();
        if (pagesLeft()) loadRemainingEpisodes();
        else afterPage(false);
      }));
    }

    // Play takes focus once it exists, unless the viewer has already moved somewhere else.
    function focusPrimary() {
      var cur = Nav.current();
      if (cur && root.document.documentElement.contains(cur) && Nav.moves() !== movesAtStart) return;
      var first = actionsEl.querySelector('.fc');
      if (first) Nav.focus(first);
    }

    function factsLine() {
      var parts = [
        Model.year((detail && detail.year) || card.year),
        Model.cleanText(detail && detail.duration),
        Model.cleanText(detail && detail.country),
        Model.rating((detail && detail.rating) || card.rating) ? '★ ' + Model.rating((detail && detail.rating) || card.rating) : ''
      ];
      return parts.filter(function (x) { return !!x; }).join('  ·  ');
    }

    function renderBody() {
      Dom.disposeTree(el);
      Dom.clear(el);
      cols = {};
      page = new Sozo.Page({ viewH: app.contentH(false) });

      var genres = Model.nameList((detail && detail.genres) || card.genres);
      var cast = Model.nameList(detail && detail.cast).slice(0, 8);
      var director = Model.cleanText(detail && detail.director);
      var desc = Model.cleanText((detail && detail.description) || card.description);
      if (desc === title()) desc = '';
      var backdrop = isLive ? '' : Model.bannerUrl((detail && detail.banner) || card.banner);
      var posterEl = Dom.lazyImage(Model.posterUrl(poster()), 'detail-poster-img' + (isLive ? ' card-img-contain' : ''), 0);
      var facts = factsLine();

      var head = h('div', 'detail-head', [
        h('div', 'detail-poster', [h('div', 'card-ph', h('span', 'card-ph-title', title())), posterEl]),
        h('div', 'detail-info', [
          h('div', 'detail-title', title()),
          facts ? h('div', 'detail-facts', facts) : null,
          genres.length ? h('div', 'detail-genres', genres.join(', ')) : null,
          desc ? h('div', 'detail-desc', desc) : null,
          director ? h('div', 'detail-line', [h('span', 'detail-line-key', View.t('detail.director') + ': '), director]) : null,
          cast.length ? h('div', 'detail-line', [h('span', 'detail-line-key', View.t('detail.cast') + ': '), cast.join(', ')]) : null,
          actionsEl
        ])
      ]);
      View.staticGroup(actionsEl);

      if (backdrop) {
        el.appendChild(h('div', 'detail-backdrop', Dom.lazyImage(backdrop, 'detail-backdrop-img', 0)));
        el.appendChild(h('div', 'detail-backdrop-shade'));
      }
      page.add(head);
      page.snapNode = head;
      page.add(episodesSlot);

      renderEpisodes();

      var related = provider && provider.related ? Filter.cards(detail && detail.related, providerId) : [];
      if (related.length) {
        var relCol = new Sozo.Collection({
          key: 'related', items: related, cellW: View.POSTER.cellW, cellH: View.POSTER.cellH,
          index: saved.indices && saved.indices.related || 0,
          render: function (c) { return View.posterCard(c, function () { app.openDetail(c, providerId); }); }
        });
        cols.related = relCol;
        page.add(View.row(View.t('detail.related'), relCol), relCol);
      }
      el.appendChild(page.root);
      page.updateActive();
    }

    function blocked() {
      Dom.clear(el);
      el.appendChild(View.errorView({ kind: 'adult' }, null, [
        View.button(View.t('common.back'), function () { app.back(); }, { primary: true })
      ]));
      Nav.ensure();
    }

    function loadDetail() {
      Dom.clear(el);
      el.appendChild(View.spinner());
      Nav.ensure();
      reqs.add(app.api.detail(providerId, url, function (err, body) {
        if (err) {
          Dom.clear(el);
          el.appendChild(View.errorView(err, loadDetail, [
            View.button(View.t('common.back'), function () { app.back(); })
          ]));
          Nav.ensure();
          return;
        }
        if (Filter.isAdultDetail(body)) {
          reqs.abortAll();
          episodes = [];
          envelope = null;
          blocked();
          return;
        }
        detail = body;
        if (body.isSerial === true) isSerial = true;
        renderBody();
        renderActions();
        if (episodesState === 'loading') {
          loadEpisodes();
          // Episodes can take a while: Play takes focus when they answer, if the viewer has not moved.
          if (!Nav.current()) Nav.ensure();
        } else {
          focusPrimary();
        }
      }));
    }

    return {
      el: el,
      start: function () {
        movesAtStart = Nav.moves();
        if (!provider || !url || Filter.isAdultCard(card)) {
          blocked();
          return;
        }
        if (detail) {
          renderBody();
          renderActions();
          if (episodesState === 'loading') loadEpisodes();
          else loadRemainingEpisodes();
          if (saved.focus && View.restoreFocus(saved.focus, el, cols)) return;
          focusPrimary();
          return;
        }
        loadDetail();
      },
      capture: function () {
        var idx = {};
        for (var k in cols) if (Object.prototype.hasOwnProperty.call(cols, k)) idx[k] = cols[k].index;
        return {
          detail: detail, envelope: envelope, episodes: episodes, isSerial: isSerial,
          episodesState: episodesState, seasonIndex: seasonIndex, indices: idx, pageNext: pageNext, pageTotal: pageTotal
        };
      },
      onOnline: function () {
        if (!detail) loadDetail();
        else if (pagesFailed) retryPages();
      },
      destroy: function () { reqs.abortAll(); }
    };
  };
}(window));
