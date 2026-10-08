import { useRef, useState } from 'preact/hooks'
import { PickedFile } from 'mymemos-web-core'
import type { ImportResultRow, TemplateRow } from 'mymemos-web-core'
import { attempt, refresh, saveFile, session, toast, useData } from '../core'
import { askPassword, confirmDialog, Dialog, promptDialog, Switch } from '../components/ui'
import { Icon } from '../components/Icon'
import { href, openMemo, type Route } from '../router'

type Theme = 'system' | 'light' | 'dark'

const THEME_KEY = 'mymemos.theme'

function readTheme(): Theme {
  try {
    const t = localStorage.getItem(THEME_KEY)
    return t === 'light' || t === 'dark' ? t : 'system'
  } catch {
    return 'system'
  }
}

function applyTheme(theme: Theme): void {
  try {
    if (theme === 'system') localStorage.removeItem(THEME_KEY)
    else localStorage.setItem(THEME_KEY, theme)
  } catch {
    // Storage can be blocked; the theme still applies for this visit.
  }
  if (theme === 'system') delete document.documentElement.dataset.theme
  else document.documentElement.dataset.theme = theme
}

function today(): string {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

// Switch has its own vertical padding and must span the row, so a row holding one drops both.
const SWITCH_ROW = { display: 'block', paddingTop: 0, paddingBottom: 0 }

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

/** Runs a session setter, then bumps the UI so it re-reads the preference. */
async function setPref(run: () => Promise<void>): Promise<void> {
  await attempt(run, 'Could not save that')
  refresh()
}

export function SettingsView(props: { route: Route }) {
  useData()
  return (
    <>
      <div class="pane-header">
        <h1>Settings</h1>
      </div>
      <div class="pane-body">
        <Appearance />
        <Writing />
        <LockedMemos />
        <Templates />
        <Recurring />
        <Reminders route={props.route} />
        <Digest />
        <Notifications />
        <YourData />
        <About />
      </div>
    </>
  )
}

function Appearance() {
  const [theme, setTheme] = useState<Theme>(readTheme())
  const pick = (t: Theme) => {
    applyTheme(t)
    setTheme(t)
    refresh()
  }
  return (
    <>
      <div class="section-title">Appearance</div>
      <div class="card-box">
        <div class="row">
          <span class="grow">Theme</span>
          {(['system', 'light', 'dark'] as const).map((t) => (
            <button key={t} class={`chip${theme === t ? ' selected' : ''}`} onClick={() => pick(t)}>
              {t === 'system' ? 'System' : t === 'light' ? 'Light' : 'Dark'}
            </button>
          ))}
        </div>
        <div class="row" style={SWITCH_ROW}>
          <Switch label="Compact list" checked={session.compactList()} onChange={(v) => void setPref(() => session.setCompactList(v))} />
        </div>
        <div class="row" style={SWITCH_ROW}>
          <Switch label="Sort by last changed" checked={session.sortByModified()} onChange={(v) => void setPref(() => session.setSortByModified(v))} />
        </div>
        <div class="row" style={SWITCH_ROW}>
          <Switch
            label="Map previews"
            hint="Tiles come from openstreetmap.org, which then learns roughly where a located memo was written. Off means nothing leaves this device."
            checked={session.mapTilesEnabled()}
            onChange={(v) => void setPref(() => session.setMapTiles(v))}
          />
        </div>
      </div>
    </>
  )
}

function Writing() {
  return (
    <>
      <div class="section-title">Writing</div>
      <div class="card-box">
        <div class="row" style={SWITCH_ROW}>
          <Switch
            label="Sink completed tasks"
            hint="Ticked tasks move below open ones when you save."
            checked={session.sortCompletedTasks()}
            onChange={(v) => void setPref(() => session.setSortCompletedTasks(v))}
          />
        </div>
      </div>
    </>
  )
}

function LockedMemos() {
  const held = session.hasPassword()
  return (
    <>
      <div class="section-title">Locked memos</div>
      <div class="card-box">
        <div class="row">
          <Icon name={held ? 'unlock' : 'lock'} />
          <span class="grow">{held ? 'Password is held' : 'No password held'}</span>
          {held ? (
            <button
              class="btn text"
              onClick={() => {
                session.forgetPassword()
                refresh()
                toast('Password forgotten')
              }}
            >
              Forget
            </button>
          ) : (
            <button class="btn tonal" onClick={async () => (await askPassword()) && refresh()}>
              Set password
            </button>
          )}
        </div>
      </div>
      <p class="small muted">In a browser the password is kept only until this tab closes.</p>
    </>
  )
}

const PLACEHOLDER_NOTE = 'Placeholders: {{date}}, {{isodate}}, {{time}}, {{weekday}}, {{month}}, {{year}}.'

async function editTemplate(t: TemplateRow | null): Promise<void> {
  const v = await promptDialog(
    t ? 'Edit template' : 'New template',
    [
      { name: 'title', label: 'Title', value: t?.title ?? '' },
      { name: 'body', label: 'Body', type: 'textarea', value: t?.body ?? '' },
    ],
    'Save',
    PLACEHOLDER_NOTE,
  )
  if (!v) return
  if (!v.title.trim()) {
    toast('A template needs a title')
    return
  }
  await attempt(() => session.saveTemplate(t?.id ?? 0, v.title.trim(), v.body), 'Could not save the template')
}

function Templates() {
  const list = session.templates()
  return (
    <>
      <div class="section-title" style={{ display: 'flex', alignItems: 'center' }}>
        <span style={{ flex: 1 }}>Templates</span>
        <button class="btn text" onClick={() => void editTemplate(null)}>
          <Icon name="plus" size={18} /> New
        </button>
      </div>
      <div class="card-box">
        {list.length === 0 && <div class="row muted">No templates yet.</div>}
        {list.map((t) => (
          <div class="row" key={t.id}>
            <Icon name="template" />
            <div class="grow">
              <div>{t.title}</div>
              <div class="muted small" style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {t.body.split('\n')[0]}
              </div>
            </div>
            <button class="icon-btn" aria-label={`Edit ${t.title}`} onClick={() => void editTemplate(t)}>
              <Icon name="edit" size={20} />
            </button>
            <button
              class="icon-btn"
              aria-label={`Delete ${t.title}`}
              onClick={async () => {
                if (await confirmDialog('Delete template?', `“${t.title}” will be removed.`, 'Delete', true)) {
                  await attempt(() => session.deleteTemplate(t.id), 'Could not delete the template')
                }
              }}
            >
              <Icon name="trash" size={20} />
            </button>
          </div>
        ))}
      </div>
      <p class="small muted">{PLACEHOLDER_NOTE}</p>
    </>
  )
}

function Recurring() {
  const rows = session.recurring()
  return (
    <>
      <div class="section-title">Recurring templates</div>
      <div class="card-box">
        {rows.length === 0 && <div class="row muted">Add a template first, then it can make a memo every day.</div>}
        {rows.map((r) => (
          <div class="row" key={r.templateId} style={{ paddingTop: 0, paddingBottom: 0 }}>
            <div class="grow">
              <Switch
                label={r.templateTitle}
                hint={r.enabled ? r.nextLabel : undefined}
                checked={r.enabled}
                onChange={(v) => void setPref(() => session.setRecurring(r.templateTitle, r.hour, r.minute, v))}
              />
            </div>
            <input
              class="input"
              type="time"
              style={{ width: 'auto' }}
              aria-label={`Time for ${r.templateTitle}`}
              value={`${pad(r.hour)}:${pad(r.minute)}`}
              onChange={(e) => {
                const [h, m] = (e.target as HTMLInputElement).value.split(':').map(Number)
                if (Number.isFinite(h) && Number.isFinite(m)) void setPref(() => session.setRecurring(r.templateTitle, h, m, r.enabled))
              }}
            />
          </div>
        ))}
      </div>
      <p class="small muted">A browser can only create these while MyMemos is open. Any that were missed are made when it is next opened.</p>
    </>
  )
}

function Reminders(props: { route: Route }) {
  const list = session.reminders()
  return (
    <>
      <div class="section-title">Reminders</div>
      <div class="card-box">
        {list.length === 0 && <div class="row muted">No reminders.</div>}
        {list.map((r) => (
          <div class="row" key={r.id}>
            <Icon name="alarm" />
            <button
              class="grow"
              style={{ textAlign: 'left', background: 'none', border: 0, padding: 0, color: 'inherit', font: 'inherit', cursor: 'pointer' }}
              onClick={() => openMemo(r.memoLocalId, props.route)}
            >
              <div>{r.memoTitle || 'Untitled'}</div>
              <div class={`small${r.overdue ? ' error-text' : ' muted'}`}>
                {r.whenLabel}
                {r.overdue ? ' (overdue)' : ''}
                {r.note ? ` · ${r.note}` : ''}
              </div>
            </button>
            <button class="icon-btn" aria-label="Remove reminder" onClick={() => void attempt(() => session.removeReminder(r.id), 'Could not remove the reminder')}>
              <Icon name="close" size={20} />
            </button>
          </div>
        ))}
      </div>
      <p class="small muted">Set a reminder from a memo's menu.</p>
    </>
  )
}

function Digest() {
  return (
    <>
      <div class="section-title">Weekly digest</div>
      <div class="card-box">
        <div class="row" style={SWITCH_ROW}>
          <Switch
            label="Weekly digest"
            hint={session.weeklyDigest() ? session.digestNextLabel() : 'A short look back at your week.'}
            checked={session.weeklyDigest()}
            onChange={(v) => void setPref(() => session.setWeeklyDigest(v))}
          />
        </div>
      </div>
    </>
  )
}

function Notifications() {
  const supported = 'Notification' in window
  const [permission, setPermission] = useState<NotificationPermission | null>(supported ? Notification.permission : null)
  if (!supported) return null
  const words: Record<NotificationPermission, string> = { granted: 'Allowed', denied: 'Blocked', default: 'Not asked yet' }
  return (
    <>
      <div class="section-title">Notifications</div>
      <div class="card-box">
        <div class="row">
          <Icon name="bell" />
          <span class="grow">{permission ? words[permission] : ''}</span>
          {permission === 'default' && (
            <button class="btn tonal" onClick={async () => setPermission(await Notification.requestPermission())}>
              Allow
            </button>
          )}
        </div>
      </div>
      {permission === 'denied' && <p class="small muted">Notifications are blocked. Change this in the browser's site settings.</p>}
    </>
  )
}

function YourData() {
  const importInput = useRef<HTMLInputElement>(null)
  const restoreInput = useRef<HTMLInputElement>(null)
  const [result, setResult] = useState<ImportResultRow | null>(null)
  const [busy, setBusy] = useState(false)

  const run = async (work: () => Promise<void>) => {
    setBusy(true)
    try {
      await work()
    } finally {
      setBusy(false)
    }
  }

  const exportAll = () =>
    run(async () => {
      const bytes = await attempt(() => session.exportMarkdown(), 'Could not export')
      if (bytes) saveFile(bytes, `mymemos-export-${today()}.zip`, 'application/zip')
    })

  const importFiles = (files: File[]) =>
    run(async () => {
      const picked = await Promise.all(files.map(async (f) => new PickedFile(f.name, new Uint8Array(await f.arrayBuffer()), f.lastModified)))
      const r = await attempt(() => session.importMarkdown(picked), 'Could not import')
      if (r) setResult(r)
    })

  const backup = async () => {
    const v = await promptDialog(
      'Encrypted backup',
      [
        { name: 'password', label: 'Password', type: 'password' },
        { name: 'again', label: 'Password again', type: 'password' },
      ],
      'Back up',
      'At least 8 characters. Without it the backup cannot be opened.',
    )
    if (!v) return
    if (v.password.length < 8) return toast('The password needs at least 8 characters')
    if (v.password !== v.again) return toast('The passwords do not match')
    await run(async () => {
      const bytes = await attempt(() => session.backup(v.password), 'Could not make the backup')
      if (bytes) saveFile(bytes, `mymemos-web-backup-${today()}.mymemos`, 'application/octet-stream')
    })
  }

  const restore = async (file: File) => {
    const v = await promptDialog('Restore backup', [{ name: 'password', label: 'Backup password', type: 'password' }], 'Next')
    if (!v || !v.password) return
    const ok = await confirmDialog('Restore this backup?', "It replaces this browser's copy of the active account.", 'Restore', true)
    if (!ok) return
    await run(async () => {
      try {
        await session.restore(new Uint8Array(await file.arrayBuffer()), v.password)
        refresh()
        toast('Backup restored')
      } catch {
        // restore() fails the same way for a wrong password and a damaged file, so both get one message.
        toast('Wrong password or damaged backup')
      }
    })
  }

  return (
    <>
      <div class="section-title">Your data</div>
      <div class="card-box">
        <div class="row">
          <Icon name="download" />
          <span class="grow">Export as Markdown</span>
          <button class="btn tonal" disabled={busy} onClick={() => void exportAll()}>
            Export
          </button>
        </div>
        <div class="row">
          <Icon name="upload" />
          <span class="grow">Import Markdown files or a zip</span>
          <button class="btn tonal" disabled={busy} onClick={() => importInput.current?.click()}>
            Import
          </button>
          <input
            ref={importInput}
            type="file"
            hidden
            multiple
            accept=".md,.markdown,.zip,text/markdown,application/zip"
            onChange={(e) => {
              const input = e.target as HTMLInputElement
              const files = Array.from(input.files ?? [])
              input.value = ''
              if (files.length) void importFiles(files)
            }}
          />
        </div>
        <div class="row">
          <Icon name="shield" />
          <span class="grow">Encrypted backup</span>
          <button class="btn tonal" disabled={busy} onClick={() => void backup()}>
            Back up
          </button>
        </div>
        <div class="row">
          <Icon name="undo" />
          <span class="grow">Restore a backup</span>
          <button class="btn outline" disabled={busy} onClick={() => restoreInput.current?.click()}>
            Restore
          </button>
          <input
            ref={restoreInput}
            type="file"
            hidden
            accept=".mymemos,application/octet-stream"
            onChange={(e) => {
              const input = e.target as HTMLInputElement
              const file = input.files?.[0]
              input.value = ''
              if (file) void restore(file)
            }}
          />
        </div>
      </div>
      <p class="small muted">
        A web backup holds this browser's copy. The apps cannot read it, and their backups cannot be read here. The server is what they all share.
      </p>
      {result && (
        <Dialog
          title="Import finished"
          onClose={() => setResult(null)}
          actions={
            <button class="btn" onClick={() => setResult(null)}>
              OK
            </button>
          }
        >
          <p>
            {result.imported} imported, {result.duplicates} already here, {result.skipped} skipped.
          </p>
          {result.failures.length > 0 && (
            <>
              <p class="error-text small">These could not be read:</p>
              <ul class="small" style={{ maxHeight: 200, overflowY: 'auto' }}>
                {result.failures.map((f, i) => (
                  <li key={i}>{f}</li>
                ))}
              </ul>
            </>
          )}
        </Dialog>
      )}
    </>
  )
}

function About() {
  const shortcuts: [string, string][] = [
    ['N', 'New memo'],
    ['/', 'Search'],
    ['Ctrl/Cmd + Enter', 'Save'],
    ['Esc', 'Close'],
  ]
  return (
    <>
      <div class="section-title">About</div>
      <div class="card-box">
        <div class="row">
          <span class="grow">Signed in as</span>
          <span class="muted">{session.signedInAs() ?? 'Nobody'}</span>
        </div>
        <div class="row">
          <span class="grow">Server version</span>
          <span class="muted">{session.serverVersion() || 'Unknown'}</span>
        </div>
        <a class="row" href={href('sync')} style={{ color: 'inherit', textDecoration: 'none' }}>
          <Icon name="sync" />
          <span class="grow">Sync status</span>
          <Icon name="chevron" size={18} />
        </a>
      </div>
      <div class="section-title">Keyboard shortcuts</div>
      <div class="card-box">
        {shortcuts.map(([key, what]) => (
          <div class="row" key={key}>
            <span class="grow">{what}</span>
            <kbd class="chip">{key}</kbd>
          </div>
        ))}
      </div>
    </>
  )
}
