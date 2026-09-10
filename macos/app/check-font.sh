#!/bin/bash
# Checks the bundled Google Sans Flex is present, licensed and resolvable.
set -euo pipefail
cd "$(dirname "$0")/../.."
macos/app/build.sh > /dev/null
OUT=$(mktemp -d)
swiftc -O -parse-as-library -target arm64-apple-macos15.0 \
    macos/app/Checks/FontCheck.swift -o "$OUT/fontcheck"
"$OUT/fontcheck"
