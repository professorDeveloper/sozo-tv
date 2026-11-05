'use strict';
// Copies exactly what ships into dist/app, for ares-package. Nothing from test/ or node_modules/.
const fs = require('fs');
const path = require('path');
const { ROOT, shippedFiles } = require('../test/helper.js');

const DIST = path.join(ROOT, 'dist', 'app');

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

if (require.main === module) {
  stage();
  console.log(DIST);
}

module.exports = { DIST, stage };
