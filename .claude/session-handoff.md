# Session handoff

Last updated: 2026-09-24

## State

Branch `feat/mac-list-polish`, not pushed, no PR yet. Mac feedback round, then locked-memo titles.

Locked-memo titles (all platforms): plain `# Title` line after the `mymemos-enc:v1:` blob
(`Memo.lockedTitleOf`, `MemoCipher.withTitle`, tests in `LockedTitleTest`). Lock asks for an
optional title (empty default); Rename on locked memos needs no password. Android Rename is
in the detail overflow menu only, not the card long-press.

Tag-only lines are hidden on cards and in the Mac/iOS detail (chips show them).

Earlier in the round:

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

Android: new build installed on the SM-F971B on 2026-09-24 after an uninstall (old install
had another machine's debug key; 0 pending ops checked first). User must sign in again.
Not yet checked on the phone: Lock title dialog, Rename, "Passports" title from the Mac.

## Build environment gotchas

- Android builds need a JDK 25 override. Init script that works (keep outside the repo):
  `gradle.beforeProject { p -> p.afterEvaluate { if (p.extensions.findByName('kotlin') != null) p.kotlin.jvmToolchain(25) } }`
  run with `--init-script <file> -Pkotlin.jvm.target.validation.mode=warning`. It has to be
  `beforeProject` so it runs ahead of AGP's afterEvaluate.

- `build.sh` defaults JAVA_HOME to temurin-21, which is not installed. Run with
  `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home`.
- Android: no JDK 17 (project pins toolchain 17); `ANDROID_HOME` is
  `/opt/homebrew/share/android-commandlinetools`.

## Open questions

- Server stats (`StatsRow.tagCounts`) still include `colour` and `colour/x`.
- Android foldable: NavigationRail for tablets; back press does not close the right pane first.
