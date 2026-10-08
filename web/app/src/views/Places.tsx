import { useState } from 'preact/hooks'
import { attempt, refresh, session, useData, type PlacedMemo } from '../core'
import { Icon } from '../components/Icon'
import { MemoCard } from '../components/MemoList'
import { TileMap } from '../components/TileMap'
import { useTagStyles } from '../components/ui'
import { openMemo, type Route } from '../router'

type Tab = 'nearby' | 'journey'

/** Memos by where they were written: what is near you now, and a day's route on a map. */
export function PlacesView(props: { route: Route }) {
  useData()
  const [tab, setTab] = useState<Tab>('nearby')
  return (
    <>
      <div class="pane-header">
        <h1>Places</h1>
      </div>
      <div class="pane-body">
        <div style={{ display: 'flex', gap: 6, margin: '8px 0 12px' }}>
          <button class={`chip${tab === 'nearby' ? ' selected' : ''}`} onClick={() => setTab('nearby')}>
            Nearby
          </button>
          <button class={`chip${tab === 'journey' ? ' selected' : ''}`} onClick={() => setTab('journey')}>
            Journey
          </button>
        </div>
        {tab === 'nearby' ? <Nearby route={props.route} /> : <Journey route={props.route} />}
      </div>
    </>
  )
}

function distance(metres: number): string {
  if (metres < 1000) return `${Math.round(metres)} m`
  return `${(metres / 1000).toFixed(1)} km`
}

type Fix = { state: 'idle' | 'locating' } | { state: 'ready'; lat: number; lon: number } | { state: 'failed'; message: string }

function Nearby(props: { route: Route }) {
  const tagStyle = useTagStyles()
  const [fix, setFix] = useState<Fix>({ state: 'idle' })

  const locate = () => {
    if (!('geolocation' in navigator)) {
      setFix({ state: 'failed', message: 'This browser cannot share your location.' })
      return
    }
    setFix({ state: 'locating' })
    navigator.geolocation.getCurrentPosition(
      (p) => setFix({ state: 'ready', lat: p.coords.latitude, lon: p.coords.longitude }),
      (e) =>
        setFix({
          state: 'failed',
          message:
            e.code === e.PERMISSION_DENIED
              ? 'Location access is blocked. Allow it for this site in your browser settings, then try again.'
              : e.code === e.TIMEOUT
                ? 'Finding your location took too long. Try again.'
                : 'Your location is not available right now.',
        }),
      { enableHighAccuracy: false, timeout: 15_000, maximumAge: 60_000 },
    )
  }

  const rows = fix.state === 'ready' ? session.nearby(fix.lat, fix.lon) : []

  return (
    <>
      <div class="card-box" style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px', flexWrap: 'wrap' }}>
        <span class="grow muted small" style={{ flex: 1, minWidth: 180 }}>
          Your location is used here in the browser to sort memos by distance. It is not saved or sent anywhere.
        </span>
        <button class="btn tonal" onClick={locate} disabled={fix.state === 'locating'}>
          <Icon name="place" size={18} /> {fix.state === 'locating' ? 'Locating…' : fix.state === 'ready' ? 'Update location' : 'Use my location'}
        </button>
      </div>
      {fix.state === 'failed' && (
        <p class="error-text small" role="alert">
          {fix.message}
        </p>
      )}
      {fix.state === 'ready' && rows.length === 0 && <div class="empty">No memos with a place yet.</div>}
      {rows.map((n) => (
        <div key={n.row.localId}>
          <div class="muted small" style={{ display: 'flex', gap: 8, margin: '12px 0 4px' }}>
            <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{n.placeName || 'Unnamed place'}</span>
            <span>{distance(n.metres)}</span>
          </div>
          <MemoCard memo={n.row} selected={props.route.memo === n.row.localId} tagStyle={tagStyle} />
        </div>
      ))}
    </>
  )
}

function Journey(props: { route: Route }) {
  const tagStyle = useTagStyles()
  const located = session.locatedMemos()
  const tiles = session.mapTilesEnabled()

  // Rows arrive newest first, so the first time a day is seen keeps the days newest first too.
  const days: { key: string; label: string; memos: PlacedMemo[] }[] = []
  const byKey = new Map<string, PlacedMemo[]>()
  for (const p of located) {
    let list = byKey.get(p.dayKey)
    if (!list) {
      list = []
      byKey.set(p.dayKey, list)
      days.push({ key: p.dayKey, label: p.row.dateLabel || p.dayKey, memos: list })
    }
    list.push(p)
  }

  const [picked, setPicked] = useState<string | null>(null)
  const at = Math.max(0, days.findIndex((d) => d.key === picked))
  const day = days[at]

  if (!day) return <div class="empty">No memos with a place yet. Add one from a memo’s menu.</div>

  const stops = [...day.memos].reverse()

  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 10 }}>
        <button class="icon-btn" title="Older day" aria-label="Older day" disabled={at >= days.length - 1} onClick={() => setPicked(days[at + 1].key)}>
          <Icon name="back" size={20} />
        </button>
        <select class="input" style={{ width: 'auto', flex: 1, maxWidth: 320 }} value={day.key} onChange={(e) => setPicked((e.target as HTMLSelectElement).value)}>
          {days.map((d) => (
            <option key={d.key} value={d.key}>
              {d.label} ({d.memos.length})
            </option>
          ))}
        </select>
        <button class="icon-btn" title="Newer day" aria-label="Newer day" disabled={at === 0} onClick={() => setPicked(days[at - 1].key)}>
          <span style={{ display: 'inline-flex', transform: 'scaleX(-1)' }}>
            <Icon name="back" size={20} />
          </span>
        </button>
      </div>

      {tiles ? (
        <TileMap
          height={280}
          route
          points={stops.map((s, i) => ({
            lat: s.latitude,
            lon: s.longitude,
            label: String(i + 1),
            onClick: () => openMemo(s.row.localId, props.route),
          }))}
        />
      ) : (
        <div class="card-box" style={{ padding: '12px 16px' }}>
          <p style={{ margin: '4px 0 8px' }}>Map previews are off.</p>
          <p class="muted small" style={{ margin: '0 0 10px' }}>
            Maps are drawn from openstreetmap.org tiles, so turning them on lets OpenStreetMap learn roughly where your memos were written.
          </p>
          <button class="btn tonal" onClick={() => void attempt(() => session.setMapTiles(true).then(refresh), 'Could not turn on maps')}>
            Show maps
          </button>
        </div>
      )}

      {stops.map((s, i) => (
        <div key={s.row.localId}>
          <div class="muted small" style={{ display: 'flex', alignItems: 'center', gap: 8, margin: '12px 0 4px' }}>
            <span
              style={{
                display: 'inline-grid',
                placeItems: 'center',
                width: 22,
                height: 22,
                borderRadius: '50%',
                background: 'var(--primary)',
                color: 'var(--on-primary)',
                fontSize: 12,
                fontWeight: 700,
                flex: 'none',
              }}
            >
              {i + 1}
            </span>
            <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{s.placeName || 'Unnamed place'}</span>
            <span>{s.row.timeLabel}</span>
          </div>
          <MemoCard memo={s.row} selected={props.route.memo === s.row.localId} tagStyle={tagStyle} />
        </div>
      ))}
    </>
  )
}
