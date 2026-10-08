import { useEffect, useState } from 'preact/hooks'

/**
 * Hash routes, so the page works from any path nginx serves it on and needs no rewrites:
 *
 *   #/<view>[/<arg>][?m=<memo id>&q=<query>]
 *
 * The memo rides as a parameter rather than a path so the list it was opened from stays
 * put beside it, which is the two-pane layout the apps have on wide screens.
 */
export interface Route {
  view: string
  arg: string
  memo: string | null
  q: string
}

export function parse(hash = location.hash): Route {
  const raw = hash.replace(/^#\/?/, '')
  const [path, query = ''] = raw.split('?')
  const [view = 'memos', ...rest] = path.split('/')
  const params = new URLSearchParams(query)
  return { view: view || 'memos', arg: safeDecode(rest.join('/')), memo: params.get('m'), q: params.get('q') ?? '' }
}

/** A malformed escape in a pasted link should land on a page, not take the app down. */
function safeDecode(s: string): string {
  try {
    return decodeURIComponent(s)
  } catch {
    return s
  }
}

export function href(view: string, arg = '', extra: { memo?: string | null; q?: string } = {}): string {
  const params = new URLSearchParams()
  if (extra.memo) params.set('m', extra.memo)
  if (extra.q) params.set('q', extra.q)
  const qs = params.toString()
  return `#/${view}${arg ? '/' + encodeURIComponent(arg) : ''}${qs ? '?' + qs : ''}`
}

export function go(view: string, arg = '', extra: { memo?: string | null; q?: string } = {}, replace = false): void {
  const next = href(view, arg, extra)
  if (replace) history.replaceState(null, '', next)
  else location.hash = next
  if (replace) window.dispatchEvent(new HashChangeEvent('hashchange'))
}

/** Opens a memo beside whatever list is showing. */
export function openMemo(localId: string | null, route: Route = parse()): void {
  go(route.view, route.arg, { memo: localId, q: route.q })
}

export function useRoute(): Route {
  const [route, set] = useState(parse())
  useEffect(() => {
    const on = () => set(parse())
    window.addEventListener('hashchange', on)
    return () => window.removeEventListener('hashchange', on)
  }, [])
  return route
}
