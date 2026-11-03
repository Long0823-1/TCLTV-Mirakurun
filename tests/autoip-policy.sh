#!/bin/bash
set -euo pipefail
repo=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d /tmp/tcl-autoip-test-XXXXXX)
trap 'case "$work" in /tmp/tcl-autoip-test-*) rm -rf -- "$work";; esac' EXIT
mkdir "$work/bin"
cat >"$work/bin/ip" <<'EOF'
#!/bin/bash
case "$*" in
 'link show eth0'|'link set eth0 up') exit 0;;
 '-4 -o addr show dev eth0') [ "$TEST_LAN" = 0 ] || echo '3: eth0 inet 192.168.50.10/24';exit 0;;
 'addr del '*) echo "$*" >>"$TEST_BASE/actions";;
 *) exit 7;;
esac
EOF
cat >"$work/bin/chroot" <<'EOF'
#!/bin/bash
echo "$*" >>"$TEST_BASE/started"
EOF
cat >"$work/bin/sleep" <<'EOF'
#!/bin/bash
/usr/bin/sleep 0.1
rm -f "$TEST_BASE/enabled"
EOF
chmod +x "$work/bin/"*
for item in auto:0:1:yes auto:1:1:no auto:0:0:no direct:0:1:yes router:0:1:no static:0:1:no; do
 IFS=: read -r mode lan carrier expected <<<"$item"
 base="$work/$mode-$lan-$carrier";mkdir -p "$base/rootfs/run/tcl"
 touch "$base/enabled" "$base/rootfs/run/tcl/autoip-hook.sh"
 echo "$mode" >"$base/network-mode"
 if [ "$item" = auto:0:1:yes ]; then echo '169.254.95.200/16' >"$base/wired-address";fi
 sed -e "s|^BASE=.*|BASE=$base|" -e "s|^APP_FILES=.*|APP_FILES=$base/no-app|" -e "s|^export PATH=.*|export PATH=$work/bin:/usr/bin:/bin|" -e 's/^waiting=0$/waiting=14/' -e "s|carrier=\$(cat /sys/class/net/eth0/carrier 2>/dev/null)|carrier=$carrier|g" "$repo/android/assets/network-watch.sh" >"$base/network-watch.sh"
 TEST_BASE="$base" TEST_LAN="$lan" bash "$base/network-watch.sh"
 if [ "$expected" = yes ]; then grep -q '/bin/busybox zcip -f eth0' "$base/started";else [ ! -e "$base/started" ];fi
 if [ "$item" = auto:0:1:yes ]; then grep -q 'addr del 169.254.95.200/16 dev eth0' "$base/actions";fi
 echo "PASS DHCP/link-local policy $item"
done
