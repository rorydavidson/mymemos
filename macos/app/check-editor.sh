#!/bin/bash
# Checks Return inside a list continues it, through the same shared logic the phone uses.
#
# The contract is easy to get wrong: the shared code runs after the newline has landed, not
# instead of it, so an editor that intercepts Return before the insert gets nil every time and
# quietly does nothing. That was the first attempt at this.
set -euo pipefail
cd "$(dirname "$0")/../.."
JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home} \
    ./gradlew :apple-shared:linkDebugFrameworkMacosArm64 --console=plain -q
OUT=$(mktemp -d)
swiftc -O -parse-as-library -target arm64-apple-macos15.0 \
    macos/app/Checks/EditorCheck.swift apple/ui/AppleCrypto.swift \
    -F apple/shared/build/bin/macosArm64/debugFramework -framework Shared -lsqlite3 \
    -o "$OUT/editorcheck"
"$OUT/editorcheck" 2>&1 | grep -v "^W/SyncEngine"
