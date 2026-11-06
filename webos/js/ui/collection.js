(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});
  var Spatial = Sozo.Spatial;
  var Dom = Sozo.Dom;

  // A horizontal row (cols = 0) or a grid of equal cells. Only a window around the focused index
  // is in the DOM, each cell absolutely placed, so long lists stay cheap.
  // options: key, items, cols, cellW, cellH, viewW, before, after, index, render(item, i),
  //          onIndex(i), onNearEnd(), nearEnd
  function Collection(o) {
    this.key = o.key || '';
    this.items = o.items || [];
    this.cols = o.cols || 0;
    this.cellW = o.cellW;
    this.cellH = o.cellH;
    this.viewW = o.viewW || 1760;
    this.before = o.before === undefined ? (this.cols ? 3 : 7) : o.before;
    this.after = o.after === undefined ? (this.cols ? 4 : 9) : o.after;
    this.render = o.render;
    this.onIndex = o.onIndex || null;
    this.onNearEnd = o.onNearEnd || null;
    this.nearEnd = o.nearEnd === undefined ? (this.cols ? this.cols * 2 : 6) : o.nearEnd;
    this.index = Math.max(0, Math.min(o.index || 0, Math.max(0, this.items.length - 1)));
    this.scroll = 0;
    this.active = true;
    this.nodes = {};
    this.track = Dom.h('div', 'col-track');
    this.root = Dom.h('div', 'col' + (this.cols ? ' col-grid' : ' col-row'), this.track);
    this.root._group = this;
    this._size();
    this._alignRow(this.cellW);
    this._window();
  }

  var P = Collection.prototype;

  P._size = function () {
    if (this.cols) {
      var rows = Math.ceil(this.items.length / this.cols);
      this.root.style.height = rows * this.cellH + 'px';
    } else {
      this.root.style.height = this.cellH + 'px';
    }
  };

  P._place = function (i, el) {
    el._idx = i;
    var col = this.cols ? i % this.cols : i;
    var row = this.cols ? Math.floor(i / this.cols) : 0;
    el.style.left = col * this.cellW + 'px';
    el.style.top = row * this.cellH + 'px';
  };

  P._window = function () {
    var range = this.active
      ? Spatial.windowRange(this.index, this.cols, this.items.length, this.before, this.after)
      : { start: 0, end: 0 };
    var key;
    for (key in this.nodes) {
      if (!Object.prototype.hasOwnProperty.call(this.nodes, key)) continue;
      var n = parseInt(key, 10);
      if (n < range.start || n >= range.end) {
        Dom.remove(this.nodes[key]);
        delete this.nodes[key];
      }
    }
    for (var i = range.start; i < range.end; i++) {
      if (this.nodes[i]) continue;
      var el = this.render(this.items[i], i);
      this._place(i, el);
      this.nodes[i] = el;
      this.track.appendChild(el);
    }
  };

  P._alignRow = function (pad) {
    if (this.cols) return;
    var content = this.items.length * this.cellW;
    var next = Spatial.minimalScroll(this.scroll, this.index * this.cellW, this.cellW, this.viewW, content, pad);
    this.scroll = next;
    Dom.setTransform(this.track, -next, 0);
  };

  P._nearEnd = function () {
    if (this.onNearEnd && this.index >= this.items.length - this.nearEnd) this.onNearEnd();
  };

  // Group interface for Nav.
  P.move = function (dir) {
    var next = Spatial.nextIndex(this.index, dir, this.cols, this.items.length);
    if (next < 0) return null;
    this.index = next;
    this._window();
    return this.nodes[next] || null;
  };

  P.restore = function () {
    if (!this.items.length) return null;
    if (!this.active) this.setActive(true);
    this._window();
    return this.nodes[this.index] || null;
  };

  P.onFocus = function (el, opts) {
    var i = el._idx;
    if (typeof i !== 'number') return;
    this.index = i;
    this._alignRow(opts && opts.pointer ? 0 : Math.round(this.cellW * 0.6));
    this._window();
    if (this.onIndex) this.onIndex(i);
    this._nearEnd();
  };

  P.setActive = function (active) {
    if (this.active === active) return;
    this.active = active;
    this._window();
  };

  P.addItems = function (items) {
    this.items = this.items.concat(items);
    this._size();
    this._window();
  };

  P.setItems = function (items, index) {
    var key;
    for (key in this.nodes) {
      if (Object.prototype.hasOwnProperty.call(this.nodes, key)) Dom.remove(this.nodes[key]);
    }
    this.nodes = {};
    this.items = items;
    this.index = Math.max(0, Math.min(index || 0, Math.max(0, items.length - 1)));
    this.scroll = 0;
    this._size();
    this._alignRow(this.cellW);
    this._window();
  };

  // Re-renders one cell in place, keeping focus if it was on that cell.
  P.update = function (i) {
    var old = this.nodes[i];
    if (!old) return;
    var wasFocus = Sozo.Nav.current() === old;
    var el = this.render(this.items[i], i);
    this._place(i, el);
    this.track.replaceChild(el, old);
    Dom.disposeTree(old);
    this.nodes[i] = el;
    if (wasFocus) Sozo.Nav.focus(el, { pointer: true });
  };

  Sozo.Collection = Collection;

  // Vertical scroller for a whole screen. Rows far from view drop their cards from the DOM.
  // options: viewH
  function Page(o) {
    this.viewH = o.viewH;
    this.scroll = 0;
    this.track = Dom.h('div', 'page-track');
    this.root = Dom.h('div', 'page', this.track);
    this.root.style.height = o.viewH + 'px';
    this.root._scroller = this;
    this.collections = [];
    this.lastKey = null;
    this.snapNode = null;
  }

  var G = Page.prototype;

  G.add = function (node, collection) {
    this.track.appendChild(node);
    if (collection) this.collections.push({ col: collection, node: node });
  };

  G.reveal = function (el, opts) {
    var unit = el;
    for (var p = el; p && p !== this.track; p = p.parentNode) {
      if (p.classList && p.classList.contains('reveal-unit')) { unit = p; break; }
    }
    var group = Sozo.Nav.groupOf(el);
    if (group && group.key) this.lastKey = group.key;
    var trackRect = Sozo.Nav.logicalRect(this.track);
    var rect = Sozo.Nav.logicalRect(unit);
    var start = rect.y - trackRect.y;
    var pad = opts && opts.pointer ? 0 : 56;
    var content = this.track.offsetHeight + 40;
    var next = Spatial.minimalScroll(this.scroll, start, rect.h, this.viewH, content, pad);
    // A unit half cut by the top bar looks broken, so it is either shown or scrolled away whole.
    if (this.snapNode && this.snapNode.contains(el)) {
      next = 0;
    } else if (this.snapNode && next > 0) {
      var snap = this.snapNode.offsetTop + this.snapNode.offsetHeight;
      if (next < snap) next = snap;
    }
    if (next !== this.scroll) {
      this.scroll = next;
      Dom.setTransform(this.track, 0, -next);
    }
    this.updateActive();
  };

  G.updateActive = function () {
    var margin = 620;
    for (var i = 0; i < this.collections.length; i++) {
      var entry = this.collections[i];
      if (entry.col.cols) continue;
      var top = entry.node.offsetTop;
      var bottom = top + entry.node.offsetHeight;
      entry.col.setActive(bottom > this.scroll - margin && top < this.scroll + this.viewH + margin);
    }
  };

  Sozo.Page = Page;
}(window));
