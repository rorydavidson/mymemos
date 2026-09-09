# Phase 0 spike results

Run 9 September 2026 on macOS 26.6, Xcode 26.6, Swift 6.3.3, JDK 21, Gradle 9.7.1,
Kotlin 2.4.20. The spike sources live in their own repository, `MyMemos-spikes`, because
none of it is meant to ship. Paths below are relative to that repository.

**Verdict: proceed with KMP core plus SwiftUI, with one substitution.** Three of the four
spikes passed outright. The fourth found a blocker in a tool rather than in the
architecture, and the workaround is priced below.

| Spike | Question | Result |
|-------|----------|--------|
| 1 | Room KMP on `macosArm64`, FTS4, auto-migrations | **Pass** |
| 2 | Ktor Darwin: bearer auth, cookie jar, refresh on 401 | **Pass** |
| 3 | SKIE over Flow and sealed classes | **Blocked**, hand-written bridges instead |
| 4 | Crypto parity between javax.crypto and CryptoKit | **Pass** both directions |

## Spike 1: Room on macosArm64

`room-kmp`, run with `./gradlew -p room-kmp jvmTest macosArm64Test`.

The spike takes the real `core-database` sources and the real exported schemas, adds the
`macosArm64` target, and runs two tests on both targets.

- `androidx.room:room-compiler:2.8.4` runs under KSP for `macosArm64` and generates the
  same DAOs it generates for the JVM. `room-runtime`, `room-testing` and
  `androidx.sqlite:sqlite-bundled:2.7.0` all publish `macosarm64` artifacts.
- The external-content `@Fts4` table works. The test inserts memos, runs the real
  `MemoDao.search` MATCH query, then updates a memo and proves the stale FTS row is gone,
  which only happens if Room's content-sync triggers fire. This was the biggest unknown in
  the plan and it is now closed.
- Both auto-migrations run. The test builds a database file from the DDL in
  `schemas/1.json`, including the FTS triggers and the `room_master_table` identity hash,
  puts a row in it, then opens it with the version 3 database. The row survives, the
  `parent` and `colour` columns exist, the tables added in versions 2 and 3 are usable, and
  the FTS index still answers queries.
- The result was checked by mutation: breaking an assertion does fail the `macosArm64` run,
  so the passes are real and not an empty test task.

Two changes were needed and both belong in the real conversion:

1. `MyMemosDatabase` needs `@ConstructedBy` and an `expect object` implementing
   `RoomDatabaseConstructor`. Room's compiler plugin supplies the actual per target.
2. `PendingOpEntity.createdAtEpochMs` defaults to `System.currentTimeMillis()`, the only
   JVM call in the whole module. `kotlin.time.Clock.System.now().toEpochMilliseconds()`
   replaces it.

## Spike 2: Ktor on the Darwin engine

`ktor-darwin`, run with `python3 ktor-darwin/mockmemos.py 8731 &` then
`./gradlew -p ktor-darwin jvmTest macosArm64Test`.

The spike reimplements `BearerInterceptor`, `PersistentCookieJar` and
`RefreshAuthenticator` as Ktor plugins against the same `TokenStore` interface, and runs
four tests against a mock Memos server on both targets. All four pass on `macosArm64`, and
the mock server's own counters confirm the traffic really happened rather than being
short-circuited.

- Sign-in persists the refresh cookie through `TokenStore` and the bearer header is then
  attached to subsequent calls.
- An access token the server has rotated away produces a 401, exactly one refresh, a retry
  that succeeds, and the new token written back to the store.
- A personal access token is never refreshed; the 401 surfaces to the caller.
- A refresh that fails does not loop.

Notes for the real port:

- Ktor's `Auth` plugin replaces the whole `RefreshAuthenticator` including its recursion
  guards, so that class goes away rather than being translated. `sendWithoutRequest { true }`
  is needed or Ktor waits for a 401 before ever sending the header.
- The cookie store keeps the same newline-separated `Set-Cookie` lines the OkHttp jar
  wrote, so an Android install upgrading in place still finds its refresh cookie. Worth an
  explicit test during Phase 1.
- `runBlocking` inside the OkHttp interceptors disappears: Ktor's hooks are already
  suspending. That removes a class of main-thread problems rather than porting them.
- Cleartext to `127.0.0.1` worked from a test binary, so App Transport Security did not get
  in the way. A bundled `.app` talking to a plain-http home server is a separate question
  and needs an ATS exception; the same is true of a self-signed certificate.

## Spike 3: Swift interop, and the SKIE problem

`skie-interop`.

**SKIE 0.10.14, the current release, refuses to run:** "SKIE 0.10.14 does not support
Kotlin 2.4.20." Its published compiler artifacts stop at the Kotlin 2.2 line. Using SKIE
means pinning the whole build, Android included, back to Kotlin 2.2.x, which fights AGP
9.4.0 and the August 2026 Compose BOM. That is not worth doing for interop sugar.

So the spike measured the fallback instead: a plain Kotlin/Native framework consumed from
Swift, with the glue written by hand. It compiles and runs green. What the Swift side
actually gets:

- **Suspend functions cost nothing.** `suspend fun syncNow(): SyncState` arrives as
  `try await repo.syncNow()`. No glue.
- **Enums cost nothing.** `Visibility`, `MemoState`, `SyncStatus`, `NoteColour` and friends
  export cleanly.
- **Flows need a bridge, and the bridge cannot be generic.** Swift will not let an
  extension on a generic Objective-C class touch its type parameter, so a single
  `FlowBridge<T>` is unusable from Swift. Each observed payload type needs a concrete
  subclass in Kotlin plus its own `AsyncThrowingStream` extension in Swift. Two flows and
  one sealed class cost 31 lines of Kotlin and 39 of Swift.
