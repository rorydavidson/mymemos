import { useEffect, useState } from 'preact/hooks'
import { errorText, session } from '../core'
import { Icon } from '../components/Icon'

/**
 * Sign in to a Memos server. The default is the server this page is served beside; a
 * remembered address can be picked, or another typed. Password sign-in mints a token for
 * this browser, as the apps do; a token made on the server works too.
 */
export function SignIn(props: { onDone: () => void; adding?: boolean }) {
  const known = session.knownServers()
  const [server, setServer] = useState(known[0] ?? session.defaultServer())
  const [mode, setMode] = useState<'password' | 'token'>('password')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [token, setToken] = useState('')
  const [version, setVersion] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    setVersion(null)
    const t = setTimeout(() => {
      if (!server.trim()) return
      session.probe(server).then(
        (v) => setVersion(v || 'unknown'),
        () => setVersion(''),
      )
    }, 400)
    return () => clearTimeout(t)
  }, [server])

  const submit = async (e: Event) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      if (mode === 'password') await session.signIn(server, username.trim(), password)
      else await session.signInWithToken(server, token.trim())
      setPassword('')
      setToken('')
      void session.sync(true)
      props.onDone()
    } catch (err) {
      setError(errorText(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div style={{ minHeight: '100%', display: 'grid', placeItems: 'center', padding: 16 }}>
      <form class="dialog" style={{ boxShadow: 'var(--shadow)' }} onSubmit={(e) => void submit(e)}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
          <img src="/icons/icon-192.png" alt="" width={44} height={44} style={{ borderRadius: 12 }} />
          <div>
            <h2 style={{ margin: 0 }}>{props.adding ? 'Add an account' : 'MyMemos'}</h2>
            <div class="muted small">A client for your Memos server</div>
          </div>
        </div>
        <div class="field">
          <label for="server">Server</label>
          <input id="server" class="input" list="known-servers" value={server} onInput={(e) => setServer((e.target as HTMLInputElement).value)} autocomplete="url" inputMode="url" />
          <datalist id="known-servers">
            {[session.defaultServer(), ...known].map((s) => (
              <option key={s} value={s} />
            ))}
          </datalist>
          <span class="small muted">
            {version === null ? 'Checking…' : version ? `Memos ${version}` : 'No Memos server answers there yet.'}
          </span>
        </div>
        <div style={{ display: 'flex', gap: 6, margin: '8px 0' }}>
          <button type="button" class={`chip${mode === 'password' ? ' selected' : ''}`} onClick={() => setMode('password')}>
            Password
          </button>
          <button type="button" class={`chip${mode === 'token' ? ' selected' : ''}`} onClick={() => setMode('token')}>
            Access token
          </button>
        </div>
        {mode === 'password' ? (
          <>
            <div class="field">
              <label for="username">Username</label>
              <input id="username" class="input" value={username} autocomplete="username" autoCapitalize="none" onInput={(e) => setUsername((e.target as HTMLInputElement).value)} />
            </div>
            <div class="field">
              <label for="password">Password</label>
              <input id="password" class="input" type="password" value={password} autocomplete="current-password" onInput={(e) => setPassword((e.target as HTMLInputElement).value)} />
            </div>
            <p class="small muted">
              The password is used once to make an access token for this browser, good for 90 days and revoked when you sign out. It is not stored.
            </p>
          </>
        ) : (
          <div class="field">
            <label for="token">Personal access token</label>
            <input id="token" class="input" type="password" value={token} autocomplete="off" onInput={(e) => setToken((e.target as HTMLInputElement).value)} />
            <span class="small muted">Made in Memos under Settings. It stays yours: signing out here leaves it alone.</span>
          </div>
        )}
        {error && (
          <p class="error-text" role="alert">
            <Icon name="warning" size={16} /> {error}
          </p>
        )}
        <div class="actions">
          {props.adding && (
            <button type="button" class="btn text" onClick={props.onDone}>
              Cancel
            </button>
          )}
          <button class="btn" type="submit" disabled={busy || (mode === 'password' ? !username || !password : !token)}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </div>
      </form>
    </div>
  )
}
