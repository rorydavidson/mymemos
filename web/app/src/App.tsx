import type { ComponentType } from 'preact'
import { useEffect, useState } from 'preact/hooks'
import { refresh, session, toast, useData } from './core'
import { Editor } from './components/Editor'
import { Icon } from './components/Icon'
import { MemoView } from './components/MemoView'
import { promptDialog, Toasts } from './components/ui'
import { go, href, openMemo, useRoute, type Route } from './router'
import { AccountView } from './views/Account'
import { AdminView } from './views/Admin'
import { GraphView } from './views/Graph'
import { ArchiveView, MemosView, ShortcutView, TagView } from './views/Memos'
import { NotificationsView } from './views/Notifications'
import { PlacesView } from './views/Places'
import { ReviewView } from './views/Review'
import { SettingsView } from './views/Settings'
import { SignIn } from './views/SignIn'
import { SyncView } from './views/Sync'
import { TagsView } from './views/Tags'
import { TasksView } from './views/Tasks'

/** Every list-pane screen takes the route and draws its own header and body. */
export type View = ComponentType<{ route: Route }>

const views: Record<string, View> = {
  memos: MemosView,
  tag: TagView,
  archive: ArchiveView,
  shortcut: ShortcutView,
  tasks: TasksView,
  review: ReviewView,
  tags: TagsView,
  places: PlacesView,
  graph: GraphView,
  notifications: NotificationsView,
  settings: SettingsView,
  account: AccountView,
  admin: AdminView,
  sync: SyncView,
  more: MoreView,
}

/** Screens that are not lists of memos fill the width rather than leaving room for one. */
const wide = new Set(['more', 'settings', 'account', 'admin', 'graph', 'notifications', 'sync', 'tags'])

export function App() {
  useData()
  const [signedIn, setSignedIn] = useState(session.isSignedIn())
  useEffect(() => {
    const t = setInterval(() => setSignedIn(session.isSignedIn()), 1000)
    return () => clearInterval(t)
  }, [])
  if (!signedIn) {
    return (
      <>
        <SignIn onDone={() => setSignedIn(true)} />
        <Toasts />
      </>
    )
  }
  return <Shell />
}

function Shell() {
  useData()
  const route = useRoute()
  const [composing, setComposing] = useState<string | null>(null)
  const [online, setOnline] = useState(navigator.onLine)
  const sync = session.syncState()
  useChores(setOnline)
  useLaunchParams(setComposing)

  useEffect(() => {
    const key = (e: KeyboardEvent) => {
      const typing = /^(INPUT|TEXTAREA|SELECT)$/.test((e.target as HTMLElement).tagName)
      if (typing || e.metaKey || e.ctrlKey || e.altKey || composing != null) return
      if (e.key === 'n') {
        e.preventDefault()
        setComposing('')
      } else if (e.key === '/') {
        e.preventDefault()
        if (route.view !== 'memos') go('memos')
        setTimeout(() => document.querySelector<HTMLInputElement>('input.search')?.focus(), 50)
      }
    }
    addEventListener('keydown', key)
    return () => removeEventListener('keydown', key)
  }, [route.view, composing])

  const ViewComponent = views[route.view] ?? MemosView
  const hasDetail = !!route.memo
  const layout = hasDetail ? 'has-detail' : 'no-detail'

  return (
    <div class={`shell ${layout}`}>
      <Nav route={route} />
      <main class="list-pane" aria-label="List">
        {!online && (
          <div class="banner info">
            <Icon name="cloudOff" /> Offline. Changes are kept here and sent when the connection returns.
          </div>
        )}
        {sync.authExpired && <AuthBanner />}
        <ViewComponent route={route} />
      </main>
      {hasDetail && (
        <aside class="detail-pane" aria-label="Memo">
          <MemoView localId={route.memo!} />
        </aside>
      )}
      {!hasDetail && !wide.has(route.view) && (
        <button class="fab" onClick={() => setComposing('')} aria-label="New memo">
          <Icon name="plus" /> <span>New</span>
        </button>
      )}
      <BottomNav route={route} />
      {composing != null && (
        <Editor
          initial={composing}
          onClose={(id) => {
            setComposing(null)
            if (id) openMemo(id)
          }}
        />
      )}
      <Toasts />
    </div>
  )
}

