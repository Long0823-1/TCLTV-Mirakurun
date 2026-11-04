'use strict';
// Run inside the existing rootfs. Package installation is staged separately
// from the running server; never evaluate user-supplied commands or URLs.
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const https = require('node:https');
const assert = require('node:assert/strict');
const candidate = '/opt/mirakurun.update-new';
const current = '/opt/mirakurun';
const rollback = '/opt/mirakurun.update-previous';
const requestFile = '/run/tcl/core-update-request.json';
const action = process.argv[2];
function metadata(version) {
    return new Promise((resolve, reject) => {
        const req = https.get('https://registry.npmjs.org/mirakurun/' + encodeURIComponent(version), { headers: { Accept: 'application/json' } }, res => {
            if (res.statusCode !== 200) { res.resume(); reject(new Error('npm metadata HTTP ' + res.statusCode)); return; }
            let text = '';
            res.on('data', chunk => { text += chunk; if (text.length > 1024 * 1024) { req.destroy(new Error('Metadata too large')); } });
            res.on('end', () => { try { resolve(JSON.parse(text)); } catch (e) { reject(e); } });
            res.on('error', reject);
        });
        req.setTimeout(30000, () => req.destroy(new Error('npm metadata timeout')));
        req.on('error', reject);
    });
}
function safeDirectory(dir) {
    if (![candidate, current, rollback].includes(dir)) throw new Error('Invalid update directory');
    if (fs.existsSync(dir) && (!fs.lstatSync(dir).isDirectory() || fs.lstatSync(dir).isSymbolicLink())) throw new Error('Unsafe update directory');
}
function versionOf(dir) { return JSON.parse(fs.readFileSync(path.join(dir, 'node_modules/mirakurun/package.json'), 'utf8')).version; }
function clearCandidate() { safeDirectory(candidate); fs.rmSync(candidate, { recursive: true, force: true }); }
function verifyFiles(dir) {
    for (const file of ['lib/server.js', 'lib/Mirakurun/Server.js', 'lib/Mirakurun/system.js', 'LICENSE']) {
        if (!fs.statSync(path.join(dir, 'node_modules/mirakurun', file)).isFile()) throw new Error('Missing Mirakurun file: ' + file);
    }
}
async function prepare() {
    const request = JSON.parse(fs.readFileSync(requestFile, 'utf8'));
    if (!/^4\.\d+\.\d+$/.test(request.version)) throw new Error('Only stable Mirakurun 4.x is supported by this updater');
    const pkg = await metadata(request.version);
    if (pkg.name !== 'mirakurun' || pkg.version !== request.version || pkg.license !== 'Apache-2.0') throw new Error('Unexpected npm package identity/license');
    if (pkg.dist.integrity !== request.integrity || !/^sha512-[A-Za-z0-9+/]+=*$/.test(request.integrity)) throw new Error('Package integrity metadata changed');
    if (pkg.dist.tarball !== 'https://registry.npmjs.org/mirakurun/-/mirakurun-' + pkg.version + '.tgz') throw new Error('Unexpected tarball source');
    // Use the current runtime's semver implementation to enforce Node engines.
    const semver = require(current + '/node_modules/semver');
    if (!pkg.engines || !semver.satisfies(process.version, pkg.engines.node)) throw new Error('This update needs a different Node.js version');
    if (!semver.gt(pkg.version, versionOf(current))) throw new Error('The selected version is not newer than the installed version');
    const space = fs.statfsSync('/opt');
    if (space.bavail * space.bsize < 512 * 1024 * 1024) throw new Error('更新には512MB以上の空き容量が必要です');
    safeDirectory(current); clearCandidate();
    console.log('Downloading Mirakurun ' + pkg.version + ' and dependencies from npm...');
    const installed = spawnSync(process.execPath, ['/usr/bin/npm', '--prefix', candidate, 'install', 'mirakurun@' + pkg.version, '--omit=dev', '--ignore-scripts', '--no-audit', '--no-fund', '--engine-strict', '--registry=https://registry.npmjs.org', '--cache=' + candidate + '/npm-cache'], {
        stdio: 'inherit', timeout: 7 * 60 * 1000, env: { ...process.env, PATH: '/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin', HOME: '/root', TMPDIR: '/tmp', npm_config_update_notifier: 'false', NODE_OPTIONS: '--max-old-space-size=128' }
    });
    if (installed.error) throw installed.error;
    if (installed.status !== 0) throw new Error('npm install failed: ' + installed.status);
    fs.rmSync(candidate + '/npm-cache', { recursive: true, force: true });
    const lock = JSON.parse(fs.readFileSync(candidate + '/package-lock.json', 'utf8'));
    if (lock.packages['node_modules/mirakurun'].integrity !== request.integrity || versionOf(candidate) !== pkg.version) throw new Error('Installed package integrity/version mismatch');
    verifyFiles(candidate);
    // ARMv7/musl has no matching crc32 binary; retain our tested JS fallback.
    const crcPath = candidate + '/node_modules/@node-rs/crc32/index.js';
    if (!fs.existsSync(crcPath)) throw new Error('Unsupported crc32 dependency layout');
    fs.copyFileSync('/run/tcl/crc32-fallback.cjs', crcPath);
    const crc = require(crcPath);
    assert.equal(crc.crc32('123456789'), 0xcbf43926);
    assert.equal(crc.crc32c('123456789'), 0xe3069283);
    require(candidate + '/node_modules/@chinachu/aribts');
    const patch = spawnSync(process.execPath, ['/run/tcl/patch-runtime.cjs'], { stdio: 'inherit', env: { ...process.env, TCL_RUNTIME_PREFIX: candidate } });
    if (patch.status !== 0) throw new Error('The new version is incompatible with required TV network patches');
    fs.writeFileSync(candidate + '/tcl-update.json', JSON.stringify({ version: pkg.version, integrity: request.integrity, preparedAt: new Date().toISOString() }));
    const licenses = [];
    for (const [name, entry] of Object.entries(lock.packages)) {
        if (!name) continue;
        licenses.push({ package: name.replace(/^node_modules\//, ''), version: entry.version, license: entry.license || 'See package license files' });
    }
    fs.writeFileSync(candidate + '/tcl-dependency-licenses.json', JSON.stringify(licenses, null, 2));
    console.log('PREPARED ' + pkg.version + ' / ' + licenses.length + ' dependency license entries');
}
function activate() {
    safeDirectory(current); safeDirectory(candidate); safeDirectory(rollback); verifyFiles(candidate);
    if (!fs.existsSync(candidate + '/tcl-update.json')) throw new Error('Candidate was not validated');
    fs.rmSync(rollback, { recursive: true, force: true });
    fs.renameSync(current, rollback);
    try { fs.renameSync(candidate, current); } catch (e) { fs.renameSync(rollback, current); throw e; }
    console.log('ACTIVATED ' + versionOf(current));
}
function revert() {
    safeDirectory(current); safeDirectory(candidate); safeDirectory(rollback); verifyFiles(rollback);
    clearCandidate(); fs.renameSync(current, candidate);
    try { fs.renameSync(rollback, current); } catch (e) { fs.renameSync(candidate, current); throw e; }
    fs.renameSync(candidate, rollback);
    console.log('RESTORED ' + versionOf(current));
}
async function main() {
    if (action === 'prepare') await prepare();
    else if (action === 'activate') activate();
    else if (action === 'revert') revert();
    else if (action === 'clean-candidate') clearCandidate();
    else if (action === 'discard-previous') { safeDirectory(rollback); fs.rmSync(rollback, { recursive: true, force: true }); }
    else if (action === 'versions') console.log(JSON.stringify({ current: versionOf(current), previous: fs.existsSync(rollback) ? versionOf(rollback) : null, node: process.version }));
    else throw new Error('Invalid action');
}
module.exports = { prepare, activate, revert, versionOf };
if (require.main === module) main().catch(error => { console.error(error.stack || error); process.exitCode = 1; });
