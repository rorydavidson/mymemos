# MyMemos

An Android client for a self-hosted [Memos](https://usememos.com) server. Offline-first:
memos live in a local Room database, the UI only ever reads from there, and changes are
queued and pushed when the network returns. Built for Memos v0.30; newer server features
are gated on the version the server reports.

## Thanks

None of this would exist without [Memos](https://github.com/usememos/memos), the
self-hosted, privacy-first note service by [boojack](https://github.com/boojack) and the
[Memos contributors](https://github.com/usememos/memos/graphs/contributors), released under
the MIT licence. This app is a client for their
server and follows the same instincts: your notes stay on infrastructure you control, and
nothing is collected along the way. Thank you.

## What it does

### Writing

- Markdown editor with a toolbar for tasks, lists, bold, code, tags and images, tag
  autocompletion from your own tags, and templates with `{{date}}`, `{{time}}`,
  `{{weekday}}` and similar placeholders.
- Return inside a bullet, task or numbered item carries the marker to the next line,
  incrementing numbers and starting tasks unticked. Return on an item you left empty
  clears the marker, which is how you get out of a list.
- Typing `@` completes a due date: today, tomorrow, the next few weekdays with their
  dates, or a picker that writes `@yyyy-MM-dd`. The suggestions come from the same parser
  the tasks screen reads, so a completion can never mean a different day than it shows.
- Attachments from the photo picker, staged locally and uploaded when online.
- Visibility, pinning, archiving, and a location captured from the device with reverse
  geocoding, no Google Play Services involved.
- Optional "sink completed tasks" rule that moves ticked tasks below open ones on save.
- End-to-end encryption per memo: lock a memo with a password and the server only ever
  stores the scrambled text. One password for all locked memos, remembered on the device
  if you choose.

### Reading

- Full CommonMark rendering: headings, lists, task lists with live checkboxes that edit
  the source line, fenced code, quotes, tables, links, strikethrough, autolinks and tags.
- Tags carry an emoji and a colour of your choosing, shown everywhere; the colour is
  mirrored to the server's own tag setting so the web UI matches.
- Note colours from the sixteen basic web colours as a pastel tint, synced across devices.
- Memos with a location show an OpenStreetMap preview with a marker; tap to open your
  maps app.
- Comments, reactions, references with backlinks, and public share links with revoke.
- Offline full-text search, tag filters, saved server-side shortcuts (CEL filters), an
  archive view, and a two-pane layout on tablets and landscape phones.
- The timeline folds as it goes back: days for the current week, a week header for earlier
  weeks of this month, a month header before that. Every header collapses, and what you
  folded away is still folded next time you open the app.
- A compact view swaps the cards for one line per memo, showing its title, time and any
  lock or pin badge, for finding something rather than reading it.
- Sort by when memos were written or when they were last changed. The group headers, the
  card timestamps and the date on a memo all follow that choice, so what you are sorting
  on is always the date you can see. A memo's detail lists both dates at the foot.

### Sync

- Every write lands in Room and an outbox in one transaction; a debounced sync pushes it
  at once, and WorkManager catches up in the background.
- Delta pulls with a clock-skew overlap, plus a full reconcile of server memo names on
  pull-to-refresh and at least every six hours so deletions made elsewhere propagate.
- Concurrent edits go through a line-based three-way merge. Text that cannot be merged is
  kept as a conflict copy rather than lost.
- If the server has lost a memo you edited, the edit is recreated as a new memo instead
  of being parked forever.
- Password sign-in mints a personal access token per device, good for 90 days, and revokes
  it on sign out. An expired credential shows a banner rather than failing quietly. Several
  accounts and servers can be signed in at once.
- The sign-in screen remembers the servers you have used, so an unwanted sign-out does not
  mean retyping an address. Only the address is kept; no username, password or token.

### Tasks, reminders and routines

- A tasks screen lists every open checkbox across memos, grouped by memo, with due dates
  parsed from `@today`, `@tomorrow`, `@fri`, `@2026-09-12` or `@12/9`, sorted and flagged
  when overdue.
- Reminders on a date and time for any memo, delivered as notifications on every device
  you are signed in on.
- Recurring templates: a template that creates itself daily at a set time unless one
  exists already (a journal at 21:00, say).
- A weekly digest notification on Sunday evening: memos written, tasks closed, your
  streak, and a few old memos worth revisiting.

### Review

- Day-by-day review with swipe to archive or keep, plus quick pin, tag, edit and delete.
- "On this day" resurfaces memos from the same date in earlier months and years.
- Nearby lists memos by distance from where you are; Journey plots a day's located memos
  as a numbered route on a map; Graph draws memos and the references between them.
- A writing streak and a twelve-week activity heatmap.

### Capture from anywhere

- Share-sheet target for text, links and images from any app.
- Home-screen widgets: open tasks (tick from the widget) and recent memos with a
  quick-capture button. A Quick Settings tile opens a blank memo.
- An intent for automation tools:

  ```
  am start -a com.keltruc.mymemos.action.CREATE_MEMO --es content "text" --es visibility PRIVATE --ez pinned false --ez open false
  ```

  `open true` opens the editor prefilled instead of saving silently.

### Account and administration

- Profile, password, server-side default visibility, personal access tokens, webhooks,
  notifications with an unread badge, and statistics.
- For admins: user management and the instance's general settings and storage figures.

### Your data

- Export everything as a zip of Markdown files with YAML front matter (Obsidian-ready)
  plus local attachments.
- Import Markdown back: any number of `.md` files, or zips of them, in one pick. Dates come
  from the files rather than the clock, so imported notes land where they belong on the
  timeline, and folders inside a zip become nested tags. Front matter written by this app or
  by Obsidian is understood, and re-importing an export skips what you already have instead
  of duplicating it.
- Encrypted local backup and restore of the database, attachments and settings, AES-256
  under a password of your choosing.
- Deleting or archiving a memo offers Undo. A delete waits a few seconds before it is sent,
  so taking it back really does take it back rather than racing the server.
- Settings that need to follow you between devices (tag styles, reminders, recurring
  templates, the digest switch) live in a hidden memo tagged `#mymemos/config`, so they
  sync through the server like everything else without any extra service.

### Privacy notes

- Credentials sit in the Android Keystore; the app collects no analytics and talks only
  to the server you configure.
- Map previews are off until you turn them on. With them on, tiles come from
  openstreetmap.org, which means the rough position of a located memo reaches OSM when a
  card renders; with them off nothing is drawn and nothing leaves the device. There is no
  other third-party traffic either way.
- Links in a memo only open `http`, `https`, `mailto` and `geo`. Text arriving from a server
  or a shared note cannot talk the app into launching anything else.

## Building

Requires JDK 17+ and the Android SDK (`platforms;android-37.0`, `build-tools;37.0.0`).
Point `local.properties` at your SDK:

```
sdk.dir=/path/to/android-sdk
```

Then:

```bash
./gradlew :app:assembleDebug
./gradlew test
```

`core-network` is a Kotlin Multiplatform module, built for the JVM and for `macosArm64` in
preparation for a macOS client (see `docs/MACOS_PLAN.md`). `./gradlew test` covers the JVM
side on any machine; the native tests need a Mac:

```bash
./gradlew :core-network:macosArm64Test
```

## Layout

| Module          | Purpose                                                              |
|-----------------|----------------------------------------------------------------------|
| `core-model`    | Plain Kotlin domain types, no Android or framework dependencies.     |
| `core-network`  | Ktor client for the Memos API, bearer auth, token refresh, persistent cookie storage. Multiplatform (JVM and macOS), tested with Ktor's MockEngine. |
| `core-database` | Room schema: accounts, memos (with FTS index), attachments, outbox, relations, reactions, shortcuts, templates. |
| `core-data`     | Repositories, sync engine and three-way merge, mappers, encrypted credential store, memo cipher, config memo, Hilt wiring. |
| `app`           | Jetpack Compose UI (Material 3), navigation, widgets, notifications, view models. |

## Fonts and licences

Google Sans Flex, bundled under the SIL Open Font License 1.1 (see `app/GOOGLE_SANS_FLEX_OFL.txt`).
"Google Sans Flex" is a trademark of Google LLC; its use here does not imply affiliation.
Markdown rendering by commonmark-java (BSD-2), diffing by java-diff-utils (Apache-2.0),
map tiles © OpenStreetMap contributors.

## Release builds

Copy `keystore.properties.example` to `keystore.properties` and point it at your keystore;
`./gradlew :app:assembleRelease` then produces a signed, shrunk APK. CI (`.gitea/workflows/ci.yml`,
mirrored for GitHub) runs unit tests, lint and both builds, signing when the `STORE_*`/`KEY_*`
secrets are set. See `docs/HANDOFF.md` for Play Store submission notes.
