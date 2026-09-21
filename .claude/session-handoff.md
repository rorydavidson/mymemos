# Session handoff

Last updated: 2026-09-21

## State

Branch `feat/foldable-two-pane`, two commits (48b1f2d, f3f37a4), not pushed, no PR yet.

Android two-pane layout now triggers from Medium width (unfolded foldables), not only
Expanded. Changes are in `navigation/MyMemosNavHost.kt` (`PaneLayout`, `ListDetailPanes`,
`topLevelPanes`) and `MainActivity.kt`. README "Reading" section updated.

- Split lands on the hinge when `androidx.window` (1.5.0, added) reports a vertical
  `FoldingFeature`; `MainActivity` reads it and passes `hinge: Rect?` down.
- No hinge: Medium 50/50, Expanded 42/58 as before.
- Nav bar sits under the left pane in two-pane mode; detail pane is full height.
- Tasks and Review open memos in the right pane too.
- Fold/unfold moves the open memo between the pane and `MemoDetailRoute`.

## Verified

On the `flights_fold` emulator (851dp unfolded, Rory signed in): divider sits on the hinge,
Tasks tab keeps the open memo on the right, folding shows that memo full-screen with a
back arrow, unfolding puts it back in the right pane. No crashes in logcat.
Not checked: a Galaxy-style (Medium) fold, RTL, tabletop (horizontal hinge) posture.
No tests cover the app module's navigation.

## Build environment gotchas

- No JDK 17 on this Mac (only 25 and 26); the project pins `jvmToolchain(17)`. Compiled
  locally with a throwaway init script forcing toolchain 25 plus
  `-Pkotlin.jvm.target.validation.mode=warning`. Nothing in the repo was changed for this.
- Android SDK is at `/opt/homebrew/share/android-commandlinetools`; there is no
  `local.properties`, so set `ANDROID_HOME`.

## Open questions

- NavigationRail instead of the bar under the left pane: rejected for now because at
  673dp the timeline header overflows in the narrower list. Worth revisiting for tablets.
- Back press does not close the right pane first.
