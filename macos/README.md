# MyMemos for macOS

A macOS client sharing its data layer with the Android app: the same sync engine, outbox,
three-way merge, Room database and memo cipher, in `core-model`, `core-network`,
`core-database` and `core-data`. Only the interface and the platform seams are written twice.

Requires macOS 15 on Apple silicon.

## Building and running

```bash
macos/app/build.sh && open macos/app/build/MyMemos.app
```

`macos/shared` is the Kotlin framework the app links against. `macos/app` is the SwiftUI
application, built by script rather than an Xcode project, because the Swift side is a handful
of files and one less thing to keep in step is worth having.

## Checks

Two things are checked rather than asserted, both because getting them wrong is quiet:

```bash
macos/app/check-cipher.sh   # a memo locked on Android opens here, and back again
macos/app/check-tiles.sh    # no map tile is fetched while previews are off
macos/app/check-font.sh     # the bundled font is present, licensed and resolvable
macos/app/check-appearance.sh   # light, dark and system map and persist correctly
```

`check-tiles.sh` has a control line: it turns previews on and expects a fetch, so a broken
check fails rather than passing quietly. `check-font.sh` has one too: it confirms the family
is not already installed system-wide before registering it, otherwise a machine that happened
to have Google Sans would make the check pass while proving nothing about the bundle.

## Look and feel

The palette is the Android app's Material scheme rather than an approximation of it, so the
two read as the same product: a warm paper ground, a deep green accent, brown-black ink. Type
is Google Sans Flex, the same file Android ships, bundled under the SIL Open Font License
whose text travels in the app bundle beside it.

Light and dark are both real palettes rather than one derived from the other, and there is a
switcher in the toolbar, the View menu and Settings. It is a per-machine choice, so it lives
in UserDefaults rather than the settings memo that syncs between devices.

The sidebar and toolbar are built rather than left to the defaults: sidebar rows carry counts
and select as a filled capsule in the app's own green, and the toolbar says when the app last
agreed with the server, which on an offline-first app is the one thing worth knowing, since
everything on screen came from the local database.

Sizes are set explicitly rather than taken from the platform's text styles. Those are tuned
for controls, and 13pt with control leading is a list row, not a page, so reading sizes and
chrome sizes are kept apart in `Type`. If the bundled font ever fails to register the theme
falls back to the system font, which is why `check-font.sh` exists: the failure is otherwise
silent.

## Packaging

```bash
macos/app/package.sh
```

With no credentials this produces an ad-hoc signed DMG. That works on the machine that built
it and Gatekeeper refuses it anywhere else, which is fine for trying it and no use for
handing to anyone.

For a DMG that opens on someone else's Mac you need two things this repository cannot supply:

1. **A Developer ID Application certificate.** Needs a paid Apple Developer Program
   membership. Create it in the developer portal or from Xcode's Accounts pane, then check it
   arrived:

   ```bash
   security find-identity -v -p codesigning
   ```

   `package.sh` picks it up on its own, or set `SIGN_IDENTITY` to choose between several.

2. **Notarisation credentials.** An app-specific password from appleid.apple.com, stored once:

   ```bash
   xcrun notarytool store-credentials "notary" \
       --apple-id you@example.com --team-id TEAMID --password app-specific-password
   ```

   Then `NOTARY_PROFILE=notary macos/app/package.sh`, which signs with the hardened runtime,
   submits to Apple, waits, staples the ticket and verifies the result.

`MyMemos.entitlements` is deliberately short: outgoing network for the user's own server and
for openstreetmap.org when map previews are on, and read access to files chosen through the
open panel. Nothing else, because an entitlement list that asks for everything tells the
reader nothing.

## What is not built yet

Comments and reactions, the account and admin screens, reminders and recurring templates, and
export, import and encrypted backup, which need a multiplatform zip that nothing provides
well. Nearby is deliberately absent: it ranks memos by distance from where you are, and this
app does not ask the machine where it is.
