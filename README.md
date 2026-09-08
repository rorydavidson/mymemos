# MyMemos

An Android client for a self-hosted [Memos](https://usememos.com) server. Offline-first:
memos live in a local Room database, the UI only ever reads from there, and changes are
queued and pushed when the network returns.

Targets the Memos v1 REST API (v0.30.x). Newer server features are gated on the reported
server version.

## Building

Requires JDK 17+ and the Android SDK (platform 37, build-tools 36+). Point `local.properties`
at your SDK:

```
sdk.dir=/path/to/android-sdk
```

Then:

```bash
./gradlew :app:assembleDebug
./gradlew test
```

## Layout

| Module          | Purpose                                                              |
|-----------------|----------------------------------------------------------------------|
| `core-model`    | Plain Kotlin domain types, no Android or framework dependencies.     |
| `core-network`  | Retrofit/OkHttp client for the Memos API, bearer auth, token refresh, persistent cookie jar. Pure JVM, tested with MockWebServer. |
| `core-database` | Room schema: accounts, memos (with FTS index), attachments.          |
| `core-data`     | Repositories, DTO/entity/model mappers, encrypted credential store, Hilt wiring. |
| `app`           | Jetpack Compose UI (Material 3), navigation, view models.            |

## Fonts

Google Sans Flex, bundled under the SIL Open Font License 1.1 (see `app/GOOGLE_SANS_FLEX_OFL.txt`).
"Google Sans Flex" is a trademark of Google LLC; its use here does not imply affiliation.

## Status

Phase 1: sign in (password or personal access token), pull and browse memos, offline
search and tag filter. The sync engine (outbox, conflict merge, attachments) is phase 2.
