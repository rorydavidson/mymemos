#!/bin/bash
# Walks the app through its screens on a simulator and screenshots each one, for looking at
# what was built when nothing can tap the simulator for you. Needs a Memos server to sign in
# to: MEMOS_SERVER, MEMOS_USER and MEMOS_PASSWORD, ideally a throwaway one.
#
#   MEMOS_SERVER=http://localhost:5230 MEMOS_USER=tester MEMOS_PASSWORD=... ios/app/tour.sh
#
# Screenshots land in ios/app/build/tour/. DEVICE picks the simulator (default: first
# available iPhone). Builds with TESTHOOKS=1, so the bundle it installs is not one to keep.
set -euo pipefail
cd "$(dirname "$0")/../.."

: "${MEMOS_SERVER:?}" "${MEMOS_USER:?}" "${MEMOS_PASSWORD:?}"
DEVICE=${DEVICE:-$(xcrun simctl list devices available | grep -m1 "iPhone" | sed -E 's/.*\(([0-9A-F-]{36})\).*/\1/')}
BUNDLE=com.keltruc.mymemos.ios
OUT=ios/app/build/tour
mkdir -p "$OUT"

TESTHOOKS=1 ios/app/build.sh
xcrun simctl boot "$DEVICE" 2>/dev/null || true
xcrun simctl bootstatus "$DEVICE" -b >/dev/null
xcrun simctl uninstall "$DEVICE" "$BUNDLE" 2>/dev/null || true
xcrun simctl install "$DEVICE" ios/app/build/MyMemos.app

# name, steps, seconds to wait before the screenshot
scene() {
    xcrun simctl terminate "$DEVICE" "$BUNDLE" 2>/dev/null || true
    SIMCTL_CHILD_MYMEMOS_TEST_SIGNIN="$MEMOS_SERVER|$MEMOS_USER|$MEMOS_PASSWORD" \
    SIMCTL_CHILD_MYMEMOS_TEST_STEPS="$2" \
        xcrun simctl launch "$DEVICE" "$BUNDLE" >/dev/null
    sleep "${3:-6}"
    xcrun simctl io "$DEVICE" screenshot "$OUT/$1.png" >/dev/null 2>&1
    echo "  $1"
}

echo "touring on $DEVICE"
scene timeline "" 10
scene compact "compact"
scene detail "open:0"
scene editor "edit:0"
scene new-memo "new"
scene search "query:coffee"
scene tasks "pane:tasks"
scene review "pane:review"
echo "screenshots in $OUT"