function Nav(props: { route: Route }) {
  const r = props.route
  const unread = session.unreadNotifications()
  const shortcuts = session.shortcuts()
  const tags = session.tags().slice(0, 12)
  const link = (view: string, icon: string, label: string, badge?: number) => (
    <a href={href(view)} class={r.view === view ? 'active' : ''} aria-current={r.view === view ? 'page' : undefined} title={label}>
      <Icon name={icon} />
      <span class="label">{label}</span>
      {!!badge && <span class="badge">{badge}</span>}
    </a>
  )
  return (
    <nav class="nav" aria-label="Sections">
      <div class="nav-brand">
        <img src="/icons/icon-192.png" alt="" />
        <span>MyMemos</span>
      </div>
      {link('memos', 'notes', 'Memos')}
      {link('tasks', 'tasks', 'Tasks')}
      {link('review', 'review', 'Review')}
      {link('tags', 'tag', 'Tags')}
      {link('archive', 'archive', 'Archive')}
      {link('places', 'place', 'Places')}
      {link('graph', 'graph', 'Graph')}
      {link('notifications', 'bell', 'Notifications', unread)}
      {shortcuts.length > 0 && <div class="nav-section">Shortcuts</div>}
      {shortcuts.map((s) => (
        <a key={s.name} href={href('shortcut', s.name)} class={r.view === 'shortcut' && r.arg === s.name ? 'active' : ''} title={s.title}>
          <Icon name="bolt" />
          <span class="label">{s.title}</span>
        </a>
      ))}
      {tags.length > 0 && <div class="nav-section">Tags</div>}
      {tags.map((t) => (
        <a key={t} class={`tag-link${r.view === 'tag' && r.arg === t ? ' active' : ''}`} href={href('tag', t)}>
          #{t}
        </a>
      ))}
      <div class="spacer" style={{ minHeight: 16 }} />
      <SyncLink />
      {link('settings', 'settings', 'Settings')}
      {link('account', 'person', 'Account')}
      {session.isAdmin() && link('admin', 'shield', 'Admin')}
    </nav>
  )
}

function SyncLink() {
  const s = session.syncState()
  const label = s.running ? 'Syncing…' : s.failed > 0 ? `${s.failed} failed` : s.pending > 0 ? `${s.pending} waiting` : 'Synced'
  return (
    <a href={href('sync')} title={s.lastSuccessLabel ? `Last synced ${s.lastSuccessLabel}` : 'Sync'}>
      <Icon name={s.failed > 0 || s.lastError ? 'warning' : 'sync'} />
      <span class="label">{label}</span>
      {s.conflicts > 0 && <span class="badge">{s.conflicts}</span>}
    </a>
  )
}

function BottomNav(props: { route: Route }) {
  const r = props.route
  const item = (view: string, icon: string, label: string) => (
    <a href={href(view)} class={r.view === view ? 'active' : ''}>
      <Icon name={icon} />
      {label}
    </a>
  )
  return (
    <nav class="bottom-nav" aria-label="Sections">
      {item('memos', 'notes', 'Memos')}
      {item('tasks', 'tasks', 'Tasks')}
      {item('review', 'review', 'Review')}
      {item('more', 'more', 'More')}
    </nav>
  )
}

function AuthBanner() {
  const reauth = async () => {
    const a = await promptDialog('Sign in again', [{ name: 'password', label: 'Password', type: 'password' }], 'Sign in', 'This browser’s access has lapsed. Your unsent changes are safe and go once you are back in.')
    if (!a?.password) return
    if (await session.reauthenticate(a.password)) {
      toast('Signed in again')
      session.requestSync()
    } else toast('That did not work. Check the password and try again.')
  }
  return (
    <div class="banner">
      <Icon name="warning" />
      <span style={{ flex: 1 }}>The server no longer accepts this browser’s sign-in.</span>
      <button class="btn text" onClick={() => void reauth()}>
        Sign in again
      </button>
    </div>
  )
}

