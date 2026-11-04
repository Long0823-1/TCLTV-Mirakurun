#!/system/bin/sh
# Transactional Mirakurun-only update. Android, Node, tuner and network settings
# stay in place. A previous package tree and pre-update data snapshot are kept.
set -eu
BASE=/data/local/tcl-mirakurun
R=$BASE/rootfs
STAGE=$1
ACTION=$2
export PATH=/system/bin:/system/xbin:/vendor/bin
[ "$STAGE" = /data/user/0/io.github.long0823.tclmirakurun/files/core-update ] || exit 20
case "$ACTION" in update|rollback|versions) ;; *) exit 20;; esac
[ "$(readlink -f "$R")" = "$R" ] || exit 20
[ -f "$R/opt/mirakurun/node_modules/mirakurun/package.json" ] || { echo '先にセットアップしてください'; exit 21; }
LOCK=$BASE/core-update.lock
if [ -d "$LOCK" ]; then
 owner=$(cat "$LOCK/pid" 2>/dev/null || true)
 running=0
 case "$owner" in ''|*[!0-9]*) ;; *) if kill -0 "$owner" 2>/dev/null && grep -q 'update-core.sh' "/proc/$owner/cmdline" 2>/dev/null; then running=1; fi;; esac
 [ "$running" = 0 ] || { echo '本体の更新処理が実行中です'; exit 22; }
 echo '中断した更新のロックを解除します'
 rm -rf "$LOCK"
fi
if ! mkdir "$LOCK" 2>/dev/null; then echo '本体の更新処理が実行中です'; exit 22; fi
chmod 700 "$LOCK"
echo $$ >"$LOCK/pid"
swapped=0
stopped=0
recovery_config=$BASE/core-update-backup/config
recovery_db=$BASE/core-update-backup/db
finish() {
 result=$?
 trap - EXIT HUP INT TERM
 if [ "$result" -ne 0 ] && [ "$swapped" = 1 ]; then
  echo '起動確認に失敗しました。前の本体と設定・番組DBへ戻します'
  sh "$BASE/control.sh" stop || true
  if chroot "$R" /usr/bin/node /run/tcl/core-update.cjs revert; then
   # Fixed, application-owned snapshot paths only; no caller-supplied target.
   rm -rf "$R/usr/local/etc/mirakurun" "$R/usr/local/var/db/mirakurun"
   cp -a "$recovery_config" "$R/usr/local/etc/mirakurun"
   cp -a "$recovery_db" "$R/usr/local/var/db/mirakurun"
   if [ "$ACTION" = update ]; then chroot "$R" /usr/bin/node /run/tcl/core-update.cjs discard-previous; fi
  fi
 fi
 if [ "$result" -ne 0 ] && [ "$stopped" = 1 ]; then sh "$BASE/control.sh" start || true; fi
 if [ "$ACTION" = update ]; then chroot "$R" /usr/bin/node /run/tcl/core-update.cjs clean-candidate || true; fi
 rm -rf "$LOCK"
 exit "$result"
}
trap finish EXIT
trap 'exit 130' HUP INT TERM
mkdir -p "$R/run/tcl"
for file in core-update.cjs core-health.cjs patch-runtime.cjs crc32-fallback.cjs; do
 cp "$STAGE/$file" "$R/run/tcl/$file"
 chmod 600 "$R/run/tcl/$file"
done
if [ "$ACTION" = versions ]; then
 chroot "$R" /usr/bin/node /run/tcl/core-update.cjs versions
 exit 0
fi
if [ "$ACTION" = update ]; then
 [ -s "$STAGE/resolv.conf" ] || { echo 'インターネット接続のDNSを取得できません。TVのネットワーク設定を確認してください'; exit 25; }
 [ -f "$R/etc/resolv.conf.tcl-before-update" ] || cp -p "$R/etc/resolv.conf" "$R/etc/resolv.conf.tcl-before-update"
 cp "$STAGE/resolv.conf" "$R/etc/resolv.conf"
 cp "$STAGE/request.json" "$R/run/tcl/core-update-request.json"
 chmod 600 "$R/run/tcl/core-update-request.json"
fi
# The TV has limited RAM: stop the server before running npm. Explicitly reject
# viewer/recording sessions first; background EPG may be stopped for maintenance.
chroot "$R" /usr/bin/node /run/tcl/core-health.cjs idle
sh "$BASE/control.sh" stop
stopped=1
if [ "$ACTION" = update ]; then
 echo '配信を停止して更新用の本体を準備します'
 chroot "$R" /usr/bin/node /run/tcl/core-update.cjs prepare
 echo '設定と番組DBをバックアップしています'
 rm -rf "$BASE/core-update-backup.new"
 mkdir -p "$BASE/core-update-backup.new"
 chmod 700 "$BASE/core-update-backup.new"
 cp -a "$R/usr/local/etc/mirakurun" "$BASE/core-update-backup.new/config"
 cp -a "$R/usr/local/var/db/mirakurun" "$BASE/core-update-backup.new/db"
 rm -rf "$BASE/core-update-backup"
 mv "$BASE/core-update-backup.new" "$BASE/core-update-backup"
 chroot "$R" /usr/bin/node /run/tcl/core-update.cjs activate
 swapped=1
else
 [ -d "$BASE/core-update-backup/config" ] && [ -d "$BASE/core-update-backup/db" ] || { echo '復元用の設定・DBがありません'; exit 23; }
 # Restore the old package's data format, retaining a copy of current data.
 cp -a "$R/usr/local/etc/mirakurun" "$LOCK/config-current"
 cp -a "$R/usr/local/var/db/mirakurun" "$LOCK/db-current"
 recovery_config=$LOCK/config-current
 recovery_db=$LOCK/db-current
 chroot "$R" /usr/bin/node /run/tcl/core-update.cjs revert
 swapped=1
 rm -rf "$R/usr/local/etc/mirakurun" "$R/usr/local/var/db/mirakurun"
 cp -a "$BASE/core-update-backup/config" "$R/usr/local/etc/mirakurun"
 cp -a "$BASE/core-update-backup/db" "$R/usr/local/var/db/mirakurun"
fi
expected=$(chroot "$R" /usr/bin/node -p 'require("/opt/mirakurun/node_modules/mirakurun/package.json").version')
sh "$BASE/control.sh" start
healthy=0
for attempt in $(seq 1 30); do
 if chroot "$R" /usr/bin/node /run/tcl/core-health.cjs "$expected" >/dev/null 2>&1; then healthy=1; echo "HEALTHY $expected"; break; fi
 sleep 2
done
[ "$healthy" = 1 ] || { echo '更新後のAPIを確認できませんでした'; exit 24; }
swapped=0
stopped=0
if [ "$ACTION" = rollback ]; then
 rm -rf "$BASE/core-update-backup/config" "$BASE/core-update-backup/db"
 mv "$LOCK/config-current" "$BASE/core-update-backup/config"
 mv "$LOCK/db-current" "$BASE/core-update-backup/db"
fi
echo "Mirakurun本体 $expected の起動を確認しました。TVの再起動は不要です"
