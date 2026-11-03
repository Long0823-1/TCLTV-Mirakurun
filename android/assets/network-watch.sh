#!/system/bin/sh
BASE=/data/local/tcl-mirakurun
APP_FILES=/data/user/0/io.github.long0823.tclmirakurun/files
export PATH=/system/bin:/system/xbin:/vendor/bin
previous=$(cat "$BASE/network.pid" 2>/dev/null)
case "$previous" in
 ''|*[!0-9]*) ;;
 *) if kill -0 "$previous" 2>/dev/null && grep -q "$BASE/network-watch.sh" "/proc/$previous/cmdline" 2>/dev/null; then exit 0; fi;;
esac
R=$BASE/rootfs
autoip_pid=
waiting=0
stop_autoip() {
 if [ -n "$autoip_pid" ]; then kill "$autoip_pid" 2>/dev/null || true; wait "$autoip_pid" 2>/dev/null || true; autoip_pid=; fi
 owned=$(cat "$R/run/tcl/autoip-address" 2>/dev/null)
 case "$owned" in 169.254.*\/16) ip addr del "$owned" dev eth0 2>/dev/null || true;; esac
 rm -f "$R/run/tcl/autoip-address"
}
echo $$ >"$BASE/network.pid"
trap 'stop_autoip; [ "$(cat "$BASE/network.pid" 2>/dev/null)" != "$$" ] || rm -f "$BASE/network.pid"' EXIT
trap 'exit 0' TERM INT
while [ -f "$BASE/enabled" ]; do
 # These files are created by the app, retaining its ownership and SELinux label.
 # Publish from this existing root daemon; never launch su for periodic monitoring.
 if [ -f "$APP_FILES/network-state" ]; then
  carrier=$(cat /sys/class/net/eth0/carrier 2>/dev/null); gateway=0
  ip -4 route show dev eth0 2>/dev/null | grep -q '^default ' && gateway=1
  printf '%s\n%s\n%s\n' "$carrier" "$gateway" "$(date +%s)" >"$APP_FILES/network-state"
 fi
 if [ -f "$APP_FILES/mdns-hosts-request.json" ] && [ -d "$BASE/rootfs/run/tcl" ]; then
  if ! cmp -s "$APP_FILES/mdns-hosts-request.json" "$BASE/rootfs/run/tcl/mdns-hosts.json"; then
   cp "$APP_FILES/mdns-hosts-request.json" "$BASE/rootfs/run/tcl/mdns-hosts.json.new" && chmod 600 "$BASE/rootfs/run/tcl/mdns-hosts.json.new" && mv "$BASE/rootfs/run/tcl/mdns-hosts.json.new" "$BASE/rootfs/run/tcl/mdns-hosts.json"
  fi
 fi
 if ip link show eth0 >/dev/null 2>&1; then
  ip link set eth0 up 2>/dev/null
  mode=$(cat "$BASE/network-mode" 2>/dev/null); [ -n "$mode" ] || mode=auto
  # Remove only the legacy alias installed by this app, never OS-owned addresses.
  owned=$(cat "$BASE/network-added-address" 2>/dev/null)
  legacy=$(cat "$BASE/wired-address" 2>/dev/null); [ -n "$legacy" ] || legacy=169.254.95.200/16
  if [ "$mode" != static ] && [ ! -f "$BASE/autoip-migrated" ] && [ -f "$BASE/wired-address" ]; then
   case "$legacy" in 169.254.*\/16) ip addr del "$legacy" dev eth0 2>/dev/null || true;; esac
   touch "$BASE/autoip-migrated"
  fi
  if [ "$owned" = "$legacy" ]; then ip addr del "$owned" dev eth0 2>/dev/null || true; rm -f "$BASE/network-added-address"; fi
  carrier=$(cat /sys/class/net/eth0/carrier 2>/dev/null)
  lan=$(ip -4 -o addr show dev eth0 2>/dev/null | grep ' inet ' | grep -v ' inet 169\.254\.' | grep -v ' inet 127\.' || true)
  case "$mode" in
   auto|direct)
    if [ "$carrier" = 1 ] && [ -z "$lan" ]; then
     waiting=$((waiting+1))
     # Let Android DHCP negotiate first. Windows APIPA needs no DHCP server.
     if [ "$waiting" -ge 15 ] && { [ -z "$autoip_pid" ] || ! kill -0 "$autoip_pid" 2>/dev/null; }; then
      if [ -f "$R/run/tcl/autoip-hook.sh" ]; then
       stop_autoip
       chroot "$R" /bin/busybox zcip -f eth0 /run/tcl/autoip-hook.sh >>"$BASE/network.log" 2>&1 &
       autoip_pid=$!
      fi
     fi
    else waiting=0; stop_autoip; fi
    ;;
   router|static) waiting=0; stop_autoip;;
   *) waiting=0; stop_autoip; echo 'Unknown network mode; retaining OS configuration';;
  esac
 fi
 sleep 2
done
