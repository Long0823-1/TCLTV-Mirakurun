#!/system/bin/sh
set -eu
STAGE=$1
BASE=/data/local/tcl-mirakurun
R=$BASE/rootfs
export PATH=/system/bin:/system/xbin:/vendor/bin
# The deletion target is fixed; never follow a substituted directory or symlink.
[ ! -e "$BASE" ] || [ "$(readlink -f "$BASE")" = "$BASE" ] || { echo '環境のパスが不正です'; exit 20; }
[ ! -e "$R" ] || [ "$(readlink -f "$R")" = "$R" ] || { echo '実行環境のパスが不正です'; exit 20; }
rm -f "$BASE/enabled"
if [ -f "$BASE/control.sh" ]; then sh "$BASE/control.sh" stop; fi
rm -f /data/adb/service.d/99-tcl-mirakurun.sh
for attempt in $(seq 1 10); do
 busy=0
 for proc in /proc/[0-9]*; do
  [ "$(readlink "$proc/root" 2>/dev/null || true)" != "$R" ] || busy=1
 done
 [ "$busy" = 0 ] && break
 sleep 1
done
[ "$busy" = 0 ] || { echo '実行環境を使用中のプロセスが残っています。削除を中止しました'; exit 21; }
for point in "$R/run/tcl/tsd_token" "$R/proc" "$R/dev"; do
 # Android mountpoint does not identify bind-mounted files. Inspect mountinfo.
 while awk -v target="$point" '$2==target {found=1} END {exit !found}' /proc/mounts; do
  umount "$point"
 done
done
if awk -v prefix="$BASE/" 'index($2,prefix)==1 {found=1} END {exit !found}' /proc/mounts; then
 echo '未解除のマウントがあります。削除を中止しました'; exit 22
fi
owned=0
if [ -f "$BASE/tuner-owned" ]; then
 sh "$STAGE/restore.sh"
 owned=1
fi
rm -rf -- "$BASE"
echo 'Mirakurun本体・局設定・番組DB・ログ・自動起動を削除しました'
if [ "$owned" = 1 ]; then
 echo 'このアプリが導入したチューナー設定も復元しました。TVを再起動してください'
else
 echo '既存のBonDriver・チューナー配信環境は保持しました'
fi
