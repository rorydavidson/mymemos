# MyMemos handoff

State as of 11 September 2026, evening. The iOS work is on `feat/ios-app`, unmerged, in
nine commits; `main` is as it was in the morning.

There are now three clients over one data layer: the Android app, which is feature complete
against Memos v0.30; an iOS app, which matches it except for export, import and backup; and
a macOS app, which does the daily work. Almost everything that is not a screen is shared
between them, and since the iOS work most of the screens are shared between the Mac and the
phone too.

**The iOS commits are unsigned.** 1Password's SSH signer refused every commit in that
session ("failed to fill whole buffer"), so they were made with `commit.gpgsign=false`.
Re-sign before merging if signed history matters:
`git rebase --exec 'git commit --amend --no-edit -S' main`.

The one piece of Android work still outstanding is item 3 below, locked notes keeping a
readable title. Two shared fixes from the same day matter to Android too: `#follow-up` style
tags were extracted as `follow` on the Apple platforms (Kotlin/Native's regex engine read the
trailing hyphen as a range), and the exporter, import parser and their tests now live in
`commonMain`; Android's behaviour is unchanged and its tests moved with them. It was deliberately left until last because it changes a stored format, and it
now has three places waiting on it rather than two.

## Where things stand

- **243 unit tests pass** on `main`, and `:app:lintDebug` reports no errors.

  | Source set | Tests | Runs on |
  | --- | --- | --- |
  | `core-data/androidHostTest` | 138 | JVM |
  | `core-data/commonTest` (as `macosArm64Test`) | 89 | Kotlin/Native |
  | `core-network` | 7 each on JVM and native | both |
  | `core-database/macosArm64Test` | 2 | Kotlin/Native |

  The native numbers include everything in `commonTest`, so a shared test written once is run
  twice, against two quite different compilers. That has already caught real differences.

- **Six modules**: `app`, `core-model`, `core-network`, `core-database`, `core-data`,
  `apple-shared` (at `apple/shared`). The four `core-*` modules are Kotlin Multiplatform and
  build for Android, `macosArm64`, `iosArm64` and `iosSimulatorArm64`. `app` is Android only;
  `apple-shared` builds the `Shared` framework for the three Apple targets from one
  `appleMain` source set.

- **CI** is `.gitea/workflows/ci.yml` with an identical mirror at `.github/workflows/ci.yml`.
  Two jobs: `android` on Linux, and `macos`, which builds the Mac app, its checks and now the
  iOS simulator bundle, and **needs a runner labelled `macos-latest`**.
  Apple targets cannot be cross-compiled, so without such a runner that job waits rather than
  fails. That is easy to misread as passing, so check it is actually running.

- Note colours sync as a trailing `#colour/<name>` line, hidden in both apps.

- Password sign-in mints a personal access token per device, good for 90 days, and revokes it
  on sign out. If a device loses that token or it expires, the app shows a "Sign in again"
  banner rather than failing quietly.

## The web client

Built on 8 October 2026 on `feature/web-app`, so iPhones can use MyMemos without the App
Store. `web/README.md` covers running, building and what a browser cannot do. What a reader of
this file needs on top:

- **The rule files are shared by path.** `web/core/build.gradle.kts` lists which `core-data`
  files it compiles (`sharedLogic`) and which tests come with them. A new pure rule file is
  shared by adding it to that list. A file that imports Room cannot be, which is why tag
  extraction, the export format and template expansion were lifted into `Tags`,
  `MarkdownFormat` and `Templates` with the old call sites delegating.
- **Write regexes JavaScript can read.** No inline flags such as `(?m)`, no `\A`, no
  `DOT_MATCHES_ALL`, and escape a literal `]`. `./gradlew :web-core:test` runs the shared tests
  under Node and catches these.
- **`WebSyncEngine` and `WebMemos` are ports**, not shared code, because the originals are
  written against Room DAOs. Change `SyncEngine` or `MemoRepository` and change these to match.
  One deliberate difference: an absorbed server memo with ops still queued keeps local tags,
  references and location too, not just content, pin, visibility and state.
- **`-PwebOnly`** configures only `core-model`, `core-network` and `web-core`, so the image
  builds without an Android SDK.
