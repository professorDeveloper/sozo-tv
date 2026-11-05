(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});

  var MAX_DEPTH = 24;
  var KEEP_STATE = 8;

  // Screen stack. Home is always the root; a tab sits directly on Home.
  function create() {
    var stack = [];
    var tabState = {};

    function current() { return stack.length ? stack[stack.length - 1] : null; }

    function trim() {
      while (stack.length > MAX_DEPTH) stack.splice(1, 1);
      for (var i = 1; i < stack.length - KEEP_STATE; i++) stack[i].state = null;
    }

    return {
      current: current,
      depth: function () { return stack.length; },
      names: function () { return stack.map(function (e) { return e.name; }); },
      saveState: function (state) {
        var c = current();
        if (!c) return;
        c.state = state;
        if (c.tab) tabState[c.name] = state;
      },
      reset: function (name, params) {
        stack = [{ name: name, params: params || {}, state: null, tab: true }];
        tabState = {};
        return current();
      },
      push: function (name, params) {
        stack.push({ name: name, params: params || {}, state: null, tab: false });
        trim();
        return current();
      },
      tab: function (name, params) {
        var entry = { name: name, params: params || {}, state: tabState[name] || null, tab: true };
        if (stack.length && stack[0].name === name) {
          stack = [stack[0]];
        } else {
          stack = stack.slice(0, 1).concat(entry);
        }
        return current();
      },
      back: function () {
        if (stack.length <= 1) return null;
        stack.pop();
        return current();
      },
      isRoot: function () { return stack.length <= 1; }
    };
  }

  var api = { create: create, MAX_DEPTH: MAX_DEPTH, KEEP_STATE: KEEP_STATE };
  Sozo.Router = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
}(typeof window !== 'undefined' ? window : global));
