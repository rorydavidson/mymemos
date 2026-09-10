#!/bin/bash
# Regenerates MyMemos.icns from the Android launcher artwork.
set -euo pipefail
cd "$(dirname "$0")"
OUT=$(mktemp -d)
swiftc -O -parse-as-library -target arm64-apple-macos15.0 MakeIcon.swift -o "$OUT/makeicon"
"$OUT/makeicon" "$OUT/MyMemos.iconset"
iconutil -c icns "$OUT/MyMemos.iconset" -o MyMemos.icns
echo "wrote $(pwd)/MyMemos.icns ($(du -h MyMemos.icns | cut -f1))"
