import { useEffect, useState } from 'preact/hooks'
import { InstanceRow } from 'mymemos-web-core'
import { attempt, refresh, session, toast, useData, useOnline, type InstanceStatsRow, type UserRow } from '../core'
import { Icon } from '../components/Icon'
import { confirmDialog, Dialog, Switch } from '../components/ui'
import type { Route } from '../router'

function Banner(props: { what: string; error: string; retry: () => void }) {
  return (
    <div class="banner" style={{ margin: '8px 0' }}>
      <span style={{ flex: 1 }}>
        Could not load {props.what}: {props.error}
      </span>
      <button class="btn text" onClick={props.retry}>
        Retry
      </button>
    </div>
  )
}

/** Server administration: users, instance settings and storage. Admins only. */
export function AdminView(_: { route: Route }) {
  useData()
  const admin = session.isAdmin()
  return (
    <>
      <div class="pane-header">
        <h1>Admin</h1>
      </div>
      <div class="pane-body">
        {admin ? (
          <>
            <Users />
            <Instance />
            <Storage />
          </>
        ) : (
          <div class="empty">
            <Icon name="shield" />
            <p>Only server admins can see this page.</p>
          </div>
        )}
      </div>
    </>
  )
}

// ---- users ----------------------------------------------------------------------------------

function Users() {
  const [list, loading, error, reload] = useOnline<UserRow[]>(() => session.users(), [], [])
  const [creating, setCreating] = useState(false)
  const me = session.accounts().find((a) => a.active)?.username

  const archive = async (u: UserRow) => {
    if (!u.archived) {
      const ok = await confirmDialog('Archive user?', `${u.username} will no longer be able to sign in. Their memos are kept, and you can unarchive them later.`, 'Archive', true)
      if (!ok) return
    }
    const done = await attempt(() => session.setUserArchived(u.name, !u.archived), u.archived ? 'Could not unarchive' : 'Could not archive')
    if (done !== undefined) reload()
  }

  const remove = async (u: UserRow) => {
    const ok = await confirmDialog(
      'Delete user?',
      `${u.username} and everything they own on this server, including their memos, attachments and access tokens, will be deleted. This cannot be undone. Archive them instead if you might need it back.`,
      'Delete',
      true,
    )
    if (!ok) return
    const done = await attempt(() => session.deleteUser(u.name), 'Could not delete the user')
    if (done !== undefined) reload()
  }

  return (
    <>
      <div class="section-title" style={{ display: 'flex', alignItems: 'center' }}>
        <span style={{ flex: 1 }}>Users</span>
        <button class="btn text" onClick={() => setCreating(true)} disabled={!!error}>
          <Icon name="plus" size={18} /> New user
        </button>
      </div>
      {error && <Banner what="users" error={error} retry={reload} />}
      {!error && (
        <div class="card-box">
          {loading && list.length === 0 && <div class="row muted small">Loading…</div>}
          {list.map((u) => {
            const self = u.username === me
            return (
              <div class="row" key={u.name}>
                <Icon name={u.admin ? 'shield' : 'person'} />
                <div class="grow">
                  <div>
                    {u.displayName || u.username}
                    {u.admin && <span class="chip" style={{ marginLeft: 6 }}>Admin</span>}
                    {u.archived && <span class="chip" style={{ marginLeft: 6 }}>Archived</span>}
                    {self && <span class="small muted"> (you)</span>}
                  </div>
                  <div class="small muted" style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {u.username}
                    {u.email && ` · ${u.email}`}
                  </div>
                </div>
                {!self && (
                  <>
                    <button class="icon-btn" title={u.archived ? 'Unarchive' : 'Archive'} aria-label={u.archived ? `Unarchive ${u.username}` : `Archive ${u.username}`} onClick={() => void archive(u)}>
                      <Icon name={u.archived ? 'undo' : 'archive'} />
                    </button>
                    <button class="icon-btn" title="Delete" aria-label={`Delete ${u.username}`} onClick={() => void remove(u)}>
                      <Icon name="trash" />
                    </button>
                  </>
                )}
              </div>
            )
          })}
        </div>
      )}
      {creating && (
        <CreateUser
          onClose={() => setCreating(false)}
          onCreated={() => {
            setCreating(false)
            reload()
          }}
        />
      )}
    </>
  )
}

function CreateUser(props: { onClose: () => void; onCreated: () => void }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [admin, setAdmin] = useState(false)
  const [busy, setBusy] = useState(false)

  const submit = async (e?: Event) => {
    e?.preventDefault()
    if (!username.trim() || !password) return toast('Enter a username and a password')
    setBusy(true)
    const ok = await attempt(() => session.createUser(username.trim(), password, admin), 'Could not create the user')
    setBusy(false)
    if (ok === undefined) return
    toast(`Created ${username.trim()}`)
    props.onCreated()
  }

  return (
    <Dialog
      title="New user"
      onClose={props.onClose}
      actions={
        <>
          <button class="btn text" onClick={props.onClose}>
            Cancel
          </button>
          <button class="btn" disabled={busy} onClick={() => void submit()}>
            Create
          </button>
        </>
      }
    >
      <form onSubmit={(e) => void submit(e)}>
        <div class="field">
          <label for="nu-name">Username</label>
          <input id="nu-name" class="input" autocomplete="off" value={username} autoFocus onInput={(e) => setUsername((e.target as HTMLInputElement).value)} />
        </div>
        <div class="field">
          <label for="nu-pass">Password</label>
          <input id="nu-pass" class="input" type="password" autocomplete="new-password" value={password} onInput={(e) => setPassword((e.target as HTMLInputElement).value)} />
        </div>
        <Switch label="Admin" hint="Admins can manage users and change server settings." checked={admin} onChange={setAdmin} />
        <button type="submit" hidden />
      </form>
    </Dialog>
  )
}

