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
