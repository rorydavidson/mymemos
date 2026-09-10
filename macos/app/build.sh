#!/bin/bash
# Builds the macOS app into a .app bundle. There is no Xcode project yet: the Swift side is
# one file, and a script keeps it runnable from the same place the Gradle build lives.
set -euo pipefail
cd "$(dirname "$0")/../.."

JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home} \
    ./gradlew :macos-shared:linkDebugFrameworkMacosArm64 --console=plain -q

FRAMEWORK_DIR="macos/shared/build/bin/macosArm64/debugFramework"
APP="macos/app/build/MyMemos.app"

rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"

cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key><string>MyMemos</string>
    <key>CFBundleDisplayName</key><string>MyMemos</string>
    <key>CFBundleIdentifier</key><string>com.keltruc.mymemos.macos</string>
    <key>CFBundleExecutable</key><string>MyMemos</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>0.1.0</string>
    <key>LSMinimumSystemVersion</key><string>15.0</string>
    <key>NSHighResolutionCapable</key><true/>
</dict>
</plist>
PLIST

swiftc -O \
    -target arm64-apple-macos15.0 \
    -F "$FRAMEWORK_DIR" \
    -framework Shared \
    -lsqlite3 \
    -parse-as-library \
    macos/app/Sources/MyMemosApp.swift \
    -o "$APP/Contents/MacOS/MyMemos"

echo "built $APP"
