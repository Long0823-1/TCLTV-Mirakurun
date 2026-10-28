#!/system/bin/sh
BASE=/data/local/tcl-mirakurun
for attempt in $(seq 1 120); do
 [ -f "$BASE/enabled" ] || exit 0
 if [ "$(getprop sys.boot_completed)" = 1 ] && /data/local/tmp/tsctl --status 2>/dev/null | grep -q ' running=1 '; then
  am start-foreground-service -n io.github.long0823.tclmirakurun/.NetworkService >/dev/null 2>&1
  sh "$BASE/control.sh" start >>"$BASE/boot.log" 2>&1
  exit $?
 fi
 sleep 2
done
