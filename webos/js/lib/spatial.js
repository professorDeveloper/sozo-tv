(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});

  var TOLERANCE = 4;

  function primaryGap(from, c, dir) {
    if (dir === 'down') return c.y - (from.y + from.h);
    if (dir === 'up') return from.y - (c.y + c.h);
    if (dir === 'right') return c.x - (from.x + from.w);
    return from.x - (c.x + c.w);
  }

  function crossGap(from, c, dir) {
    if (dir === 'up' || dir === 'down') {
      if (c.x + c.w <= from.x) return from.x - (c.x + c.w);
      if (c.x >= from.x + from.w) return c.x - (from.x + from.w);
      return 0;
    }
    if (c.y + c.h <= from.y) return from.y - (c.y + c.h);
    if (c.y >= from.y + from.h) return c.y - (from.y + from.h);
    return 0;
  }

  function outsideCross(c, dir, bounds) {
    if (!bounds) return false;
    if (dir === 'up' || dir === 'down') return c.x + c.w <= bounds.x || c.x >= bounds.x + bounds.w;
    return c.y + c.h <= bounds.y || c.y >= bounds.y + bounds.h;
  }

  // Candidates carry {x, y, w, h}. Returns the best one, or null when nothing lies that way.
  function findNext(from, dir, candidates, bounds) {
    var horizontal = dir === 'left' || dir === 'right';
    var best = null;
    var bestScore = Infinity;
    for (var i = 0; i < candidates.length; i++) {
      var c = candidates[i];
      var primary = primaryGap(from, c, dir);
      if (primary < -TOLERANCE) continue;
      if (outsideCross(c, dir, bounds)) continue;
      var cross = crossGap(from, c, dir);
      // Moving sideways must not jump to a neighbouring row.
      if (horizontal && cross > 0) continue;
      var centre = horizontal
        ? Math.abs((c.y + c.h / 2) - (from.y + from.h / 2))
        : Math.abs((c.x + c.w / 2) - (from.x + from.w / 2));
      var score = Math.max(primary, 0) * 2 + cross * 3 + centre * 0.25;
      if (score < bestScore) { bestScore = score; best = c; }
    }
    return best;
  }

  // cols = 0 means a single horizontal row. Returns -1 at an edge.
  function nextIndex(index, dir, cols, count) {
    if (count <= 0) return -1;
    if (!cols) {
      if (dir === 'left') return index > 0 ? index - 1 : -1;
      if (dir === 'right') return index < count - 1 ? index + 1 : -1;
      return -1;
    }
    var row = Math.floor(index / cols);
    var lastRow = Math.floor((count - 1) / cols);
    if (dir === 'left') return index % cols > 0 ? index - 1 : -1;
    if (dir === 'right') return index % cols < cols - 1 && index < count - 1 ? index + 1 : -1;
    if (dir === 'up') return row > 0 ? index - cols : -1;
    if (row >= lastRow) return -1;
    return Math.min(index + cols, count - 1);
  }

  // Half-open range of items to keep in the DOM around the focused index.
  function windowRange(index, cols, count, before, after) {
    if (count <= 0) return { start: 0, end: 0 };
    var i = Math.max(0, Math.min(index, count - 1));
    if (!cols) {
      return { start: Math.max(0, i - before), end: Math.min(count, i + after + 1) };
    }
    var row = Math.floor(i / cols);
    return {
      start: Math.max(0, row - before) * cols,
      end: Math.min(count, (row + after + 1) * cols)
    };
  }

  // Smallest scroll change that shows [start, start + size) with pad around it.
  function minimalScroll(current, start, size, view, content, pad) {
    var max = Math.max(0, content - view);
    if (size >= view) return Math.max(0, Math.min(max, start));
    pad = Math.min(pad, (view - size) / 2);
    var next = current;
    if (start - pad < current) next = start - pad;
    else if (start + size + pad > current + view) next = start + size + pad - view;
    return Math.max(0, Math.min(max, next));
  }

  var api = { findNext: findNext, nextIndex: nextIndex, windowRange: windowRange, minimalScroll: minimalScroll };
  Sozo.Spatial = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
}(typeof window !== 'undefined' ? window : global));
