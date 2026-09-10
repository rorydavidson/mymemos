#!/bin/bash
# Checks that the macOS cipher agrees with Android's, using the same fixed vectors.
set -euo pipefail
cd "$(dirname "$0")/../.."

JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home} \
    ./gradlew :macos-shared:linkDebugFrameworkMacosArm64 --console=plain -q

OUT=$(mktemp -d)
swiftc -O -parse-as-library \
    -target arm64-apple-macos15.0 \
    macos/app/Checks/CipherParityCheck.swift \
    macos/app/Sources/AppleCrypto.swift \
    -F macos/shared/build/bin/macosArm64/debugFramework \
    -framework Shared -lsqlite3 \
    -o "$OUT/ciphercheck"

"$OUT/ciphercheck"