/**
 * What WorkManager and alarms do in the apps, done while a tab is open: sync on start, on
 * reconnect, when the tab comes back and every few minutes; run due recurring templates;
 * announce due reminders and the weekly digest; keep the unread count fresh.
 */
function useChores(setOnline: (v: boolean) => void) {
  useEffect(() => {
    const syncSoon = () => session.requestSync()
    const minute = async () => {
      const notices = await session.dueNotices().catch(() => [])
      for (const n of notices) announce(n.title, n.body, n.memoLocalId ?? null)
    }
    const fiveMinutes = async () => {
      syncSoon()
      const made = await session.runDueRecurring().catch(() => [])
      if (made.length) toast(`Created from template: ${made.join(', ')}`)
      await session.refreshUnread().catch(() => 0)
      refresh()
    }
    void session.sync(false).finally(() => {
      void fiveMinutes()
      void minute()
    })
    const online = () => {
      setOnline(true)
      syncSoon()
    }
    const offline = () => setOnline(false)
    const visible = () => document.visibilityState === 'visible' && syncSoon()
    addEventListener('online', online)
    addEventListener('offline', offline)
    document.addEventListener('visibilitychange', visible)
    const t1 = setInterval(() => void minute(), 60_000)
    const t5 = setInterval(() => void fiveMinutes(), 5 * 60_000)
    return () => {
      removeEventListener('online', online)
      removeEventListener('offline', offline)
      document.removeEventListener('visibilitychange', visible)
      clearInterval(t1)
      clearInterval(t5)
    }
  }, [])
}

function announce(title: string, body: string, memo: string | null) {
  if ('Notification' in window && Notification.permission === 'granted') {
    const n = new Notification(title, { body, icon: '/icons/icon-192.png', tag: title })
    n.onclick = () => {
      focus()
      if (memo) openMemo(memo)
    }
  } else {
    toast(body ? `${title}: ${body}` : title, memo ? { label: 'Open', run: () => openMemo(memo) } : undefined, 10_000)
  }
}

/**
 * Text arriving from the share sheet (the manifest's share target) or a link such as
 * `/?content=…` opens the editor prefilled. It is never saved without a tap on Save: unlike
 * an Android intent, any web page can send someone to a URL.
 */
function useLaunchParams(compose: (text: string) => void) {
  useEffect(() => {
    const p = new URLSearchParams(location.search)
    const parts = [p.get('content'), p.get('title'), p.get('text'), p.get('url')].filter((x): x is string => !!x && x.trim() !== '')
    if (parts.length === 0) return
    history.replaceState(null, '', location.pathname + location.hash)
    compose(Array.from(new Set(parts)).join('\n\n'))
  }, [])
}

/** On a phone the side navigation is gone; this is everything it held. */
function MoreView(_: { route: Route }) {
  useData()
  const unread = session.unreadNotifications()
  const row = (view: string, icon: string, label: string, arg = '', badge = 0) => (
    <a class="row" href={href(view, arg)} style={{ color: 'inherit', textDecoration: 'none' }}>
      <Icon name={icon} />
      <span class="grow">{label}</span>
      {badge > 0 && <span class="badge">{badge}</span>}
    </a>
  )
  return (
    <>
      <div class="pane-header">
        <h1>More</h1>
      </div>
      <div class="pane-body">
        <div class="card-box">
          {row('tags', 'tag', 'Tags')}
          {row('archive', 'archive', 'Archive')}
          {row('places', 'place', 'Places')}
          {row('graph', 'graph', 'Graph')}
          {row('notifications', 'bell', 'Notifications', '', unread)}
          {row('sync', 'sync', 'Sync')}
        </div>
        {session.shortcuts().length > 0 && <div class="section-title">Shortcuts</div>}
        {session.shortcuts().length > 0 && <div class="card-box">{session.shortcuts().map((s) => row('shortcut', 'bolt', s.title, s.name))}</div>}
        <div class="section-title">You</div>
        <div class="card-box">
          {row('settings', 'settings', 'Settings')}
          {row('account', 'person', 'Account')}
          {session.isAdmin() && row('admin', 'shield', 'Admin')}
        </div>
      </div>
    </>
  )
}
