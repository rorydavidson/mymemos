# Session handoff

Last updated: 2026-10-11

## State

Branch `fix/markdown-and-sync`, 4 commits, not pushed, no PR. Off `main` after the web client merge.

- Web keeps single line breaks (commonmark softbreak renders as `<br>`), matching Android and Mac.
- Format buttons (Mac `EditorEdit.wrap`, web `Editor.tsx` wrap) keep edge whitespace outside
  the markers. Cause of `**text **` showing literal asterisks. Existing memos with that
  pattern still need hand fixing; the renderer is deliberately strict CommonMark.
- Mac toolbar sync label ticks via `TimelineView` so "Just now" ages.
- Web: `SyncButton` in the memo list header and on the Sync page; `syncNow()` in `core.ts`
  toasts the outcome ("will retry" used to be silent). Sidebar shows the last sync time.

Verified: Mac `build.sh`, `check-editor.sh`, wrap logic exercised standalone; web `tsc` and
`vite build` pass. Not seen in a running app (no local Memos server, no Docker). The deployed
web at mymemos.onetwozero.org needs its container rebuilt to pick this up.

`web/app/node_modules` now installed; esbuild/fsevents install scripts were not approved.

## Inline editing (branch `feature/inline-edit`, stacked on `fix/markdown-and-sync`, not pushed)

Click a memo's text in the Mac detail pane or web memo pane to edit it there. Saves on
leaving (another memo, click outside on web, Done, Cmd+Enter); Escape discards with a confirm
if changed. Locked memos keep the sheet. Mac: `InlineEditor.swift`, macOS only (iOS untouched);
`model.inlineSave` makes the sheet wait for an in-flight inline save. Web: `Editor` `inline`
prop; header Edit button carries `data-pop-out`. Also fixed: Mac detail pane did not refresh
after an edit (`Shell.swift` now keys on `model.selectedMemo`).

Verified: Mac build, check-editor, web tsc + vite build. NOT tried live on either.
Origin is now GitHub (rorydavidson/mymemos); PR #18 is the markdown/sync fix, auto-fix on.

## Previous session (2026-09-24)

### State

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
