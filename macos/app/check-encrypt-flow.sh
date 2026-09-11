#!/bin/bash
# Reproduces choosing Encrypt with no memo password held: the first attempt must refuse, and
# the attempt after a password arrives must actually encrypt. The bug this covers was that
# supplying the password was never connected to the job that asked for it, so the second half
# silently did nothing.
set -euo pipefail
cd "$(dirname "$0")/../.."
JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home} \
    ./gradlew :apple-shared:linkDebugFrameworkMacosArm64 --console=plain -q
OUT=$(mktemp -d)
swiftc -O -parse-as-library -target arm64-apple-macos15.0 \
    macos/app/Checks/EncryptFlowCheck.swift apple/ui/AppleCrypto.swift \
    -F apple/shared/build/bin/macosArm64/debugFramework -framework Shared -lsqlite3 \
    -o "$OUT/encryptflow"
"$OUT/encryptflow" 2>&1 | grep -v "^W/SyncEngine"
