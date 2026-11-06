'use strict';
// Copies exactly what ships into dist/app, for ares-package. Nothing from test/ or node_modules/.
const fs = require('fs');
const path = require('path');
const { ROOT, shippedFiles } = require('../test/helper.js');

const DIST = path.join(ROOT, 'dist', 'app');
const SERVICE_SRC = path.join(ROOT, 'service');
const SERVICE_DIST = path.join(ROOT, 'dist', 'service');

function stage() {
  fs.rmSync(DIST, { recursive: true, force: true });
  const files = [...new Set(shippedFiles())];
  for (const f of files) {
    const to = path.join(DIST, f);
    fs.mkdirSync(path.dirname(to), { recursive: true });
    fs.copyFileSync(path.join(ROOT, f), to);
  }
  return files;
}

// The loopback relay service ships next to the app; every file in service/ goes in.
function stageService() {
  fs.rmSync(SERVICE_DIST, { recursive: true, force: true });
  const files = fs.readdirSync(SERVICE_SRC).filter((f) => fs.statSync(path.join(SERVICE_SRC, f)).isFile()).sort();
  fs.mkdirSync(SERVICE_DIST, { recursive: true });
  for (const f of files) fs.copyFileSync(path.join(SERVICE_SRC, f), path.join(SERVICE_DIST, f));
  return files;
}

if (require.main === module) {
  stage();
  stageService();
  console.log(DIST);
  console.log(SERVICE_DIST);
}

module.exports = { DIST, SERVICE_DIST, stage, stageService };
