# MyMemos for macOS: plan

Drafted 9 September 2026, revised the same day once the Phase 0 spikes had run. See
`docs/MACOS_PHASE0.md` for what those proved and where they contradicted the first draft.
This is the shape of the work, the decisions already taken, and the places where I expect
to be wrong.

## Decisions taken

- **Architecture.** Convert `core-model`, `core-network`, `core-database` and `core-data` to
  Kotlin Multiplatform with an added `macosArm64` target, and write the macOS UI in SwiftUI.
  The sync engine, three-way merge, memo cipher, parsers and DTOs stay single-source. The
  Android app keeps building and shipping throughout.
- **Scope for v1.** Core app first: sign in, timeline, editor, memo detail, search, tags,
  tasks, sync, offline. Widgets, share extension, Shortcuts, reminders, review, maps, admin,
  import and export follow in a second phase.
- **Target.** macOS 15 and later, Apple silicon only, Developer ID signed and notarised, DMG
  from a direct download. Not the App Store, so no sandbox, which keeps backup, restore,
  export and import straightforward.

## Why this route and not the others

Compose Multiplatform on the desktop would have been far cheaper: the JVM runs `core-network`
and `core-data` almost untouched, and maybe seventy per cent of the Compose UI survives a
layout rework. It was rejected because a large part of what this app does is operating system
integration. WidgetKit, a share extension, Shortcuts, Services and Spotlight are all Swift
only, so "replicates all the app functionality" is not reachable from a JVM process.

A straight Swift rewrite was rejected for the opposite reason. The riskiest code in the
repository is the sync engine, the three-way merge and the memo cipher. Two independent
implementations of those, both talking to the same server and the same encrypted memos, is a
correctness hazard that never goes away.

## Where the Android code actually sits

Measured, not guessed:

| Module          | Lines | Portability |
|-----------------|-------|-------------|
| `core-model`    | 228   | No Android, but four of nine files use `java.time.Instant`. |
| `core-network`  | 921   | Retrofit and OkHttp are JVM only. 47 endpoints to move to Ktor. Proven in spike 2. |
| `core-database` | 708   | Room annotations plus one `System.currentTimeMillis()`. Proven on `macosArm64` in spike 1. |
| `core-data`     | 4270  | 24 files of 46 use an Android or JVM-only API. |
| `app`           | 7961  | Rewritten in SwiftUI regardless. |

The Android surface inside `core-data` is small and well defined: `Context`, `Uri`,
DataStore, `EncryptedSharedPreferences`, WorkManager and `android.util.Base64`, in 11 files.
The JVM surface is wider than the first draft claimed. Counting `java.*` and `javax.*` as
well it is 24 files: `javax.inject` in 17 of them (which disappears with Hilt and costs
nothing), `java.time` in 10, `java.util.UUID` in 4, `java.util.zip` in the export and import
paths, `java.io` streams, and `Locale` plus `DateTimeFormatter` for date formatting. The
`java.time` to `kotlinx-datetime` move is the real work, and it reaches into `core-model`
too.

There are 90 unit tests across `core-data` and `core-network`. They are the safety net for the
whole conversion: every phase below finishes with those tests green on both targets.

## Target module layout

```
core-model     commonMain
core-network   commonMain  + ktor darwin / okhttp engines
core-database  commonMain  (Room KMP, bundled SQLite driver)
core-data      commonMain  + androidMain / macosMain actuals
app            Android, unchanged
macos          SwiftUI app + framework produced from the KMP targets
```

This stays one repository. The whole reason for choosing a shared Kotlin core over a Swift
rewrite was that the sync engine, the merge and the cipher should have a single
implementation, and splitting the apps across repositories reintroduces that risk through
either a publish-and-consume loop or a submodule pointer. A change to the sync engine and
both user interfaces should be one commit. The Phase 0 spikes are the exception and have
been moved out to `MyMemos-spikes`, since they exist to be cited rather than shipped.

## The platform seams

Eight of these, all small. Each becomes an `expect`/`actual` pair or a plain interface with two
implementations.

