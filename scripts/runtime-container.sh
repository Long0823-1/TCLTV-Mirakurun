#!/bin/sh
set -eu
apk add --no-cache nodejs npm python3
npm --prefix /opt/mirakurun install mirakurun@4.1.5 --omit=dev --no-audit --no-fund
cp /src/runtime/crc32-fallback.cjs /opt/mirakurun/node_modules/@node-rs/crc32/index.js
node - <<'JS'
const assert = require('node:assert/strict');
const crc = require('/opt/mirakurun/node_modules/@node-rs/crc32');
assert.equal(crc.crc32('123456789'), 0xcbf43926);
assert.equal(crc.crc32c('123456789'), 0xe3069283);
assert.equal(crc.crc32('56789', crc.crc32('1234')), 0xcbf43926);
require('/opt/mirakurun/node_modules/@chinachu/aribts');
JS
mkdir -p /src/android/assets
tar -czf /src/android/assets/runtime.tar.gz bin etc lib sbin usr opt
cd /src/android/assets
sha256sum runtime.tar.gz > runtime.sha256
