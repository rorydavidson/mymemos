#!/bin/bash
# Regenerates the app icon from the Android launcher artwork, as the one 1024 px square iOS
# wants; the system rounds the corners itself.
set -euo pipefail
cd "$(dirname "$0")"
OUT=$(mktemp -d)
swiftc -O -parse-as-library -target arm64-apple-macos15.0 MakeIcon.swift -o "$OUT/makeicon"
"$OUT/makeicon" ../Resources/Assets.xcassets/AppIcon.appiconset/icon-1024.png
echo "wrote icon-1024.png"
