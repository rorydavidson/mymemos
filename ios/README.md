# MyMemos for iOS

An iPhone and iPad client sharing its data layer with the Android and macOS apps: the same
sync engine, outbox, three-way merge, Room database and memo cipher, in `core-model`,
`core-network`, `core-database` and `core-data`. Only the interface and the platform seams
are written again, and much of the interface is shared with the Mac.

Requires iOS 17 on an iPhone or iPad. Building needs Xcode 26 and XcodeGen.

## Building and running

```bash
ios/app/build.sh              # generates the project and builds for the simulator
open ios/app/MyMemos.xcodeproj
```

`ios/app/project.yml` is the source of truth for the Xcode project, which is generated and
git-ignored. The Kotlin framework is built from a run-script phase in the project through
Gradle's `embedAndSignAppleFrameworkForXcode`, so opening the project in Xcode and pressing
Run is enough.

Running on a device needs a signing team: set `DEVELOPMENT_TEAM` in `project.yml` or in
Xcode's Signing pane. The simulator needs none.

## The plan, and what it decided

Drafted 11 September 2026. The aim is an iPhone and iPad client with the Android app's
features, as far as the platform allows, built the way the macOS client was: the same Kotlin
data layer underneath, SwiftUI on top. Corrections are recorded here as each phase lands, the
way `docs/MACOS_PLAN.md` did it.

### Decisions taken

- **One framework for every Apple target.** `macos/shared` became `apple/shared`
  (`:apple-shared`), built for `macosArm64`, `iosArm64` and `iosSimulatorArm64` from a single
  `appleMain` source set. Nothing in it was specific to a Mac: Foundation, the Security
  framework, Room's bundled driver and DataStore behave the same on a phone. The four
  `core-*` modules gained the two iOS targets, and their `macosArm64Main` actuals moved to
  `appleMain`. The one genuine difference, the device name used to label a minted token,
  keeps a per-platform actual. `MacCrypto` is now `HostCrypto`, because it is no longer the
  Mac's.
- **Shared SwiftUI where a view is the same view.** `apple/ui/` holds the Swift the two apps
  have in common: the session model, theme, Markdown renderer, map, tasks, review, reminders,
  templates, notifications and the CryptoKit half of the cipher. Where AppKit and UIKit
  disagree the file says so with `#if os(macOS)`. The macOS app compiles `apple/ui` plus its
  own `macos/app/Sources`; the iOS app compiles `apple/ui` plus `ios/app/Sources`. This is the
  same rule the Kotlin side follows: one implementation of anything that decides something.
- **Xcode project generated, not committed.** `project.yml` drives XcodeGen; the `.xcodeproj`
  is ignored, so there is no merge conflict magnet in the repository.
- **Simulator first, device by Team ID.** Nothing in this repository can sign for a device.
- **iOS 17 and later.** `NavigationSplitView` on iPad, `NavigationStack` on iPhone, and the
  `PhotosPicker` the editor wants all settle at 17.
- **Location is allowed on iOS.** The macOS client refuses to ask the machine where it is,
  which was the right call for a desktop. The Android app captures a location on request and
  offers Nearby, and the phone is the device where that makes sense, so iOS matches Android:
  CoreLocation on demand, reverse geocoded with `CLGeocoder`, nothing in the background. Map
  tiles stay OpenStreetMap behind the same off-by-default switch.

### What Android has, and where iOS gets it

| Android feature | Shared code | iOS |
|---|---|---|
| Sign in, several accounts, remembered servers | `AccountRepository`, `AppPreferences` | Phase C, D |
| Timeline with folding headers, compact rows, sort by modified | `TimelineGrouping`, `MemoTitle` | Phase C |
| Search, tag filter, archive view | `MemoRepository` | Phase C, D |
| Editor: toolbar, list continuation, tag and `@date` completion, templates | `MarkdownContinuation`, `DueDateParser.suggest` | Phase C |
| Rendered Markdown with live checkboxes | `TaskLine` | Phase C |
| Pin, visibility, archive, delete with undo, colours | `MemoRepository` | Phase C, D |
| Locked memos | `MemoCipher`, CryptoKit provider | Phase C |
| Attachments from the photo picker and files | `AttachmentStore` | Phase C |
| Location capture and clear | `MemoRepository.setLocation` | Phase D |
| Comments, reactions, references, backlinks, share links | `MemoRepository`, `ShareRepository` | Phase D |
| Tasks screen with due dates | `TaskLine`, `DueDateParser` | Phase C |
| Reminders, recurring templates, weekly digest | `ConfigRepository`, `Schedule`, `Digest` | Phase C |
| Review: streak, heatmap, on this day, nearby, journey, graph | `MemoRepository` | Phase C, D |
| Shortcuts (CEL filters) | `ShortcutRepository` | Phase D |
| Sync status, failed ops, conflicts | `SyncState` | Phase D |
| Tag styles (emoji and colour) | `ConfigRepository.setTagStyle` | Phase D |
| Account: profile, password, default visibility, tokens, webhooks, notifications, stats | `AccountSettingsRepository` | Phase D |
| Admin: users, instance settings | `AccountSettingsRepository` | Phase D |
| Background sync | `BackgroundSync` seam | Phase E, `BGAppRefreshTask` |
| Share sheet target | Android share intent | Phase E, share extension |
| Widgets and quick capture | Glance widgets | Phase E, WidgetKit |
| Automation intent | `CREATE_MEMO` | Phase E, `mymemos://` URL scheme |
| Export, import, encrypted backup | Android-only `java.util.zip` code | Not in this plan; see below |

### Layout

```
apple/shared     :apple-shared, the Kotlin framework, appleMain only
apple/ui         SwiftUI shared by both apps
macos/app        the Mac app: build.sh, checks, Mac-only views
ios/app          the iOS app: project.yml, iOS-only views, resources
```

### Phases

- **A: the framework builds for iOS.** Targets added, actuals moved, module renamed. Done
  when `:apple-shared:linkDebugFrameworkIosSimulatorArm64` produces a framework and
  `macos/app/build.sh` still produces a working Mac app.
- **B: the shared SwiftUI.** The portable files move to `apple/ui`. Done when the Mac app
  builds and its four CI checks pass.
- **C: the iOS app, first cut.** Everything the Mac app does today, on a phone. Done when it
  builds for the simulator and can be driven through sign in, sync, write and read.
- **D: the rest of Android.** The `MemosSession` surface grows to cover what `core-data`
  already knows how to do. Each is a small Kotlin addition and a screen. The Mac gains the
  Kotlin side for free and can pick up the screens later.
- **E: what only a phone can do.** Background refresh, the share extension, a widget, a URL
  scheme.

### Risks and things left out

- **Export, import and backup** stay Android-only. The exporter and importer live in
  `core-data/androidMain` on `java.util.zip` and `java.time`. Moving them to `commonMain`
  needs an `expect`/`actual` zip, and the encrypted backup streams AES-GCM in a way CryptoKit
  cannot. Both are design decisions rather than typing, and belong in their own piece of work.
- **The editor on a phone.** `UITextView` behaves differently from `NSTextView` around
  Return: the shared continuation logic runs after the newline lands, which is the same on
  both, but autocorrect and smart punctuation have to be switched off or Markdown is
  corrupted the same way the Mac editor found.
- **A background task never runs when you want it to.** `BGAppRefreshTask` is at the
  system's discretion. The design already copes: the outbox pushes on the next launch, and
  reminders are scheduled notifications rather than background work.
- **Three apps, one server, one encryption format.** The cipher parity check should be run on
  iOS too before locked memos are trusted there.
