# MyMemos handoff

State as of 8 September 2026. Everything up to and including the security review, plus
feature items 1 and 2 below, is merged to `main`. Open branch: `feature/timeline-grouping`,
which carries items 4, 5 and 6.

## Where things stand

- Feature complete against Memos v0.30 (see README for the list). 49 unit tests pass, a
  shrunk release build has been smoke-tested on an emulator, and `:app:lintDebug` is clean
  of errors.
- Two known gaps that were deliberately left: geofenced reminders (below) and Wear OS.
- Note colours sync as a trailing `#colour/<name>` line, hidden in the app.
- Password sign-in mints a personal access token per device, good for 90 days, and revokes it
  on sign out. If a device ever loses that token or it expires, the app shows a "Sign in
  again" banner rather than failing quietly.

## Security review, 8 September 2026

A full review was run over the app. Every finding is now closed.

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
- Map tiles are behind a "Map previews" setting (`Settings.mapTiles`, off by default, provided
  through `LocalMapTiles`). With it off `MapPreview` and `RouteMap` return early and render
  nothing, so openstreetmap.org is never called. A located memo still shows its place-name
  chip on the detail screen, and the journey tab still lists the day's memos in order.
- CI now passes `KEYSTORE_BASE64` to the release step and only decodes it when both it and
  `STORE_FILE` are set. `.kotlin/` is git-ignored and the committed error logs are gone.
- The two lint errors are fixed: `Notifier.post` checks `POST_NOTIFICATIONS` inline so lint
  can see the guard, and `MemoDetailScreen` hoists `reminder_exact_hint` out of the
  `LaunchedEffect`. `:app:lintDebug` reports no errors; the remaining findings are warnings
  that pre-date the review.

Note for automation users: apps sending `CREATE_MEMO` now need the
`com.keltruc.mymemos.permission.CREATE_MEMO` permission granted once.

## Requested next

Rory reordered this on 8 September: 4, 5 and 6 come before 3, which is now last. Items 1 and
2 are merged; 4, 5 and 6 are done on `feature/timeline-grouping`; 3 and 7 are not started.

1. **Better Markdown editing.** Done. `MarkdownContinuation` in core-data decides what a
   return should do; `EditorScreen.update` calls `continueAfterReturn` with the field before
   and after the change. One trap worth remembering: the keyboard commits its composing text
   in the same change as the return, and it may recase that text as it does ("1. first"
   arrives as "1. First\n"), so the guard compares the cursor, the length and everything
   after the cursor, never the text before it.
2. **Completion for `@` dates.** Done. `DueDateParser.suggest` produces the completions, so
   they cannot drift from what `parse` understands; a test round-trips every suggestion back
   through `parse` for seven different "todays". Weekday suggestions stop six days out,
   because the seventh wraps to today's own weekday and `parse` would read it as today.
   Tokens stay English even under another locale, since that is all `parse` knows; the
   localised date rides along as a hint on the chip. "Pick a date" opens a new
   `DueDatePickerDialog`, date only, rather than the existing `DateTimePickerDialog`, whose
   time step has nowhere to go in a `@yyyy-MM-dd` token.
3. **Locked notes keep their title visible.** Today the whole body is encrypted, so the
   list shows only a lock badge. Change `MemoRepository.updateLockedContent` so that when the
   first line is a Markdown heading it is written in clear ahead of the `mymemos-enc:v1:`
   blob, and `Memo.isLocked` / `displayContent` treat "heading plus blob" as locked with a
   title. Decrypt must strip the heading before joining with the plaintext. This is a format
   change: old blobs have no heading and must still decode, so keep the prefix check on
   the encrypted line, not the whole content. Flag in the UI that the title is not encrypted.
4. **Collapsible timeline groups.** Done. `TimelineGrouping.group` in core-data replaces
   `groupByDay`: days for the current week, a week header for earlier weeks of the current
   month, a month header before that. Collapsed keys live in `AppPreferences.collapsedGroups`.
   The keys are derived from the dates (`day:2026-09-08`, `week:2026-08-31`,
   `month:2026-09-01`) rather than the header label as first sketched, so collapsing "Today"
   does not come back as a collapsed tomorrow and a translated label does not lose the
   choice. A collapsed header shows its memo count.
5. **Compact list view.** Done, except for one thread left hanging by the reordering. The
   toggle sits in the timeline top bar, next to archive; `AppPreferences.compactList` holds
   the choice. `MemoTitle.of` in core-data works out the title (first heading, else first
   non-blank line, with Markdown decoration and the hidden colour line taken off) and returns
   null when there is nothing to show, so the wording of the fallback stays in the UI where it
   can be translated. `CompactMemoRow` draws it with the time and lock or pin badges.

   Left hanging: item 5 was meant to follow item 3, so locked memos would show a clear title.
   With 3 now last, `CompactMemoRow` shows a lock badge and the words "Locked memo" instead.
   When item 3 lands, that is the one place to change: drop the `memo.isLocked` guard around
   `MemoTitle.of` and let the heading through.