- **Verified** against `neosmemo/memos:0.30.0` in Docker, through the dev server and through
  the built container under its CSP: sign-in mints and names a token, create, tick, lock,
  unlock, export, backup and restore, and a three-way merge with a concurrent server edit.
  Not verified: a real iPhone home-screen install, the service worker (it only registers on
  HTTPS), Web Notifications, and attachments.

## The iOS client

Built on 11 September 2026 over the same data layer, in one session, on branch
`feat/ios-app`. `ios/README.md` has the build route, the tour, what it does, what it does not
do and why, and what was never seen on a screen. Read that first; what follows is what a
reader of this file needs to know that it does not say.

### How it relates to the Mac

The iOS work restructured the Apple side: `macos/shared` became `apple/shared` and the
portable SwiftUI moved from `macos/app/Sources` to `apple/ui`, so the Mac app now compiles
`apple/ui/*.swift` plus its own `macos/app/Sources`. Everything the Mac did before still
works and every check passes, but the Mac has not yet picked up the screens the phone gained
(archive, shortcuts, tags, sync status, accounts, the account and admin screens, nearby is
deliberately absent). The Kotlin side of all of them is in `MemosSession` and the views are
in `apple/ui`, so wiring them into the Mac's sidebar and menu bar is the next Mac task and
should be a small one.

### Things that will bite

- **Kotlin property names.** Never call a property `description` on a class Swift will see.
  Kotlin/Native exports it as `description_` and `row.description` in Swift is `NSObject`'s,
  the object dump. Three rows had to be renamed (`about`, `label`).
- **xcodebuild refuses iOS without a matching runtime.** See `ios/README.md`. The script
  build is the one CI runs; the Xcode project is for people, and for devices.
- **Entitlements on the simulator** have to be a linker section, not part of the signature.
- **CoreSimulator can hang `simctl list`** for minutes on this Mac. Device ids are on disk
  under `~/Library/Developer/CoreSimulator/Devices/*/device.plist`.
- **A Kotlin exception crossing into Swift ends the process** unless the suspend function
  carries `@Throws(Throwable::class)`. Every public suspend function on `MemosSession` now
  does. Before that, every `try? await session.…` in the Swift was catching nothing.
- **Nothing can tap the simulator from an agent session.** The native panel needs
  `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`, and `osascript` is
  denied assistive access. `tour.sh` and its `TESTHOOKS` driver exist because of this.
- **A test server is the way to test sign-in.** `neosmemo/memos:0.30.0` in Docker on
  `localhost:5230`; create the first user with `POST /api/v1/users` (role HOST), sign in with
  `POST /api/v1/auth/signin`, which returns a bearer `accessToken`. Each tour run mints a
  fresh personal access token on that server and never revokes it, which is harmless there
  and would be untidy on a real one.

### Not verified

The list in `ios/README.md`, including a backup restored across platforms, plus one thing
worth a real phone: whether `thisDevice` ever
comes up true in the tokens list. Every token the tour minted showed false, which suggests
`mintedTokenName()` is not being stored on sign-in against a v0.30 server, or the name the
server returns at creation does not match the one it lists. Android has the same code path,
so check it there first.

## The macOS client

Built over four phases in September 2026. `macos/README.md` covers building, signing,
packaging and the look; `docs/MACOS_PLAN.md` has the phase plan with corrections recorded as
each one landed, and `docs/MACOS_PHASE0.md` the spike results that decided the approach.

### What it does

Sign in, timeline with the shared grouping, search over FTS, tags, an editor with a formatting
bar, attachments, locked memos, tasks, review, journey on OpenStreetMap tiles, the native menu
bar, settings, a light and dark switch, reminders, recurring templates, the weekly digest, and
a compact list. The app icon and Google Sans Flex are the Android ones.

### What is shared, and what is not

The rule that kept this honest: **anything that decides something is shared; anything that
writes words or draws pixels is not.** So `TimelineGrouping` decides which bucket a memo falls
into and `AppleTimelineLabels` writes "Monday 31 August" with NSDateFormatter. `Digest` counts
the week and `AppleDigestLabels` phrases it. `Schedule` works out when a daily template next
comes round; the app sets the alarm.

