import { useEffect, useState } from 'preact/hooks'
import { attempt, refresh, session, toast, useData, useOnline } from '../core'
import { Icon } from '../components/Icon'
import { MemoList } from '../components/MemoList'
import { confirmDialog, Menu, promptDialog } from '../components/ui'
import { go, type Route } from '../router'

/** The timeline, with offline search over it. */
export function MemosView(props: { route: Route }) {
  useData()
  const [q, setQ] = useState(props.route.q)
  useEffect(() => setQ(props.route.q), [props.route.q])
  // The query lives in the URL so back and forward walk through searches.
  useEffect(() => {
    const t = setTimeout(() => q !== props.route.q && go('memos', '', { q, memo: props.route.memo }, true), 250)
    return () => clearTimeout(t)
  }, [q])
  const sections = q.trim() ? session.search(q) : session.timeline()
  return (
    <>
      <div class="pane-header">
        <input
          class="search"
          type="search"
          placeholder="Search memos, or #tag"
          value={q}
          onInput={(e) => setQ((e.target as HTMLInputElement).value)}
          aria-label="Search"
        />
        <ViewOptions />
      </div>
      <div class="pane-body">
        <MemoList sections={sections} empty={q.trim() ? 'No memos match.' : 'No memos yet. Press N or the New button to write one.'} />
      </div>
    </>
  )
}

/** The compact switch and the sort order, as in the apps' list menu. */
export function ViewOptions() {
  const [open, setOpen] = useState(false)
  const compact = session.compactList()
  const byModified = session.sortByModified()
  return (
    <div style={{ position: 'relative' }}>
      <button class="icon-btn" aria-label="View options" aria-haspopup="menu" onClick={() => setOpen(!open)}>
        <Icon name={compact ? 'compact' : 'cards'} />
      </button>
      {open && (
        <Menu onClose={() => setOpen(false)} style={{ top: '42px', right: '0' }}>
          <button onClick={() => void session.setCompactList(!compact).then(refresh)}>
            <Icon name={compact ? 'cards' : 'compact'} size={18} /> {compact ? 'Show cards' : 'Compact list'}
          </button>
          <button onClick={() => void session.setSortByModified(!byModified).then(refresh)}>
            <Icon name="sort" size={18} /> {byModified ? 'Sort by date written' : 'Sort by last changed'}
          </button>
          <button onClick={() => void newShortcut()}>
            <Icon name="bolt" size={18} /> New shortcut
          </button>
        </Menu>
      )}
    </div>
  )
}

export function TagView(props: { route: Route }) {
  useData()
  const tag = props.route.arg
  return (
    <>
      <div class="pane-header">
        <button class="icon-btn" onClick={() => history.back()} aria-label="Back">
          <Icon name="back" />
        </button>
        <h1>#{tag}</h1>
        <ViewOptions />
      </div>
      <div class="pane-body">
        <MemoList sections={session.search(`#${tag}`)} empty="No memos carry this tag." />
      </div>
    </>
  )
}

export function ArchiveView(_: { route: Route }) {
  useData()
  return (
    <>
      <div class="pane-header">
        <h1>Archive</h1>
        <ViewOptions />
      </div>
      <div class="pane-body">
        <MemoList sections={session.archived()} empty="Nothing archived." />
      </div>
    </>
  )
}

/**
 * A saved server-side filter. Running one needs the server, since the filter is CEL the
 * server evaluates; the memos it returns are kept locally like any others. It is run once per
 * visit rather than on every local change, since running it writes those memos locally.
 */
export function ShortcutView(props: { route: Route }) {
  useData()
  const name = props.route.arg
  const shortcut = session.shortcuts().find((s) => s.name === name)
  const [sections, loading, error, reload] = useOnline(() => session.runShortcut(name), [name], [])
  const [menu, setMenu] = useState(false)

  const edit = async () => {
    if (!shortcut) return
    const a = await promptDialog('Edit shortcut', [
      { name: 'title', label: 'Title', value: shortcut.title },
      { name: 'filter', label: 'Filter', type: 'textarea', value: shortcut.filter, hint: 'A Memos CEL filter, such as tag in ["work"] or pinned' },
    ], 'Save')
    if (a && (await attempt(() => session.saveShortcut(name, a.title, a.filter)))) reload()
  }

  const remove = async () => {
    if (!shortcut || !(await confirmDialog('Delete shortcut?', shortcut.title, 'Delete', true))) return
    await attempt(() => session.deleteShortcut(name))
    go('memos')
  }

  return (
    <>
      <div class="pane-header">
        <h1>{shortcut?.title ?? 'Shortcut'}</h1>
        <button class="icon-btn" aria-label="Run again" onClick={reload}>
          <Icon name="sync" />
        </button>
        <div style={{ position: 'relative' }}>
          <button class="icon-btn" aria-label="Shortcut options" onClick={() => setMenu(!menu)}>
            <Icon name="more" />
          </button>
          {menu && (
            <Menu onClose={() => setMenu(false)} style={{ top: '42px', right: '0' }}>
              <button onClick={() => void edit()}>
                <Icon name="edit" size={18} /> Edit
              </button>
              <button onClick={() => void newShortcut()}>
                <Icon name="plus" size={18} /> New shortcut
              </button>
              <button onClick={() => void remove()} style={{ color: 'var(--error)' }}>
                <Icon name="trash" size={18} /> Delete
              </button>
            </Menu>
          )}
        </div>
      </div>
      <div class="pane-body">
        {shortcut && <p class="small muted" style={{ fontFamily: 'var(--mono)' }}>{shortcut.filter || 'All memos'}</p>}
        {error && <div class="banner">Needs the server: {error}</div>}
        {loading && sections.length === 0 ? <div class="empty">Asking the server…</div> : <MemoList sections={sections} empty="Nothing matches this shortcut." />}
      </div>
    </>
  )
}

export async function newShortcut(): Promise<void> {
  const a = await promptDialog('New shortcut', [
    { name: 'title', label: 'Title' },
    { name: 'filter', label: 'Filter', type: 'textarea', placeholder: 'tag in ["work"]', hint: 'A Memos CEL filter. The server runs it, so shortcuts need a connection.' },
  ], 'Create')
  if (!a?.title.trim()) return
  if ((await attempt(() => session.saveShortcut(null, a.title.trim(), a.filter.trim()))) !== undefined) toast('Shortcut created')
}