| Seam | Android today | macOS |
|------|---------------|-------|
| Secret storage | `SecureTokenStore`, `PasswordSession` on `EncryptedSharedPreferences` | Keychain, one item per account, same key namespacing (`serverUrl\|userResourceName`) |
| Preferences | `AppPreferences` on DataStore with a `Context` receiver | DataStore multiplatform, backed by okio, same keys |
| File handles | `Uri` in `AttachmentStore.stage`, `MemoRepository.addAttachment`, `MarkdownImporter.import` | A `PlatformFile` carrying name, last modified and a byte source. `NSOpenPanel` and `PhotosPicker` produce it |
| Streams | `MarkdownExporter` takes an `OutputStream` | okio `Sink` on both sides |
| Base64 | `java.util.Base64` in `MemoCipher`, `android.util.Base64` in `SyncEngine` | `kotlin.io.encoding.Base64` everywhere. Watch the encoding flags: `SyncEngine` uses `NO_WRAP` |
| Crypto | javax.crypto AES-GCM plus PBKDF2 in `MemoCipher` and `BackupCrypto` | CryptoKit and CommonCrypto. Byte compatibility proven both ways in spike 4, including unicode passwords |
| Background sync | `SyncWorker` on WorkManager | `NSBackgroundActivityScheduler` plus the existing debounced in-process push |
| Dependency injection | Hilt throughout `core-data` | Strip Hilt from `core-data` down to plain constructors. Android keeps Hilt by binding them in the app module, macOS uses a hand written composition root or Koin |

Two more that are not `expect`/`actual` but still have to be solved:

- **Diff.** `ThreeWayMerge` uses java-diff-utils. Only a small part of its API is touched, so
  the answer is a pure Kotlin Myers diff in `commonMain`, validated against the existing
  `ThreeWayMergeTest`.
- **Markdown.** commonmark-java is JVM only, and rendering has to be native anyway.
  `MemoContent.kt` is 369 lines with live task checkboxes that edit the source line, so the
  Swift side needs swift-markdown or cmark-gfm plus the same source-offset trick.
- **Dates.** `java.time` reaches ten files in `core-data` and four in `core-model`.
  kotlinx-datetime covers most of it; locale-aware weekday and month names, which
  `TimelineGrouping` and the digest rely on, are thinner there than in `DateTimeFormatter`
  and may need formatting to move to the platform layer.
- **Zip.** `java.util.zip` drives export and import and has no standard multiplatform
  equivalent. Either an `expect`/`actual` pair over `java.util.zip` and Foundation, or a
  pure Kotlin zip library. The export format is already in the wild, so decide before
  Phase 5 rather than during it.

## What macOS replaces rather than ports

- Home screen widgets become WidgetKit widgets. The widget extension should read a small JSON
  snapshot written by the main app into an App Group container, not the Room database. Linking
  the Kotlin framework into a widget extension is possible but it is a lot of binary for a
  timeline provider.
- The Quick Settings tile becomes a menu bar item with a global hotkey for quick capture.
- The share sheet target becomes a Share Extension. Extensions cannot reach into the main app's
  store directly, so it drops the payload into the App Group and pings the app.
- The `am start` automation intent becomes a `mymemos://` URL scheme plus App Intents, which
  gets Shortcuts and Spotlight for free.

## Two privacy deltas to decide on

The Android app deliberately avoids Google Play Services, and map tiles are off by default.
Carry both forward:

- Reverse geocoding on Android is done without Play Services. `CLGeocoder` sends coordinates to
  Apple. Either accept that and say so in the settings copy, or call the same Nominatim style
  endpoint the Android app uses.
- Use OpenStreetMap tiles behind the same off-by-default setting rather than MapKit, so the
  behaviour and the privacy note stay identical on both platforms.

## Phases

### Phase 0: spikes, before committing to anything

**Done, 9 September 2026. Full write-up in `docs/MACOS_PHASE0.md`; the spike sources are in
the separate `MyMemos-spikes` repository.** Room KMP with FTS4 and
both auto-migrations works on `macosArm64`; Ktor on the Darwin engine reproduces the bearer,
cookie and refresh behaviour; the memo cipher is byte compatible with CryptoKit in both
directions, unicode passwords included. SKIE is the one failure: the current release does not
support Kotlin 2.4.20 and its compiler artifacts stop at the 2.2 line, so Flow bridges get
written by hand instead, at a measured cost of roughly 250 to 350 lines of glue for the
dozen or so flows the UI observes.

Two loose ends carried forward rather than blocking anything:

- Regenerate the crypto vectors from an instrumented Android test. Android does not use
  SunJCE, and a provider that encodes `char[]` as Latin-1 would break non-ASCII passwords
  across platforms.
- `BackupCrypto` streams AES-GCM through a `CipherOutputStream`; CryptoKit's GCM is one-shot.
  Either hold a backup in memory or move to chunked GCM, which is a format change on both
  platforms. Phase 5 work, but decide before writing it.

### Phase 1: `core-model` and `core-network` to KMP

`core-model` moves with no changes. `core-network` swaps Retrofit for Ktor across 47 endpoints,
keeps kotlinx.serialization, and rebuilds bearer auth, the cookie jar and token refresh as Ktor
plugins. `RefreshAuthenticatorTest` and the MockWebServer tests move to Ktor's `MockEngine`.
Done when the Android app builds and passes on the new network module.

