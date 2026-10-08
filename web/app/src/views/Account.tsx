import type { ComponentChildren } from 'preact'
import { useEffect, useState } from 'preact/hooks'
import { attempt, errorText, refresh, session, toast, useData, useOnline, type ProfileRow, type StatsRow, type TokenRow, type WebhookRow } from '../core'
import { Icon } from '../components/Icon'
import { confirmDialog, Dialog, promptDialog } from '../components/ui'
import { go, type Route } from '../router'
import { SignIn } from './SignIn'

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

function SectionHeader(props: { title: string; action?: ComponentChildren }) {
  return (
    <div class="section-title" style={{ display: 'flex', alignItems: 'center' }}>
      <span style={{ flex: 1 }}>{props.title}</span>
      {props.action}
    </div>
  )
}

/** Accounts, profile, tokens, webhooks and statistics for the signed-in user. */
export function AccountView(_: { route: Route }) {
  return (
    <>
      <div class="pane-header">
        <h1>Account</h1>
      </div>
      <div class="pane-body">
        <Accounts />
        <Profile />
        <DefaultVisibility />
        <Tokens />
        <Webhooks />
        <Stats />
      </div>
    </>
  )
}

// ---- accounts -------------------------------------------------------------------------------

function Accounts() {
  useData()
  const [adding, setAdding] = useState(false)
  const accounts = session.accounts()
  const active = accounts.find((a) => a.active)

  const switchTo = async (id: number) => {
    const ok = await attempt(() => session.switchAccount(id), 'Could not switch account')
    if (ok === undefined) return
    refresh()
    go('memos')
  }

  const signOut = async () => {
    if (!active) return
    const ok = await confirmDialog(
      'Sign out?',
      `This signs ${active.username} out of this browser. The access token this browser made is revoked on the server, and every memo, draft and unsent change stored here for this account is removed. Anything not yet synced will be lost. Other devices are not affected.`,
      'Sign out',
      true,
    )
    if (!ok) return
    const done = await attempt(() => session.signOut(), 'Could not sign out')
    if (done === undefined) return
    refresh()
    if (session.accounts().length === 0) location.reload()
    else go('memos')
  }

  return (
    <>
      <SectionHeader
        title="Accounts"
        action={
          <button class="btn text" onClick={() => setAdding(true)}>
            <Icon name="plus" size={18} /> Add account
          </button>
        }
      />
      <div class="card-box">
        {accounts.map((a) => (
          <div class="row" key={a.id}>
            <Icon name={a.active ? 'check' : 'person'} />
            <div class="grow">
              <div>
                {a.displayName || a.username} {a.admin && <span class="chip" style={{ marginLeft: 6 }}>Admin</span>}
              </div>
              <div class="small muted" style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>
                {a.username} on {a.serverUrl}
              </div>
            </div>
            {a.active ? (
              <span class="small muted">Active</span>
            ) : (
              <button class="btn outline" onClick={() => void switchTo(a.id)}>
                Switch
              </button>
            )}
          </div>
        ))}
        {active && (
          <div class="row">
            <span class="grow muted small">Signed in as {active.username}</span>
            <button class="btn text danger" onClick={() => void signOut()}>
              <Icon name="logout" size={18} /> Sign out
            </button>
          </div>
        )}
      </div>
      {adding && (
        <div class="sheet-full">
          <div class="pane-header">
            <h1>Add account</h1>
            <button class="icon-btn" aria-label="Close" title="Close" onClick={() => setAdding(false)}>
              <Icon name="close" />
            </button>
          </div>
          <div style={{ flex: 1, overflowY: 'auto' }}>
            <SignIn
              adding
              onDone={() => {
                setAdding(false)
                refresh()
                go('memos')
              }}
            />
          </div>
        </div>
      )}
    </>
  )
}

// ---- profile --------------------------------------------------------------------------------

