(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});
  var doc = root.document;
  var Spatial = Sozo.Spatial;
  var Keys = Sozo.Keys;

  var BOUNDS = { x: 0, y: 0, w: 1920, h: 1080 };

  var appRoot = null;
  var current = null;
  var lastRect = null;
  var scopes = [];
  var moves = 0;
  var okHeldAt = null;
  var backHeldAt = null;
  var handlers = { back: null, capture: null };
  var keyThrottle = Keys.throttle(90);
  var okGuard = Keys.activationGuard(300);
  var wheelThrottle = Keys.throttle(160);
  var pointer = Keys.pointerTracker();
  var clickExpected = false;
  var clickTimer = null;
  var CLICK_WAIT = 400;

  function closestWith(el, prop) {
    for (var p = el; p; p = p.parentNode) if (p[prop]) return p[prop];
    return null;
  }

  // Offsets ignore running transitions and the focus scale, and _tx/_ty hold each track's target
  // translation. Rects stay stable while a track is still animating, even on fast key repeat.
  function logicalRect(el) {
    var x = 0;
    var y = 0;
    var p;
    for (p = el; p && p !== doc.body; p = p.offsetParent) {
      x += p.offsetLeft;
      y += p.offsetTop;
    }
    for (p = el; p && p !== doc; p = p.parentNode) {
      if (p._tx) x += p._tx;
      if (p._ty) y += p._ty;
    }
    return { x: x, y: y, w: el.offsetWidth, h: el.offsetHeight };
  }

  function scope() {
    return scopes.length ? scopes[scopes.length - 1] : appRoot;
  }

  function inDom(el) {
    return !!el && doc.documentElement.contains(el);
  }

  function inScope(el) {
    return !!el && scope().contains(el);
  }

  function candidates(excludeGroup) {
    var list = scope().querySelectorAll('.fc');
    var out = [];
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      if (el === current || !el.offsetWidth) continue;
      if (excludeGroup && closestWith(el, '_group') === excludeGroup) continue;
      var r = logicalRect(el);
      r.el = el;
      out.push(r);
    }
    return out;
  }

  function scopeHolding(el) {
    for (var i = scopes.length - 1; i >= 0; i--) if (scopes[i].contains(el)) return i;
    return -1;
  }

  function focus(el, opts) {
    if (!el) return false;
    // Content arriving under a dialog or the player waits there, and takes focus when that closes.
    var holder = scopeHolding(el);
    if (holder < scopes.length - 1) {
      scopes[holder + 1]._prevFocus = el;
      return false;
    }
    var o = opts || {};
    if (current && current !== el) {
      current.classList.remove('is-focus');
      if (current._onBlur) current._onBlur();
    }
    current = el;
    el.classList.add('is-focus');
    var group = closestWith(el, '_group');
    if (group && group.onFocus) group.onFocus(el, o);
    var scroller = closestWith(el, '_scroller');
    if (scroller) scroller.reveal(el, o);
    lastRect = logicalRect(el);
    if (el._onFocus) el._onFocus(el);
    return true;
  }

  // Keeps the promise that focus is never lost: nearest survivor, else the first focusable.
  function ensure() {
    if (inDom(current) && inScope(current)) return true;
    current = null;
    var list = candidates();
    if (!list.length) return false;
    var best = list[0];
    if (lastRect) {
      var bestD = Infinity;
      for (var i = 0; i < list.length; i++) {
        var dx = list[i].x - lastRect.x;
        var dy = list[i].y - lastRect.y;
        var d = dx * dx + dy * dy;
        if (d < bestD) { bestD = d; best = list[i]; }
      }
    }
    var group = closestWith(best.el, '_group');
    var restored = group && group.restore ? group.restore() : null;
    return focus(restored && inDom(restored) ? restored : best.el);
  }

  function move(dir) {
    if (!ensure()) return false;
    var cur = current;
    var group = closestWith(cur, '_group');
    if (group && group.move) {
      var inner = group.move(dir, cur);
      if (inner) {
        moves++;
        return focus(inner);
      }
    }
    var from = logicalRect(cur);
    var exclude = group && group.move ? group : null;
    // Content scrolled up behind the top bar is still nearer in meaning than the tabs over it.
    var all = candidates(exclude);
    var scroller = closestWith(cur, '_scroller');
    var hit = scroller ? Spatial.findNext(from, dir, all.filter(function (c) {
      return closestWith(c.el, '_scroller') === scroller;
    }), BOUNDS) : null;
    if (!hit) hit = Spatial.findNext(from, dir, all, BOUNDS);
    if (!hit) return false;
    var target = hit.el;
    var nextGroup = closestWith(target, '_group');
    if (nextGroup && nextGroup !== group && nextGroup.restore) {
      var restored = nextGroup.restore();
      if (restored && inDom(restored) && inScope(restored)) target = restored;
    }
    moves++;
    return focus(target);
  }

  function activate(el, source) {
    if (!el || !el._onOk) return false;
    if (!okGuard.allow(source)) return false;
    el._onOk(el);
    return true;
  }

  function pressOk(e) {
    if (handlers.capture && handlers.capture('ok', e) === true) return;
    if (ensure()) activate(current, 'key');
  }

  // A press with the pointer showing is a click, which follows the pointer and not the focus. If
  // the click never comes, the reported state was wrong: the key acts, and keys act from then on.
  function clickDidNotCome(e) {
    clickTimer = null;
    pointer.setVisible(false);
    pressOk(e);
  }

  function onKeyDown(e) {
    var a = Keys.action(e.keyCode);
    if (a === 'ok' && pointer.visible() === true) {
      e.preventDefault();
      clickExpected = true;
      return;
    }
    // A held Back repeats keydown. Without this it would keep leaving whatever appears next.
    if (a === 'back') {
      var b = Date.now();
      if (backHeldAt !== null && b - backHeldAt < 1500) { e.preventDefault(); return; }
      backHeldAt = b;
    }
    if (handlers.capture && handlers.capture(a, e) === true) {
      e.preventDefault();
      return;
    }
    if (!a) return;
    if (Keys.isDirection(a)) {
      e.preventDefault();
      if (keyThrottle.allow(a)) move(a);
      return;
    }
    if (a === 'ok') {
      e.preventDefault();
      // A held OK repeats keydown. Without this it would open whatever appears next.
      var t = Date.now();
      if (okHeldAt !== null && t - okHeldAt < 1500) return;
      okHeldAt = t;
      if (ensure()) activate(current, 'key');
      return;
    }
    if (a === 'back') {
      e.preventDefault();
      if (keyThrottle.allow('back') && handlers.back) handlers.back();
    }
  }

  function onKeyUp(e) {
    var a = Keys.action(e.keyCode);
    if (a) keyThrottle.release(a);
    if (a === 'ok') {
      okHeldAt = null;
      okGuard.release('key');
      if (clickExpected && clickTimer === null) clickTimer = root.setTimeout(function () { clickDidNotCome(e); }, CLICK_WAIT);
    }
    if (a === 'back') backHeldAt = null;
  }

  function focusableFrom(target) {
    for (var p = target; p && p !== doc; p = p.parentNode) {
      if (p.classList && p.classList.contains('fc')) return inScope(p) ? p : null;
    }
    return null;
  }

  // mousemove, not mouseover: a card that scrolls under a resting pointer must not take focus.
  function onMouseMove(e) {
    if (!pointer.moved(e)) return;
    var el = focusableFrom(e.target);
    if (el && el !== current) {
      moves++;
      focus(el, { pointer: true });
    }
  }

  function onClick(e) {
    clickExpected = false;
    if (clickTimer !== null) root.clearTimeout(clickTimer);
    clickTimer = null;
    var el = focusableFrom(e.target);
    if (!el) return;
    if (el !== current) focus(el, { pointer: true });
    activate(el, 'pointer');
  }

  function onWheel(e) {
    var dir = e.deltaY > 0 ? 'down' : 'up';
    if (!e.deltaY) return;
    e.preventDefault();
    if (wheelThrottle.allow(dir)) move(dir);
  }

  function onCursorState(e) {
    var visible = e && e.detail && e.detail.visibility;
    appRoot.classList.toggle('pointer-hidden', visible === false);
    if (typeof visible === 'boolean') pointer.setVisible(visible);
  }

  function init(el) {
    appRoot = el;
    doc.addEventListener('keydown', onKeyDown, true);
    doc.addEventListener('keyup', onKeyUp, true);
    doc.addEventListener('mousemove', onMouseMove, true);
    doc.addEventListener('click', onClick, true);
    doc.addEventListener('wheel', onWheel, false);
    doc.addEventListener('cursorStateChange', onCursorState, false);
    // A focused node removed by a late re-render must not leave the viewer with nothing lit up.
    root.setInterval(function () {
      if (!current || !inDom(current)) ensure();
    }, 500);
    // Stops the browser scrolling a clipped container, which would break the offset maths.
    doc.addEventListener('scroll', function (e) {
      var t = e.target;
      if (t && t !== doc && (t.scrollTop || t.scrollLeft)) { t.scrollTop = 0; t.scrollLeft = 0; }
    }, true);
  }

  function pushScope(el) {
    scopes.push(el);
    if (current) {
      el._prevFocus = current;
      current.classList.remove('is-focus');
      current = null;
    }
  }

  function popScope(el) {
    var i = scopes.indexOf(el);
    if (i >= 0) scopes.splice(i, 1);
    var prev = el._prevFocus;
    el._prevFocus = null;
    current = null;
    if (prev && inDom(prev)) focus(prev, { pointer: true });
    else ensure();
  }

  Sozo.Nav = {
    init: init,
    focus: focus,
    move: move,
    ensure: ensure,
    logicalRect: logicalRect,
    pushScope: pushScope,
    popScope: popScope,
    current: function () { return current; },
    // A dialog over a screen that is being replaced keeps its focus.
    clear: function () {
      if (scopes.length) return;
      if (current) current.classList.remove('is-focus');
      current = null;
    },
    pointerMoved: function (e) { return pointer.moved(e); },
    moves: function () { return moves; },
    onBack: function (fn) { handlers.back = fn; },
    // fn(action, event) returning true consumes the key. The player and search use this.
    capture: function (fn) { handlers.capture = fn; },
    groupOf: function (el) { return closestWith(el, '_group'); },
    closestWith: closestWith
  };
}(window));
