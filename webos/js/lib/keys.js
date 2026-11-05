(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});

  var ACTIONS = {
    37: 'left', 38: 'up', 39: 'right', 40: 'down', 13: 'ok',
    461: 'back', 27: 'back',
    415: 'play', 19: 'pause', 413: 'stop', 412: 'rewind', 417: 'forward',
    33: 'channelUp', 34: 'channelDown',
    403: 'red', 404: 'green', 405: 'yellow', 406: 'blue'
  };

  function action(keyCode) {
    return ACTIONS[keyCode] || null;
  }

  function isDirection(a) {
    return a === 'left' || a === 'right' || a === 'up' || a === 'down';
  }

  // Printable character from a hardware keyboard, or '' for anything else.
  function printable(e) {
    if (!e || e.ctrlKey || e.altKey || e.metaKey) return '';
    var k = e.key;
    return typeof k === 'string' && k.length === 1 ? k : '';
  }

  // Held keys repeat faster than an old TV can lay out. First press always passes.
  function throttle(minMs, now) {
    var clock = now || Date.now;
    var last = {};
    return {
      allow: function (a) {
        var t = clock();
        if (last[a] !== undefined && t - last[a] < minMs) return false;
        last[a] = t;
        return true;
      },
      release: function (a) { delete last[a]; }
    };
  }

  // The Magic Remote can deliver key 13 and a click for one press, so a second activation from the
  // other input is dropped. Repeated presses on one input are the viewer typing and pass.
  function activationGuard(minMs, now) {
    var clock = now || Date.now;
    var lastAt = -Infinity;
    var lastSource = null;
    return {
      allow: function (source) {
        var t = clock();
        if (lastSource !== null && lastSource !== source && t - lastAt < minMs) return false;
        lastAt = t;
        lastSource = source;
        return true;
      },
      // A click comes after release, so a long press must count from there.
      release: function (source) {
        if (lastSource === source) lastAt = clock();
      }
    };
  }

  // Old Blink fires mousemove at the last position after layout or scroll, and webOS keeps the
  // pointer's place while it is hidden. Neither is the viewer moving. From memory, not seen on a set.
  // One event may be asked about by several listeners, and gets one answer.
  function pointerTracker() {
    var x = null;
    var y = null;
    var visible = null;
    var lastEvent = null;
    var lastAnswer = false;
    return {
      moved: function (e) {
        if (e === lastEvent) return lastAnswer;
        var same = e.clientX === x && e.clientY === y;
        x = e.clientX;
        y = e.clientY;
        lastEvent = e;
        lastAnswer = !same && visible !== false;
        return lastAnswer;
      },
      // null until the platform first reports it, which it may never do.
      visible: function () { return visible; },
      setVisible: function (v) { visible = v; }
    };
  }

  var api = { action: action, isDirection: isDirection, printable: printable, throttle: throttle, activationGuard: activationGuard, pointerTracker: pointerTracker };
  Sozo.Keys = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
}(typeof window !== 'undefined' ? window : global));
