#!/bin/bash
#
# Builds MyMemos, signs it, notarises it and produces a DMG.
#
# Signing and notarising need credentials this script will not invent:
#
#   SIGN_IDENTITY   the Developer ID Application identity, e.g.
#                   "Developer ID Application: Your Name (TEAMID)".
#                   `security find-identity -v -p codesigning` lists what you have.
#   NOTARY_PROFILE  a notarytool keychain profile, created once with
#                   `xcrun notarytool store-credentials`.
#
# With neither, it still produces a working DMG: ad-hoc signed, which is fine for this Mac
# and will be refused by Gatekeeper on anyone else's. It says so rather than looking finished.
set -euo pipefail
cd "$(dirname "$0")/../.."

APP="macos/app/build/MyMemos.app"
DIST="macos/app/build/dist"
VERSION=$(/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" "$APP/Contents/Info.plist" 2>/dev/null || echo "0.1.0")
DMG="$DIST/MyMemos-$VERSION.dmg"

echo "==> Building"
macos/app/build.sh > /dev/null

rm -rf "$DIST"
mkdir -p "$DIST"

# ---- sign -------------------------------------------------------------------------------

SIGN_IDENTITY=${SIGN_IDENTITY:-}
if [ -z "$SIGN_IDENTITY" ]; then
    SIGN_IDENTITY=$(security find-identity -v -p codesigning 2>/dev/null \
        | grep "Developer ID Application" | head -1 | sed -E 's/.*"(.*)"/\1/' || true)
fi

if [ -n "$SIGN_IDENTITY" ]; then
    echo "==> Signing as: $SIGN_IDENTITY"
    # --options runtime is the hardened runtime, which notarisation requires.
    codesign --force --deep --timestamp --options runtime \
        --entitlements macos/app/MyMemos.entitlements \
        --sign "$SIGN_IDENTITY" "$APP"
    SIGNED=real
else
    echo "==> No Developer ID Application certificate found; signing ad hoc."
    echo "    The DMG will work on this Mac and be refused on any other."
    codesign --force --deep --sign - "$APP"
    SIGNED=adhoc
fi

codesign --verify --strict --verbose=1 "$APP" 2>&1 | sed 's/^/    /'

# ---- package ----------------------------------------------------------------------------

echo "==> Building $DMG"
STAGE=$(mktemp -d)
cp -R "$APP" "$STAGE/"
ln -s /Applications "$STAGE/Applications"
hdiutil create -volname "MyMemos" -srcfolder "$STAGE" -ov -format UDZO -quiet "$DMG"
rm -rf "$STAGE"

if [ "$SIGNED" = "real" ]; then
    codesign --force --timestamp --sign "$SIGN_IDENTITY" "$DMG"
fi

# ---- notarise ---------------------------------------------------------------------------

NOTARY_PROFILE=${NOTARY_PROFILE:-}
if [ "$SIGNED" = "real" ] && [ -n "$NOTARY_PROFILE" ]; then
    echo "==> Notarising (this waits on Apple, usually a few minutes)"
    xcrun notarytool submit "$DMG" --keychain-profile "$NOTARY_PROFILE" --wait
    echo "==> Stapling"
    xcrun stapler staple "$DMG"
    xcrun stapler validate "$DMG" && echo "    stapled"
    spctl --assess --type open --context context:primary-signature -v "$DMG" 2>&1 | sed 's/^/    /'
else
    echo "==> Not notarising."
    [ "$SIGNED" != "real" ] && echo "    Needs a Developer ID Application certificate."
    [ -z "$NOTARY_PROFILE" ] && echo "    Needs NOTARY_PROFILE (see xcrun notarytool store-credentials)."
fi

echo
echo "Built $DMG"
ls -lh "$DMG" | awk '{print "  " $5, $9}'
