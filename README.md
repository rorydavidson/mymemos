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

## Release builds

Copy `keystore.properties.example` to `keystore.properties` and point it at your keystore;
`./gradlew :app:assembleRelease` then produces a signed, shrunk APK. CI (`.gitea/workflows/ci.yml`,
mirrored for GitHub) runs unit tests, lint and both builds, signing when the `STORE_*`/`KEY_*`
secrets are set.

## Automation

`am start -a com.keltruc.mymemos.action.CREATE_MEMO --es content "text" --es visibility PRIVATE --ez pinned false --ez open false`
creates a memo without opening the app; `open true` opens the editor prefilled. Sharing text or
images from any app does the same through the share sheet.

## Status

Feature complete against Memos v0.30: offline-first editing with an outbox and three-way
merge, attachments, comments, reactions, references, share links, location, shortcuts,
profile and instance administration, widgets, templates, review, export and encrypted backup.
