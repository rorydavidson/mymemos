# MyMemos for iOS

An iPhone and iPad client sharing its data layer with the Android and macOS apps: the same
sync engine, outbox, three-way merge, Room database and memo cipher, in `core-model`,
`core-network`, `core-database` and `core-data`. Only the interface and the platform seams
are written again, and most of the interface is shared with the Mac through `apple/ui`.

Requires iOS 17 on an iPhone or iPad. Building needs Xcode 26; XcodeGen only for the Xcode
project.

## Building and running

```bash
ios/app/build.sh                      # a simulator bundle at ios/app/build/MyMemos.app
xcrun simctl install booted ios/app/build/MyMemos.app
xcrun simctl launch booted com.keltruc.mymemos.ios
```

`build.sh` works the way `macos/app/build.sh` does: Gradle builds the Kotlin framework for
`iosSimulatorArm64`, `swiftc` compiles `apple/ui` plus `ios/app/Sources` against the
simulator SDK, `actool` compiles the icon, and the share extension and widget are compiled
into `PlugIns`. It needs the SDK and nothing else.

That matters because `xcodebuild` refuses to build for iOS at all until a simulator runtime
matching the SDK has been downloaded (Xcode 26.6 wants iOS 26.5; a machine with only 26.2
gets "iOS 26.5 is not installed"). The script does not care: the app it builds runs on
whichever runtime is there.

`ios/app/project.yml` describes the same app, its extensions and the Gradle run-script phase
for XcodeGen, for working in Xcode and for a device. Generate it with `xcodegen generate`;
the `.xcodeproj` is git-ignored. A device needs a signing team: set `DEVELOPMENT_TEAM` in
`project.yml` or in Xcode's Signing pane. Xcode writes the Keychain and app-group
entitlements itself; the script build links `Simulator.entitlements` into the binary as a
`__TEXT,__entitlements` section, because iOS refuses Keychain access to an app with no
application identifier even on the simulator, and launchd refuses an ad hoc signature that
carries one.

## Looking at it without touching it

```bash
MEMOS_SERVER=http://localhost:5230 MEMOS_USER=tester MEMOS_PASSWORD=… ios/app/tour.sh
```

Nothing on a build machine can tap a simulator, and the accessibility route needs
permissions an agent session does not have. `tour.sh` builds with `TESTHOOKS=1`, which
compiles in `TestHooks.swift`: a driver that reads `MYMEMOS_TEST_SIGNIN` and
`MYMEMOS_TEST_STEPS` from the launch environment and works the session model directly, so
each screen can be launched into and screenshotted from outside. The steps cover opening,
editing, ticking, reacting, commenting, colouring, archiving, deleting, the library screens
and a `mymemos://` URL. Screenshots land in `ios/app/build/tour/`. The bundle it installs is
not one to keep: no installable build has the hooks.

Use a throwaway server. A Memos v0.30 container on `localhost:5230` is what this was built
against; the app allows plain http to local addresses (`NSAllowsLocalNetworking`) for that
and for a server on the home network, and nothing else.

## What it does

Everything the Android app does except export, import and encrypted backup. Sign in with
several accounts and remembered servers; the timeline with folding headers, compact rows,
sort by last changed, search, tag filters and the archive; the editor with the formatting
bar, list continuation, `#tag` and `@date` completions, templates and photos; rendered
Markdown with live checkboxes; pin, visibility, colours, archive and delete with Undo;
locked memos; attachments; location on request, and Nearby; comments, reactions, references
with backlinks and public share links; tasks with due dates; reminders, recurring templates
and the weekly digest as notifications, a tap opening the memo; review with the streak, the
heatmap, day by day with keep or archive, on this day, journey and the graph; shortcuts;
the sync sheet with failed operations, conflict copies and signing in again; tag emoji and
colours; the profile, password, default visibility, tokens, webhooks, notifications and
statistics; for an admin, users and instance settings; background refresh; a share
extension; recent-memos and open-tasks widgets; and `mymemos://new?content=…` and
`mymemos://memo/<id>` for automation, the shape of the Android intent.