Behind the macOS UI is the real data layer: the same sync engine, outbox, three-way merge and
Room database, not a reimplementation. `MemosSession` in `apple-shared` is the whole Swift
facing surface, deliberately plain classes and lists because Swift cannot extend a generic
Kotlin type and only gets `suspend` as `async` off a plain class.

### Things that will bite

- **No SKIE.** It does not support Kotlin 2.4.20; its ceiling is 2.2.x. So no sealed class
  exhaustiveness and no `Flow` bridging in Swift, which is why `MemosSession` returns snapshots
  from `suspend` functions rather than exposing flows.
- **Kotlin/Native rejects commas in backticked names.** A `commonTest` name that reads well on
  the JVM will fail to compile for the native target with "Name contains illegal characters".
- **Room's KSP must be wired at target level** (`kspAndroid`), not compilation level
  (`kspAndroidMain`). Getting that wrong generates nothing, the build stays green, and the app
  crashes at launch. It was only found by installing the APK.
- **A KMP module has no `test` task.** `./gradlew test` silently skipped whole modules twice
  before each one got a `test` task registered. If a module's tests suddenly "pass" very fast,
  check they ran at all.
- **AGP 9 rejects `com.android.library` with KMP**; the multiplatform modules use
  `com.android.kotlin.multiplatform.library`.
- **`platforms;android-37` does not exist.** Platform packages carry a minor version now, so it
  is `platforms;android-37.0`. The wrong name installs nothing without complaining.
- **Kotlin/Native exposes PBKDF2 but no AES-GCM**, so the macOS cipher half is CryptoKit and
  CommonCrypto in Swift (`AppleCrypto.swift`), reached through an `expect`/`actual`.
  `check-cipher.sh` exists because that is exactly the kind of thing that silently diverges.

### Signing and the Keychain

The Keychain scopes credentials by code identity, so every rebuild of an ad-hoc signed app
looked like a different application asking for someone else's password, and macOS put up a
dialog the app then blocked on. `macos/app/make-signing-cert.sh` creates a self-signed
certificate that gives a stable designated requirement, and `build.sh` uses it when present.

If that script ever needs rerunning: `security import` fails with OpenSSL 3 defaults. It needs
`-legacy -keypbe PBE-SHA1-3DES -certpbe PBE-SHA1-3DES -macalg sha1` and a non-empty password.

There is no notarisation and no Developer ID. Rory asked for local signing only, since the app
is for his own machine.

### Checks

Seven scripts under `macos/app/`, for things whose failure is silent rather than loud:

```bash
macos/app/check-cipher.sh         # a memo locked on Android opens here, and back again
macos/app/check-font.sh           # the bundled font is present, licensed and resolvable
macos/app/check-appearance.sh     # light, dark and system map and persist correctly
macos/app/check-editor.sh         # Return inside a list continues it
macos/app/check-tiles.sh          # no map tile is fetched while previews are off
macos/app/check-notifications.sh  # the notification centre is reachable from a signed bundle
macos/app/check-encrypt-flow.sh   # Encrypt with no password held asks, then actually encrypts
```

CI runs the first four. `check-encrypt-flow.sh` covers a bug Rory found by using the app: the
dialog asking for a password was never connected to the job that wanted it, so it opened, took
a password, and silently did nothing. `check-tiles.sh` needs the network and `check-notifications.sh` needs a
user session, neither of which a build runner has.

Two of them have a control line, which is the part worth copying if more are ever written.
`check-tiles.sh` turns previews on and expects a fetch, so a check that has stopped working
fails rather than passing quietly. `check-font.sh` confirms the family is not already installed
system-wide before registering it, or a machine that happened to have Google Sans would pass
while proving nothing about the bundle.

An earlier version of the tiles check used `lsof` to look for open sockets. It was worthless:
the app holds no sockets at rest, so a negative proved nothing. The check now enforces the rule
inside `TileLoader` itself.

### Reminders, recurring templates and the digest

All three live in the config memo, which syncs, so they are the same on the Mac as on the
phone. A reminder set on either arrives on the other, and whichever device fires first clears
it for both.

