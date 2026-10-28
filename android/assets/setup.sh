#!/system/bin/sh
set -eu
STAGE=$1
BASE=/data/local/tcl-mirakurun
R=$BASE/rootfs
mkdir -p "$BASE"
chmod 700 "$BASE"
# Refuse unsupported firmware before making any tuner changes.
check_hash() { [ "$(sha256sum "$1" | cut -d ' ' -f 1)" = "$2" ] || { echo '対応ファームウェアではありません'; exit 10; }; }
check_hash /vendor/tvos/libBionic/libfpp.so e31b65a665f9d7cd8c68a882bc23e9213c40ac30d388222bdd7a6c421b7c907b
check_hash /vendor/tvos/libBionic/libisdb.so f389445d2868bd091f842175592f77539480b2a6357ecc143a276149efe1a215
check_hash /vendor/lib/libmi3.so 05b6276bdc96e6ae769e0ce4f4a99e77a2181f964fddeb414c0f0770679b9aa6
if [ -f "$BASE/control.sh" ]; then sh "$BASE/control.sh" stop; fi
if [ ! -f "$R/opt/mirakurun/node_modules/mirakurun/package.json" ]; then
 [ ! -e "$R" ] || { echo '不完全な実行環境が存在します。ログを確認してください'; exit 12; }
 echo 'Mirakurun実行環境を展開しています'
 mkdir -p "$BASE/rootfs.new"
 tar -xzf "$STAGE/runtime.tar.gz" -C "$BASE/rootfs.new"
 [ -f "$BASE/rootfs.new/opt/mirakurun/node_modules/mirakurun/lib/server.js" ]
 mv "$BASE/rootfs.new" "$R"
fi
mkdir -p "$R/usr/local/etc/mirakurun" "$R/usr/local/var/db/mirakurun" "$R/usr/local/bin" "$R/root" "$R/var/run" "$R/tmp" "$R/dev" "$R/proc" "$R/run/tcl"
cp "$STAGE/patch-runtime.cjs" "$R/tmp/patch-runtime.cjs"
chroot "$R" /usr/bin/node /tmp/patch-runtime.cjs
cp "$STAGE/channels.yml" "$R/usr/local/etc/mirakurun/channels.yml"
cp "$STAGE/tuners.yml" "$R/usr/local/etc/mirakurun/tuners.yml"
[ -f "$R/usr/local/etc/mirakurun/server.yml" ] || cp "$STAGE/server.yml" "$R/usr/local/etc/mirakurun/server.yml"
cp "$STAGE/tcl-tuner" "$R/usr/local/bin/tcl-tuner"
cp "$STAGE/control.sh" "$BASE/control.sh"
cp "$STAGE/99-tcl-mirakurun.sh" /data/adb/service.d/99-tcl-mirakurun.sh
chmod 755 "$BASE/control.sh" /data/adb/service.d/99-tcl-mirakurun.sh
if /data/local/tmp/tsctl --status >/dev/null 2>&1; then
 echo '動作中のチューナー配信環境を利用します'
else
 sh "$STAGE/install.sh" "$STAGE"
 touch "$BASE/tuner-owned"
 echo 'チューナー配信を導入しました。TVを再起動後に起動してください'
fi
touch "$BASE/enabled"
sync
echo 'セットアップ完了'