### Phase 2: `core-database` to KMP

**Done.** Room KMP with an Android target alongside `macosArm64`, so Android goes on using
the platform's SQLite and Room's Android artifacts exactly as before and only the macOS
build gets the bundled driver. Schema and version 3 are untouched, and the exported schema
JSON is byte for byte what it was, identity hash included, so an install from the first
release still migrates. Spike 1's FTS and auto-migration tests now live in the module and
run on the macOS target.

Two things worth remembering from doing it. AGP 9 refuses to apply `com.android.library`
alongside the multiplatform plugin, so the module uses
`com.android.kotlin.multiplatform.library` and configures Android inside the `kotlin` block.
And Room's KSP dependency has to go on the target-level `kspAndroid` configuration, not the
compilation-level `kspAndroidMain`: on the latter it runs, sees none of `commonMain`,
generates nothing, and the build stays green while the app dies on launch with
`MyMemosDatabase_Impl does not exist`.

### Phase 3: `core-data` to KMP

The bulk of the conversion. Strip Hilt to plain constructors, introduce the eight seams above,
replace the diff library, move the 90 tests to `commonTest` and run them on both targets. The
Android app is rewired to the new constructors and must stay shippable at the end of this
phase. This is the phase where the schedule slips, so treat anything else as optional until it
lands.

### Phase 4: the macOS core app, version 0.1

**Started.** Credentials live in the Keychain, so a signed-in account survives a quit, and the
app restores and syncs on launch without asking again. There is a check for whether the
Keychain will take a write at all, because an application it cannot identify is refused and an
unsigned build would otherwise forget everything on quit while looking like a bug elsewhere.

Locked memos work. Kotlin/Native's CommonCrypto bindings expose PBKDF2 but no AES-GCM at all,
so the cipher's primitives are handed in from Swift, where CryptoKit does it properly.
`macos/app/check-cipher.sh` opens the same fixed vectors the Android instrumented test uses,
so a memo locked on the phone demonstrably opens on the Mac, unicode and emoji passwords
included.

Still to do: the timeline's folding headers, the editor, memo detail, search, tags and tasks,
plus the native shell the rest of this section describes.


Sign in with multiple accounts and remembered servers, timeline with its folding date headers
and compact mode, the Markdown editor with the toolbar, list continuation, tag completion and
`@` due dates, memo detail with rendered Markdown and live checkboxes, offline search, tag
filters, archive, pin, visibility, memo locking, and the sync status surface.

Native from the start rather than retrofitted: a proper menu bar, keyboard-first navigation, a
three column split view instead of the two pane tablet layout, and multiple windows.

Ships as a notarised DMG. This is the first thing worth using.

### Phase 5: everything else

Notifications, reminders and recurring templates on `UNUserNotificationCenter`; the weekly
digest; review, on this day, nearby, journey and graph; the activity heatmap; comments,
reactions, references and share links; account, tokens, webhooks and admin screens; export,
import, encrypted backup and restore; then the integration layer, meaning WidgetKit, the share
extension, App Intents and menu bar capture.

## Risks, honestly

- **Phase 3 is the whole project.** If the seams turn out messier than the grep suggests, the
  fallback is to leave `core-data` on Android and expose it over a thin local service, which
  would be a bad outcome. Watch this closely at the start of the phase, not the end.
- **The interop tax is paid every day, and SKIE is not there to soften it.** Suspend
  functions and enums cost nothing, but every observed Flow needs a concrete bridge class in
  Kotlin and a matching stream extension in Swift, and sealed classes lose exhaustiveness so
  their Swift shims need tests. Measured, not guessed: spike 3. Worth rechecking whether SKIE
  supports Kotlin 2.4 before Phase 4 starts.
- **Two apps, one server, one encryption format.** Every format touching change from here needs
  test vectors shared between the two platforms, not just unit tests on one side.
- **Devil's advocate, now resolved.** The escape hatch was to abandon KMP for a Swift rewrite
  with shared specs and golden vectors if the database or the interop spike failed. The
  database spike passed cleanly, so that hatch is closed and the KMP route stands. The
  remaining schedule risk is concentrated in Phase 3, not in the architecture.

## Open questions

- Should the Mac app read the Android encrypted backup file directly, so moving between devices
  does not need a server round trip?
- Is a single shared Room database file across the main app, the widget extension and the share
  extension worth an App Group, or is the JSON snapshot enough?
- Does the config memo (`#mymemos/config`) need a schema version bump before a second client
  starts writing to it?