One thing is genuinely different on a Mac, and the app says so in its own UI rather than
papering over it: the system delivers a notification scheduled earlier whether or not the app
is running, but **nothing can write a memo while the app is closed**. So a recurring template
gets a repeating alarm, which keeps telling you, and the memo itself is written on the next
launch after its time. `MemosSession.runDueRecurring()` is that catch-up, and it will not write
a template twice because it compares first lines against everything written today, phone
included.

The weekly digest has the same shape for the same reason. A notification scheduled a week ago
can only carry words written a week ago, so that one is a nudge, and the real summary is posted
at launch for any Sunday that has gone by without one. The Reminders pane shows the same text
at any time, switched on or not, because waiting until Sunday to find out what it would say is
a poor way to decide whether you want it.

### Not verified on the macOS client

Worth knowing before trusting any of it:

- **Three write paths have never been proven end to end**: the editor's push to the server,
  attachment upload, and sign-out. The problem is structural rather than laziness. A test
  binary is a different application as far as the Keychain is concerned, so it cannot
  authenticate; only the app itself can exercise these, and only by doing them for real.
- **A notification has never actually been delivered.** `check-notifications.sh` proves the
  centre is reachable from a bundle signed the way this one is, and that scheduling is
  correctly refused until permission is granted, but permission is Rory's to give. Open
  Reminders, press "Allow Notifications", then set one a couple of minutes out.
- **The newest screens have not been looked at.** Screen recording and accessibility were
  denied to the agent session that built them, so `screencapture` and `osascript` both failed
  and nothing could be checked by eye. The Reminders and Templates panes and the compact row
  all build, launch and stay up, and every check passes, but a human has not seen them. Given
  how many layout problems Rory has caught by looking, that is the gap most likely to matter.

### Not built on macOS

Nearby is deliberately absent: it ranks memos by distance from where you are, and this app does
not ask the machine where it is. No Apple location service is used at all, by choice. Everything
else the Android app does, the Mac now does too; comments, reactions, the account and admin
screens and export, import and backup arrived with the iOS work of 11 September.

## Security review, 8 September 2026

A full review was run over the Android app. Every finding is closed. The fixes below are all in
`core-data` unless noted, so the macOS client inherits them.

Fixed on `fix/security-review`: image bearer token sent to look-alike hosts, permissionless
`CREATE_MEMO` activity, `file://` URIs accepted from the share sheet, unvalidated config memo
from any creator, off-by-a-separator zip-slip check in restore.

Fixed on `fix/security-review-low`, the remaining low findings and the two lint errors:

- Export zip entries go through `MarkdownExporter.attachmentPath`, which reduces a
  server-supplied filename to a bare name. Covered by unit tests.
- Markdown links only open `http`, `https`, `mailto` and `geo` (`isOpenable` in
  `MemoContent.kt`). No test: the app module has no unit test source set.
- A full reconcile that gets an empty memo list now skips the delete pass when the account
  still has synced memos locally, and logs a warning (`SyncEngine.pull`, `MemoDao.countSynced`).
- Sign-out deletes the account's cached attachment files, and clears the remembered memo
  password once the last account goes.
- Debug HTTP logging is `HEADERS` with `Authorization`, `Cookie` and `Set-Cookie` redacted,
  so the sign-in password no longer reaches logcat.
- The token minted at password sign-in expires after 90 days
  (`AccountRepository.TOKEN_LIFETIME_DAYS`). The password is never stored, so it cannot be
  renewed silently: when it lapses the existing "sign in again" banner asks for the password.
- `AccountRepository` now records `PERSONAL_ACCESS_TOKEN` when minting succeeded. The field
  is only read by `signOut`, so stored accounts need no migration.
- Coordinates are no longer logged in `MemoDetailViewModel`.
- Map tiles are behind a "Map previews" setting, off by default. With it off nothing is drawn
  and openstreetmap.org is never called. The macOS client has the same setting and the same
  default, enforced in `TileLoader` and checked by `check-tiles.sh`.
