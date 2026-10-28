#!/bin/sh
# Requires Docker + ARMv7 binfmt/QEMU support. Run from any working directory.
set -eu
ROOT=$(CDPATH='' cd -- "$(dirname "$0")/.." && pwd)
docker run --rm --platform linux/arm/v7 -v "$ROOT:/src" alpine:3.22.7 /bin/sh /src/scripts/runtime-container.sh
