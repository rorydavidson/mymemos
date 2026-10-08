import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { session, useData, type GraphData, type MemoRow } from '../core'
import { openMemo, type Route } from '../router'

const RADIUS = 9
const ITERATIONS = 300

interface Laid {
  row: MemoRow
  x: number
  y: number
}

interface View {
  x: number
  y: number
  w: number
  h: number
}

/**
 * Memos and the references between them, laid out once by a small force simulation:
 * every pair repels, each reference pulls like a spring, and a weak pull keeps the whole
 * thing centred. Starting from a circle by index keeps the layout the same between visits.
 */
function layout(data: GraphData): { nodes: Laid[]; edges: [number, number][] } {
  const n = data.nodes.length
  const index = new Map(data.nodes.map((r, i) => [r.localId, i]))
  const edges: [number, number][] = []
  for (const e of data.edges) {
    const a = index.get(e.from)
    const b = index.get(e.to)
    if (a !== undefined && b !== undefined && a !== b) edges.push([a, b])
  }
  const start = 40 * Math.sqrt(n) + 40
  const x = new Float64Array(n)
  const y = new Float64Array(n)
  for (let i = 0; i < n; i++) {
    const t = (2 * Math.PI * i) / Math.max(n, 1)
    x[i] = start * Math.cos(t)
    y[i] = start * Math.sin(t)
  }
  const spring = 70
  const repel = 2400
  const fx = new Float64Array(n)
  const fy = new Float64Array(n)
  for (let it = 0; it < ITERATIONS; it++) {
    fx.fill(0)
    fy.fill(0)
    for (let i = 0; i < n; i++) {
      for (let j = i + 1; j < n; j++) {
        let dx = x[i] - x[j]
        let dy = y[i] - y[j]
        let d2 = dx * dx + dy * dy
        if (d2 < 0.01) {
          // Coincident nodes would never separate; nudge them apart deterministically.
          dx = 0.1 * ((i % 3) - 1 || 1)
          dy = 0.1
          d2 = dx * dx + dy * dy
        }
        const f = repel / d2
        const d = Math.sqrt(d2)
        fx[i] += (dx / d) * f
        fy[i] += (dy / d) * f
        fx[j] -= (dx / d) * f
        fy[j] -= (dy / d) * f
      }
    }
    for (const [a, b] of edges) {
      const dx = x[b] - x[a]
      const dy = y[b] - y[a]
      const d = Math.sqrt(dx * dx + dy * dy) || 0.01
      const f = (d - spring) * 0.08
      fx[a] += (dx / d) * f
      fy[a] += (dy / d) * f
      fx[b] -= (dx / d) * f
      fy[b] -= (dy / d) * f
    }
    // Cooling: big moves early, settling later.
    const cap = 30 * (1 - it / ITERATIONS) + 1
    for (let i = 0; i < n; i++) {
      fx[i] -= x[i] * 0.02
      fy[i] -= y[i] * 0.02
      const m = Math.sqrt(fx[i] * fx[i] + fy[i] * fy[i])
      const s = m > cap ? cap / m : 1
      x[i] += fx[i] * s
      y[i] += fy[i] * s
    }
  }
  return { nodes: data.nodes.map((row, i) => ({ row, x: x[i], y: y[i] })), edges }
}

function fit(nodes: Laid[]): View {
  if (nodes.length === 0) return { x: -100, y: -100, w: 200, h: 200 }
  const pad = 60
  const minX = Math.min(...nodes.map((p) => p.x)) - pad
  const maxX = Math.max(...nodes.map((p) => p.x)) + pad
  const minY = Math.min(...nodes.map((p) => p.y)) - pad
  const maxY = Math.max(...nodes.map((p) => p.y)) + pad
  return { x: minX, y: minY, w: Math.max(maxX - minX, 200), h: Math.max(maxY - minY, 200) }
}

function label(row: MemoRow): string {
  const t = (row.locked ? row.lockedTitle : row.title) || row.body.split('\n')[0] || 'Untitled'
  return t.length > 24 ? t.slice(0, 23) + '…' : t
}

