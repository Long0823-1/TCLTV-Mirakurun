'use strict';
const http = require('node:http');
function get(endpoint) {
    return new Promise((resolve, reject) => {
        const req = http.get({ host: '127.0.0.1', port: 40772, path: endpoint, timeout: 5000 }, res => {
            if (res.statusCode !== 200) { res.resume(); reject(new Error('HTTP ' + res.statusCode)); return; }
            let text = '';
            res.on('data', chunk => { text += chunk; if (text.length > 1024 * 1024) req.destroy(new Error('API response too large')); });
            res.on('end', () => { try { resolve(JSON.parse(text)); } catch (e) { reject(e); } });
            res.on('error', reject);
        });
        req.on('timeout', () => req.destroy(new Error('API timeout')));
        req.on('error', reject);
    });
}
async function main() {
    const status = await get('/api/status');
    const tuners = await get('/api/tuners');
    if (!Array.isArray(tuners) || tuners.length !== 2 || tuners.some(t => !Array.isArray(t.types) || !t.types.includes('GR'))) throw new Error('Unexpected tuner configuration');
    if (process.argv[2] === 'idle') {
        if (tuners.some(t => !Array.isArray(t.users) || t.users.some(u => typeof u.priority !== 'number' || u.priority >= 0))) throw new Error('視聴・録画中です。終了後に更新してください');
        console.log('IDLE (background EPG is excluded)');
    } else {
        if (status.version !== process.argv[2]) throw new Error('Unexpected running Mirakurun version');
        console.log('HEALTHY ' + status.version);
    }
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