function Profile() {
  const [profile, loading, error, reload] = useOnline<ProfileRow | null>(() => session.profile(), [], null)
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [about, setAbout] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!profile) return
    setDisplayName(profile.displayName)
    setEmail(profile.email)
    setAbout(profile.about)
  }, [profile])

  const dirty = !!profile && (displayName !== profile.displayName || email !== profile.email || about !== profile.about)

  const save = async (e: Event) => {
    e.preventDefault()
    setSaving(true)
    const ok = await attempt(() => session.updateProfile(displayName.trim(), about, email.trim()), 'Could not save profile')
    setSaving(false)
    if (ok === undefined) return
    toast('Profile saved')
    reload()
    refresh()
  }

  const changePassword = async () => {
    const v = await promptDialog(
      'Change password',
      [
        { name: 'password', label: 'New password', type: 'password' },
        { name: 'confirm', label: 'Type it again', type: 'password' },
      ],
      'Change',
      'Other devices signed in with a password will need the new one next time they sign in. Access tokens keep working.',
    )
    if (!v) return
    if (!v.password) return toast('The password cannot be empty')
    if (v.password !== v.confirm) return toast('The passwords do not match')
    const ok = await attempt(() => session.changePassword(v.password), 'Could not change password')
    if (ok !== undefined) toast('Password changed')
  }

  return (
    <>
      <SectionHeader
        title="Profile"
        action={
          <button class="btn text" onClick={() => void changePassword()} disabled={!profile}>
            <Icon name="lock" size={18} /> Change password
          </button>
        }
      />
      {error && <Banner what="your profile" error={error} retry={reload} />}
      {loading && !profile && <p class="muted small">Loading…</p>}
      {profile && (
        <form class="card-box" style={{ padding: '6px 16px 14px' }} onSubmit={(e) => void save(e)}>
          <div class="field">
            <label class="label">Username</label>
            <span>{profile.username}</span>
          </div>
          <div class="field">
            <label for="acc-name">Display name</label>
            <input id="acc-name" class="input" value={displayName} onInput={(e) => setDisplayName((e.target as HTMLInputElement).value)} />
          </div>
          <div class="field">
            <label for="acc-email">Email</label>
            <input id="acc-email" class="input" type="email" value={email} onInput={(e) => setEmail((e.target as HTMLInputElement).value)} />
          </div>
          <div class="field">
            <label for="acc-about">About</label>
            <textarea id="acc-about" class="textarea" rows={3} value={about} onInput={(e) => setAbout((e.target as HTMLTextAreaElement).value)} />
          </div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <button
              type="button"
              class="btn text"
              disabled={!dirty || saving}
              onClick={() => {
                setDisplayName(profile.displayName)
                setEmail(profile.email)
                setAbout(profile.about)
              }}
            >
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

// ---- default visibility ---------------------------------------------------------------------

const visibilities: [string, string][] = [
  ['PRIVATE', 'Private'],
  ['PROTECTED', 'Workspace'],
  ['PUBLIC', 'Public'],
]

function DefaultVisibility() {
  const [value, loading, error, reload] = useOnline<string>(() => session.defaultVisibility(), [], '')

  const set = async (v: string) => {
    const ok = await attempt(() => session.setDefaultVisibility(v), 'Could not change the default')
    if (ok !== undefined) reload()
  }

  return (
    <>
      <SectionHeader title="New memos are" />
      {error && <Banner what="the default visibility" error={error} retry={reload} />}
      <div class="card-box">
        <div class="row">
          <span class="grow">Default visibility</span>
          <select class="input" style={{ width: 'auto' }} value={value} disabled={loading || !!error} onChange={(e) => void set((e.target as HTMLSelectElement).value)}>
            {!value && <option value="">…</option>}
            {visibilities.map(([v, label]) => (
              <option key={v} value={v}>
                {label}
              </option>
            ))}
          </select>
        </div>
      </div>
    </>
  )
}

// ---- access tokens --------------------------------------------------------------------------

function Tokens() {
  const [list, loading, error, reload] = useOnline<TokenRow[]>(() => session.tokens(), [], [])
  const [created, setCreated] = useState<string | null>(null)

  const create = async () => {
    const v = await promptDialog(
      'New access token',
      [
        { name: 'label', label: 'Label', placeholder: 'e.g. Laptop script' },
        { name: 'days', label: 'Expires after (days)', type: 'number', value: '30', hint: 'Use 0 for a token that never expires.' },
      ],
      'Create',
      'Anyone holding this token can read and change your memos until it expires or is deleted.',
    )
    if (!v) return
    const label = v.label.trim()
    const days = Number(v.days || '0')
    if (!label) return toast('Give the token a label')
    if (!Number.isInteger(days) || days < 0) return toast('Expiry must be a whole number of days')
    const token = await attempt(() => session.createToken(label, days), 'Could not create the token')
    if (token === undefined) return
    setCreated(token)
    reload()
  }

  const remove = async (t: TokenRow) => {
    const ok = t.thisDevice
      ? await confirmDialog(
          'Delete this browser’s token?',
          `"${t.label}" is the token this browser uses to sync. Deleting it signs this browser out of sync straight away: changes not yet sent will wait until you sign in again. This cannot be undone.`,
          'Delete and sign out of sync',
          true,
        )
      : await confirmDialog('Delete token?', `"${t.label}" stops working immediately. Anything using it will lose access. This cannot be undone.`, 'Delete', true)
    if (!ok) return
    const done = await attempt(() => session.deleteToken(t.name), 'Could not delete the token')
    if (done === undefined) return
    reload()
    refresh()
  }

  const copy = async () => {
    if (!created) return
    try {
      await navigator.clipboard.writeText(created)
      toast('Token copied')
    } catch (e) {
      toast(`Could not copy: ${errorText(e)}`)
    }
  }

  return (
    <>
      <SectionHeader
        title="Access tokens"
        action={
          <button class="btn text" onClick={() => void create()} disabled={!!error}>
            <Icon name="plus" size={18} /> New token
          </button>
        }
      />
      {error && <Banner what="access tokens" error={error} retry={reload} />}
      {!error && (
        <div class="card-box">
          {loading && list.length === 0 && <div class="row muted small">Loading…</div>}
          {!loading && list.length === 0 && <div class="row muted small">No access tokens.</div>}
          {list.map((t) => (
            <div class="row" key={t.name}>
              <Icon name="lock" />
              <div class="grow">
                <div>
                  {t.label || 'Untitled'} {t.thisDevice && <span class="chip" style={{ marginLeft: 6 }}>This browser</span>}
                </div>
                <div class="small muted">
                  Created {t.createdLabel || 'unknown'} · {t.expiresLabel ? `Expires ${t.expiresLabel}` : 'Never expires'} · {t.lastUsedLabel ? `Last used ${t.lastUsedLabel}` : 'Not used yet'}
                </div>
              </div>
              <button class="icon-btn" title="Delete" aria-label={`Delete ${t.label}`} onClick={() => void remove(t)}>
                <Icon name="trash" />
              </button>
            </div>
          ))}
        </div>
      )}
      {created && (
        <Dialog
          title="Token created"
          onClose={() => setCreated(null)}
          actions={
            <>
              <button class="btn text" onClick={() => void copy()}>
                Copy
              </button>
              <button class="btn" onClick={() => setCreated(null)}>
                Done
              </button>
            </>
          }
        >
          <p class="muted small">Copy it now. It will not be shown again, and it gives full access to your account, so keep it somewhere safe.</p>
          <input class="input" readOnly value={created} style={{ fontFamily: 'monospace' }} onFocus={(e) => (e.target as HTMLInputElement).select()} />
        </Dialog>
      )}
    </>
  )
}

// ---- webhooks -------------------------------------------------------------------------------

function Webhooks() {
  const [list, loading, error, reload] = useOnline<WebhookRow[]>(() => session.webhooks(), [], [])

  const create = async () => {
    const v = await promptDialog(
      'New webhook',
      [
        { name: 'name', label: 'Name' },
        { name: 'url', label: 'URL', type: 'url', placeholder: 'https://' },
      ],
      'Create',
      'The server will send your memo changes to this address, including private memos.',
    )
    if (!v) return
    const name = v.name.trim()
    const url = v.url.trim()
    if (!name || !url) return toast('Give the webhook a name and a URL')
    const ok = await attempt(() => session.createWebhook(name, url), 'Could not create the webhook')
    if (ok !== undefined) reload()
  }

  const remove = async (w: WebhookRow) => {
    const ok = await confirmDialog('Delete webhook?', `"${w.displayName}" will stop receiving memo changes.`, 'Delete', true)
    if (!ok) return
    const done = await attempt(() => session.deleteWebhook(w.name), 'Could not delete the webhook')
    if (done !== undefined) reload()
  }

  return (
    <>
      <SectionHeader
        title="Webhooks"
        action={
          <button class="btn text" onClick={() => void create()} disabled={!!error}>
            <Icon name="plus" size={18} /> New webhook
          </button>
        }
      />
      {error && <Banner what="webhooks" error={error} retry={reload} />}
      {!error && (
        <div class="card-box">
          {loading && list.length === 0 && <div class="row muted small">Loading…</div>}
          {!loading && list.length === 0 && <div class="row muted small">No webhooks.</div>}
          {list.map((w) => (
            <div class="row" key={w.name}>
              <Icon name="link" />
              <div class="grow">
                <div>{w.displayName || 'Untitled'}</div>
                <div class="small muted" style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {w.url}
                </div>
              </div>
              <button class="icon-btn" title="Delete" aria-label={`Delete ${w.displayName}`} onClick={() => void remove(w)}>
                <Icon name="trash" />
              </button>
            </div>
          ))}
        </div>
      )}
    </>
  )
}

// ---- statistics -----------------------------------------------------------------------------

function Stats() {
  const [stats, loading, error, reload] = useOnline<StatsRow | null>(() => session.stats(), [], null)
  const rows: [string, number][] = stats
    ? [
        ['Memos', stats.totalMemos],
        ['With links', stats.links],
        ['With code', stats.code],
        ['Tasks', stats.todos],
        ['Open tasks', stats.undone],
        ['Days written', stats.activeDays],
      ]
    : []
  return (
    <>
      <SectionHeader title="Statistics" />
      {error && <Banner what="statistics" error={error} retry={reload} />}
      {loading && !stats && <p class="muted small">Loading…</p>}
      {stats && (
        <div class="card-box">
          {rows.map(([label, n]) => (
            <div class="row" key={label}>
              <span class="grow">{label}</span>
              <span class="muted">{n.toLocaleString()}</span>
            </div>
          ))}
          {stats.tagCounts.length > 0 && (
            <div class="row" style={{ flexWrap: 'wrap', gap: 6 }}>
              {stats.tagCounts.map((t) => (
                <span class="chip" key={t.tag}>
                  #{t.tag} {t.count}
                </span>
              ))}
            </div>
          )}
        </div>
      )}
    </>
  )
}