export function GraphView(props: { route: Route }) {
  const v = useData()
  const graph = useMemo(() => layout(session.referenceGraph()), [v])
  const home = useMemo(() => fit(graph.nodes), [graph])
  const [view, setView] = useState<View>(home)
  const svg = useRef<SVGSVGElement>(null)
  const pan = useRef<{ x: number; y: number; view: View } | null>(null)

  useEffect(() => setView(home), [home])

  // Wheel zoom needs a non-passive listener to stop the page scrolling underneath.
  useEffect(() => {
    const el = svg.current
    if (!el) return
    const onWheel = (e: WheelEvent) => {
      e.preventDefault()
      const rect = el.getBoundingClientRect()
      setView((cur) => {
        // With preserveAspectRatio="xMidYMid meet" the drawn area can be letterboxed, so
        // work out the real scale and offset before mapping the cursor into graph space.
        const scale = Math.min(rect.width / cur.w, rect.height / cur.h)
        const ox = (rect.width - cur.w * scale) / 2
        const oy = (rect.height - cur.h * scale) / 2
        const gx = cur.x + (e.clientX - rect.left - ox) / scale
        const gy = cur.y + (e.clientY - rect.top - oy) / scale
        const k = Math.exp(e.deltaY * 0.0015)
        const w = Math.min(Math.max(cur.w * k, 60), home.w * 8)
        const h = (cur.h * w) / cur.w
        return { x: gx - ((gx - cur.x) * w) / cur.w, y: gy - ((gy - cur.y) * h) / cur.h, w, h }
      })
    }
    el.addEventListener('wheel', onWheel, { passive: false })
    return () => el.removeEventListener('wheel', onWheel)
  }, [home, graph.nodes.length > 0])

  if (graph.nodes.length === 0) {
    return (
      <>
        <div class="pane-header">
          <h1>Graph</h1>
        </div>
        <div class="pane-body">
          <div class="empty">No references yet. Add one from a memo’s menu to see it here.</div>
        </div>
      </>
    )
  }

  const scaleOf = () => {
    const rect = svg.current?.getBoundingClientRect()
    return rect ? Math.min(rect.width / view.w, rect.height / view.h) : 1
  }

  return (
    <>
      <div class="pane-header">
        <h1>Graph</h1>
        <span class="muted small">
          {graph.nodes.length} memos, {graph.edges.length} links
        </span>
        <button class="btn text" onClick={() => setView(home)}>
          Reset view
        </button>
      </div>
      <div class="pane-body">
        <svg
          ref={svg}
          viewBox={`${view.x} ${view.y} ${view.w} ${view.h}`}
          preserveAspectRatio="xMidYMid meet"
          style={{ width: '100%', height: '70vh', display: 'block', background: 'var(--card)', borderRadius: 'var(--r-m)', cursor: pan.current ? 'grabbing' : 'grab', touchAction: 'none', userSelect: 'none' }}
          onPointerDown={(e) => {
            if (e.button !== 0) return
            pan.current = { x: e.clientX, y: e.clientY, view }
            ;(e.currentTarget as SVGSVGElement).setPointerCapture(e.pointerId)
          }}
          onPointerMove={(e) => {
            const p = pan.current
            if (!p) return
            const s = scaleOf()
            setView({ ...p.view, x: p.view.x - (e.clientX - p.x) / s, y: p.view.y - (e.clientY - p.y) / s })
          }}
          onPointerUp={() => (pan.current = null)}
          onPointerCancel={() => (pan.current = null)}
        >
          <defs>
            <marker id="graph-arrow" viewBox="0 0 10 10" refX="10" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
              <path d="M0 0 10 5 0 10Z" fill="var(--outline)" />
            </marker>
          </defs>
          <g>
            {graph.edges.map(([a, b], i) => {
              const p = graph.nodes[a]
              const q = graph.nodes[b]
              const dx = q.x - p.x
              const dy = q.y - p.y
              const d = Math.sqrt(dx * dx + dy * dy) || 1
              // Stop at the circle's edge so the arrowhead is not hidden under the node.
              const ux = dx / d
              const uy = dy / d
              return (
                <line
                  key={i}
                  x1={p.x + ux * RADIUS}
                  y1={p.y + uy * RADIUS}
                  x2={q.x - ux * (RADIUS + 2)}
                  y2={q.y - uy * (RADIUS + 2)}
                  stroke="var(--outline)"
                  stroke-width={1.4}
                  marker-end="url(#graph-arrow)"
                />
              )
            })}
          </g>
          <g>
            {graph.nodes.map((p) => {
              const selected = props.route.memo === p.row.localId
              return (
                <g
                  key={p.row.localId}
                  transform={`translate(${p.x} ${p.y})`}
                  style={{ cursor: 'pointer' }}
                  role="button"
                  tabIndex={0}
                  aria-label={label(p.row)}
                  onPointerDown={(e) => e.stopPropagation()}
                  onClick={() => openMemo(p.row.localId, props.route)}
                  onKeyDown={(e) => e.key === 'Enter' && openMemo(p.row.localId, props.route)}
                >
                  <title>{label(p.row)}</title>
                  <circle r={RADIUS} fill="var(--primary)" stroke={selected ? 'var(--on-bg)' : 'var(--card)'} stroke-width={selected ? 3 : 2} />
                  <text
                    y={RADIUS + 14}
                    text-anchor="middle"
                    font-size="12"
                    fill="var(--on-bg)"
                    style={{ paintOrder: 'stroke', stroke: 'var(--card)', strokeWidth: 3, strokeLinejoin: 'round' }}
                  >
                    {label(p.row)}
                  </text>
                </g>
              )
            })}
          </g>
        </svg>
        <p class="muted small">Drag to move around, scroll to zoom, click a memo to open it.</p>
      </div>
    </>
  )
}
