#!/bin/bash
# Checks the notification centre can be reached from a bundle signed the way this app is.
#
# The check has to run from inside a .app: UNUserNotificationCenter traps on a loose binary,
# which would fail for a reason that says nothing about the app. So a throwaway bundle is
# built around the check, signed with the same identity build.sh uses.
set -euo pipefail
cd "$(dirname "$0")/../.."

OUT=$(mktemp -d)
APP="$OUT/NotificationCheck.app"
mkdir -p "$APP/Contents/MacOS"

cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key><string>NotificationCheck</string>
    <key>CFBundleIdentifier</key><string>com.keltruc.mymemos.macos.check</string>
    <key>CFBundleExecutable</key><string>NotificationCheck</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>0.1.0</string>
    <key>LSMinimumSystemVersion</key><string>15.0</string>
    <key>LSBackgroundOnly</key><true/>
</dict>
</plist>
PLIST

swiftc -O -parse-as-library -target arm64-apple-macos15.0 \
    macos/app/Checks/NotificationCheck.swift \
    -o "$APP/Contents/MacOS/NotificationCheck"

if security find-certificate -c "MyMemos Local Signing" >/dev/null 2>&1; then
    codesign --force --sign "MyMemos Local Signing" "$APP" 2>/dev/null
else
    codesign --force --sign - "$APP" 2>/dev/null
fi

"$APP/Contents/MacOS/NotificationCheck"
