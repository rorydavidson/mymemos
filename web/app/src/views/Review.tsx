import { useEffect, useRef, useState } from 'preact/hooks'
import { attempt, session, toast, useData, type MemoRow } from '../core'
import { Icon } from '../components/Icon'
import { MemoCard } from '../components/MemoList'
import { promptDialog, useTagStyles } from '../components/ui'
import { openMemo, type Route } from '../router'

const WEEKS = 12
const SWIPE = 100

/** yyyy-MM-dd in local time, which is what the session keys days by. */
function isoDay(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function shiftDay(iso: string, by: number): string {
  const [y, m, d] = iso.split('-').map(Number)
  return isoDay(new Date(y, m - 1, d + by))
}

function longDate(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })
}

/**
 * Looking back: the writing streak and recent activity, memos from this day in earlier
 * years, a day-by-day deck to keep, archive or tidy each memo, and the weekly digest.
 */
export function ReviewView(props: { route: Route }) {
  useData()
  const throwbacks = session.onThisDay()
  const tagStyle = useTagStyles()
  const digest = session.digestText()
  const next = session.digestNextLabel()
  return (
    <>
      <div class="pane-header">
        <h1>Review</h1>
      </div>
      <div class="pane-body">
        <Activity />

        <h2 class="section-title">On this day</h2>
        {throwbacks.length === 0 && <div class="muted small">Nothing from this day in earlier years.</div>}
        {throwbacks.map((t) => (
          <div key={t.row.localId}>
            <div class="muted small" style={{ margin: '10px 0 4px' }}>
              {t.whenLabel}
            </div>
            <MemoCard memo={t.row} selected={props.route.memo === t.row.localId} tagStyle={tagStyle} />
          </div>
        ))}

        <DayDeck route={props.route} />

        <h2 class="section-title">This week</h2>
        <div class="card-box" style={{ padding: '12px 16px' }}>
          <div style={{ whiteSpace: 'pre-wrap' }}>{digest || <span class="muted">Nothing written this week yet.</span>}</div>
          {next && <div class="muted small" style={{ marginTop: 8 }}>Next digest: {next}</div>}
        </div>
      </div>
    </>
  )
}

function Activity() {
  const streak = session.streak()
  const active = new Set(session.activeDays())
  const today = new Date()
  // Weeks start on Monday; the last column is this week, so days after today are left blank.
  const monday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - ((today.getDay() + 6) % 7))
  const start = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() - (WEEKS - 1) * 7)
  const todayIso = isoDay(today)
  const days: { iso: string; future: boolean }[] = []
  for (let i = 0; i < WEEKS * 7; i++) {
    const iso = isoDay(new Date(start.getFullYear(), start.getMonth(), start.getDate() + i))
    days.push({ iso, future: iso > todayIso })
  }
  const count = days.filter((d) => active.has(d.iso)).length
  return (
    <div class="card-box" style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 24, padding: '14px 16px', marginTop: 8 }}>
      <div>
        <div style={{ fontSize: 32, fontWeight: 700, lineHeight: 1.1 }}>{streak}</div>
        <div class="muted small">{streak === 1 ? 'day in a row' : 'days in a row'}</div>
      </div>
      <div>
        <div class="heatmap" aria-label={`Active on ${count} of the last ${WEEKS * 7} days`}>
          {days.map((d) => (
            <span
              key={d.iso}
              class={active.has(d.iso) ? 'on' : undefined}
              title={d.future ? undefined : `${longDate(d.iso)}${active.has(d.iso) ? ', wrote' : ''}`}
              style={d.future ? { visibility: 'hidden' } : undefined}
            />
          ))}
        </div>
        <div class="muted small" style={{ marginTop: 6 }}>
          Last {WEEKS} weeks
        </div>
      </div>
    </div>
  )
}

