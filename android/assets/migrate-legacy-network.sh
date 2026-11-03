#!/system/bin/sh
# Only adapt the known legacy alias keeper, preserving a backup and tuner service.
BASE=/data/local/tcl-mirakurun
legacy=/data/local/tmp/tcl-pvr-net.sh
if grep -q 'Keep the point-to-point wired alias' "$legacy" 2>/dev/null && ! grep -q 'Mirakurun owns automatic addressing' "$legacy"; then
 cp -p "$legacy" "$BASE/legacy-network-before-autoip.sh"
 sed '/^while :; do$/a\
 # Mirakurun owns automatic addressing while enabled.\
 if [ -f /data/local/tcl-mirakurun/enabled ]; then sleep 2; continue; fi' "$legacy" >"$legacy.new" || exit 1
 chmod 700 "$legacy.new" && mv -f "$legacy.new" "$legacy" || exit 1
 p=$(cat /data/local/tmp/tcl-pvr-net.pid 2>/dev/null)
 case "$p" in ''|*[!0-9]*) ;; *) if grep -q '/data/local/tmp/tcl-pvr-net.sh' "/proc/$p/cmdline" 2>/dev/null; then kill "$p" 2>/dev/null || true; fi;; esac
 # The known legacy alias may have been installed without an ownership marker.
 ip addr del 169.254.95.200/16 dev eth0 2>/dev/null || true
fi
