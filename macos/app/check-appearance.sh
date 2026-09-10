#!/bin/bash
# Checks light, dark and system map to the right NSAppearance, persist, and that an unknown
# stored value falls back rather than leaving the app with no appearance at all.
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=$(mktemp -d)
swiftc -O -parse-as-library -target arm64-apple-macos15.0 \
    macos/app/Checks/AppearanceCheck.swift \
    macos/app/Sources/Appearance.swift \
    -o "$OUT/appearancecheck"
"$OUT/appearancecheck"
