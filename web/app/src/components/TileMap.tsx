import { useEffect, useRef, useState } from 'preact/hooks'

export interface MapPoint {
  lat: number
  lon: number
  /** Shown in a numbered circle; a plain pin when absent. */
  label?: string
  onClick?: () => void
}

/**
 * A map drawn from OpenStreetMap tiles with nothing but image tags: fit to the points, one
 * zoom level, no panning. Enough for "where was this written" and a day's route, and no
 * map library to ship. Only rendered when the reader has turned map tiles on, since the
 * tiles reveal roughly where a memo was written, and this app's address, to
 * tile.openstreetmap.org.
 */
export function TileMap(props: { points: MapPoint[]; height?: number; route?: boolean; onOpen?: () => void }) {
  const box = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState(0)
  useEffect(() => {
    const el = box.current
    if (!el) return
    const ro = new ResizeObserver(() => setWidth(el.clientWidth))
    ro.observe(el)
    setWidth(el.clientWidth)
    return () => ro.disconnect()
  }, [])
  const height = props.height ?? 200
  if (props.points.length === 0) return null

  const zoom = fitZoom(props.points, width || 320, height)
  const pts = props.points.map((p) => ({ ...p, ...project(p.lat, p.lon, zoom) }))
  const minX = Math.min(...pts.map((p) => p.x))
  const maxX = Math.max(...pts.map((p) => p.x))
  const minY = Math.min(...pts.map((p) => p.y))
  const maxY = Math.max(...pts.map((p) => p.y))
  const cx = (minX + maxX) / 2
  const cy = (minY + maxY) / 2
  const left = cx - (width || 320) / 2
  const top = cy - height / 2
  const tiles: { x: number; y: number }[] = []
  const n = 2 ** zoom
  for (let tx = Math.floor(left / 256); tx <= Math.floor((left + (width || 320)) / 256); tx++) {
    for (let ty = Math.floor(top / 256); ty <= Math.floor((top + height) / 256); ty++) {
      if (ty >= 0 && ty < n) tiles.push({ x: tx, y: ty })
    }
  }

  return (
    <div class="tile-map" ref={box} style={{ height }} onClick={props.onOpen} role={props.onOpen ? 'button' : undefined}>
      {width > 0 &&
        tiles.map((t) => (
          <img
            key={`${t.x}/${t.y}`}
            class="tile"
            alt=""
            loading="lazy"
            // OSM's tile policy refuses browser requests with no Referer. The origin alone is
            // enough for them and says nothing about which memo is open.
            referrerpolicy="origin"
            src={`https://tile.openstreetmap.org/${zoom}/${((t.x % n) + n) % n}/${t.y}.png`}
            style={{ left: t.x * 256 - left, top: t.y * 256 - top }}
          />
        ))}
      {props.route && pts.length > 1 && (
        <svg style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', pointerEvents: 'none' }}>
          <polyline
            points={pts.map((p) => `${p.x - left},${p.y - top}`).join(' ')}
            fill="none"
            stroke="var(--primary)"
            stroke-width="3"
            stroke-dasharray="6 5"
            stroke-linecap="round"
          />
        </svg>
      )}
      {pts.map((p, i) =>
        p.label ? (
          <span
            key={i}
            class="num"
            style={{ left: p.x - left, top: p.y - top, cursor: p.onClick ? 'pointer' : undefined }}
            onClick={(e) => {
              if (!p.onClick) return
              e.stopPropagation()
              p.onClick()
            }}
          >
            {p.label}
          </span>
        ) : (
          <span key={i} class="pin" style={{ left: p.x - left, top: p.y - top }}>
            <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 22s-7-6-7-12a7 7 0 0 1 14 0c0 6-7 12-7 12Z" />
              <circle cx="12" cy="10" r="2.6" fill="#fff" />
            </svg>
          </span>
        ),
      )}
      <span class="credit">© OpenStreetMap contributors</span>
    </div>
  )
}

function project(lat: number, lon: number, zoom: number): { x: number; y: number } {
  const scale = 256 * 2 ** zoom
  const s = Math.sin((Math.max(-85, Math.min(85, lat)) * Math.PI) / 180)
  return { x: ((lon + 180) / 360) * scale, y: (0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)) * scale }
}

function fitZoom(points: MapPoint[], width: number, height: number): number {
  if (points.length === 1) return 15
  for (let z = 17; z > 1; z--) {
    const ps = points.map((p) => project(p.lat, p.lon, z))
    const w = Math.max(...ps.map((p) => p.x)) - Math.min(...ps.map((p) => p.x))
    const h = Math.max(...ps.map((p) => p.y)) - Math.min(...ps.map((p) => p.y))
    if (w < width - 60 && h < height - 60) return z
  }
  return 1
}

/** A link to the place in whatever map the reader's device prefers. */
export function mapsLink(lat: number, lon: number): string {
  return `https://www.openstreetmap.org/?mlat=${lat}&mlon=${lon}#map=16/${lat}/${lon}`
}
