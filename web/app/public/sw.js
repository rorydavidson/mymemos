// The app shell, kept so MyMemos opens with no connection. Memos themselves live in
// IndexedDB, not here, and nothing under /api or /file is ever cached by this worker: those
// carry the credential and the server's answers, and are the page's business.
const SHELL = 'mymemos-shell-v2'
const PRECACHE = ['/', '/manifest.webmanifest', '/icons/icon-192.png', '/icons/icon-512.png', '/fonts/GoogleSansFlex.ttf']

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(SHELL).then((c) => c.addAll(PRECACHE)).then(() => self.skipWaiting()))
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== SHELL).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const req = event.request
  if (req.method !== 'GET') return
  const url = new URL(req.url)
  if (url.origin !== self.location.origin) return
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/file/')) return

  // Pages: the network first, so a new build is picked up; the cached shell when offline.
  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req)
        .then((res) => {
          // Only a good copy of the app shell itself becomes the offline page; a 404, an error
          // page or a visit straight to some other file must not replace it.
          const isShell = url.pathname === '/' || url.pathname === '/index.html'
          if (isShell && res.ok && (res.headers.get('content-type') ?? '').startsWith('text/html')) {
            const copy = res.clone()
            caches.open(SHELL).then((c) => c.put('/', copy))
          }
          return res
        })
        .catch(() => caches.match('/')),
    )
    return
  }

  // Built assets have content hashes in their names, so a cached copy is never stale.
  event.respondWith(
    caches.match(req).then(
      (hit) =>
        hit ||
        fetch(req).then((res) => {
          if (res.ok && res.type === 'basic') {
            const copy = res.clone()
            caches.open(SHELL).then((c) => c.put(req, copy))
          }
          return res
        }),
    ),
  )
})
