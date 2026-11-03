'use strict';
// Mirakurun 4.1.5 snapshots interface addresses at startup. Android can acquire
// Wi-Fi after Magisk starts the server. Bind IPv4 once and keep upstream ACLs.
const fs = require('node:fs');
const path = '/opt/mirakurun/node_modules/mirakurun/lib/Mirakurun/Server.js';
const marker = '// TCLTV-Mirakurun: IPv4 wildcard binding';
let source = fs.readFileSync(path, 'utf8');
const binding = 'addresses.push(...systemIPv4s);';
const loopback = 'addresses.push("127.0.0.1");';
if (!source.includes(marker) && (!source.includes(binding) || !source.includes(loopback))) {
  throw new Error('Unsupported Mirakurun listener implementation');
}
if (!fs.existsSync(path + '.tcl-original')) fs.copyFileSync(path, path + '.tcl-original');
if (!source.includes(marker)) source = source.replace(binding, marker + '\n                            addresses.push("0.0.0.0");')
               .replace(loopback, '// IPv4 loopback is included in 0.0.0.0.');
fs.writeFileSync(path, source);
console.log('Applied IPv4 listener compatibility patch; upstream IP/origin ACLs retained');

// Permit only the exact mDNS names published by this application. Never trust
// an arbitrary .local Origin, a remote IP, or request-controlled file paths.
const systemPath = '/opt/mirakurun/node_modules/mirakurun/lib/Mirakurun/system.js';
let system = fs.readFileSync(systemPath, 'utf8');
const hostMarker = '// TCLTV-Mirakurun: managed mDNS origins';
if (!system.includes(hostMarker)) {
    const hostCheck = 'if (u.hostname === "localhost" ||';
    if (!system.includes(hostCheck)) throw new Error('Unsupported Mirakurun host validation implementation');
    const helper = `${hostMarker}
function isManagedMdnsOrigin(u) {
    if (u.protocol !== "http:" || u.port !== "40772") return false;
    try {
        const hosts = JSON.parse(require("node:fs").readFileSync("/run/tcl/mdns-hosts.json", "utf8"));
        return Array.isArray(hosts) && hosts.length <= 16 && hosts.some(h =>
            typeof h === "string" && /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.local$/.test(h) && u.hostname === h);
    } catch (_) { return false; }
}
`;
    fs.copyFileSync(systemPath, systemPath + '.tcl-mdns-original');
    system = '"use strict";\n' + helper + system.replace(hostCheck, 'if (isManagedMdnsOrigin(u) || u.hostname === "localhost" ||');
    fs.writeFileSync(systemPath, system);
}
console.log('Applied exact managed mDNS origin allowance; upstream client IP ACL retained');

// A dedicated Windows DHCP link uses a private /30 with exactly two host IPs.
// Permit only that currently attached Ethernet segment; keep all other ACLs.
system = fs.readFileSync(systemPath, 'utf8');
const directMarker = '// TCLTV-Mirakurun: dedicated Ethernet /30';
if (!system.includes(directMarker)) {
    const entry = 'function isPermittedIPAddress(addr) {';
    if (!system.includes(entry)) throw new Error('Unsupported Mirakurun IP validation implementation');
    const helper = `${directMarker}
function isManagedDirectAddress(addr) {
    const parse = value => {
        if (typeof value !== "string" || !/^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$/.test(value)) return null;
        const bytes = value.split(".").map(Number);
        return bytes.every(n => n >= 0 && n <= 255) ? bytes : null;
    };
    const target = parse(addr);
    if (!target || ![1, 2].includes(target[3] % 4)) return false;
    try {
        return (require("node:os").networkInterfaces().eth0 || []).some(link => {
            if (link.family !== "IPv4" || link.internal || link.netmask !== "255.255.255.252") return false;
            const own = parse(link.address);
            if (!own || ![1, 2].includes(own[3] % 4)) return false;
            const privateLink = own[0] === 10 || (own[0] === 172 && own[1] >= 16 && own[1] <= 31) || (own[0] === 192 && own[1] === 168);
            return privateLink && own[0] === target[0] && own[1] === target[1] && own[2] === target[2] && Math.floor(own[3] / 4) === Math.floor(target[3] / 4);
        });
    } catch (_) { return false; }
}
`;
    if (!fs.existsSync(systemPath + '.tcl-direct-original')) fs.copyFileSync(systemPath, systemPath + '.tcl-direct-original');
    system = '"use strict";\n' + helper + system.replace(entry, entry + '\n    if (isManagedDirectAddress(addr)) return true;');
    fs.writeFileSync(systemPath, system);
}
console.log('Applied exact private Ethernet /30 allowance; all other client IP ACLs retained');
