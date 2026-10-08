import { useEffect, useState } from 'preact/hooks'
import { openSession, type WebSession } from 'mymemos-web-core'

export type * from 'mymemos-web-core'

/**
 * The one WebSession the page talks to. Everything about memos, sync and the server goes
 * through it; this file only adds what a UI needs on top: re-rendering when data changes,
 * toasts, and turning errors into words.
 */
export let session: WebSession

const listeners = new Set<() => void>()
let version = 0

let publicMemosUrl = ''

export async function boot(): Promise<void> {
  session = await openSession()
  // Set by the container when Memos' own pages live at another address than this app.
  fetch('/config.json')
    .then((r) => (r.ok ? r.json() : null))
    .then((c: { publicMemosUrl?: string } | null) => {
      publicMemosUrl = (c?.publicMemosUrl ?? '').replace(/\/+$/, '')
    })
    .catch(() => undefined)
  // WebSession has no way to remove a listener, so one is registered and fanned out here.
  session.onChange(() => {
    version++
    listeners.forEach((l) => l())
  })
}

/** Re-renders the calling component whenever local data changes. Returns a change counter. */
export function useData(): number {
  const [, set] = useState(version)
  useEffect(() => {
    const l = () => set(version)
    listeners.add(l)
    return () => {
      listeners.delete(l)
    }
  }, [])
  return version
}

/** Bumps every subscriber, for changes the session does not report itself (a pref, say). */
export function refresh(): void {
  version++
  listeners.forEach((l) => l())
}

/** Runs an async read, re-running it when [deps] change or local data does. */
export function useAsync<T>(load: () => Promise<T>, deps: unknown[], initial: T): [T, boolean, string | null, () => void] {
  const v = useData()
  const [state, setState] = useState<{ value: T; loading: boolean; error: string | null }>({ value: initial, loading: true, error: null })
  const [nonce, setNonce] = useState(0)
  useEffect(() => {
    let live = true
    setState((s) => ({ ...s, loading: true }))
    load().then(
      (value) => live && setState({ value, loading: false, error: null }),
      (e) => live && setState((s) => ({ ...s, loading: false, error: errorText(e) })),
    )
    return () => {
      live = false
    }
  }, [...deps, nonce, v])
  return [state.value, state.loading, state.error, () => setNonce((n) => n + 1)]
}

/** The same, but only re-running on [deps]: for online-only screens that should not refetch on every local change. */
export function useOnline<T>(load: () => Promise<T>, deps: unknown[], initial: T): [T, boolean, string | null, () => void] {
  const [state, setState] = useState<{ value: T; loading: boolean; error: string | null }>({ value: initial, loading: true, error: null })
  const [nonce, setNonce] = useState(0)
  useEffect(() => {
    let live = true
    setState((s) => ({ ...s, loading: true, error: null }))
    load().then(
      (value) => live && setState({ value, loading: false, error: null }),
      (e) => live && setState((s) => ({ ...s, loading: false, error: errorText(e) })),
    )
    return () => {
      live = false
    }
  }, [...deps, nonce])
  return [state.value, state.loading, state.error, () => setNonce((n) => n + 1)]
}

/** A share link on Memos' own address when the container names one, so it opens Memos' page. */
export function shareUrl(url: string): string {
  if (!publicMemosUrl) return url
  const u = new URL(url)
  return publicMemosUrl + u.pathname + u.search
}

export function errorText(e: unknown): string {
  if (e instanceof Error) return e.message || e.name
  if (typeof e === 'string') return e
  const m = (e as { message?: unknown } | null)?.message
  return typeof m === 'string' && m ? m : 'Something went wrong'
}

// ---- toasts ---------------------------------------------------------------------------------

export interface Toast {
  id: number
  text: string
  action?: { label: string; run: () => void }
}

let toastId = 0
let toasts: Toast[] = []
const toastListeners = new Set<(t: Toast[]) => void>()

export function toast(text: string, action?: Toast['action'], ms = 5000): void {
  const t = { id: ++toastId, text, action }
  toasts = [...toasts, t]
  toastListeners.forEach((l) => l(toasts))
  setTimeout(() => {
    toasts = toasts.filter((x) => x.id !== t.id)
    toastListeners.forEach((l) => l(toasts))
  }, ms)
}

export function useToasts(): Toast[] {
  const [list, set] = useState(toasts)
  useEffect(() => {
    toastListeners.add(set)
    return () => {
      toastListeners.delete(set)
    }
  }, [])
  return list
}

/** Wraps an action so a failure becomes a toast rather than an unhandled rejection. */
export async function attempt<T>(run: () => Promise<T>, failure = 'Could not do that'): Promise<T | undefined> {
  try {
    return await run()
  } catch (e) {
    toast(`${failure}: ${errorText(e)}`)
    return undefined
  }
}

/** Uint8Array → a file the browser saves. */
export function saveFile(bytes: Uint8Array, name: string, type: string): void {
  const url = URL.createObjectURL(new Blob([bytes as BlobPart], { type }))
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

export function hex(colour: number): string {
  return '#' + colour.toString(16).padStart(6, '0')
}

/** The pastel tint a note colour is shown as, as on Android: mostly card, a little colour. */
export function tint(colour: number, dark: boolean): string {
  return `color-mix(in srgb, ${hex(colour)} ${dark ? 22 : 16}%, var(--card))`
}

export function isDark(): boolean {
  const forced = document.documentElement.dataset.theme
  if (forced) return forced === 'dark'
  return matchMedia('(prefers-color-scheme: dark)').matches
}
