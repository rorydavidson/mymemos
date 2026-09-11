#!/bin/bash
# Builds the iOS app for the simulator into a .app bundle, the same way macos/app/build.sh
# builds the Mac: Gradle for the Kotlin framework, swiftc for the Swift, no Xcode project in
# the loop. The generated Xcode project (project.yml) is for working in Xcode and for
# devices; this script is what CI and `run.sh` use, and it only needs the SDK, not a matching
# simulator runtime download.
set -euo pipefail
cd "$(dirname "$0")/../.."

JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home} \
    ./gradlew :apple-shared:linkDebugFrameworkIosSimulatorArm64 --console=plain -q

FRAMEWORK_DIR="apple/shared/build/bin/iosSimulatorArm64/debugFramework"
SDK=$(xcrun --sdk iphonesimulator --show-sdk-path)
SDK_VERSION=$(xcrun --sdk iphonesimulator --show-sdk-version)
OUT="ios/app/build"
APP="$OUT/MyMemos.app"

rm -rf "$APP"
mkdir -p "$APP"

# Google Sans Flex, the same file the Android and Mac apps ship, under the same licence.
cp ios/app/Resources/GoogleSansFlex.ttf "$APP/"
cp ios/app/Resources/GOOGLE_SANS_FLEX_OFL.txt "$APP/"

# The icon, compiled from the asset catalogue the Xcode project also uses.
xcrun actool ios/app/Resources/Assets.xcassets \
    --compile "$APP" \
    --platform iphonesimulator \
    --minimum-deployment-target 17.0 \
    --app-icon AppIcon \
    --output-partial-info-plist "$OUT/icon.plist" \
    --notices --warnings --errors >/dev/null || true
# actool also grumbles when no simulator runtime matches the SDK, while still writing the
# files. What matters is that they were written.
[ -f "$OUT/icon.plist" ] || { echo "actool produced no icon plist" >&2; exit 1; }

cat > "$APP/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key><string>MyMemos</string>
    <key>CFBundleDisplayName</key><string>MyMemos</string>
    <key>CFBundleIdentifier</key><string>com.keltruc.mymemos.ios</string>
    <key>CFBundleExecutable</key><string>MyMemos</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>0.1.0</string>
    <key>CFBundleVersion</key><string>1</string>
    <key>CFBundleSupportedPlatforms</key><array><string>iPhoneSimulator</string></array>
    <key>DTPlatformName</key><string>iphonesimulator</string>
    <key>DTSDKName</key><string>iphonesimulator${SDK_VERSION}</string>
    <key>DTPlatformVersion</key><string>${SDK_VERSION}</string>
    <key>MinimumOSVersion</key><string>17.0</string>
    <key>UIDeviceFamily</key><array><integer>1</integer><integer>2</integer></array>
    <key>UILaunchScreen</key><dict/>
    <key>UIRequiredDeviceCapabilities</key><array><string>arm64</string></array>
    <key>UISupportedInterfaceOrientations</key>
    <array>
        <string>UIInterfaceOrientationPortrait</string>
        <string>UIInterfaceOrientationLandscapeLeft</string>
        <string>UIInterfaceOrientationLandscapeRight</string>
    </array>
    <key>UISupportedInterfaceOrientations~ipad</key>
    <array>
        <string>UIInterfaceOrientationPortrait</string>
        <string>UIInterfaceOrientationPortraitUpsideDown</string>
        <string>UIInterfaceOrientationLandscapeLeft</string>
        <string>UIInterfaceOrientationLandscapeRight</string>
    </array>
    <key>LSApplicationCategoryType</key><string>public.app-category.productivity</string>
    <key>NSHumanReadableCopyright</key><string>Bundles Google Sans Flex under the SIL Open Font License 1.1.</string>
    <!-- Registers the bundled font, so the app reads the same as the Android one. -->
    <key>UIAppFonts</key><array><string>GoogleSansFlex.ttf</string></array>
    <key>NSLocationWhenInUseUsageDescription</key>
    <string>Your location is read only when you choose to add it to a memo, and is stored with that memo on your own server.</string>
    <!-- A self-hosted server on the home network is often plain http. Only local addresses. -->
    <key>NSAppTransportSecurity</key><dict><key>NSAllowsLocalNetworking</key><true/></dict>
    <key>UIBackgroundModes</key><array><string>fetch</string></array>
    <!-- mymemos://new?content=... and mymemos://memo/<id>, the Android automation intent's shape. -->
    <key>CFBundleURLTypes</key>
    <array><dict>
        <key>CFBundleURLName</key><string>com.keltruc.mymemos</string>
        <key>CFBundleURLSchemes</key><array><string>mymemos</string></array>
    </dict></array>
    <key>BGTaskSchedulerPermittedIdentifiers</key><array><string>com.keltruc.mymemos.ios.refresh</string></array>
