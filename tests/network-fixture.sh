#!/bin/bash
# Compatibility entry point for the current automatic addressing policy.
exec bash "$(dirname "$0")/autoip-policy.sh"