- CI passes `KEYSTORE_BASE64` to the release step and only decodes it when both it and
  `STORE_FILE` are set. The Gitea copy was missing this until 11 September: it decoded
  `$KEYSTORE_BASE64` without declaring it, which wrote an empty keystore over the store file.
  Both files are now identical, which is the point of them being identical.
- The two lint errors are fixed: `Notifier.post` checks `POST_NOTIFICATIONS` inline so lint
  can see the guard, and `MemoDetailScreen` hoists `reminder_exact_hint` out of the
  `LaunchedEffect`.

Note for automation users: apps sending `CREATE_MEMO` need the
`com.keltruc.mymemos.permission.CREATE_MEMO` permission granted once.

## The one thing left on Android

**Locked notes keep their title visible.** Today the whole body is encrypted, so a list shows
only a lock badge.

Change `MemoRepository.updateLockedContent` so that when the first line is a Markdown heading
it is written in clear ahead of the `mymemos-enc:v1:` blob, and `Memo.isLocked` /
`displayContent` treat "heading plus blob" as locked with a title. Decrypt must strip the
heading before joining with the plaintext. This is a format change: old blobs have no heading
and must still decode, so keep the prefix check on the encrypted line, not the whole content.

**Talk it through with Rory before writing it.** Two things deserve a decision rather than an
assumption. The heading leaves the device unencrypted, which is a real change to what a locked
memo promises and needs saying plainly in the UI, not just in a commit message. And an old blob
that gains a heading can no longer be read by an older build, so the format only moves one way.
Migration is lazy either way, since old blobs still decode.

Three places downstream are waiting for it, and all three do the same thing today:

1. `CompactMemoRow` (Android) guards `MemoTitle.of` with `memo.isLocked` and shows the words
   "Locked memo" where the title would go.
2. `CompactMemoRowView` (macOS, in `MemoListView.swift`) does the same, in `private var title`.
3. `MemoCard` (Android) renders locked memos as a badge and a hint, and could show the heading.

The macOS side needs one more change than it looks. `MemosSession.toRow` already calls
`MemoTitle.of` on every memo, but for a locked one that runs over the ciphertext and returns the
blob's first line, which the Swift side then throws away and replaces with "Locked memo". So
`toRow` has to stop feeding the encrypted line through before the guard in `CompactMemoRowView`
is worth removing.

## Finished work worth knowing about

The numbering is Rory's, from the September list. Everything here is merged.

1. **Better Markdown editing.** `MarkdownContinuation` in core-data decides what a return
   should do. One trap worth remembering: the keyboard commits its composing text in the same
   change as the return, and may recase it as it does ("1. first" arrives as "1. First\n"), so
   the guard compares the cursor, the length and everything after the cursor, never the text
   before it. The macOS editor got this wrong first time by intercepting Return *before* the
   newline landed, which returns nil every time and does nothing quietly; `check-editor.sh`
   exists because of that.
2. **Completion for `@` dates.** `DueDateParser.suggest` produces the completions, so they
   cannot drift from what `parse` understands; a test round-trips every suggestion back through
   `parse` for seven different "todays". Weekday suggestions stop six days out, because the
   seventh wraps to today's own weekday and `parse` would read it as today. Tokens stay English
   even under another locale, since that is all `parse` knows.
4. **Collapsible timeline groups.** `TimelineGrouping.group` in core-data: days for the current
   week, a week header for earlier weeks of the current month, a month header before that. The
   collapsed keys are derived from the dates (`day:2026-09-08`, `week:2026-08-31`) rather than
   the header label, so collapsing "Today" does not come back as a collapsed tomorrow and a
   translated label does not lose the choice. Both clients use this.
5. **Compact list view.** On both clients now. `MemoTitle.of` works out the title and returns
   null when there is nothing to show, so the wording of the fallback stays in the UI where it
   can be translated. See item 3 above for the thread still hanging off this.