- **Sealed classes lose exhaustiveness.** They arrive as a class hierarchy, so the Swift
  side casts with `case let x as MemoLoad.Loaded` and needs a `default`. Adding a Kotlin
  case will not fail the Swift build. There are only two sealed hierarchies in the whole
  core (`ThreeWayMerge.Result` and `SyncEngine.Outcome`), so this is a small, containable
  hazard, but each shim needs a test.
- **`KotlinThrowable` is not a Swift `Error`** and needs wrapping at every boundary.
- **Data classes arrive as reference types** with `doCopy(...)` instead of `copy(...)`.
  `==` does work, routed through Kotlin's `equals` via NSObject bridging, so SwiftUI
  diffing is fine. What is lost is value semantics, so view models should copy into their
  own Swift structs rather than hold Kotlin objects as view state.
- **Nullable primitives box.** `Long?` becomes `KotlinLong?` and needs `.int64Value`.

Scale of the tax: `core-data` and `core-database` expose 35 Flow-returning functions across
about twenty payload types, of which perhaps a dozen to fifteen reach the UI. At roughly
twenty lines of glue each that is 250 to 350 lines, written once, plus a fixed cost per new
Flow thereafter. Annoying, not disqualifying. Revisit SKIE when it catches up with Kotlin
2.4; nothing in this approach blocks adopting it later.

## Spike 4: crypto parity

`crypto-parity`, run with `run.sh`.

Three steps: a JVM program emits vectors using exactly the primitives `MemoCipher` and
`BackupCrypto` use; a Swift program derives the same keys with CommonCrypto's PBKDF2 and
seals with CryptoKit's `AES.GCM`, checking keys, ciphertext and plaintext against them; then
the JVM opens blobs the Swift side produced with fresh random salts and nonces.

All five vectors match in both directions, including the cases that were expected to be
fragile: a password with German, French and Chinese characters, and one with emoji. So
SunJCE's `PBEKeySpec(char[])` encodes to UTF-8, the same as `Array(password.utf8)` on the
Swift side. A memo locked on one platform opens on the other, byte for byte.

**Android settled too, 9 September 2026.** Android does not use SunJCE: it resolves
`PBKDF2WithHmacSHA256` through **Bouncy Castle 1.77**, and providers have historically
disagreed about whether a `char[]` password becomes UTF-8 or Latin-1 bytes. That would have
broken any non-ASCII password across platforms while working fine on each one alone.
`AndroidCryptoParityTest` in `core-data/src/androidTest` settles it on a real Android
runtime, against Android 16 on API 36: the derived keys match SunJCE and CryptoKit exactly,
`MemoCipher` opens blobs sealed on the other two platforms, and blobs it seals open on both.
`BackupCrypto` round-trips with a non-ASCII password as well. Run it with
`./gradlew :core-data:connectedDebugAndroidTest`.

One thing this spike did **not** settle:

- **CryptoKit has no streaming AES-GCM.** `BackupCrypto` wraps a `CipherOutputStream`
  around the whole backup. `AES.GCM.seal` is one-shot, so a Swift implementation either
  holds the entire backup in memory or the format changes to chunked GCM with a per-chunk
  tag. Backup and restore is Phase 5 work, so the decision can wait, but it is a format
  change and therefore needs both platforms moving together.

## What this changes in the plan

Three corrections, all in the direction of more work:

1. **`core-data` is more coupled than the plan said.** The plan counted 11 of 46 files
   touching `android.*`. Counting JVM-only APIs as well, it is **24 of 46**: `javax.inject`
   in 17 files (deleted with Hilt, trivial), `java.time` in 10, `java.util.UUID` in 4,
   `java.util.zip` in the export and import paths, `java.io` streams, `java.util.Locale`
   and `DateTimeFormatter` for date formatting. Phase 3 is bigger than advertised.
2. **`core-model` is not portable as is.** It has no Android dependency, but four of its
   nine files use `java.time.Instant`. That single type change ripples through every module.
3. **`java.util.zip` has no multiplatform answer in the standard library.** Export and
   import need either an `expect`/`actual` pair over `java.util.zip` and Foundation, or a
   pure Kotlin zip library. Phase 5, but worth deciding early because the export format is
   already in the wild.

Nothing here changes the architecture decision. The three passes cover the parts that could
have invalidated it: the database, the network stack and the encryption format all move.

## Reproducing

See the README in the `MyMemos-spikes` repository. All four were re-run from there after
the split and are green: 2 tests in `room-kmp` and 4 in `ktor-darwin`, on both the JVM and
`macosArm64`, plus the two Swift binaries.

## The live server

`https://memos.keltruc.com` is a real Memos **0.30.0** instance to develop against, with a
valid certificate and no redirect. An anonymous caller gets `200` and an empty list from
`/api/v1/memos`, so nothing leaks without a credential.

`ktor-darwin` builds a native `liveCheck` binary that runs the auth flow against it, which
is the part the mock server cannot vouch for: real TLS, the server's own cookie attributes
and its real token lifetimes. The anonymous half is confirmed green, so Ktor on the Darwin
engine does reach the real instance over TLS and parse what it sends back.

The sign-in, refresh and sign-out half is left to be run by hand. It is only reachable
through a password, and a password does not belong in a repository, a shell history or a
chat log, so the binary reads `MEMOS_URL`, `MEMOS_USERNAME` and `MEMOS_PASSWORD` from the
environment. It creates no memos and mints no token, and signs out at the end to revoke the
session it made. The command is in the `MyMemos-spikes` README.
