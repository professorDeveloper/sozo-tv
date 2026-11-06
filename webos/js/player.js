(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});
  var doc = root.document;
  var Dom = Sozo.Dom;
  var h = Dom.h;
  var Nav = Sozo.Nav;
  var View = Sozo.View;
  var Model = Sozo.Model;
  var Filter = Sozo.Filter;
  var Keys = Sozo.Keys;
  var Playback = Sozo.Playback;
  var Subtitles = Sozo.Subtitles;
  var TIMES = Playback.TIMES;

  // Sozo.Player.play(request), with request:
  //   provider      string   allowlisted provider id
  //   contentUrl    string   title url, as sent to /detail and /episodes
  //   title, poster, year    strings, may be ''
  //   isSerial, isLive       booleans
  //   envelope      object|null  film: the /episodes response holding videoSources. Skips /media.
  //   episode       object|null  series, or film without sources: {mediaRef, season, episode, label, availableLangs}
  //   episodes      array    loaded episodes in order
  //   allEpisodes   function optional, the list as it grows while later pages load
  //   loadMoreEpisodes  function(cb) optional. False when the list is whole, else cb(failed) follows each page.
  //   startPosition number   seconds to resume from, 0 to start over
  //   onExit        function called once when the player closes
  // Eligibility and the failure ladder live in js/lib/playback.js, subtitles in js/lib/subtitles.js.

  var HLS_SRC = 'vendor/hls.js/hls.min.js';
  var hlsWaiters = null;
  var active = null;

  function installHlsPolyfills() {
    // hls.js 1.5.20 calls this unguarded, and Chromium 53 does not have it.
    if (!Object['entries']) {
      Object['entries'] = function (o) {
        return Object.keys(o).map(function (k) { return [k, o[k]]; });
      };
    }
  }

  function loadHls(cb) {
    if (root.Hls) { cb(null); return; }
    if (hlsWaiters) { hlsWaiters.push(cb); return; }
    hlsWaiters = [cb];
    installHlsPolyfills();
    var script = doc.createElement('script');
    function settle(err) {
      var waiting = hlsWaiters;
      hlsWaiters = null;
      script.onload = null;
      script.onerror = null;
      if (err && script.parentNode) script.parentNode.removeChild(script);
      for (var i = 0; i < waiting.length; i++) waiting[i](err);
    }
    script.onload = function () { settle(root.Hls ? null : new Error('hls')); };
    script.onerror = function () { settle(new Error('hls')); };
    script.src = HLS_SRC;
    doc.getElementsByTagName('head')[0].appendChild(script);
  }

  function detectEnv(video) {
    var nativeHls = '';
    try { nativeHls = video.canPlayType('application/vnd.apple.mpegurl') || video.canPlayType('application/x-mpegURL'); } catch (e) { nativeHls = ''; }
    return {
      isWebOS: Sozo.Platform.isWebOS(root.navigator && root.navigator.userAgent),
      mse: !!root.MediaSource,
      nativeHls: !!nativeHls
    };
  }

  function isOnline() {
    return !(root.navigator && root.navigator.onLine === false);
  }

  function finite(n) {
    return typeof n === 'number' && isFinite(n) && n > 0;
  }

  function createSession(request, provider, app) {
    var S = {};
    var store = app.store;
    var api = app.api;
    var isLive = !!request.isLive;
    var ep = request.episode || null;
    var envelope = request.envelope || null;
    var open = true;
    var guard = Keys.activationGuard(300);

    var video = doc.createElement('video');
    video.className = 'pl-video';
    video.setAttribute('preload', 'auto');
    video.setAttribute('playsinline', 'playsinline');
    var env = detectEnv(video);

    var hls = null;
    var hasSource = false;
    var ladder = null;
    var token = 0;
    var failedToken = -1;
    var resolveReq = null;
    var resolveTimer = null;
    var subReq = null;
    var cues = null;
    var subtitleTracks = [];
    var subtitleOn = null;
    var subOffset = 0;
    var cueText = '';

    var startAt = 0;
    var applyResumeRule = false;
    var position = 0;
    var duration = 0;
    var everStarted = false;
    var loadStarted = false;
    var metaSeen = false;
    var loadAt = 0;
    var lastCur = -1;
    var lastProgressAt = 0;
    var lastSaveAt = 0;
    var healthySince = 0;
    var reseeks = 0;
    var wantPlay = true;
    var pausedAt = 0;
    var resumeOnVisible = false;
    var offlineHold = false;

    var pendingTarget = null;
    var seekBurst = 0;
    var lastSeekPress = 0;
    var seekTimer = null;
    var okDownAt = null;
    var barPointerX = null;
    var barPointerAt = 0;

    var controlsOn = false;
    var lastActivity = 0;
    var panel = null;
    var pane = null;
    var countdown = null;
    var toastTimer = null;
    var tickTimer = null;
    var leaveDialog = null;

    var el = h('div', 'player');
    var subsEl = h('div', 'pl-subs');
    var busyText = h('div', 'pl-busy-text');
    var busyEl = h('div', 'pl-busy', [h('div', 'spinner'), busyText]);
    var toastEl = h('div', 'pl-toast');
    var titleEl = h('div', 'pl-title');
    var subtitleEl = h('div', 'pl-subtitle');
    var topEl = h('div', 'pl-top', [
      titleEl, subtitleEl,
      isLive ? h('div', 'pl-live', View.t('player.live')) : null
    ]);
    var timeNow = h('div', 'pl-time');
    var timeEnd = h('div', 'pl-time pl-time-end');
    var barFill = h('div', 'pl-bar-fill');
    var barBuffer = h('div', 'pl-bar-buffer');
    var barKnob = h('div', 'pl-bar-knob');
    var barEl = h('div', 'pl-bar fc', [h('div', 'pl-bar-track', [barBuffer, barFill]), barKnob]);
    barEl._fid = 'seek';
    var seekEl = h('div', 'pl-seek', [timeNow, barEl, timeEnd]);
    var buttonsEl = h('div', 'pl-buttons');
    var bottomEl = h('div', 'pl-bottom', [isLive ? null : seekEl, buttonsEl]);
    var playBtn = null;
    View.staticGroup(buttonsEl);

    Dom.add(el, [video, subsEl, busyEl, toastEl, topEl, bottomEl]);
    topEl.style.display = 'none';
    bottomEl.style.display = 'none';
    toastEl.style.display = 'none';
    subsEl.style.display = 'none';

    function setTitles() {
      titleEl.textContent = Model.cleanText(request.title);
      var line = '';
      if (ep && request.isSerial) {
        var n = Model.episodeNumber(ep);
        var season = Model.seasonOf(ep);
        var parts = [];
        if (season !== null) parts.push(View.t('detail.season', { n: season }));
        if (n) parts.push(View.t('detail.episodeN', { n: n }));
        var label = Model.cleanText(ep.label);
        // "Episode 371 · Эпизод 371" says one thing twice.
        if (label && parts.join(' ').indexOf(label) < 0 && (!n || label.indexOf(String(n)) < 0 || !/^\D*\d+\D*$/.test(label))) parts.push(label);
        line = parts.join('  ·  ');
      }
      subtitleEl.textContent = line;
    }

    function setBusy(on, text) {
      busyEl.style.display = on ? '' : 'none';
      busyText.textContent = text || '';
    }

    function toast(text) {
      toastEl.textContent = text;
      toastEl.style.display = '';
      if (toastTimer !== null) root.clearTimeout(toastTimer);
      toastTimer = root.setTimeout(function () { toastTimer = null; toastEl.style.display = 'none'; }, 4000);
    }

    function allEpisodes() {
      return request.allEpisodes ? request.allEpisodes() : request.episodes || [];
    }

    function nextEp() {
      return isLive || !request.isSerial ? null : Playback.nextEpisode(allEpisodes(), ep);
    }

    /* Time and the seek bar */

    function shownTime() {
      if (pendingTarget !== null) return pendingTarget;
      return position;
    }

    function renderBar() {
      if (isLive || !controlsOn) return;
      var t = shownTime();
      var ratio = Model.progressRatio(t, duration);
      var pct = (ratio * 100).toFixed(2) + '%';
      barFill.style.width = pct;
      barKnob.style.left = pct;
      timeNow.textContent = Model.clock(t);
      timeEnd.textContent = duration ? Model.clock(duration) : '';
      if (duration && video.buffered && video.buffered.length) {
        var end = 0;
        try { end = video.buffered.end(video.buffered.length - 1); } catch (e) { end = 0; }
        barBuffer.style.width = (Model.progressRatio(end, duration) * 100).toFixed(2) + '%';
      }
    }

    function renderCue(t) {
      var next = cues ? Subtitles.textAt(cues, t - subOffset) : '';
      if (next === cueText) return;
      cueText = next;
      Dom.clear(subsEl);
      if (next) subsEl.appendChild(h('span', 'pl-subs-text', next));
      subsEl.style.display = next ? '' : 'none';
    }

    /* Controls */

    function defaultControl() {
      return isLive ? buttonsEl.querySelector('.fc') : barEl;
    }

    function touch() { lastActivity = Date.now(); }

    function showControls(focusEl) {
      touch();
      if (!controlsOn) {
        controlsOn = true;
        topEl.style.display = '';
        bottomEl.style.display = '';
        el.classList.add('controls-on');
        renderBar();
        var target = focusEl || defaultControl();
        if (target && !panel && !pane && !countdown) Nav.focus(target, { pointer: true });
      } else if (focusEl) {
        Nav.focus(focusEl, { pointer: true });
      }
    }

    function hideControls() {
      if (!controlsOn) return;
      controlsOn = false;
      topEl.style.display = 'none';
      bottomEl.style.display = 'none';
      el.classList.remove('controls-on');
    }

    function refreshPlayBtn() {
      if (!playBtn) return;
      var playing = wantPlay && !video.ended;
      View.setButtonLabel(playBtn, View.t(playing ? 'player.pause' : 'player.play'));
      var svg = playBtn.querySelector('svg');
      if (svg) playBtn.replaceChild(Dom.icon(playing ? 'pause' : 'play'), svg);
    }

    function audioChoices() {
      if (hls && hls.audioTracks && hls.audioTracks.length > 1) {
        return hls.audioTracks.map(function (t, i) {
          return {
            label: Model.cleanText(t.name) || Model.cleanText(t.lang) || View.t('player.audioN', { n: i + 1 }),
            selected: hls.audioTrack === i,
            pick: function () { hls.audioTrack = i; }
          };
        });
      }
      var native = !hls && video.audioTracks;
      if (native && native.length > 1) {
        var out = [];
        for (var i = 0; i < native.length; i++) out.push(nativeAudioChoice(native, i));
        return out;
      }
      return [];
    }

    function nativeAudioChoice(tracks, i) {
      return {
        label: Model.cleanText(tracks[i].label) || Model.cleanText(tracks[i].language) || View.t('player.audioN', { n: i + 1 }),
        selected: !!tracks[i].enabled,
        pick: function () {
          for (var j = 0; j < tracks.length; j++) tracks[j].enabled = j === i;
        }
      };
    }

    function levelCount() {
      return hls && hls.levels ? hls.levels.length : 0;
    }

    function refreshButtons() {
      var cur = Nav.current();
      var keep = cur && buttonsEl.contains(cur) ? cur._fid : null;
      Dom.clear(buttonsEl);
      playBtn = View.button(View.t('player.pause'), togglePlay, { icon: 'pause', fid: 'playpause' });
      buttonsEl.appendChild(playBtn);
      refreshPlayBtn();
      if (nextEp()) {
        buttonsEl.appendChild(View.button(View.t('player.next'), function () { startEpisode(nextEp()); }, { icon: 'next', fid: 'next' }));
      }
      var sourceCount = ladder ? ladder.list().length : 0;
      if (sourceCount > 1 || levelCount() > 1) {
        buttonsEl.appendChild(View.button(View.t('player.quality'), openQualityPanel, { icon: 'tune', fid: 'quality' }));
      }
      if (subtitleTracks.length) {
        buttonsEl.appendChild(View.button(View.t('player.subtitles'), openSubtitlePanel, { icon: 'subtitles', fid: 'subtitles' }));
      }
      if (audioChoices().length) {
        buttonsEl.appendChild(View.button(View.t('player.audio'), openAudioPanel, { icon: 'audio', fid: 'audio' }));
      }
      // Down from the wide seek bar lands on Play, not on whichever button is nearest its middle.
      buttonsEl._group.set(playBtn);
      if (!keep) return;
      var list = buttonsEl.querySelectorAll('.fc');
      for (var i = 0; i < list.length; i++) {
        if (list[i]._fid === keep) { Nav.focus(list[i], { pointer: true }); return; }
      }
      if (!panel && !pane) Nav.focus(defaultControl(), { pointer: true });
    }

    /* Side panel: a plain list, and a full page when it is the only way forward */

    function closePanel() {
      if (!panel) return;
      var p = panel;
      panel = null;
      if (p.parentNode) p.parentNode.removeChild(p);
      Nav.popScope(p);
      touch();
    }

    // items: {group} or {label, selected, onOk}; extra: a node placed under the title
    function openPanel(title, message, items, wide, extra) {
      closePanel();
      var track = h('div', 'pl-panel-track');
      var focusables = [];
      var first = null;
      items.forEach(function (it) {
        if (it.group) { track.appendChild(h('div', 'pl-group', it.group)); return; }
        var row = h('div', 'pl-item fc' + (it.selected ? ' is-selected' : ''), it.label);
        row._onOk = it.onOk;
        track.appendChild(row);
        focusables.push(row);
        if (it.selected && !first) first = row;
      });
      var clip = h('div', 'pl-panel-clip', track);
      var head = h('div', 'pl-panel-head', [
        h('div', 'pl-panel-title', title),
        message ? h('div', 'pl-panel-message', message) : null,
        extra || null
      ]);
      var p = h('div', 'pl-panel' + (wide ? ' is-wide' : ''), [head, clip]);
      var last = null;
      var scrollY = 0;
      track._group = {
        move: function (dir, cur) {
          var i = focusables.indexOf(cur);
          if (dir === 'up') return focusables[i - 1] || null;
          if (dir === 'down') return focusables[i + 1] || null;
          return null;
        },
        restore: function () { return last || focusables[0] || null; },
        onFocus: function (cur) { last = cur; }
      };
      track._scroller = {
        reveal: function (row) {
          var viewH = clip.offsetHeight;
          var top = row.offsetTop;
          var bottom = top + row.offsetHeight;
          if (top - 40 < scrollY) scrollY = Math.max(0, top - 40);
          else if (bottom + 40 > scrollY + viewH) scrollY = bottom + 40 - viewH;
          Dom.setTransform(track, 0, -scrollY);
        }
      };
      el.appendChild(p);
      clip.style.top = head.offsetHeight + 'px';
      panel = p;
      panel._wide = !!wide;
      Nav.pushScope(p);
      Nav.focus(first || focusables[0], {});
    }

    function sourceLabel(s) {
      return s.label || View.t('player.sourceN', { n: s.order });
    }

    function sourceItems(wide) {
      var cur = ladder.current();
      return ladder.list().map(function (s, i) {
        return {
          label: sourceLabel(s),
          selected: !wide && s === cur,
          onOk: function () {
            if (s === cur && !wide) { closePanel(); return; }
            closePanel();
            ladder.select(i);
            if (s.quality) store.setSetting('quality.' + provider.id, s.quality);
            hidePane();
            refreshButtons();
            load(position);
          }
        };
      });
    }

    function openQualityPanel() {
      var items = [];
      if (levelCount() > 1) {
        items.push({ group: View.t('player.levels') });
        items.push({
          label: View.t('player.auto'), selected: hls.autoLevelEnabled,
          onOk: function () { hls.currentLevel = -1; closePanel(); }
        });
        hls.levels.forEach(function (lv, i) {
          items.push({
            label: lv.height ? lv.height + 'p' : Model.cleanText(lv.name) || String(i + 1),
            selected: !hls.autoLevelEnabled && hls.currentLevel === i,
            onOk: function () { hls.currentLevel = i; closePanel(); }
          });
        });
        items.push({ group: View.t('player.source') });
      }
      openPanel(View.t('player.quality'), '', items.concat(sourceItems(false)));
    }

    function offsetLabel(v) {
      var r = Math.round(v * 10) / 10;
      return (r > 0 ? '+' : r < 0 ? '\u2212' : '') + Math.abs(r) + ' s';
    }

    // A positive offset shows each line later. It lasts while this track is on.
    function timingRow() {
      var label = h('div', 'pl-timing-label', View.t('player.subOffset', { s: offsetLabel(subOffset) }));
      function chip(text, delta) {
        var c = h('div', 'pl-chip fc', text);
        c._onOk = function () {
          subOffset = delta === null ? 0 : Math.max(-60, Math.min(60, subOffset + delta));
          label.textContent = View.t('player.subOffset', { s: offsetLabel(subOffset) });
          cueText = null;
          renderCue(position);
        };
        return c;
      }
      var chips = [chip(offsetLabel(-0.5), -0.5), chip(offsetLabel(0.5), 0.5), chip(View.t('player.subReset'), null)];
      var row = h('div', 'pl-timing', [chips[0], label, chips[1], chips[2]]);
      var last = null;
      // Entering the row lands on +0.5, or on the chip used last.
      row._group = {
        move: function (dir, cur) {
          var i = chips.indexOf(cur);
          if (dir === 'left') return chips[i - 1] || null;
          if (dir === 'right') return chips[i + 1] || null;
          return null;
        },
        restore: function () { return last || chips[1]; },
        onFocus: function (cur) { last = cur; }
      };
      return row;
    }

    function openSubtitlePanel() {
      var items = [{
        label: View.t('player.subOff'), selected: !subtitleOn,
        onOk: function () {
          store.setSetting('subtitle', 'off');
          setSubtitle(null);
          closePanel();
        }
      }];
      subtitleTracks.forEach(function (t) {
        items.push({
          label: t.label, selected: subtitleOn === t,
          onOk: function () {
            store.setSetting('subtitle', t.base);
            setSubtitle(t);
            closePanel();
          }
        });
      });
      openPanel(View.t('player.subtitles'), '', items, false, subtitleOn ? timingRow() : null);
    }

    function openAudioPanel() {
      openPanel(View.t('player.audio'), '', audioChoices().map(function (a) {
        return { label: a.label, selected: a.selected, onOk: function () { a.pick(); closePanel(); } };
      }));
    }

    /* Message pages: nothing to play, an API failure */

    function hidePane() {
      if (!pane) return;
      var p = pane;
      pane = null;
      if (p.parentNode) p.parentNode.removeChild(p);
      Nav.popScope(p);
    }

    function closeLeaveDialog() {
      if (leaveDialog) Sozo.Dialog.close(leaveDialog);
      leaveDialog = null;
    }

    function showPane(title, body, buttons) {
      closeLeaveDialog();
      hidePane();
      closePanel();
      hideControls();
      setBusy(false);
      pane = h('div', 'pl-pane', h('div', 'status status-error', [
        h('div', 'status-title', title),
        h('div', 'status-text', body),
        h('div', 'status-actions', buttons)
      ]));
      el.appendChild(pane);
      Nav.pushScope(pane);
      Nav.focus(buttons[0], { pointer: true });
    }

    function changeSourceBtn(primary) {
      return View.button(View.t('common.changeSource'), function () {
        S.exit();
        app.push('sources', {});
      }, { primary: primary, fid: 'change-source' });
    }

    function backBtn() {
      return View.button(View.t('common.back'), function () { S.exit(); }, { fid: 'player-back' });
    }

    function showNoSources() {
      var buttons = [changeSourceBtn(true)];
      var next = nextEp();
      if (next) {
        buttons.push(View.button(View.t('player.next'), function () { hidePane(); startEpisode(next); }, { fid: 'next-from-pane' }));
      }
      buttons.push(backBtn());
      showPane(View.t('player.noSource.t'), View.t('player.noSource.m'), buttons);
    }

    function showResolveError(err, retry) {
      var m = View.errorMessage(err);
      var buttons = [];
      if (retry) {
        buttons.push(View.button(View.t('common.retry'), function () { hidePane(); retry(); }, { primary: true, fid: 'retry' }));
      }
      buttons.push(changeSourceBtn(!retry));
      buttons.push(backBtn());
      showPane(m.title, m.body, buttons);
    }

    function showChooser() {
      closeLeaveDialog();
      unload();
      setBusy(false);
      hideControls();
      var items = sourceItems(true);
      items.push({
        label: View.t('common.changeSource'),
        onOk: function () { S.exit(); app.push('sources', {}); }
      });
      items.push({ label: View.t('common.back'), onOk: function () { S.exit(); } });
      openPanel(View.t('player.failed.t'), View.t('player.failed.m'), items, true);
    }

    /* Resolving: film envelope, else /media. Never waits forever. */

    function cancelResolve() {
      if (resolveReq) { try { resolveReq.abort(); } catch (e) { resolveReq = null; } }
      resolveReq = null;
      if (resolveTimer !== null) root.clearTimeout(resolveTimer);
      resolveTimer = null;
    }

    function fetchEnvelope(cb) {
      cancelResolve();
      resolveTimer = root.setTimeout(function () {
        cancelResolve();
        cb({ kind: 'timeout' });
      }, TIMES.resolve);
      function done(err, body) {
        if (!open) return;
        cancelResolve();
        cb(err, body);
      }
      if (ep) {
        if (!ep.mediaRef) { root.setTimeout(function () { done({ kind: 'http', status: 404, code: null }); }, 0); return; }
        var langs = ep.availableLangs;
        var lang = Array.isArray(langs) && langs.length ? langs[0] : 'sub';
        resolveReq = api.media(provider.id, ep.mediaRef, lang, done);
        return;
      }
      resolveReq = api.episodes(provider.id, request.contentUrl, 1, function (err, body) {
        if (!err && !Model.isFilmEnvelope(body)) { done({ kind: 'http', status: 404, code: null }); return; }
        done(err, body);
      });
    }

    function checkEnvelope(body) {
      if (body && (Filter.isAdultText(body.title) || Filter.isAdultText(body.description))) return null;
      return Playback.eligible(body, provider.id).sources;
    }

    function openEnvelope(body) {
      var sources = checkEnvelope(body);
      if (sources === null) { showResolveError({ kind: 'adult' }, null); return; }
      if (!sources.length) { showNoSources(); return; }
      envelope = body;
      if (finite(Number(body.duration))) duration = Number(body.duration);
      ladder = Playback.createLadder(sources, Playback.pickStart(sources, store.getSetting('quality.' + provider.id, null)), {
        env: env, hlsjsAllowed: provider.hlsjs === true, isLive: isLive, canReresolve: true
      });
      initSubtitles(body);
      refreshButtons();
      load(startAt);
    }

    function resolveThenOpen() {
      setBusy(true, View.t('player.opening'));
      fetchEnvelope(function (err, body) {
        if (err) { showResolveError(err, resolveThenOpen); return; }
        openEnvelope(body);
      });
    }

    /* Subtitles */

    function initSubtitles(body) {
      subtitleTracks = Subtitles.eligible(body, Sozo.I18n.get());
      setSubtitle(Subtitles.pickDefault(subtitleTracks, Sozo.I18n.get(), store.getSetting('subtitle', null)));
    }

    function dropSubtitleRequest() {
      if (subReq) {
        var x = subReq;
        subReq = null;
        x.onload = null;
        x.onerror = null;
        x.ontimeout = null;
        x.onprogress = null;
        try { x.abort(); } catch (e) { return; }
      }
    }

    function setSubtitle(track) {
      dropSubtitleRequest();
      if (track !== subtitleOn) subOffset = 0;
      subtitleOn = track;
      cues = null;
      renderCue(0);
      if (!track) return;
      var x = new root.XMLHttpRequest();
      subReq = x;
      function failed() {
        if (subReq !== x) return;
        dropSubtitleRequest();
        subtitleOn = null;
        toast(View.t('player.subError'));
      }
      x.onload = function () {
        if (subReq !== x) return;
        subReq = null;
        var parsed = x.status >= 200 && x.status < 300 ? Subtitles.parse(x.responseText) : null;
        if (!parsed) { subReq = x; failed(); return; }
        cues = parsed;
        renderCue(position);
      };
      x.onerror = failed;
      x.ontimeout = failed;
      x.onprogress = function (e) { if (e.loaded > Subtitles.MAX_BYTES) failed(); };
      try {
        x.open('GET', track.file, true);
        x.timeout = TIMES.subtitle;
        x.send();
      } catch (e) {
        failed();
      }
    }

    /* The video element */

    function unload() {
      token++;
      if (hls) {
        try { hls.destroy(); } catch (e) { hls = null; }
        hls = null;
      }
      if (seekTimer !== null) root.clearTimeout(seekTimer);
      seekTimer = null;
      pendingTarget = null;
      if (!hasSource) return;
      hasSource = false;
      try { video.pause(); } catch (e2) { hasSource = false; }
      var kids = video.querySelectorAll('source');
      for (var i = 0; i < kids.length; i++) {
        kids[i].onerror = null;
        video.removeChild(kids[i]);
      }
      video.removeAttribute('src');
      try { video.load(); } catch (e3) { hasSource = false; }
    }

    function doPlay() {
      wantPlay = true;
      // Nothing starts with sound behind another app. onVisibility plays it on return.
      if (doc.hidden) { resumeOnVisible = true; return; }
      var p = null;
      try { p = video.play(); } catch (e) { p = null; }
      if (p && p['catch']) {
        p['catch'](function (err) {
          if (err && err.name === 'NotAllowedError' && open) {
            wantPlay = false;
            refreshPlayBtn();
            setBusy(false);
            showControls();
          }
        });
      }
    }

    function loadWithHlsjs(source, id) {
      loadHls(function (err) {
        if (id !== token || !open) return;
        if (err || !root.Hls.isSupported()) { fail({}); return; }
        var Hls = root.Hls;
        hls = new Hls(Playback.hlsConfig(startAt > 0 && !applyResumeRule ? startAt : -1));
        var recovered = false;
        hls.on(Hls.Events.ERROR, function (name, data) {
          if (id !== token || !data || !data.fatal) return;
          if (data.type === Hls.ErrorTypes.MEDIA_ERROR && !recovered) {
            recovered = true;
            hls.recoverMediaError();
            return;
          }
          fail({ status: data.response && data.response.code });
        });
        hls.on(Hls.Events.MANIFEST_PARSED, function () { if (id === token) { refreshButtons(); doPlay(); } });
        hls.on(Hls.Events.AUDIO_TRACKS_UPDATED, function () { if (id === token) refreshButtons(); });
        hls.attachMedia(video);
        hls.loadSource(source.url);
      });
    }

    function load(at) {
      unload();
      var id = token;
      offlineHold = false;
      startAt = at || 0;
      loadAt = Date.now();
      lastProgressAt = loadAt;
      healthySince = 0;
      metaSeen = false;
      loadStarted = false;
      lastCur = -1;
      reseeks = 0;
      wantPlay = true;
      hasSource = true;
      refreshPlayBtn();
      if (busyEl.style.display === 'none') setBusy(true);
      var source = ladder.current();
      var eng = ladder.engine();
      if (eng === 'file') {
        video.src = source.url;
        video.load();
        doPlay();
      } else if (eng === 'native') {
        var child = doc.createElement('source');
        // A failing <source> reports here, not on the video element.
        child.onerror = function () { if (id === token) fail({}); };
        child.type = 'application/x-mpegURL';
        child.src = relabelled(source.url);
        video.appendChild(child);
        video.load();
        doPlay();
      } else if (eng === 'hlsjs') {
        loadWithHlsjs(source, id);
      } else {
        root.setTimeout(function () { if (id === token) fail({}); }, 0);
      }
    }

    // Native HLS through the loopback relay where the provider's hosts mislabel segments.
    function relabelled(u) {
      var r = app && app.relabel;
      return provider.relabel === true && r && r.ready() ? r.wrap(u) : u;
    }

    function fail(info) {
      if (!open || !ladder || failedToken === token) return;
      failedToken = token;
      if (!isOnline()) { holdOffline(); return; }
      runStep(ladder.fail({ started: loadStarted, status: info && info.status }));
    }

    function holdOffline() {
      offlineHold = true;
      unload();
      showResolveError({ kind: 'offline' }, function () { resumeAfterOffline(); });
    }

    function resumeAfterOffline() {
      hidePane();
      offlineHold = false;
      if (!isOnline()) { holdOffline(); return; }
      load(position || startAt);
    }

    function runStep(step) {
      var at = position || startAt;
      if (step.action === 'chooser') { showChooser(); return; }
      if (step.action === 'reresolve') {
        var started = loadStarted;
        unload();
        setBusy(true, View.t('player.switching'));
        fetchEnvelope(function (err, body) {
          var fresh = err ? null : checkEnvelope(body);
          if (!err && fresh === null) { showResolveError({ kind: 'adult' }, null); return; }
          if (!fresh || !fresh.length) {
            runStep(ladder.fail({ started: started, status: 404 }));
            return;
          }
          envelope = body;
          var adopted = ladder.adopt(fresh, { started: started });
          refreshButtons();
          runStep(adopted);
        });
        return;
      }
      setBusy(true, View.t('player.switching'));
      refreshButtons();
      load(at);
    }

    /* Saving */

    function save() {
      if (isLive || !everStarted) return;
      var pos = pendingTarget !== null ? pendingTarget : position;
      var entry = store.progress(provider.id, request.contentUrl);
      if (!Playback.shouldWrite(pos, duration, entry, !!request.isSerial)) return;
      lastSaveAt = Date.now();
      store.saveProgress({
        provider: provider.id, url: request.contentUrl, title: request.title, thumbnail: request.poster,
        year: request.year, isSerial: !!request.isSerial, position: pos, duration: duration, episode: ep
      });
    }

    /* Video events */

    function onMetadata() {
      metaSeen = true;
      lastProgressAt = Date.now();
      if (finite(video.duration)) duration = video.duration;
      if (isLive) return;
      if (applyResumeRule) {
        startAt = Playback.resumeAt(startAt, duration);
        applyResumeRule = false;
      }
      if (startAt > 0) {
        try { video.currentTime = startAt; } catch (e) { startAt = 0; }
      }
      position = startAt;
      refreshButtons();
      renderBar();
    }

    function onTimeUpdate() {
      // Tearing a source down queues a timeupdate at 0, which must not read as the viewer's place.
      if (!hasSource || !metaSeen) return;
      var cur = video.currentTime;
      if (cur === lastCur) return;
      lastCur = cur;
      var now = Date.now();
      lastProgressAt = now;
      // Some platforms ignore a seek made this early. From memory for webOS, so it is checked here.
      if (!loadStarted && startAt > 0 && Math.abs(cur - startAt) > 10 && reseeks < 2) {
        reseeks++;
        try { video.currentTime = startAt; } catch (e) { reseeks = 2; }
        return;
      }
      if (!loadStarted) {
        loadStarted = true;
        everStarted = true;
        lastSaveAt = now;
        setBusy(false);
        if (!healthySince) healthySince = now;
      }
      if (pendingTarget === null) position = cur;
      if (finite(video.duration)) duration = video.duration;
      renderCue(cur);
      renderBar();
    }

    function onPlaying() {
      lastProgressAt = Date.now();
      setBusy(false);
      refreshPlayBtn();
    }

    function onWaiting() {
      if (wantPlay && loadStarted) setBusy(true);
    }

    function onEnded() {
      if (!open) return;
      if (isLive) { fail({}); return; }
      if (duration) position = duration;
      save();
      nextOrExit();
    }

    function nextOrExit() {
      var next = nextEp();
      if (next) { setBusy(false); startCountdown(next); return; }
      // The list may just be cut short: a later page is on its way, or failed and gets another try.
      if (request.isSerial && request.loadMoreEpisodes && request.loadMoreEpisodes(onMoreEpisodes)) {
        wantPlay = false;
        setBusy(true, View.t('player.opening'));
        return;
      }
      S.exit();
    }

    function onMoreEpisodes(failed) {
      if (!open || wantPlay) return;
      if (failed) { S.exit(); return; }
      nextOrExit();
    }

    function onVideoError() {
      if (!hasSource || !video.error) return;
      fail({});
    }

    /* Auto-next */

    function cancelCountdown() {
      if (!countdown) return;
      var c = countdown;
      countdown = null;
      if (c.el.parentNode) c.el.parentNode.removeChild(c.el);
      Nav.popScope(c.el);
    }

    function startCountdown(next) {
      closePanel();
      setBusy(false);
      wantPlay = false;
      var label = h('div', 'pl-next-title');
      var left = TIMES.autoNext;
      function render() { label.textContent = View.t('player.nextIn', { s: left }); }
      render();
      var now = View.button(View.t('player.playNow'), function () { startEpisode(next); }, { primary: true, fid: 'play-now' });
      var cancel = View.button(View.t('common.cancel'), function () {
        cancelCountdown();
        refreshButtons();
        refreshPlayBtn();
        var list = buttonsEl.querySelectorAll('.fc');
        controlsOn = false;
        showControls(list[1] && list[1]._fid === 'next' ? list[1] : list[0]);
      }, { fid: 'cancel-next' });
      var box = h('div', 'pl-next', [
        label,
        h('div', 'pl-next-name', Model.cleanText(next.label) || View.t('detail.episodeN', { n: Model.episodeNumber(next) })),
        h('div', '', [now, cancel])
      ]);
      hideControls();
      el.appendChild(box);
      countdown = {
        el: box,
        tick: function () {
          left--;
          if (left <= 0) { startEpisode(next); return; }
          render();
        }
      };
      Nav.pushScope(box);
      Nav.focus(now, { pointer: true });
    }

    function startEpisode(next) {
      if (!next) return;
      save();
      cancelCountdown();
      closePanel();
      hidePane();
      hideControls();
      unload();
      dropSubtitleRequest();
      cues = null;
      renderCue(0);
      subtitleTracks = [];
      subtitleOn = null;
      ladder = null;
      ep = next;
      envelope = null;
      startAt = 0;
      applyResumeRule = false;
      position = 0;
      duration = 0;
      setTitles();
      refreshButtons();
      resolveThenOpen();
    }

    /* Transport */

    function togglePlay() {
      touch();
      if (!hasSource) return;
      if (video.ended) { position = 0; try { video.currentTime = 0; } catch (e) { position = 0; } doPlay(); refreshPlayBtn(); return; }
      if (wantPlay) pause();
      else resume();
    }

    function pause() {
      wantPlay = false;
      pausedAt = Date.now();
      try { video.pause(); } catch (e) { wantPlay = false; }
      setBusy(false);
      refreshPlayBtn();
      save();
      showControls();
    }

    function resume() {
      lastProgressAt = Date.now();
      // A live stream paused for long is far behind the edge, so it reopens.
      if (isLive && pausedAt && Date.now() - pausedAt > 10000 && ladder) {
        pausedAt = 0;
        load(0);
        return;
      }
      doPlay();
      refreshPlayBtn();
    }

    function commitSeek() {
      if (seekTimer !== null) root.clearTimeout(seekTimer);
      seekTimer = null;
      if (pendingTarget === null) return;
      var t = pendingTarget;
      pendingTarget = null;
      try { video.currentTime = t; } catch (e) { t = position; }
      position = t;
      lastProgressAt = Date.now();
      lastCur = -1;
      renderCue(t);
      renderBar();
      save();
    }

    function seekBy(dir) {
      showControls();
      if (isLive || !duration || !metaSeen) return;
      var now = Date.now();
      seekBurst = now - lastSeekPress < TIMES.seekBurst ? seekBurst + 1 : 1;
      lastSeekPress = now;
      var base = pendingTarget !== null ? pendingTarget : position;
      pendingTarget = Playback.seekTarget(base, dir * Playback.seekStep(seekBurst), duration);
      renderBar();
      if (seekTimer !== null) root.clearTimeout(seekTimer);
      seekTimer = root.setTimeout(commitSeek, TIMES.seekCommit);
    }

    function requestExit() {
      // The first timeupdate can arrive up to 250 ms after the picture does.
      var showing = hasSource && !video.paused && video.currentTime > 0;
      if (isLive && (loadStarted || showing)) {
        leaveDialog = Sozo.Dialog.show({
          title: View.t('player.leaveLive.t'),
          message: View.t('player.leaveLive.m'),
          actions: [
            { label: View.t('player.stay'), primary: true, focus: true },
            { label: View.t('player.leave'), onOk: function () { S.exit(); } }
          ]
        });
        return;
      }
      S.exit();
    }

    /* Input */

    function capture(a, e) {
      if (!open || !a || Sozo.Dialog.isOpen()) return false;
      if (a === 'stop') { S.exit(); return true; }
      var transport = a === 'play' || a === 'pause' || a === 'rewind' || a === 'forward';
      if (transport && hasSource && !panel && !pane && !countdown) {
        if (a === 'play') { if (!wantPlay) resume(); showControls(); }
        else if (a === 'pause') { if (wantPlay) pause(); }
        else seekBy(a === 'rewind' ? -1 : 1);
        return true;
      }
      if (panel) {
        if (a === 'back') {
          if (panel._wide) S.exit();
          else closePanel();
          return true;
        }
        return false;
      }
      if (pane) {
        if (a === 'back') { S.exit(); return true; }
        return false;
      }
      if (countdown) {
        if (a === 'back') {
          var cancel = countdown.el.querySelectorAll('.fc')[1];
          if (cancel && cancel._onOk) cancel._onOk(cancel);
          return true;
        }
        return false;
      }
      if (a === 'back') { requestExit(); return true; }
      if (a === 'ok') {
        var repeat = e && e.repeat;
        var now = Date.now();
        if (repeat || (okDownAt !== null && now - okDownAt < 1500)) return true;
        okDownAt = now;
      }
      if (!controlsOn) {
        if (a === 'ok') {
          if (guard.allow('key')) togglePlay();
          showControls();
          return true;
        }
        if (a === 'left' || a === 'right') { seekBy(a === 'left' ? -1 : 1); return true; }
        if (a === 'up' || a === 'down') { showControls(); return true; }
        return false;
      }
      touch();
      if (Nav.current() === barEl) {
        if (a === 'left' || a === 'right') { seekBy(a === 'left' ? -1 : 1); return true; }
        if (a === 'ok') {
          if (guard.allow('key')) togglePlay();
          return true;
        }
      }
      if (a === 'ok') okDownAt = null;
      return false;
    }

    function onKeyUp(e) {
      if (Keys.action(e.keyCode) !== 'ok') return;
      okDownAt = null;
      guard.release('key');
    }

    function onBarDown(e) {
      barPointerX = e.clientX;
      barPointerAt = Date.now();
    }

    // Only a pointer click reaches here: capture() takes OK while the bar has focus, and Nav keeps
    // OK from capture() while the pointer shows.
    function onBarActivate() {
      if (!guard.allow('pointer')) return;
      touch();
      if (barPointerX === null || Date.now() - barPointerAt > 1000 || !duration) { togglePlay(); return; }
      var rect = barEl.getBoundingClientRect();
      var ratio = rect.width ? (barPointerX - rect.left) / rect.width : 0;
      barPointerX = null;
      pendingTarget = Playback.seekTarget(0, Math.max(0, Math.min(1, ratio)) * duration, duration);
      commitSeek();
    }

    function onRootMove(e) {
      if (Nav.pointerMoved(e)) onRootWheel();
    }

    function onRootWheel() {
      el.classList.remove('pointer-idle');
      if (!pane && !panel && !countdown) showControls();
      else touch();
    }

    function onRootClick(e) {
      for (var p = e.target; p && p !== el; p = p.parentNode) {
        if (p.classList && (p.classList.contains('fc') || p.classList.contains('pl-panel') || p.classList.contains('pl-pane') || p.classList.contains('pl-next'))) {
          return;
        }
      }
      if (panel && !panel._wide) { closePanel(); return; }
      if (pane || panel || countdown || !guard.allow('pointer')) return;
      if (!controlsOn) { showControls(); return; }
      togglePlay();
    }

    function onVisibility() {
      if (doc.hidden) {
        resumeOnVisible = wantPlay && hasSource;
        save();
        try { video.pause(); } catch (e) { resumeOnVisible = false; }
        return;
      }
      lastProgressAt = Date.now();
      loadAt = lastProgressAt;
      if (resumeOnVisible && hasSource) {
        resumeOnVisible = false;
        if (isLive) load(0);
        else doPlay();
      }
    }

    function onOnline() {
      if (offlineHold) resumeAfterOffline();
    }

    /* One timer for everything periodic, so teardown has one thing to stop */

    function tick() {
      if (!open) return;
      var now = Date.now();
      if (doc.hidden) return;
      if (countdown) { countdown.tick(); return; }
      if (hasSource && ladder) {
        if (!metaSeen) {
          var limit = ladder.current() && ladder.current().isHls ? TIMES.metadataHls : TIMES.metadataFile;
          if (now - loadAt > limit) fail({});
        } else if (wantPlay && !video.ended && now - lastProgressAt > TIMES.stall) {
          // Some native pipelines stop at the end without an ended event.
          if (!isLive && duration && position >= duration - 2) onEnded();
          else fail({});
        } else if (wantPlay && loadStarted && !video.paused) {
          if (now - lastSaveAt >= TIMES.saveEvery) save();
          if (healthySince && now - healthySince >= TIMES.healthy) {
            healthySince = now;
            ladder.healthy();
          }
        }
      }
      if (controlsOn && wantPlay && !panel && !pane && !Sozo.Dialog.isOpen() && pendingTarget === null && now - lastActivity >= TIMES.hideControls) {
        hideControls();
        el.classList.add('pointer-idle');
      }
    }

    var videoEvents = {
      loadedmetadata: onMetadata, timeupdate: onTimeUpdate, playing: onPlaying, waiting: onWaiting,
      ended: onEnded, error: onVideoError, seeked: onPlaying
    };

    S.start = function () {
      var k;
      for (k in videoEvents) video.addEventListener(k, videoEvents[k], false);
      el.addEventListener('mousemove', onRootMove, false);
      el.addEventListener('click', onRootClick, false);
      el.addEventListener('wheel', onRootWheel, false);
      doc.addEventListener('keyup', onKeyUp, true);
      doc.addEventListener('visibilitychange', onVisibility, false);
      root.addEventListener('online', onOnline, false);
      barEl.addEventListener('mousedown', onBarDown, false);
      barEl._onOk = onBarActivate;

      doc.getElementById('app').appendChild(el);
      Nav.pushScope(el);
      Nav.capture(capture);
      setTitles();
      refreshButtons();
      setBusy(true, View.t('player.opening'));
      startAt = request.startPosition > 0 ? request.startPosition : 0;
      position = startAt;
      applyResumeRule = startAt > 0;
      tickTimer = root.setInterval(tick, 1000);
      if (envelope && !ep) openEnvelope(envelope);
      else resolveThenOpen();
    };

    S.exit = function () {
      if (!open) return;
      if (pendingTarget !== null) position = pendingTarget;
      save();
      closeLeaveDialog();
      open = false;
      if (tickTimer !== null) root.clearInterval(tickTimer);
      if (toastTimer !== null) root.clearTimeout(toastTimer);
      tickTimer = null;
      toastTimer = null;
      cancelResolve();
      dropSubtitleRequest();
      cues = null;
      cancelCountdown();
      closePanel();
      hidePane();
      unload();
      for (var k in videoEvents) video.removeEventListener(k, videoEvents[k], false);
      el.removeEventListener('mousemove', onRootMove, false);
      el.removeEventListener('click', onRootClick, false);
      el.removeEventListener('wheel', onRootWheel, false);
      doc.removeEventListener('keyup', onKeyUp, true);
      doc.removeEventListener('visibilitychange', onVisibility, false);
      root.removeEventListener('online', onOnline, false);
      barEl.removeEventListener('mousedown', onBarDown, false);
      Nav.capture(null);
      Dom.remove(el);
      Nav.popScope(el);
      active = null;
      if (request.onExit) request.onExit();
    };

    return S;
  }

  function play(request) {
    if (active) return;
    var app = Sozo.App;
    var provider = Sozo.Providers.byId(request.provider);
    if (!provider || Filter.isAdultText(request.title)) {
      var m = View.errorMessage({ kind: 'adult' });
      Sozo.Dialog.show({
        title: m.title, message: m.body,
        actions: [{ label: View.t('common.close'), primary: true, onOk: function () { if (request.onExit) request.onExit(); } }],
        onDismiss: function () { if (request.onExit) request.onExit(); }
      });
      return;
    }
    active = createSession(request, provider, app);
    active.start();
  }

  Sozo.Player = {
    play: play,
    isOpen: function () { return !!active; }
  };
}(window));