6. **Import Markdown.** `MarkdownImport` in core-data does the parsing and is unit tested,
   including a round trip through `MarkdownExporter.render`. Settled while building it:

   - **The server does honour a backdated `createTime` on create.** Verified against
     memos.keltruc.com v0.30.0. The whole feature rests on this, so re-check it if the server
     is ever upgraded.
   - Folders become one nested tag: `work/projects/notes.md` gets `#work/projects`. Folder
     names are made tag-safe and `..` segments are dropped rather than becoming tags.
   - Dates: front matter first, then the zip entry or file timestamp, then the clock.
   - Zips are detected by magic bytes, not extension or MIME type, because providers label
     `.md` as text/plain, octet-stream or nothing at all.
   - Imported memos are PRIVATE unless the file's front matter says otherwise. An import that
     quietly published someone's notes would be a much worse surprise than one that did not.
7. **Dates and sorting.** One accessor, `Memo.timelineTime(byModified)`, decides which date
   every surface shows, so the order, the group headers and the timestamps cannot disagree.
   Room cannot parameterise an ORDER BY, so `observeTimeline` chooses inside a CASE. The review
   screens deliberately stay on the created date: they are about revisiting what you wrote.
8. **Tapping a memo card.** `MemoContent` drew each paragraph with `ClickableText`, whose tap
   handler covers the whole paragraph and swallowed every tap on the text. Links and tags are
   now `LinkAnnotation`s inside a plain `Text`, so only the link takes the tap.
9. **Undo for delete and archive.** Two things had to change for it to mean anything: `delete`
   no longer removes attachment files, and the delete's sync is held back
   `MemoRepository.UNDO_WINDOW_MS` (5 seconds). Undo on a memo that never reached the server is
   not offered, because that one really is gone.
10. **Import de-duplication.** `MarkdownImport` reads the `memos_id` that `MarkdownExporter`
    writes and skips a file whose id this account already holds. Deliberately skip rather than
    update: overwriting a memo with an older file would quietly lose whatever was written since
    the export.
11. **Remembering servers on the sign-in screen.** The last five addresses, newest first,
    written only after a sign-in succeeds so a typo never becomes a suggestion. Only the
    address: no username, password or token.

    **Still not verified on a device.** Reaching the sign-in screen means signing out, which
    wipes the local database and needs Rory's password to put right, so nobody has watched the
    chips appear. Worth ten seconds the next time anyone signs out on purpose.

## Geofenced reminders

Still not built, and still the largest Android gap. The idea: a memo with a location reminds
you when you arrive ("pick up the parcel" fires when you reach the post office).

Note that this is unrelated to the reminders that now exist. Those are time-based and live in
the config memo; these would be local to a device and need a different table.

### What already exists

- Memos carry an optional location (`latitude`, `longitude`, `placeholder`), set from the
  detail menu and synced.
- `LocationProvider` in the app module wraps the platform `LocationManager` (no Play Services)
  and reverse-geocodes with the platform `Geocoder`.
- The review screen's "Nearby" tab sorts located memos by distance from the current fix.

### Design decision to make first

1. `GeofencingClient` from Play Services. Reliable, battery-friendly, well documented. Pulls
   Play Services in, which the project has so far avoided, and would rule out an F-Droid build.
2. `LocationManager.addProximityAlert`. Part of the platform, no dependency, works everywhere.
   Deprecated since API 29 but still functional. Behaviour differs more between manufacturers,
   and each alert needs its own `PendingIntent`.

A third route avoids geofencing altogether: a periodic `WorkManager` job (15 minutes is the
floor) taking one coarse fix. Less precise, higher battery cost when moving, and no background
permission needed only if the work runs in the foreground, which defeats the purpose.

Recommendation: start with `addProximityAlert` behind a small `ReminderScheduler` interface, so
the Play Services version can be dropped in later if reliability turns out to matter more than
the dependency.

### Permissions and Play policy

- Foreground location is already declared. This needs `ACCESS_BACKGROUND_LOCATION`, requested
  in a second step after foreground is granted, because Android will not grant both in one
  dialog. On Android 11 and later the user must pick "Allow all the time" in system settings;
  the app should deep link there and explain why.
- Google Play requires a prominent in-app disclosure before the background prompt, and a
  Permissions Declaration Form explaining the feature. Expect the reviewer to ask for a short
  video. Apps that ask for background location without a user-facing feature that clearly needs
  it get rejected.
- `POST_NOTIFICATIONS` for the reminder itself, which the app already handles.

### Suggested implementation

