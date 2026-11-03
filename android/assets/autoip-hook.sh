#!/bin/sh
# Invoked by BusyBox zcip after RFC3927 probing / conflict detection.
set -eu
export PATH=/sbin:/bin:/usr/sbin:/usr/bin
STATE=/run/tcl/autoip-address
case "$1" in
 init) rm -f "$STATE";;
 config)
  /bin/busybox ip addr add "$ip/16" dev "$interface"
  printf '%s/16\n' "$ip" >"$STATE.new"
  mv -f "$STATE.new" "$STATE"
  ;;
 deconfig)
  /bin/busybox ip addr del "$ip/16" dev "$interface" 2>/dev/null || true
  rm -f "$STATE"
  ;;
esac