function DayDeck(props: { route: Route }) {
  const v = useData()
  const tagStyle = useTagStyles()
  const [day, setDay] = useState(isoDay(new Date()))
  // The deck is a snapshot of the day's memos, so archiving one does not shift the rest
  // under the reader. It is retaken on data changes until the reader acts, which covers
  // the first sync filling an empty day.
  const [deck, setDeck] = useState<MemoRow[]>(() => session.memosOn(day))
  const [index, setIndex] = useState(0)
  const touched = useRef(false)
  const [dx, setDx] = useState(0)
  const drag = useRef<{ x: number; id: number; moved: boolean } | null>(null)

  useEffect(() => {
    touched.current = false
    setDeck(session.memosOn(day))
    setIndex(0)
  }, [day])

  useEffect(() => {
    if (!touched.current) setDeck(session.memosOn(day))
  }, [v])

  const fresh = session.memosOn(day)
  const snapshot = deck[index]
  const current = snapshot ? (fresh.find((r) => r.localId === snapshot.localId) ?? snapshot) : undefined

  const advance = () => {
    touched.current = true
    setIndex((i) => i + 1)
  }

  const keep = () => current && advance()

  const archive = async () => {
    if (!current) return
    const id = current.localId
    const at = index
    advance()
    const ok = await attempt(() => session.setArchived(id, true).then(() => true), 'Could not archive')
    if (ok)
      toast('Archived', {
        label: 'Undo',
        run: () => {
          void attempt(() => session.setArchived(id, false), 'Could not unarchive')
          setIndex(at)
        },
      })
  }

  const pin = () => current && void attempt(() => session.setPinned(current.localId, !current.pinned), 'Could not pin')

  const tag = async () => {
    if (!current || current.locked) return
    const id = current.localId
    const got = await promptDialog('Add a tag', [{ name: 'tag', label: 'Tag', placeholder: 'idea' }], 'Add')
    const name = got?.tag.trim().replace(/^#+/, '')
    if (!name) return
    if (/\s/.test(name)) {
      toast('Tags cannot contain spaces')
      return
    }
    const detail = session.memo(id)
    if (!detail) return
    await attempt(() => session.updateContent(id, `${detail.content} #${name}`), 'Could not add the tag')
  }

  const remove = async () => {
    if (!current) return
    const id = current.localId
    const at = index
    advance()
    const undoable = await attempt(() => session.deleteMemo(id), 'Could not delete')
    if (undoable === undefined) return
    if (undoable)
      toast('Deleted', {
        label: 'Undo',
        run: () => {
          void attempt(() => session.undoDelete(id), 'Could not restore')
          setIndex(at)
        },
      })
    else toast('Deleted')
  }

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.altKey || e.ctrlKey || e.metaKey || e.shiftKey) return
      const t = e.target as HTMLElement | null
      if (t && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName))) return
      if (document.querySelector('dialog[open], .dialog')) return
      if (e.key === 'ArrowRight') {
        e.preventDefault()
        keep()
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault()
        void archive()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  const onPointerDown = (e: PointerEvent) => {
    if (e.button !== 0) return
    drag.current = { x: e.clientX, id: e.pointerId, moved: false }
  }
  const onPointerMove = (e: PointerEvent) => {
    const d = drag.current
    if (!d || d.id !== e.pointerId) return
    const delta = e.clientX - d.x
    if (!d.moved && Math.abs(delta) > 6) {
      d.moved = true
      ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
    }
    if (d.moved) setDx(delta)
  }
  const onPointerUp = (e: PointerEvent) => {
    const d = drag.current
    if (!d || d.id !== e.pointerId) return
    const delta = e.clientX - d.x
    setDx(0)
    // Leave drag set for the click that follows a real drag, so it does not open the memo.
    if (!d.moved) drag.current = null
    else setTimeout(() => (drag.current = null), 0)
    if (!d.moved) return
    if (delta > SWIPE) keep()
    else if (delta < -SWIPE) void archive()
  }

  return (
    <>
      <h2 class="section-title">Day by day</h2>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap', marginBottom: 10 }}>
        <button class="icon-btn" title="Previous day" aria-label="Previous day" onClick={() => setDay(shiftDay(day, -1))}>
          <Icon name="back" size={20} />
        </button>
        <input
          class="input"
          type="date"
          style={{ width: 'auto' }}
          value={day}
          max={isoDay(new Date())}
          onChange={(e) => {
            const v = (e.target as HTMLInputElement).value
            if (v) setDay(v)
          }}
        />
        <button class="icon-btn" title="Next day" aria-label="Next day" onClick={() => setDay(shiftDay(day, 1))}>
          <span style={{ display: 'inline-flex', transform: 'scaleX(-1)' }}>
            <Icon name="back" size={20} />
          </span>
        </button>
        <span class="muted small">{deck.length === 0 ? 'No memos' : `${Math.min(index + 1, deck.length)} of ${deck.length}`}</span>
      </div>

      {deck.length === 0 && <div class="muted small">Nothing written on {longDate(day)}.</div>}

      {deck.length > 0 && !current && (
        <div class="card-box" style={{ padding: '18px 16px', textAlign: 'center' }}>
          <Icon name="check" size={28} />
          <div style={{ marginTop: 6 }}>Reviewed all {deck.length}</div>
          <button class="btn text" style={{ marginTop: 6 }} onClick={() => setIndex(0)}>
            Start again
          </button>
        </div>
      )}

      {current && (
        <>
          <div
            style={{
              transform: `translateX(${dx}px) rotate(${dx / 40}deg)`,
              transition: dx === 0 ? 'transform 0.2s' : 'none',
              opacity: 1 - Math.min(Math.abs(dx) / 400, 0.5),
              touchAction: 'pan-y',
              userSelect: dx === 0 ? undefined : 'none',
            }}
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove}
            onPointerUp={onPointerUp}
            onPointerCancel={() => {
              drag.current = null
              setDx(0)
            }}
            onClickCapture={(e) => {
              if (drag.current?.moved) {
                e.stopPropagation()
                e.preventDefault()
              }
            }}
          >
            <MemoCard key={current.localId} memo={current} selected={props.route.memo === current.localId} tagStyle={tagStyle} />
          </div>
          <div class="muted small" style={{ textAlign: 'center', minHeight: 18 }}>
            {dx > SWIPE ? 'Keep' : dx < -SWIPE ? 'Archive' : 'Swipe right to keep, left to archive, or use the arrow keys'}
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, justifyContent: 'center', marginTop: 8 }}>
            <button class="btn tonal" onClick={() => void archive()}>
              <Icon name="archive" size={18} /> Archive
            </button>
            <button class="btn" onClick={keep}>
              <Icon name="check" size={18} /> Keep
            </button>
          </div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 2, justifyContent: 'center', marginTop: 4 }}>
            <button class={`icon-btn${current.pinned ? ' on' : ''}`} title={current.pinned ? 'Unpin' : 'Pin'} aria-label={current.pinned ? 'Unpin' : 'Pin'} onClick={pin}>
              <Icon name="pin" size={20} />
            </button>
            {!current.locked && (
              <button class="icon-btn" title="Add a tag" aria-label="Add a tag" onClick={() => void tag()}>
                <Icon name="tag" size={20} />
              </button>
            )}
            <button class="icon-btn" title="Edit" aria-label="Edit" onClick={() => openMemo(current.localId, props.route)}>
              <Icon name="edit" size={20} />
            </button>
            <button class="icon-btn" title="Delete" aria-label="Delete" onClick={() => void remove()}>
              <Icon name="trash" size={20} />
            </button>
          </div>
        </>
      )}
    </>
  )
}