1. A Room migration adding a `reminders` table (`memoLocalId`, `radiusMetres`, `enabled`,
   `firedAtEpochMs`). Keep it local; the server has no concept of this.
2. A "Remind me here" action in the memo detail menu, enabled only when the memo has a
   location. Radius default 150 metres.
3. `ReminderScheduler`: one proximity alert per enabled reminder, re-registered on boot
   (`BOOT_COMPLETED`) and after an update.
4. A `BroadcastReceiver` posting a notification with the memo's first line and a deep link
   (`ACTION_OPEN_MEMO` already exists in `IntentRouter`), then marking `firedAtEpochMs`.
5. A settings entry listing active reminders with a switch each.
6. Tests: the scheduler against a fake `LocationManager`, and the receiver's notification.

Budget: two to three days including the Play review paperwork.

## Submitting to Google Play

### One-off setup

1. Create a developer account at play.google.com/console (one-time fee; identity verification
   takes a few days). New personal accounts must run a closed test with at least 12 testers for
   14 days before production access is granted, so start that clock early.
2. Generate an upload key and keep it safe. This is the only key you sign with; Google holds
   the app signing key under Play App Signing.

   ```bash
   keytool -genkeypair -v -keystore mymemos-upload.jks -alias mymemos -keyalg RSA -keysize 4096 -validity 10000
   ```

   Copy `keystore.properties.example` to `keystore.properties` and fill it in. The file is
   git-ignored. For CI, set the `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` and
   `KEYSTORE_BASE64` secrets.
3. In Play Console create the app: name "MyMemos", default language English (UK), free,
   category Productivity.

### Each release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`. Play rejects a version code
   it has seen before.
2. Build an App Bundle, not an APK:

   ```bash
   ./gradlew :app:bundleRelease
   ```

   Output: `app/build/outputs/bundle/release/app-release.aab`.
3. Upload to Internal testing first, install on a real device through the Play Store link, and
   check sign-in, sync, widgets and the share sheet. Then promote the same bundle to Closed
   testing, and finally Production.

### Store listing and forms

All mandatory before the first production release.

- Short description (80 characters) and full description (4,000). Lead with offline-first and
  self-hosted; those are the reasons someone picks this over the web app.
- Screenshots: at least two phone screenshots, 16:9 or 9:16, 1080 px minimum on the short side.
  Take them with `adb exec-out screencap -p`. A 7-inch and 10-inch tablet set unlocks the
  tablet listing and shows off the two-pane layout.
- App icon 512 by 512 PNG and a 1024 by 500 feature graphic.
- Privacy policy URL. The app sends data only to the Memos server the user configures, stores
  credentials in the Android Keystore, and collects no analytics. Say exactly that, host it on
  a page you control, and link it from the listing and from About.
- Data safety form: data is transmitted to a user-chosen server (personal info, photos,
  location if the user attaches one), encrypted in transit, and users can delete it. Nothing is
  shared with third parties. Location is "collected" only when the user taps "Add location".
- Content rating questionnaire (IARC): answer honestly; a notes app comes out as Everyone.
- Target audience: 18 and over keeps you out of the Families policy.
- App access: the reviewer needs a Memos server to log in to. Provide a throwaway account on a
  test instance, never your own.
- Permissions: fine location needs no form as long as it is foreground only. The moment
  background location is added for geofenced reminders, the declaration form and demo video
  apply.

### Things the reviewer will check

- The target SDK must be within a year of the current Android release. The app targets 36 with
  compileSdk 37, which is fine for 2026.
- `android:exported` is set explicitly on every component (it is).
- The share-sheet and `CREATE_MEMO` intent filters are fine, but the Tasker-style intent should
  be described in the listing so it does not look like an undeclared feature.
- The Quick Settings tile and widgets need no extra declarations.

### Alternative: F-Droid

No proprietary dependencies. Google Sans Flex is OFL, commonmark-java is BSD, and there are no
Play Services. An F-Droid submission needs a public repository with a tagged release, a
`metadata` YAML in fdroiddata, and a reproducible `./gradlew assembleRelease`. Keeping Play
Services out of the geofencing work preserves this option.
