(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});
  var h = Sozo.Dom.h;
  var Nav = Sozo.Nav;
  var View = Sozo.View;

  var stack = [];

  function close(entry) {
    var i = stack.indexOf(entry);
    if (i < 0) return;
    stack.splice(i, 1);
    if (entry.el.parentNode) entry.el.parentNode.removeChild(entry.el);
    Nav.popScope(entry.el);
  }

  // options: title, message, actions: [{label, primary, focus, onOk}], onDismiss
  function show(o) {
    var entry = { el: null, onDismiss: o.onDismiss || null };
    var first = null;
    var buttons = (o.actions || []).map(function (a) {
      var b = View.button(a.label, function () {
        close(entry);
        if (a.onOk) a.onOk();
      }, { primary: a.primary });
      if (a.focus || !first) first = a.focus ? b : first || b;
      return b;
    });
    entry.el = h('div', 'dialog-scrim', h('div', 'dialog', [
      h('div', 'dialog-title', o.title),
      o.message ? h('div', 'dialog-message', o.message) : null,
      h('div', 'dialog-actions', buttons)
    ]));
    var appRoot = root.document.getElementById('app');
    appRoot.appendChild(entry.el);
    stack.push(entry);
    Nav.pushScope(entry.el);
    if (first) Nav.focus(first, { pointer: true });
    return entry;
  }

  Sozo.Dialog = {
    show: show,
    close: close,
    isOpen: function () { return stack.length > 0; },
    // Back inside a dialog. Returns true when it was consumed.
    dismissTop: function () {
      if (!stack.length) return false;
      var entry = stack[stack.length - 1];
      close(entry);
      if (entry.onDismiss) entry.onDismiss();
      return true;
    }
  };
}(window));
