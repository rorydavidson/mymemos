#!/bin/bash
# Checks that no map tile is fetched while map previews are off.
#
# The middle line is the control: if turning previews on does not fetch either, the other two
# lines prove nothing and the check is broken rather than passing.
set -euo pipefail
cd "$(dirname "$0")/../.."

JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home} \
    ./gradlew :macos-shared:linkDebugFrameworkMacosArm64 --console=plain -q

OUT=$(mktemp -d)
swiftc -O -parse-as-library \
    -target arm64-apple-macos15.0 \
    macos/app/Checks/TileGuardCheck.swift \
    macos/app/Sources/MapView.swift \
    macos/app/Sources/Theme.swift \
    -F macos/shared/build/bin/macosArm64/debugFramework \
    -framework Shared \
    -o "$OUT/tileguard"

"$OUT/tileguard"