On an iPad it is three columns, as on the Mac. On a phone it is the three tabs Android has,
with the library behind the menu on the memos tab.

## Getting it onto your own iPhone and iPad

The simulator bundle cannot go on a device: it is built for the simulator's architecture
and signed by nobody. A device build goes through Xcode, and Xcode needs three things this
repository cannot supply.

1. **The iOS platform in Xcode.** Xcode › Settings › Components, download iOS 26.5 (the
   version matching Xcode 26.6's SDK). Until it is there `xcodebuild` refuses every iOS
   destination, device included; that is the same wall the script build steps round for
   the simulator.
2. **An Apple ID signed into Xcode**, under Xcode › Settings › Accounts. A free Apple ID
   gives a "Personal Team" that can sign for your own devices, with limits that matter here:
   the app expires after seven days and has to be run from Xcode again, and a personal team
   **cannot use App Groups**, which the share extension and the widgets need. On a free
   team, build the app target alone and leave the two extensions out. The Apple Developer
   Program (99 USD a year) removes both limits and is required for TestFlight and the
   App Store anyway.
3. **Developer Mode on the phone** (Settings › Privacy & Security › Developer Mode, iOS 16
   and later) and, the first time, trusting the Mac when the phone asks.

Then:

```bash
brew install xcodegen                 # once
# put your team id in ios/app/project.yml: DEVELOPMENT_TEAM: "ABCDE12345"
cd ios/app && xcodegen generate && open MyMemos.xcodeproj
```

In Xcode pick your iPhone (or iPad) as the run destination and press Run. Xcode builds the
Kotlin framework through the project's run-script phase, registers the bundle ids, makes
the provisioning profiles, and installs. The bundle ids are `com.keltruc.mymemos.ios` and
two suffixed ones for the extensions; if they clash with someone else's registration change
`bundleIdPrefix` and the three identifiers in `project.yml`. The app group id
`group.com.keltruc.mymemos` in `Extension.entitlements`, `project.yml` and `AppGroup.swift`
has to change with them.

The Keychain, app group and background-refresh entitlements are written by Xcode from the
capabilities the project declares; `Simulator.entitlements` is not used for a device.

## TestFlight and the App Store

Everything above with a paid team, plus the paperwork. In order:

1. **App Store Connect.** Create the app record: name, primary language, bundle id (the
   app's; the extensions ride along), SKU. The name "MyMemos" may already be taken on the
   store; the display name can differ from the record's name.
2. **Archive.** Xcode › Product › Archive with "Any iOS Device" selected, then Distribute
   App › App Store Connect. `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` in
   `project.yml` are the version and build; every upload needs a higher build number.
3. **TestFlight.** The uploaded build appears after processing; add yourself as an internal
   tester and it is on your phone through the TestFlight app within minutes, with a 90-day
   life and no seven-day re-signing. This is the right place to stop for a personal app.
4. **The privacy manifest** is already in the bundle (`Resources/PrivacyInfo.xcprivacy`).
   It declares no tracking and no collected data, on the grounds that everything goes to a
   server the user chose, and lists the two API categories the app touches. Read it before
   submitting and change it if you disagree with that reading.
5. **Export compliance.** `ITSAppUsesNonExemptEncryption` is already `false`, but the app
   does encrypt locked memos with AES-GCM. Standard algorithms used for data protection are
   exempt from the export documentation Apple asks about, and that is what `false` claims;
   it may still oblige a yearly self-classification report to the US Bureau of Industry and
   Security. Decide that with the rules in front of you rather than on this paragraph.
6. **The listing.** Screenshots for the 6.9-inch iPhone and 13-inch iPad (the tour's
   `ios/app/build/tour/*.png` are the right size for the phone), a 1024 icon (already in
   the asset catalogue), a description leading with offline-first and self-hosted, a
   support URL and a privacy policy URL saying the same things the Play listing says: data
   goes only to the server the user configures, credentials sit in the Keychain, nothing is
   collected. Category Productivity, age rating from the questionnaire (it comes out 4+).
7. **App Review.** The reviewer needs a Memos server to sign in to: give a throwaway account
   on a test instance, never your own, in the review notes, and say what the `mymemos://`
   scheme is for so it does not look undeclared. The share extension and widgets need no
   extra declaration. Location and notifications already have their usage strings.

Google Sans Flex under the OFL, commonmark's absence on iOS (the Swift renderer is the
app's own), OpenStreetMap tiles behind an off-by-default switch: none of these needs a
disclosure beyond the credits already in the app.

## Not built, and why

- **Export, import and encrypted backup.** The exporter and importer live in
  `core-data/androidMain` on `java.util.zip` and `java.time`, and the backup streams AES-GCM
  in a way CryptoKit cannot. Moving them needs an `expect`/`actual` zip and a format
  decision, which is its own piece of work. The Mac lacks them for the same reason.
- **Dynamic colour**, which is an Android 12 wallpaper feature with no iOS equivalent.
- **Geofenced reminders**, which Android does not have either.

## Not verified on a screen

Everything above was built, launched and screenshotted on an iPhone 17 Pro and an iPad Pro
simulator against a local Memos v0.30 server, through the tour. What the tour cannot reach:

- **The share extension in a real share sheet.** The extension is registered (pluginkit
  lists it) and the handoff was proven by writing an item into the app group's inbox and
  watching the editor open with it; the sheet itself was never shown, since nothing can
  share into the simulator from another app without tapping.
- **The widgets on a home screen.** They compile, are registered, and the snapshot they read
  is written with the right contents; adding a widget needs a long press nobody could make.
- **Background refresh actually running.** iOS decides when; the design does not depend on
  it. `xcrun simctl` cannot trigger a BGAppRefreshTask from outside Xcode's debugger.
- **A photo attached from the picker, and location capture.** Both need a tap on a system
  sheet. The code paths after the tap are the same ones the file importer and the Kotlin
  side already exercise.
- **The cipher on a phone.** The Mac's `check-cipher.sh` proves the CryptoKit half agrees
  with Android, and the same Swift is compiled here, but no locked memo has been opened on
  iOS yet.

## The plan, as it turned out

Drafted 11 September 2026 and built the same day in five phases. The decisions:

- **One framework for every Apple target.** `macos/shared` became `apple/shared`
  (`:apple-shared`), one `appleMain` source set built for `macosArm64`, `iosArm64` and
  `iosSimulatorArm64`. The `core-*` modules gained the two iOS targets and their macOS
  actuals moved to `appleMain`; only the device name that labels a minted token kept a
  per-platform actual. `MacCrypto` became `HostCrypto`.
- **Shared SwiftUI where a view is the same view.** `apple/ui` holds the session model,
  theme, Markdown renderer, map, every list and sheet; `#if os(macOS)` marks the few AppKit
  and UIKit differences, and `Platform.swift` names the recurring ones.
- **Location is allowed on iOS**, on request, as on Android, though the Mac still never asks.
- **A phone's editor** wraps `UITextView` with smart quotes and dashes off and autocorrect
  left on, the same trade the Android editor makes; the shared continuation logic copes with
  autocorrect recasing a word.
- **Kotlin property names.** `description` cannot be used: Kotlin/Native exports it under
  another name because `NSObject` has one, and Swift silently reads the object dump.
- **Suspend functions returning Bool or Int** arrive in Swift boxed (`KotlinBoolean`,
  `KotlinInt`), so every such call is unwrapped with `as? Bool` or `Int(truncating:)`.

Corrections to the first draft: Xcode's destination check (above) turned the Xcode project
from the build into an option; and CoreSimulator was so slow to answer `simctl list` on this
machine that the device ids were taken from `~/Library/Developer/CoreSimulator/Devices`.
