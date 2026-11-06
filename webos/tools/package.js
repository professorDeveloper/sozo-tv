'use strict';
// Builds the ipk from a clean copy of the shipped files, then opens it and checks what is inside.
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const { spawnSync } = require('child_process');
const { ROOT } = require('../test/helper.js');
const { DIST: APP, SERVICE_DIST: SVC, stage, stageService } = require('./stage.js');

const OUT = path.join(ROOT, 'dist', 'ipk');
const ARES = path.join(ROOT, 'node_modules', '@webos-tools', 'cli', 'bin', 'ares-package.js');
const INSTALL_ROOT = 'usr/palm/applications/';
const SERVICE_ROOT = 'usr/palm/services/';
const FORBIDDEN = /(^|\/)(test|tests|tools|node_modules|dist|package\.json|package-lock\.json|\.[^/]+)(\/|$)/;

function ares(args) {
  const r = spawnSync(process.execPath, [ARES].concat(args), { cwd: ROOT, encoding: 'utf8' });
  process.stdout.write(r.stdout || '');
  process.stderr.write(r.stderr || '');
  if (r.status !== 0) throw new Error('ares-package ' + args.join(' ') + ' exited ' + r.status);
  return (r.stdout || '') + (r.stderr || '');
}

function readAr(buf) {
  if (buf.toString('latin1', 0, 8) !== '!<arch>\n') throw new Error('not an ar archive');
  const members = {};
  let at = 8;
  while (at + 60 <= buf.length) {
    const name = buf.toString('latin1', at, at + 16).trim().replace(/\/$/, '');
    const size = parseInt(buf.toString('latin1', at + 48, at + 58).trim(), 10);
    members[name] = buf.subarray(at + 60, at + 60 + size);
    at += 60 + size + (size % 2);
  }
  return members;
}

function tarString(buf, start, len) {
  const end = buf.indexOf(0, start);
  return buf.toString('utf8', start, end < 0 || end > start + len ? start + len : end);
}

function readTar(buf) {
  const files = {};
  let longName = null;
  let at = 0;
  while (at + 512 <= buf.length && buf[at] !== 0) {
    const header = buf.subarray(at, at + 512);
    const size = parseInt(tarString(header, 124, 12).trim() || '0', 8);
    const type = String.fromCharCode(header[156] || 48);
    const data = buf.subarray(at + 512, at + 512 + size);
    at += 512 + Math.ceil(size / 512) * 512;
    if (type === 'L') { longName = tarString(data, 0, size); continue; }
    if (type === 'x' || type === 'g') continue;
    const prefix = header.toString('latin1', 257, 262) === 'ustar' ? tarString(header, 345, 155) : '';
    const name = longName || ((prefix ? prefix + '/' : '') + tarString(header, 0, 100));
    longName = null;
    if (type === '0' || type === '7') files[name.replace(/^\.\//, '')] = data;
  }
  return files;
}

function dataFiles(ipkPath) {
  const ar = readAr(fs.readFileSync(ipkPath));
  const key = Object.keys(ar).find((k) => /^data\.tar\.gz$/.test(k));
  if (!key) throw new Error('no data.tar.gz in ' + ipkPath);
  return readTar(zlib.gunzipSync(ar[key]));
}

// Returns problems. A package may hold the shipped files, byte for byte, and packageinfo.json.
// services: {serviceId: {file: sha256}} for services packaged next to the app.
function verify(files, appId, expected, services) {
  const prefix = INSTALL_ROOT + appId + '/';
  const svc = services || {};
  const svcSeen = {};
  const seen = new Set();
  const problems = [];
  for (const name of Object.keys(files)) {
    if (name === 'usr/palm/packages/' + appId + '/packageinfo.json') continue;
    const sid = Object.keys(svc).find((id) => name.startsWith(SERVICE_ROOT + id + '/'));
    if (sid) {
      const srel = name.slice((SERVICE_ROOT + sid + '/').length);
      (svcSeen[sid] = svcSeen[sid] || new Set()).add(srel);
      if (!Object.prototype.hasOwnProperty.call(svc[sid], srel)) problems.push('not a shipped service file: ' + sid + '/' + srel);
      else if (svc[sid][srel] !== crypto.createHash('sha256').update(files[name]).digest('hex')) problems.push('content changed: ' + sid + '/' + srel);
      continue;
    }
    if (!name.startsWith(prefix)) { problems.push('unexpected path: ' + name); continue; }
    const rel = name.slice(prefix.length);
    seen.add(rel);
    if (FORBIDDEN.test(rel)) problems.push('forbidden file: ' + rel);
    if (!Object.prototype.hasOwnProperty.call(expected, rel)) problems.push('not a shipped file: ' + rel);
    else if (expected[rel] !== crypto.createHash('sha256').update(files[name]).digest('hex')) problems.push('content changed: ' + rel);
  }
  for (const rel of Object.keys(expected)) if (!seen.has(rel)) problems.push('missing: ' + rel);
  for (const sid of Object.keys(svc)) {
    for (const rel of Object.keys(svc[sid])) if (!(svcSeen[sid] && svcSeen[sid].has(rel))) problems.push('missing: ' + sid + '/' + rel);
  }
  return problems;
}

function main() {
  fs.rmSync(path.join(ROOT, 'dist'), { recursive: true, force: true });
  const expected = {};
  for (const f of stage()) expected[f] = crypto.createHash('sha256').update(fs.readFileSync(path.join(APP, f))).digest('hex');
  const svcFiles = {};
  for (const f of stageService()) svcFiles[f] = crypto.createHash('sha256').update(fs.readFileSync(path.join(SVC, f))).digest('hex');
  const svcId = JSON.parse(fs.readFileSync(path.join(SVC, 'services.json'), 'utf8')).id;
  fs.mkdirSync(OUT, { recursive: true });

  ares(['--check', APP]);
  // Without --no-minify, terser rewrites every .js, the vendored hls.js included, and the
  // ES5 checks would no longer describe what ships.
  const log = ares(['--no-minify', '-o', OUT, APP, SVC]);
  // Any service triggers ares-package's generic Node.js version notice; service/ is held to ES5 instead.
  const warnings = log.split('\n').filter((l) => /\[Warning\]/.test(l) && !/Node\.js version available on the target device/.test(l));
  if (warnings.length) throw new Error('ares-package warned, see above');

  const ipks = fs.readdirSync(OUT).filter((f) => f.endsWith('.ipk'));
  if (ipks.length !== 1) throw new Error('expected one ipk, found ' + ipks.length);
  const ipk = path.join(OUT, ipks[0]);
  const appId = JSON.parse(fs.readFileSync(path.join(APP, 'appinfo.json'), 'utf8')).id;
  const files = dataFiles(ipk);
  const problems = verify(files, appId, expected, { [svcId]: svcFiles });
  if (problems.length) throw new Error('bad package:\n  ' + problems.join('\n  '));

  console.log(Object.keys(files).sort().join('\n'));
  console.log(ipk);
  console.log(Object.keys(files).length + ' files, ' + fs.statSync(ipk).size + ' bytes');
}

if (require.main === module) {
  try {
    main();
  } catch (e) {
    console.error(e.message);
    process.exit(1);
  }
}

module.exports = { readAr, readTar, dataFiles, verify };
