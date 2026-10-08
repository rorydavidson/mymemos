import { attempt, session, useData } from '../core'
import { Icon } from '../components/Icon'
import { MemoRows } from '../components/MemoList'
import { openMemo, type Route } from '../router'

/** Where sync stands: what is waiting, what the server refused, and conflict copies. */
export function SyncView(props: { route: Route }) {
  useData()
  const s = session.syncState()
  const failed = session.failedOps()
  const conflicts = session.conflicts()
  return (
    <>
      <div class="pane-header">
        <h1>Sync</h1>
        <button class="btn tonal" onClick={() => void attempt(() => session.sync(true))} disabled={s.running}>
          <Icon name="sync" size={18} /> {s.running ? 'Syncing…' : 'Sync now'}
        </button>
      </div>
      <div class="pane-body">
        <div class="card-box">
          <div class="row">
            <span class="grow">Last synced</span>
            <span class="muted">{s.lastSuccessLabel || 'Not yet'}</span>
          </div>
          <div class="row">
            <span class="grow">Waiting to send</span>
            <span class="muted">{s.pending}</span>
          </div>
          <div class="row">
            <span class="grow">Refused by the server</span>
            <span class="muted">{s.failed}</span>
          </div>
          {s.lastError && (
            <div class="row">
              <span class="grow">Last problem</span>
              <span class="error-text small">{s.lastError}</span>
            </div>
          )}
        </div>
        <p class="small muted">
          Changes are kept in this browser and sent when the server can be reached. Every few hours, and on Sync now, the whole list is compared so deletions made elsewhere arrive too.
        </p>

        {failed.length > 0 && (
          <>
            <div class="section-title" style={{ display: 'flex', alignItems: 'center' }}>
              <span style={{ flex: 1 }}>Refused changes</span>
              <button class="btn text" onClick={() => void attempt(() => session.retryFailed())}>
                Retry all
              </button>
            </div>
            <div class="card-box">
              {failed.map((f) => (
                <div class="row" key={f.id} style={{ cursor: 'pointer' }} onClick={() => openMemo(f.memoLocalId, props.route)}>
                  <Icon name="warning" />
                  <div class="grow">
                    <div>
                      {f.type}: {f.memoTitle || 'Untitled'}
                    </div>
                    <div class="small error-text">{f.error}</div>
                  </div>
                  <span class="small muted">{f.attempts}×</span>
                </div>
              ))}
            </div>
          </>
        )}

        {conflicts.length > 0 && (
          <>
            <div class="section-title">Conflict copies</div>
            <p class="small muted">
              These are your versions of edits that clashed with changes made elsewhere and could not be merged line by line. The other version kept the original memo. Read both, then keep or delete the copy.
            </p>
            <MemoRows memos={conflicts} />
          </>
        )}
      </div>
    </>
  )
}
