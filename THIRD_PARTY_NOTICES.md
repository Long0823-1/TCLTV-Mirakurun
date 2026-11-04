# Third-party components

- `vendor/tcl/native`, Android tuner setup/restore scripts, icon/banner resources,
  and `TclPolicy.java` / `RootConfigMain.java` originate from
  [Long0823-1/TCLTV-Bondriver](https://github.com/Long0823-1/TCLTV-Bondriver),
  commit `b3826d414c0905a5e2d8e23c728fee21c6a26feb` (MIT).
  Its license is preserved in `vendor/tcl/LICENSE`. Changes here remove the
  hardcoded wired address and old APK dependency and use a separate backup directory.
- [Mirakurun](https://github.com/Chinachu/Mirakurun) 4.1.5: Apache-2.0.
  `android/assets/patch-runtime.cjs` modifies the IPv4 listener to accept
  addresses acquired after startup. Its existing IP and origin ACLs are retained.
- Alpine Linux 3.22.7 ARMv7 runtime, Node.js 22, npm, Python 3 and npm
  dependencies retain their own licenses inside the bundled root filesystem
  (`/lib/apk/db/installed`, package metadata and `node_modules/**/LICENSE*`).
- The runtime's [BusyBox](https://busybox.net/) `zcip` applet provides automatic
  IPv4 link-local addressing and ARP conflict handling (GPL-2.0-only).
  Source and Alpine packaging: [Alpine busybox](https://gitlab.alpinelinux.org/alpine/aports/-/tree/3.22-stable/main/busybox).
- [JmDNS](https://github.com/jmdns/jmdns) 3.6.3: Apache-2.0.
  License and NOTICE are in `android/assets/licenses/` and included in the APK.
  `vendor/jmdns/SocketListener.java` preserves the upstream Apache-2.0 header
  and adds RFC 6762 section 6.7 legacy-unicast routing for Android one-shot
  queries. `scripts/patch-jmdns.py` replaces only that class in a separate JAR;
  the pinned upstream download and all other classes are retained.
- [SLF4J](https://www.slf4j.org/) API and NOP 2.0.17: MIT.
  License texts are in `android/assets/licenses/` and included in the APK.
  Pinned downloads and SHA-256 digests are in `scripts/java-dependencies.json`.
- `runtime/crc32-fallback.cjs` is a local MIT-licensed implementation of CRC-32
  and CRC-32C. It replaces the ARM glibc-only native entrypoint of
  `@node-rs/crc32` for ARMv7/musl. It is used by ARIB logo PNG generation.

The APK does not contain firmware, vendor libraries, broadcast recordings,
device-specific keys or signing keys. Vendor libraries are used only on the TV
after their hashes match the tested firmware.

Release runtime archives can be rebuilt with `scripts/build-runtime.sh`.
Resolved npm dependency versions are recorded in the archive's
`/opt/mirakurun/package-lock.json`. Alpine package revisions can change between
builds; the archive SHA-256 identifies each actual release payload.

## Satellite tuning attribution

The BS/110-degree CS changes are adapted from
[2m3k/TCLTV-Bondriver-3wave](https://github.com/2m3k/TCLTV-Bondriver-3wave),
commit `2c7bb42` (MIT), reviewed at `c5f121e`. Thanks to 2m3k for
the satellite demodulator parameters, database routing, tuning-space and catalog work.
The upstream MIT license and copyright notice are retained.
No precompiled fork binary is shipped: the native payload is built from the
reviewed sources in this repository. Local changes centralize serialized route
restoration, reject unsupported satellite IDs, fix an early-return allocation leak,
keep terrestrial timeouts, and use Android logcat rather than an unbounded log file.
