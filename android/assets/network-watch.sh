#!/system/bin/sh
BASE=/data/local/tcl-mirakurun
export PATH=/system/bin:/system/xbin:/vendor/bin
previous=$(cat "$BASE/network.pid" 2>/dev/null)
case "$previous" in
 ''|*[!0-9]*) ;;
 *) if kill -0 "$previous" 2>/dev/null && grep -q "$BASE/network-watch.sh" "/proc/$previous/cmdline" 2>/dev/null; then exit 0; fi;;
esac
echo $$ >"$BASE/network.pid"
trap 'rm -f "$BASE/network.pid"' EXIT
address=$(cat "$BASE/wired-address" 2>/dev/null)
[ -n "$address" ] || address=169.254.95.200/16
echo "$address" | grep -Eq '^169\.254\.[0-9]{1,3}\.[0-9]{1,3}/16$' || exit 2
while [ -f "$BASE/enabled" ]; do
 if ip link show eth0 >/dev/null 2>&1; then
  ip link set eth0 up 2>/dev/null
  ip -4 addr show eth0 | grep -Fq "$address" || ip addr add "$address" dev eth0 2>/dev/null
 fi
 sleep 2
done
