#!/system/bin/sh
set -eu
BASE=/data/local/tcl-mirakurun
R=$BASE/rootfs
PKG=io.github.long0823.tclmirakurun
export PATH=/system/bin:/system/xbin:/vendor/bin
is_mounted() { awk -v target="$1" '$2==target {found=1} END {exit !found}' /proc/mounts; }
valid_pid() {
 p=$(cat "$BASE/server.pid" 2>/dev/null || true)
 case "$p" in ''|*[!0-9]*) return 1;; esac
 [ -r "/proc/$p/root/opt/mirakurun/node_modules/mirakurun/package.json" ] && grep -qE 'Mirakurun: Server|lib/server.js' "/proc/$p/cmdline"
}
case "${1:-status}" in
 start)
  valid_pid && { echo 'Mirakurun already running'; exit 0; }
  /data/local/tmp/tsctl --status >/dev/null 2>&1 || { echo 'チューナー配信が未起動です。初回導入後はTVを再起動してください'; exit 1; }
  [ -f "$R/opt/mirakurun/node_modules/mirakurun/lib/server.js" ] || { echo '先にセットアップしてください'; exit 1; }
  mkdir -p "$R/dev" "$R/proc" "$R/run/tcl" "$R/var/run" "$R/root" "$R/tmp"
  is_mounted "$R/dev" || mount -o bind /dev "$R/dev"
  is_mounted "$R/proc" || mount -t proc proc "$R/proc"
  # Only the connection key is mounted into the runtime.
  touch "$R/run/tcl/tsd_token"
  is_mounted "$R/run/tcl/tsd_token" || mount -o bind /data/local/tmp/tsd_token "$R/run/tcl/tsd_token"
  chmod 1777 "$R/tmp"
  nohup /system/bin/chroot "$R" /usr/bin/env PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin HOME=/root TZ=Asia/Tokyo /bin/sh -c 'cd /opt/mirakurun/node_modules/mirakurun && exec node --max-old-space-size=256 -r source-map-support/register lib/server.js' </dev/null >>"$BASE/server.log" 2>&1 &
  echo $! >"$BASE/server.pid"
  echo 'Mirakurunを起動しました（初回は局情報の取得に数分かかります）'
  ;;
 stop)
  if valid_pid; then kill "$p"; fi
  # The bridge is always started from this chroot. Terminate only its children.
  for proc in /proc/[0-9]*; do
   if [ -r "$proc/root/opt/mirakurun/node_modules/mirakurun/package.json" ] && grep -q '/usr/local/bin/tcl-tuner' "$proc/cmdline" 2>/dev/null; then kill "${proc##*/}" 2>/dev/null || true; fi
  done
  rm -f "$BASE/server.pid"
  echo 'Mirakurunを停止しました'
  ;;
 status)
  if valid_pid; then echo "Mirakurun running pid=$p"; else echo 'Mirakurun stopped'; fi
  /data/local/tmp/tsctl --status 2>/dev/null || true
  ip -4 addr show eth0 2>/dev/null || true
  ip -4 addr show wlan0 2>/dev/null || true
  ;;
 *) echo 'start | stop | status'; exit 2;;
esac
