'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const vm = require('node:vm');
const { EventEmitter } = require('node:events');
const source = fs.readFileSync(path.join(__dirname, '../android/assets/core-update.cjs'), 'utf8');
let checks = 0;
const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'tcl-core-update-test-'));
function fixture(options = {}) {
    const dir = fs.mkdtempSync(path.join(temp, 'case-'));
    const map = p => path.join(dir, String(p).replace(/^\//, ''));
    for (const tree of ['mirakurun', 'mirakurun.update-new']) {
        const folder = map('/opt/' + tree + '/node_modules/mirakurun');
        fs.mkdirSync(path.join(folder, 'lib/Mirakurun'), { recursive: true });
        fs.writeFileSync(path.join(folder, 'package.json'), JSON.stringify({ version: tree === 'mirakurun' ? '4.1.5' : '4.2.1' }));
        for (const file of ['lib/server.js', 'lib/Mirakurun/Server.js', 'lib/Mirakurun/system.js', 'LICENSE']) fs.writeFileSync(path.join(folder, file), 'fixture');
    }
    fs.writeFileSync(map('/opt/mirakurun.update-new/tcl-update.json'), '{}');
    fs.mkdirSync(map('/run/tcl'), { recursive: true });
    const integrity = 'sha512-' + Buffer.alloc(64).toString('base64');
    fs.writeFileSync(map('/run/tcl/core-update-request.json'), JSON.stringify({ version: options.version || '4.2.1', integrity }));
    const pkg = { name: 'mirakurun', version: '4.2.1', license: 'Apache-2.0', engines: { node: '^22' }, dist: { integrity, tarball: 'https://registry.npmjs.org/mirakurun/-/mirakurun-4.2.1.tgz' }, ...options.pkg };
    let installs = 0;
    const fakeFs = new Proxy(fs, { get(target, key) {
        if (key === 'statfsSync') return () => ({ bavail: options.lowSpace ? 1 : 1024 ** 3, bsize: 1 });
        if (key === 'renameSync') return (a, b) => {
            if (options.renameFail && a === '/opt/mirakurun.update-new' && b === '/opt/mirakurun') throw new Error('simulated rename failure');
            return fs.renameSync(map(a), map(b));
        };
        if (['readFileSync', 'writeFileSync', 'existsSync', 'lstatSync', 'statSync', 'rmSync'].includes(key)) return (p, ...args) => fs[key](map(p), ...args);
        return target[key];
    } });
    const fakeHttps = { get(url, headers, callback) {
        assert.match(url, /^https:\/\/registry\.npmjs\.org\/mirakurun\//);
        const req = new EventEmitter(); req.setTimeout = () => {}; req.destroy = e => req.emit('error', e);
        queueMicrotask(() => {
            const res = new EventEmitter(); res.statusCode = 200; res.resume = () => {};
            callback(res); res.emit('data', JSON.stringify(pkg)); res.emit('end');
        });
        return req;
    } };
    const context = { module: { exports: {} }, process: { version: 'v22.23.4', argv: [] }, console, queueMicrotask, require(name) {
        if (name === 'node:fs') return fakeFs;
        if (name === 'node:https') return fakeHttps;
        if (name === 'node:child_process') return { spawnSync() { installs++; throw new Error('npm must not execute for rejected input'); } };
        if (name.endsWith('/node_modules/semver')) return { satisfies: () => !options.engineMismatch, gt: () => !options.oldVersion };
        return require(name);
    } };
    context.require.main = {};
    vm.runInNewContext(source, context, { filename: 'core-update.cjs' });
    return { api: context.module.exports, map, installs: () => installs };
}
(async () => {
    for (const options of [
        { version: '4.2.1;reboot' }, { version: '5.0.0' }, { pkg: { name: 'other' } },
        { pkg: { license: 'Other' } }, { pkg: { dist: { integrity: 'sha512-invalid', tarball: 'https://evil.invalid/a' } } },
        { pkg: { dist: { integrity: 'sha512-' + Buffer.alloc(64).toString('base64'), tarball: 'https://evil.invalid/a' } } },
        { engineMismatch: true }, { oldVersion: true }, { lowSpace: true }
    ]) {
        const f = fixture(options); await assert.rejects(f.api.prepare());
        assert.equal(f.installs(), 0); assert.equal(f.api.versionOf('/opt/mirakurun'), '4.1.5'); checks++;
    }
    {
        const f = fixture(); f.api.activate(); assert.equal(f.api.versionOf('/opt/mirakurun'), '4.2.1'); assert.equal(f.api.versionOf('/opt/mirakurun.update-previous'), '4.1.5'); checks++;
        f.api.revert(); assert.equal(f.api.versionOf('/opt/mirakurun'), '4.1.5'); assert.equal(f.api.versionOf('/opt/mirakurun.update-previous'), '4.2.1'); checks++;
    }
    {
        const f = fixture({ renameFail: true }); assert.throws(() => f.api.activate()); assert.equal(f.api.versionOf('/opt/mirakurun'), '4.1.5'); checks++;
    }
    {
        const f = fixture(); fs.rmSync(f.map('/opt/mirakurun.update-new/tcl-update.json')); assert.throws(() => f.api.activate()); assert.equal(f.api.versionOf('/opt/mirakurun'), '4.1.5'); checks++;
    }
    console.log('Core update guard/swap/rollback checks: ' + checks);
})().finally(() => fs.rmSync(temp, { recursive: true, force: true })).catch(error => { console.error(error); process.exitCode = 1; });
