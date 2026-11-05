'use strict';
const path = require('path');
const fs = require('fs');

const ROOT = path.join(__dirname, '..');
const Filter = require('../js/lib/filter.js');
const Providers = require('../js/lib/providers.js');
const Platform = require('../js/lib/platform.js');
const Keys = require('../js/lib/keys.js');
const Spatial = require('../js/lib/spatial.js');
const Router = require('../js/lib/router.js');
const Keyboard = require('../js/lib/keyboard.js');
const Model = require('../js/lib/model.js');
const Store = require('../js/lib/store.js');
const Api = require('../js/lib/api.js');
const I18n = require('../js/lib/i18n.js');
const Playback = require('../js/lib/playback.js');
const Subtitles = require('../js/lib/subtitles.js');

function fixture(name) {
  return JSON.parse(fs.readFileSync(path.join(__dirname, 'fixtures', name + '.json'), 'utf8'));
}

// Lists shipped files by walking from index.html and appinfo.json, not by guessing directories.
function shippedFiles() {
  const html = fs.readFileSync(path.join(ROOT, 'index.html'), 'utf8');
  const out = new Set(['index.html', 'appinfo.json']);
  for (const m of html.matchAll(/(?:src|href)="([^"]+)"/g)) out.add(m[1]);
  const info = JSON.parse(fs.readFileSync(path.join(ROOT, 'appinfo.json'), 'utf8'));
  for (const k of ['icon', 'largeIcon', 'bgImage', 'splashBackground']) out.add(info[k]);
  // Vendored files load on demand, so index.html does not name them.
  for (const f of fs.readdirSync(path.join(ROOT, 'vendor', 'hls.js'))) out.add('vendor/hls.js/' + f);
  return [...out];
}

function shippedJs() {
  return shippedFiles().filter((f) => f.endsWith('.js'));
}

function ownJs() {
  return shippedJs().filter((f) => !f.startsWith('vendor/'));
}

module.exports = { ROOT, Filter, Providers, Platform, Keys, Spatial, Router, Keyboard, Model, Store, Api, I18n, Playback, Subtitles, fixture, shippedFiles, shippedJs, ownJs };