// ---- instance -------------------------------------------------------------------------------

const weekStarts: [number, string][] = [
  [0, 'Sunday'],
  [1, 'Monday'],
  [6, 'Saturday'],
]

function Instance() {
  const [row, loading, error, reload] = useOnline<InstanceRow | null>(() => session.instanceGeneral(), [], null)
  const [title, setTitle] = useState('')
  const [about, setAbout] = useState('')
  const [noRegistration, setNoRegistration] = useState(false)
  const [noPassword, setNoPassword] = useState(false)
  const [noUsername, setNoUsername] = useState(false)
  const [noNickname, setNoNickname] = useState(false)
  const [weekStart, setWeekStart] = useState(0)
  const [saving, setSaving] = useState(false)

  const fill = (r: InstanceRow) => {
    setTitle(r.title)
    setAbout(r.about)
    setNoRegistration(r.disallowRegistration)
    setNoPassword(r.disallowPasswordAuth)
    setNoUsername(r.disallowChangeUsername)
    setNoNickname(r.disallowChangeNickname)
    setWeekStart(r.weekStartDayOffset)
  }

  useEffect(() => {
    if (row) fill(row)
  }, [row])

  const dirty =
    !!row &&
    (title !== row.title ||
      about !== row.about ||
      noRegistration !== row.disallowRegistration ||
      noPassword !== row.disallowPasswordAuth ||
      noUsername !== row.disallowChangeUsername ||
      noNickname !== row.disallowChangeNickname ||
      weekStart !== row.weekStartDayOffset)

  const save = async (e: Event) => {
    e.preventDefault()
    if (!row) return
    if (noPassword && !row.disallowPasswordAuth) {
      const ok = await confirmDialog(
        'Turn off password sign-in?',
        'Nobody will be able to sign in with a password, including you. Anyone without single sign-on or an access token will be locked out, and if this browser is signed out it cannot sign back in with a password. Make sure you have another way in first.',
        'Turn off',
        true,
      )
      if (!ok) return
    }
    setSaving(true)
    const done = await attempt(
      () => session.updateInstanceGeneral(new InstanceRow(title.trim(), about, noRegistration, noPassword, noUsername, noNickname, weekStart)),
      'Could not save instance settings',
    )
    setSaving(false)
    if (done === undefined) return
    toast('Instance settings saved')
    reload()
    refresh()
  }

  const options = weekStarts.some(([v]) => v === weekStart) ? weekStarts : [...weekStarts, [weekStart, `Day ${weekStart}`] as [number, string]]

  return (
    <>
      <div class="section-title">Instance</div>
      {error && <Banner what="instance settings" error={error} retry={reload} />}
      {loading && !row && <p class="muted small">Loading…</p>}
      {row && (
        <form class="card-box" style={{ padding: '6px 16px 14px' }} onSubmit={(e) => void save(e)}>
          <div class="field">
            <label for="in-title">Title</label>
            <input id="in-title" class="input" value={title} onInput={(e) => setTitle((e.target as HTMLInputElement).value)} />
          </div>
          <div class="field">
            <label for="in-about">About</label>
            <textarea id="in-about" class="textarea" rows={3} value={about} onInput={(e) => setAbout((e.target as HTMLTextAreaElement).value)} />
          </div>
          <Switch label="Disallow registration" hint="Only admins can create accounts." checked={noRegistration} onChange={setNoRegistration} />
          <Switch
            label="Disallow password sign-in"
            hint="Can lock out everyone who signs in with a password, including this browser if it needs to sign in again."
            checked={noPassword}
            onChange={setNoPassword}
          />
          <Switch label="Disallow changing username" checked={noUsername} onChange={setNoUsername} />
          <Switch label="Disallow changing display name" checked={noNickname} onChange={setNoNickname} />
          <div class="field">
            <label for="in-week">Week starts on</label>
            <select id="in-week" class="input" value={String(weekStart)} onChange={(e) => setWeekStart(Number((e.target as HTMLSelectElement).value))}>
              {options.map(([v, label]) => (
                <option key={v} value={String(v)}>
                  {label}
                </option>
              ))}
            </select>
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <button type="button" class="btn text" disabled={!dirty || saving} onClick={() => fill(row)}>
              Reset
            </button>
            <button type="submit" class="btn" disabled={!dirty || saving}>
              {saving ? 'Saving…' : 'Save'}
            </button>
          </div>
        </form>
      )}
    </>
  )
}

// ---- storage --------------------------------------------------------------------------------

function size(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB', 'TB']
  let n = bytes / 1024
  let i = 0
  while (n >= 1024 && i < units.length - 1) {
    n /= 1024
    i++
  }
  return `${n.toFixed(n < 10 ? 1 : 0)} ${units[i]}`
}

function Storage() {
  const [stats, loading, error, reload] = useOnline<InstanceStatsRow | null>(() => session.instanceStats(), [], null)
  return (
    <>
      <div class="section-title">Storage</div>
      {error && <Banner what="storage figures" error={error} retry={reload} />}
      {loading && !stats && <p class="muted small">Loading…</p>}
      {stats && (
        <div class="card-box">
          <div class="row">
            <span class="grow">Database</span>
            <span class="muted">{stats.databaseDriver || 'Unknown'}</span>
          </div>
          <div class="row">
            <span class="grow">Database size</span>
            <span class="muted">{size(stats.databaseBytes)}</span>
          </div>
          <div class="row">
            <span class="grow">Local file storage</span>
            <span class="muted">{size(stats.localStorageBytes)}</span>
          </div>
        </div>
      )}
    </>
  )
}
