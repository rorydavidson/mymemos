# MyMemos on the web

A browser version of the MyMemos clients, meant to run beside your Memos server so iPhones and
anything else with a browser can use it without an app store. Add it to the home screen and it
behaves like an installed app: it opens offline, keeps your memos in the browser, and sends
changes when the server can be reached.

## Running it

Copy `docker-compose.example.yml` to `docker-compose.yml`, adjust it, then from this folder:

```bash
docker compose up -d --build
```

Memos is on `:5230` and MyMemos on `:8080`. The image is also buildable on its own from the
repository root:

```bash
docker build -f web/Dockerfile -t mymemos-web .
```

Every merge to `main` on GitHub also publishes the image as
`ghcr.io/rorydavidson/mymemos-web:latest` (and a tag per commit), so a server can pull it
instead of building: swap the compose file's `build:` block for `image:` with that name.

| Variable | Default | What it does |
| --- | --- | --- |
| `MEMOS_UPSTREAM` | `http://memos:5230` | Where nginx finds Memos. `/api` and `/file` are proxied there, so the page and the API share an origin and no CORS is needed. |
| `PUBLIC_MEMOS_URL` | empty | The address people use for Memos itself. Share links point there, since this app does not serve Memos' public pages. |
| `EXTRA_CONNECT_SRC` | empty | Extra origins the page may call, for signing in to other Memos servers. Those servers must allow this origin through CORS. |

**Serve it over HTTPS.** Locked memos use the browser's WebCrypto and the offline cache uses a
service worker, and browsers only allow either on HTTPS (or `localhost`). Put it behind the
same reverse proxy as Memos.

The container runs nginx as an unprivileged user on a read-only filesystem, with a strict
Content-Security-Policy: scripts and API calls only from its own origin, images from anywhere a
memo links to.

## How it is built

The same split as the Mac and iOS apps: anything that decides something is shared Kotlin,
anything that draws pixels is not.

- `web/core` is Kotlin/JS. It compiles the rule files from `core-data` (tags, colours, titles,
  due dates, tasks, list continuation, timeline grouping, the three-way merge, the config memo,
  the export format, the cipher layout, digest and schedule) straight from their own directory,
  so they stay single-sourced; `core-data` itself cannot target JavaScript because its
  repositories are Room. Around them it adds an IndexedDB store, ports of `SyncEngine` and the
  `MemoRepository` writes, WebCrypto for locking, and `WebSession`, the Promise-based surface
  the UI calls, shaped like `MemosSession`.
- `web/app` is the UI: Preact and TypeScript, built by Vite.

The shared rules' tests run under Node as well as on the JVM and Kotlin/Native, which is what
found several regexes JavaScript rejects. The web's own sync engine is tested against a fake
server, and its cipher against vectors made by the JDK, so a memo locked on Android opens in a
browser.

```bash
./gradlew :web-core:test
```

### Developing

```bash
docker run -d -p 5230:5230 neosmemo/memos:0.30.0     # a throwaway server
cd web/app
npm run core        # builds web/core into the library the app imports
npm install
npm run dev         # http://localhost:5173, proxying /api to MEMOS_URL (default :5230)
```

Run `npm run core` again after changing any Kotlin.

## What it does

Everything in the main README's feature list that a browser can do: the timeline with its
folding groups, compact view and sort order; search; tags with emoji and colours; the editor
with list continuation, `#` and `@date` completion, templates and attachments; tasks; locked
memos; note colours; comments, reactions, references and share links; shortcuts; archive and
undo; places, journey and the graph; review, on this day, streak and heatmap; reminders,
recurring templates and the weekly digest; account, tokens, webhooks, notifications and
statistics; admin; Markdown export and import; encrypted backup; several accounts.

## What is different in a browser

- **Nothing runs while no tab is open.** Reminders and the weekly digest appear (as system
  notifications if allowed) while MyMemos is open; recurring templates are created when it is
  next opened. Doing better needs Web Push and a sender on the server.
- **The access token lives in IndexedDB.** A browser has no Keychain. Password sign-in still
  mints a 90-day token per browser and revokes it on sign-out, and the password is never
  stored. The CSP is what keeps other script away from it.
- **"Remember the memo password" lasts until the tab closes**, in session storage, rather than
  for good.
- **Places have no reverse geocoding.** You name the place yourself; no geocoding service is
  contacted.
- **Backups are the browser's own format.** The apps back up their SQLite database, which a
  browser cannot read, and the reverse. Markdown exports are the same everywhere.
- **No widgets, Quick Settings tile or iOS share target.** Android browsers do get a share
  target from the manifest. `/?content=…` opens the editor prefilled but never saves on its own,
  since any page can link someone to that address.
- **Exports are compressed less**, by a small built-in compressor rather than zlib.
