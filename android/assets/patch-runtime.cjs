'use strict';
// Mirakurun 4.1.5 snapshots interface addresses at startup. Android can acquire
// Wi-Fi after Magisk starts the server. Bind IPv4 once and keep upstream ACLs.
const fs = require('node:fs');
const path = '/opt/mirakurun/node_modules/mirakurun/lib/Mirakurun/Server.js';
const marker = '// TCLTV-Mirakurun: IPv4 wildcard binding';
let source = fs.readFileSync(path, 'utf8');
if (source.includes(marker)) process.exit(0);
const binding = 'addresses.push(...systemIPv4s);';
const loopback = 'addresses.push("127.0.0.1");';
if (!source.includes(binding) || !source.includes(loopback)) {
  throw new Error('Unsupported Mirakurun listener implementation');
}
if (!fs.existsSync(path + '.tcl-original')) fs.copyFileSync(path, path + '.tcl-original');
source = source.replace(binding, marker + '\n                            addresses.push("0.0.0.0");')
               .replace(loopback, '// IPv4 loopback is included in 0.0.0.0.');
fs.writeFileSync(path, source);
console.log('Applied IPv4 listener compatibility patch; upstream IP/origin ACLs retained');
