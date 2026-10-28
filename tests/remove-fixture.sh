#!/system/bin/sh
set -eu
T=/data/local/tcl-mirakurun-delete-test
[ ! -e "$T" ] || exit 30
mkdir -p "$T/rootfs/dev" "$T/rootfs/proc" "$T/rootfs/run/tcl"
printf 'test-key\n' >"$T/token"
touch "$T/rootfs/run/tcl/tsd_token" "$T/enabled" /data/local/tmp/mirakurun-delete-test-boot.sh
mount -o bind /dev "$T/rootfs/dev"
mount -t proc proc "$T/rootfs/proc"
mount -o bind "$T/token" "$T/rootfs/run/tcl/tsd_token"
sh /data/local/tmp/mirakurun-remove-fixture.sh /data/local/tmp
[ ! -e "$T" ]
[ ! -e /data/local/tmp/mirakurun-delete-test-boot.sh ]
sh /data/local/tmp/mirakurun-remove-fixture.sh /data/local/tmp
mkdir -p "$T"
ln -s /data/local/tcl-mirakurun/rootfs "$T/rootfs"
if sh /data/local/tmp/mirakurun-remove-fixture.sh /data/local/tmp; then exit 31; fi
rm "$T/rootfs"
rmdir "$T"
echo 'Deletion tests PASS: mount cleanup, repeated deletion, symlink refusal'