</dict>
</plist>
PLIST
# actool tells us what the icon is called; fold that in.
/usr/libexec/PlistBuddy -c "Merge $OUT/icon.plist" "$APP/Info.plist"
printf 'APPL????' > "$APP/PkgInfo"

# TESTHOOKS=1 compiles in the launch-environment driver that tour.sh uses. Never for a build
# anyone installs.
EXTRA_FLAGS=()
[ "${TESTHOOKS:-0}" = "1" ] && EXTRA_FLAGS+=(-D TESTHOOKS)

swiftc -O ${EXTRA_FLAGS[@]+"${EXTRA_FLAGS[@]}"} \
    -target arm64-apple-ios17.0-simulator \
    -sdk "$SDK" \
    -F "$FRAMEWORK_DIR" \
    -framework Shared \
    -lsqlite3 \
    -parse-as-library \
    -Xlinker -sectcreate -Xlinker __TEXT -Xlinker __entitlements -Xlinker ios/app/Simulator.entitlements \
    apple/ui/*.swift \
    ios/app/Sources/*.swift \
    -o "$APP/MyMemos"

# The share extension and the widget, each its own bundle under PlugIns. Neither links the
# Kotlin framework: they talk to the app through files in the app group.
build_extension() {
    local name=$1 dir=$2 point=$3 principal=$4 extra=$5
    local appex="$APP/PlugIns/$name.appex"
    mkdir -p "$appex"
    cat > "$appex/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key><string>$name</string>
    <key>CFBundleDisplayName</key><string>MyMemos</string>
    <key>CFBundleIdentifier</key><string>com.keltruc.mymemos.ios.$(echo "$name" | tr 'A-Z' 'a-z')</string>
    <key>CFBundleExecutable</key><string>$name</string>
    <key>CFBundlePackageType</key><string>XPC!</string>
    <key>CFBundleShortVersionString</key><string>0.1.0</string>
    <key>CFBundleVersion</key><string>1</string>
    <key>CFBundleSupportedPlatforms</key><array><string>iPhoneSimulator</string></array>
    <key>DTPlatformName</key><string>iphonesimulator</string>
    <key>DTSDKName</key><string>iphonesimulator${SDK_VERSION}</string>
    <key>MinimumOSVersion</key><string>17.0</string>
    <key>UIDeviceFamily</key><array><integer>1</integer><integer>2</integer></array>
    <key>NSExtension</key>
    <dict>
        <key>NSExtensionPointIdentifier</key><string>$point</string>
        $extra
    </dict>
</dict>
</plist>
PLIST
    # A share extension is entered through Foundation's NSExtensionMain; a widget's @main is
    # its own entry point.
    local entry=()
    [ -n "$principal" ] && entry=(-Xlinker -e -Xlinker "$principal")
    swiftc -O \
        -target arm64-apple-ios17.0-simulator \
        -sdk "$SDK" \
        -parse-as-library \
        -application-extension \
        ${entry[@]+"${entry[@]}"} \
        -Xlinker -sectcreate -Xlinker __TEXT -Xlinker __entitlements -Xlinker ios/app/Simulator.entitlements \
        apple/ui/AppGroup.swift \
        "$dir"/Sources/*.swift \
        -o "$appex/$name"
    codesign --force --sign - "$appex" 2>/dev/null
}

build_extension MyMemosShare ios/share com.apple.share-services _NSExtensionMain \
    "<key>NSExtensionPrincipalClass</key><string>ShareViewController</string>
        <key>NSExtensionAttributes</key><dict><key>NSExtensionActivationRule</key><dict>
            <key>NSExtensionActivationSupportsText</key><true/>
            <key>NSExtensionActivationSupportsWebURLWithMaxCount</key><integer>1</integer>
            <key>NSExtensionActivationSupportsImageWithMaxCount</key><integer>10</integer>
        </dict></dict>"

build_extension MyMemosWidget ios/widget com.apple.widgetkit-extension "" ""

# Ad hoc is enough for the simulator. The Keychain wants an application identifier, which is
# why the entitlements above are linked into the binary the way Xcode does it for simulator
# builds; putting them in the signature instead makes launchd refuse to spawn the app.
codesign --force --sign - "$APP" 2>/dev/null

echo "built $APP"
