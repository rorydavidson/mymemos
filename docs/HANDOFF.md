# MyMemos handoff

State as of 8 September 2026. Everything lives on `feature/polish`, which contains all
earlier branches (`feature/scaffold`, `feature/sync-engine`, `feature/extras`). Nothing is
merged to `main` yet.

## Where things stand

- Feature complete against Memos v0.30 (see README for the list). 25 unit tests pass, lint
  is clean, and a shrunk release build has been smoke-tested on an emulator.
- Two known gaps that were deliberately left: geofenced reminders (below) and Wear OS.
- Note colours are stored per device only. The server has no field for them.
- Password sign-in mints a personal access token per device and revokes it on sign out. If
  a device ever loses that token, the app shows a "Sign in again" banner rather than failing
  quietly.

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
