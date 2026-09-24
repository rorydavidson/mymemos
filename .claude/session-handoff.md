# Session handoff

Last updated: 2026-09-24

## State

Branch `feat/mac-list-polish`, four commits on main, not pushed, no PR yet. Mac feedback round:

- `ColourTag.visible()` drops the bare `colour` parent tag the server adds beside
  `colour/x` (core-data, so Android gets it too). Covered by `ColourTagTest`.
- List cards render Markdown via `MarkdownView(card: true)`; `MemoRow.bodyBelowTitle` added
  in `MemosSession.kt`.
- Tag chips are one line, middle-truncated; detail tags use a new `FlowLayout`.
- Memo tint is the background of the detail pane and compact rows.
- Compact list already existed (toolbar list icon, View menu, Settings); nothing changed.

`apple/ui` is shared with iOS, so all of the above lands on iOS too. The iOS app was not built.

## Verified

`macos/app/build.sh` builds. `:core-data:macosArm64Test` passes. List cards, compact rows and
flow tags rendered offscreen with `ImageRenderer` in light and dark look right. The live app
was not relaunched or screenshotted (no screen recording permission for the shell).

## Build environment gotchas

- `build.sh` defaults JAVA_HOME to temurin-21, which is not installed. Run with
  `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home`.
- Android: no JDK 17 (project pins toolchain 17); `ANDROID_HOME` is
  `/opt/homebrew/share/android-commandlinetools`.

## Open questions

- Cards show a trailing tag-only line (`#session`) and the same tag as a chip.
- Server stats (`StatsRow.tagCounts`) still include `colour` and `colour/x`.
- Android foldable: NavigationRail for tablets; back press does not close the right pane first.