6. **Import Markdown.** Done. "Import Markdown" in Settings > Data takes any number of `.md`
   files or zips of them, in one pick. `MarkdownImport` in core-data does the parsing (front
   matter, folder-to-tag, date resolution) and is unit tested, including a round trip through
   `MarkdownExporter.render`; `MarkdownImporter` reads the URIs and loops through the outbox.

   Settled while building it:

   - **The server does honour a backdated `createTime` on create.** Verified against
     memos.keltruc.com v0.30.0 by importing dated files on the emulator and then pulling them
     down on a second device, where they arrived in March 2024 and November 2025 rather than
     today. The whole feature rests on this, so re-check it if the server is ever upgraded.
   - Folders become one nested tag: `work/projects/notes.md` gets `#work/projects`, matching
     how Memos treats `/` as hierarchy. Folder names are made tag-safe (spaces to hyphens) and
     `..` segments are dropped rather than becoming tags of their own.
   - Dates: front matter first (`created`/`date`/`created_at`, `updated`/`modified`), then the
     zip entry or the file's own timestamp, then the clock. ISO instants, `yyyy-MM-dd HH:mm`
     and bare dates all parse.
   - Zips are detected by their magic bytes, not the extension or the reported MIME type,
     because providers label `.md` as text/plain, octet-stream or nothing at all.
   - Imported memos are PRIVATE unless the file's own front matter says otherwise. An import
     that quietly published someone's notes would be a much worse surprise than one that did not.
   - `MemoRepository.create` gained optional `createdAtEpochMs`/`updatedAtEpochMs`; the sync
     engine already sent `createTime` on create, so nothing else needed changing.
7. **Dates and sorting.** Done, on `feature/detail-modified` then `feature/sort-and-dates`.
   The detail footer lists both created and last modified. "Sort by last modified" in Settings
   flips the timeline between the two dates, and one accessor, `Memo.timelineTime(byModified)`,
   decides which date every surface shows, so the order, the group headers, the card and row
   timestamps and the date at the top of the detail screen cannot disagree. Room cannot
   parameterise an ORDER BY, so `observeTimeline` chooses inside a CASE expression. The review
   screens deliberately stay on the created date: they are about revisiting what you wrote.

8. **Tapping a memo card.** Fixed on `feature/sort-and-dates`. Only the margins of a card used
   to open the memo. `MemoContent` drew each paragraph with `ClickableText`, whose tap handler
   covers the whole paragraph and swallowed every tap on the text, which is most of a card.
   Links and tags are now `LinkAnnotation`s inside a plain `Text`, so only the link itself takes
   the tap and everything else reaches the card. This also retires a deprecated API.

## Geofenced reminders

