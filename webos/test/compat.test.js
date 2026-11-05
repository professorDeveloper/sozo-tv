'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const acorn = require('acorn');
const crypto = require('crypto');
const { ROOT, shippedFiles, shippedJs, ownJs } = require('./helper.js');

const read = (f) => fs.readFileSync(path.join(ROOT, f), 'utf8');

function walkJs(dir) {
  const out = [];
  for (const e of fs.readdirSync(path.join(ROOT, dir), { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) out.push(...walkJs(p));
    else if (e.name.endsWith('.js')) out.push(p);
  }
  return out;
}

test('every shipped js parses as ES5', () => {
  const files = shippedJs();
  assert.ok(files.length > 20);
  for (const f of files) {
    assert.doesNotThrow(() => acorn.parse(read(f), { ecmaVersion: 5, sourceType: 'script' }), f);
  }
});

test('hls.js is the exact pinned 1.5.20 build, with its licence, and loads only on demand', () => {
  const digest = crypto.createHash('sha256').update(fs.readFileSync(path.join(ROOT, 'vendor/hls.js/hls.min.js'))).digest('hex');
  assert.equal(digest, 'd016c1230496ee59f3f5b01c16cce4cc01b5a1d3d357adec200c908b131ebe49');
  assert.match(read('vendor/hls.js/LICENSE'), /Apache License/);
  assert.equal(read('index.html').includes('hls'), false);
  assert.ok(shippedFiles().includes('vendor/hls.js/hls.min.js'));
  assert.ok(shippedFiles().includes('vendor/hls.js/LICENSE'));
});

test('the player never sets crossorigin or a referrer policy, which would break native sources', () => {
  assert.equal(/crossorigin|referrerPolicy/i.test(read('js/player.js').replace(/^\s*\/\/.*$/gm, '')), false);
});

test('every js file under js/ is loaded by index.html', () => {
  const shipped = new Set(shippedJs());
  for (const f of walkJs('js')) assert.ok(shipped.has(f), f + ' is not in index.html');
});

test('every shipped file exists, and nothing is loaded from the network', () => {
  for (const f of shippedFiles()) {
    assert.equal(/^https?:|^\/\//.test(f), false, f);
    assert.ok(fs.existsSync(path.join(ROOT, f)), f);
  }
  assert.equal(/url\(\s*['"]?(https?:|\/\/)/.test(read('css/app.css')), false);
  assert.equal(/@import/.test(read('css/app.css')), false);
});

const NEWER_THAN_53 = [
  [/\bObject\.(entries|values|fromEntries)\b/, 'Object.entries/values, Chromium 54+'],
  [/\.(padStart|padEnd|trimEnd|trimStart|replaceAll|flatMap|flat|matchAll)\(/, 'string or array method newer than 53'],
  [/\.finally\(/, 'Promise.finally, Chromium 63'],
  [/\b(AbortController|globalThis|IntersectionObserver|ResizeObserver)\b/, 'API newer than 53'],
  [/\.(append|prepend|before|after|replaceWith|replaceChildren|toggleAttribute)\(/, 'ChildNode method, Chromium 54+'],
  [/\bfetch\(/, 'fetch without a check'],
  [/preventScroll|scrollIntoView\(\{/, 'option Chromium 53 ignores'],
  [/\.(innerHTML|outerHTML)\b|insertAdjacentHTML|document\.write|\beval\(|new Function\(/, 'text must never become markup']
];

test('shipped js avoids APIs newer than Chromium 53 and any way to inject markup', () => {
  for (const f of ownJs()) {
    const src = read(f);
    for (const [re, why] of NEWER_THAN_53) {
      assert.equal(re.test(src), false, f + ': ' + why);
    }
  }
});

test('no real input element can open the TV keyboard', () => {
  for (const f of ownJs().concat('index.html')) {
    const src = read(f);
    assert.equal(/createElement\(['"](input|textarea|select)['"]\)|h\(['"](input|textarea|select)['"]|<(input|textarea|select)\b|contenteditable/i.test(src), false, f);
  }
});

test('css stays inside the Chromium 53 baseline', () => {
  const css = read('css/app.css').replace(/\/\*[\s\S]*?\*\//g, '');
  const banned = [
    [/(^|[\s;{])gap\s*:/, 'gap'], [/[^-]row-gap|column-gap/, 'gap'], [/aspect-ratio/, 'aspect-ratio'], [/backdrop-filter/, 'backdrop-filter'],
    [/display\s*:\s*(inline-)?grid/, 'grid, Chromium 57'], [/position\s*:\s*sticky/, 'sticky, Chromium 56'],
    [/\b(clamp|min|max)\(/, 'math functions'], [/#[0-9a-f]{8}\b|#[0-9a-f]{4}\b/i, 'hex alpha, Chromium 62'],
    [/:(is|where|has|focus-visible|focus-within)\b/, 'selector newer than 53'], [/\binset\s*:/, 'inset shorthand'],
    [/\bvar\(--|--[a-z-]+\s*:/, 'custom properties kept out for simplicity'], [/@container|@layer/, 'newer at-rule']
  ];
  for (const [re, why] of banned) assert.equal(re.test(css), false, why);
});

test('appinfo.json holds the agreed manifest', () => {
  const a = JSON.parse(read('appinfo.json'));
  assert.equal(a.id, 'com.soplay.sozo.webos');
  assert.equal(a.version, '1.0.0');
  assert.equal(a.type, 'web');
  assert.equal(a.main, 'index.html');
  assert.equal(a.resolution, '1920x1080');
  assert.equal(a.disableBackHistoryAPI, true);
});

function pngSize(f) {
  const b = fs.readFileSync(path.join(ROOT, f));
  assert.equal(b.toString('latin1', 1, 4), 'PNG', f);
  return [b.readUInt32BE(16), b.readUInt32BE(20)];
}

test('icons and splash have the exact webOS sizes', () => {
  assert.deepEqual(pngSize('icon.png'), [80, 80]);
  assert.deepEqual(pngSize('largeIcon.png'), [130, 130]);
  assert.deepEqual(pngSize('assets/splash.png'), [1920, 1080]);
});

test('dev files are not reachable from the shipped set', () => {
  for (const f of shippedFiles()) assert.equal(/^(test|tools|node_modules)\/|package(-lock)?\.json$/.test(f), false, f);
});
