(function (root) {
  'use strict';
  var Sozo = root.Sozo || (root.Sozo = {});
  var doc = root.document;

  function add(el, kid) {
    if (kid === null || kid === undefined || kid === false) return;
    if (Array.isArray(kid)) {
      for (var i = 0; i < kid.length; i++) add(el, kid[i]);
    } else if (typeof kid === 'string' || typeof kid === 'number') {
      el.appendChild(doc.createTextNode(String(kid)));
    } else {
      el.appendChild(kid);
    }
  }

  // Text from the network only ever enters the page through createTextNode.
  function h(tag, cls, kids) {
    var el = doc.createElement(tag);
    if (cls) el.className = cls;
    add(el, kids);
    return el;
  }

  function clear(el) {
    while (el.firstChild) el.removeChild(el.firstChild);
  }

  function replace(el, kids) {
    clear(el);
    add(el, kids);
  }

  function disposeTree(el) {
    if (!el || !el.querySelectorAll) return;
    var imgs = el.querySelectorAll('img');
    for (var i = 0; i < imgs.length; i++) {
      if (imgs[i]._cancel) imgs[i]._cancel();
    }
  }

  function remove(el) {
    if (!el) return;
    disposeTree(el);
    if (el.parentNode) el.parentNode.removeChild(el);
  }

  // The src is set after a short delay so fast scrolling does not fetch what it passes by.
  // A placeholder sits under every image, so a failure just removes the image.
  function lazyImage(url, cls, delay) {
    if (!url) return null;
    var img = doc.createElement('img');
    img.className = (cls || '') + ' is-loading';
    img.alt = '';
    var timer = root.setTimeout(function () {
      timer = null;
      img.onload = function () { img.className = cls || ''; };
      img.onerror = function () { if (img._cancel) img._cancel(); if (img.parentNode) img.parentNode.removeChild(img); };
      img.src = url;
    }, delay === undefined ? 90 : delay);
    img._cancel = function () {
      if (timer !== null) root.clearTimeout(timer);
      timer = null;
      img.onload = null;
      img.onerror = null;
      img.removeAttribute('src');
      img._cancel = null;
    };
    return img;
  }

  var ICONS = {
    play: 'M8 5v14l11-7z',
    plus: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6z',
    check: 'M9 16.2 4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z',
    source: 'M4 6h16v2H4zm0 5h16v2H4zm0 5h16v2H4z',
    backspace: 'M22 4H8l-6 8 6 8h14V4zm-3.4 11.6L17.2 17 14 13.8 10.8 17l-1.4-1.4 3.2-3.2-3.2-3.2 1.4-1.4 3.2 3.2 3.2-3.2 1.4 1.4-3.2 3.2z',
    chevron: 'M9 6l1.4-1.4L17.8 12l-7.4 7.4L9 18l6-6z',
    pause: 'M6 19h4V5H6v14zm8-14v14h4V5h-4z',
    next: 'M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z',
    subtitles: 'M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zM4 12h4v2H4v-2zm10 6H4v-2h10v2zm6 0h-4v-2h4v2zm0-4H10v-2h10v2z',
    tune: 'M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z',
    audio: 'M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z'
  };

  function icon(name, cls) {
    var ns = 'http://www.w3.org/2000/svg';
    var svg = doc.createElementNS(ns, 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('class', 'icon' + (cls ? ' ' + cls : ''));
    var path = doc.createElementNS(ns, 'path');
    path.setAttribute('d', ICONS[name] || '');
    path.setAttribute('fill', 'currentColor');
    svg.appendChild(path);
    return svg;
  }

  function setTransform(el, x, y) {
    var v = 'translate3d(' + (x || 0) + 'px,' + (y || 0) + 'px,0)';
    el.style.webkitTransform = v;
    el.style.transform = v;
    el._tx = x || 0;
    el._ty = y || 0;
  }

  Sozo.Dom = { h: h, add: add, clear: clear, replace: replace, remove: remove, disposeTree: disposeTree, lazyImage: lazyImage, icon: icon, setTransform: setTransform };
}(window));