The idea: a memo with a location can remind you when you arrive there ("pick up the
parcel" fires when you reach the post office).

### What already exists

- Memos carry an optional location (`latitude`, `longitude`, `placeholder`), set from the
  detail menu and synced to the server.
- `LocationProvider` in the app module wraps the platform `LocationManager` (no Play
  Services) and reverse-geocodes with the platform `Geocoder`.
- The review screen's "Nearby" tab sorts located memos by distance from the current fix.

### Design decision to make first

Android offers two ways to be told you have entered an area:

1. `GeofencingClient` from Google Play Services. Reliable, battery-friendly, well documented.
   Pulls Play Services into the app, which the project has so far avoided, and would rule
   out an F-Droid build.
2. `LocationManager.addProximityAlert`. Part of the platform, no dependency, works on every
   device. Deprecated since API 29 but still functional. Behaviour differs more between
   manufacturers, and each alert needs its own `PendingIntent`.

A third route avoids geofencing altogether: a periodic `WorkManager` job (every 15 minutes
is the floor) that takes one coarse fix and compares it with reminder locations. Less
precise, higher battery cost when moving, but no background location permission is needed
if the work only runs while the app is in the foreground, which defeats the purpose.

Recommendation: start with `addProximityAlert` behind a small `ReminderScheduler`
interface so the Play Services version can be dropped in later if reliability turns out to
matter more than the dependency.

### Permissions and Play policy

- Foreground location is already declared. Reminders need
  `ACCESS_BACKGROUND_LOCATION`, requested in a second step after foreground is granted
  (Android will not grant both in one dialog). On Android 11 and later the user has to pick
  "Allow all the time" in system settings; the app should deep link there and explain why.
- Google Play requires a prominent in-app disclosure before the background permission
  prompt, and a Permissions Declaration Form in Play Console explaining the feature. Expect
  the reviewer to ask for a short video of the flow. Apps that ask for background location
  without a user-facing feature that clearly needs it get rejected.
- `POST_NOTIFICATIONS` (Android 13 and later) for the reminder itself.

### Suggested implementation

1. Room migration 3 to 4: a `reminders` table (`memoLocalId`, `radiusMetres`, `enabled`,
   `firedAtEpochMs`). Keep it local; the server has no concept of reminders.
2. A "Remind me here" action in the memo detail menu, enabled only when the memo has a
   location. Radius default 150 metres.
3. `ReminderScheduler`: registers one proximity alert per enabled reminder, re-registers
   them all on boot (`BOOT_COMPLETED` receiver) and after the app updates.
4. A `BroadcastReceiver` for the alert that posts a notification with the memo's first line
   and a deep link (`ACTION_OPEN_MEMO` already exists in `IntentRouter`), then marks
   `firedAtEpochMs` so it does not fire again until re-armed.
5. Settings entry listing active reminders with a switch each.
6. Tests: the scheduler against a fake `LocationManager`, and the receiver's notification
   content.

Budget: two to three days including the Play review paperwork.

## Submitting to Google Play

### One-off setup

1. Create a Google Play developer account at play.google.com/console (a one-time fee;
   identity verification takes a few days). New personal accounts must run a closed test
   with at least 12 testers for 14 days before production access is granted, so start that
   clock early.
2. Generate an upload key and keep it somewhere safe. This is the only key you sign with;
   Google holds the app signing key under Play App Signing.

   ```bash
   keytool -genkeypair -v -keystore mymemos-upload.jks -alias mymemos -keyalg RSA -keysize 4096 -validity 10000
   ```

   Copy `keystore.properties.example` to `keystore.properties` and fill it in. The file is
   git-ignored. For CI, set the `STORE_FILE`, `STORE_PASSWORD`, `KEY_ALIAS` and
   `KEY_PASSWORD` secrets.
3. In Play Console, create the app: name "MyMemos", default language English (UK), free,
   category Productivity.

### Each release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`. Play rejects a version
   code it has seen before.
2. Build an App Bundle, not an APK:

   ```bash
   ./gradlew :app:bundleRelease
   ```

   Output: `app/build/outputs/bundle/release/app-release.aab`.
3. Upload to the Internal testing track first, install it on a real device through the
   Play Store link, and check sign-in, sync, widgets and the share sheet. Then promote the
   same bundle to Closed testing, and finally Production.

### Store listing and forms

Everything below is mandatory before the first production release.

- Short description (80 characters) and full description (4,000). Lead with offline-first
  and self-hosted; those are the reasons someone picks this over the web app.
- Screenshots: at least two phone screenshots, 16:9 or 9:16, 1080 px minimum on the short
  side. Take them from the Pixel with `adb exec-out screencap -p`. A 7-inch and 10-inch
  tablet set unlocks the tablet listing and shows off the two-pane layout.
- App icon 512 by 512 PNG and a 1024 by 500 feature graphic.
- Privacy policy URL. The app sends data only to the Memos server the user configures, stores
  credentials in the Android Keystore, and collects no analytics. Say exactly that, host it
  on a page you control, and link it from the listing and from the About section.
- Data safety form: declare that data is transmitted to a user-chosen server (personal
  info, photos, location if the user attaches one), encrypted in transit, and that users can
  delete it. No data is shared with third parties. Location is "collected" only when the user
  taps "Add location".
- Content rating questionnaire (IARC): answer honestly; a notes app comes out as Everyone.
- Target audience: 18 and over keeps you out of the Families policy.
- App access: the reviewer needs a Memos server to log in to. Provide a throwaway account
  on a test instance in the "App access" section, never your own.
- Permissions: fine location needs no form as long as it is foreground only. The moment
  background location is added for reminders, the declaration form and demo video apply.

### Things the reviewer will check

- The target SDK must be within one year of the current Android release. The app targets
  36 with compileSdk 37, which is fine for 2026.
- `android:exported` is set explicitly on every component (it is).
- The share-sheet and `CREATE_MEMO` intent filters are fine, but the Tasker-style intent
  should be described in the listing so it does not look like an undeclared feature.
- The Quick Settings tile and widgets need no extra declarations.

### Alternative: F-Droid

The app has no proprietary dependencies. Google Sans Flex is OFL, commonmark-java is BSD,
and there are no Play Services. An F-Droid submission needs a public repository with a
tagged release, a `metadata` YAML in the fdroiddata repository, and a reproducible
`./gradlew assembleRelease`. Keeping Play Services out of the geofencing work preserves this
option.
