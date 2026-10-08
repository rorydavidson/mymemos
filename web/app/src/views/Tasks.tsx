import { attempt, session, toast, useData } from '../core'
import { Icon } from '../components/Icon'
import { openMemo, type Route } from '../router'

/**
 * Every open checkbox across memos, grouped by memo, soonest due first. Due dates come from
 * `@today`, `@fri`, `@2026-10-12` and the like, read by the same parser the apps use.
 */
export function TasksView(props: { route: Route }) {
  useData()
  const groups = session.openTasks()
  const total = groups.reduce((n, g) => n + g.tasks.length, 0)
  return (
    <>
      <div class="pane-header">
        <h1>Tasks</h1>
        <span class="muted small">{total} open</span>
      </div>
      <div class="pane-body">
        {groups.length === 0 && <div class="empty">No open tasks. Write “- [ ] something” in any memo.</div>}
        {groups.map((g) => (
          <section key={g.memoLocalId}>
            <button class="group-header" onClick={() => openMemo(g.memoLocalId, props.route)}>
              <Icon name="notes" size={18} />
              <span>{g.memoTitle || 'Untitled'}</span>
            </button>
            <div class="card-box">
              {g.tasks.map((t) => (
                <label class="row" key={t.lineIndex} style={{ cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    style={{ width: 18, height: 18, accentColor: 'var(--primary)' }}
                    onChange={async () => {
                      await attempt(() => session.toggleTask(t.memoLocalId, t.lineIndex, true))
                      toast('Task done', { label: 'Undo', run: () => void session.toggleTask(t.memoLocalId, t.lineIndex, false) })
                    }}
                  />
                  <span class="grow">{t.text}</span>
                  {t.dueLabel && (
                    <span class="chip" style={t.overdue ? { background: 'var(--error-container)', color: 'var(--on-error-container)' } : undefined}>
                      {t.dueLabel}
                    </span>
                  )}
                </label>
              ))}
            </div>
          </section>
        ))}
      </div>
    </>
  )
}
