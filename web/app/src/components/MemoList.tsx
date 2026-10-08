import { useState } from 'preact/hooks'
import { attempt, isDark, session, tint, type MemoRow, type TimelineSection } from '../core'
import { go, openMemo, useRoute } from '../router'
import { Icon } from './Icon'
import { Markdown } from './Markdown'
import { TagChip, useTagStyles } from './ui'

/**
 * The timeline as the apps lay it out: folding group headers (days this week, weeks this
 * month, months before), then cards, or one line per memo in the compact view. What is
 * folded stays folded next time.
 */
export function MemoList(props: { sections: TimelineSection[]; empty?: string; foldable?: boolean }) {
  const route = useRoute()
  const compact = session.compactList()
  const [folded, setFolded] = useState(new Set(session.foldedGroups()))
  const tagStyle = useTagStyles()

  if (props.sections.length === 0 || props.sections.every((s) => s.memos.length === 0)) {
    return <div class="empty">{props.empty ?? 'Nothing here yet.'}</div>
  }

  const toggle = (key: string) => {
    const next = new Set(folded)
    const fold = !next.has(key)
    if (fold) next.add(key)
    else next.delete(key)
    setFolded(next)
    void session.setFolded(key, fold)
  }

  return (
    <div>
      {props.sections.map((s) => {
        const isFolded = props.foldable !== false && folded.has(s.key)
        return (
          <section key={s.key}>
            <button class={`group-header${isFolded ? ' folded' : ''}`} onClick={() => toggle(s.key)} aria-expanded={!isFolded}>
              <Icon name="chevron" size={18} />
              <span>{s.label}</span>
              <span class="count">· {s.memos.length}</span>
            </button>
            {!isFolded &&
              s.memos.map((m) =>
                compact ? (
                  <CompactRow key={m.localId} memo={m} selected={route.memo === m.localId} />
                ) : (
                  <MemoCard key={m.localId} memo={m} selected={route.memo === m.localId} tagStyle={tagStyle} />
                ),
              )}
          </section>
        )
      })}
    </div>
  )
}

export function MemoCard(props: { memo: MemoRow; selected?: boolean; tagStyle: ReturnType<typeof useTagStyles> }) {
  const m = props.memo
  const route = useRoute()
  const dark = isDark()
  const body = m.locked ? '' : m.title && m.bodyBelowTitle !== m.body ? m.bodyBelowTitle : m.body
  const offset = m.locked ? 0 : m.body.split('\n').length - body.split('\n').length
  const hasHeading = !m.locked && m.title && m.bodyBelowTitle !== m.body
  const short = body.length < 500
  return (
    <article
      class={`memo-card${props.selected ? ' selected' : ''}${m.locked ? ' locked' : ''}`}
      style={m.colourHex >= 0 ? { background: tint(m.colourHex, dark) } : undefined}
      onClick={() => openMemo(m.localId, route)}
      tabIndex={0}
      onKeyDown={(e) => e.key === 'Enter' && openMemo(m.localId, route)}
    >
      <div class="meta">
        {m.pinned && <Icon name="pin" size={15} />}
        {m.locked && <Icon name="lock" size={15} />}
        <span>{m.timeLabel}</span>
        {m.visibility !== 'PRIVATE' && (
          <span title={m.visibility.toLowerCase()}>
            <Icon name="visibility" size={14} />
          </span>
        )}
        {m.hasPlace && <Icon name="place" size={14} />}
        {m.attachmentCount > 0 && (
          <span>
            <Icon name="attach" size={14} /> {m.attachmentCount}
          </span>
        )}
        <span class="spacer" />
        {m.conflict && <span class="chip" style={{ background: 'var(--error-container)', color: 'var(--on-error-container)' }}>conflict</span>}
        {m.pending && !m.conflict && (
          <span title="Not yet on the server">
            <Icon name="sync" size={14} />
          </span>
        )}
      </div>
      {hasHeading && <h3>{m.title}</h3>}
      {m.locked ? (
        <div class="body">
          <h3 style={{ fontStyle: 'normal', color: 'var(--on-bg)' }}>{m.title}</h3>
          Locked. Open it to read.
        </div>
      ) : (
        <div class={`body${short ? ' short' : ''}`}>
          <Markdown
            source={body}
            lineOffset={offset}
            tagStyle={props.tagStyle}
            onTag={(t) => go('tag', t)}
            onToggleTask={(line, checked) => void attempt(() => session.toggleTask(m.localId, line, checked))}
          />
        </div>
      )}
    </article>
  )
}

export function CompactRow(props: { memo: MemoRow; selected?: boolean }) {
  const m = props.memo
  const route = useRoute()
  return (
    <div class={`compact-row${props.selected ? ' selected' : ''}`} onClick={() => openMemo(m.localId, route)} tabIndex={0} onKeyDown={(e) => e.key === 'Enter' && openMemo(m.localId, route)}>
      {m.colourHex >= 0 && <span style={{ width: 8, height: 8, borderRadius: 4, background: `#${m.colourHex.toString(16).padStart(6, '0')}`, flex: 'none' }} />}
      <span class="title">{m.title || 'Untitled'}</span>
      {m.locked && <Icon name="lock" size={15} />}
      {m.pinned && <Icon name="pin" size={15} />}
      <span class="time">{m.timeLabel}</span>
    </div>
  )
}

/** A plain list of memo rows without grouping, for review, backlinks and the like. */
export function MemoRows(props: { memos: MemoRow[]; empty?: string }) {
  const tagStyle = useTagStyles()
  if (props.memos.length === 0) return <div class="empty">{props.empty ?? 'Nothing here.'}</div>
  return (
    <div>
      {props.memos.map((m) => (
        <MemoCard key={m.localId} memo={m} tagStyle={tagStyle} />
      ))}
    </div>
  )
}

export { TagChip }
