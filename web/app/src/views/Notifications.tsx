import { attempt, refresh, session, toast, useOnline, type NotificationRow } from '../core'
import { Icon } from '../components/Icon'
import { openMemo, type Route } from '../router'

function describe(type: string): string {
  if (type === 'MEMO_COMMENT') return 'commented on your memo'
  if (type === 'MEMO_MENTION') return 'mentioned you'
  return type.toLowerCase().replace(/_/g, ' ')
}

/** Comments and mentions from the server. Online only: the server keeps the inbox. */
export function NotificationsView(props: { route: Route }) {
  const [list, loading, error, reload] = useOnline<NotificationRow[]>(() => session.notifications(), [], [])

  // The nav badge reads a cached count, so it is refreshed from the server after any change.
  const changed = async () => {
    reload()
    await session.refreshUnread().catch(() => 0)
    refresh()
  }

  const markRead = async (n: NotificationRow) => {
    await attempt(() => session.markNotificationRead(n.name), 'Could not mark as read')
    await changed()
  }

  const remove = async (n: NotificationRow) => {
    await attempt(() => session.deleteNotification(n.name), 'Could not delete')
    await changed()
  }

  const open = async (n: NotificationRow) => {
    const id = n.memoRemoteName ? await attempt(() => session.localIdForRemote(n.memoRemoteName), 'Could not find that memo') : null
    if (n.unread) void markRead(n)
    if (id) openMemo(id, props.route)
    else if (id !== undefined) toast('That memo is not in this browser yet. Try syncing first.')
  }

  return (
    <>
      <div class="pane-header">
        <h1>Notifications</h1>
        <button class="icon-btn" title="Refresh" aria-label="Refresh" onClick={() => void changed()} disabled={loading}>
          <Icon name="sync" />
        </button>
      </div>
      {error && (
        <div class="banner">
          <span style={{ flex: 1 }}>Could not load notifications: {error}</span>
          <button class="btn text" onClick={reload}>
            Retry
          </button>
        </div>
      )}
      <div class="pane-body">
        {!loading && !error && list.length === 0 && (
          <div class="empty">
            <Icon name="bell" />
            <p>No notifications. Comments and mentions on your memos show up here.</p>
          </div>
        )}
        {list.length > 0 && (
          <div class="card-box">
            {list.map((n) => (
              <div
                class="row"
                key={n.name}
                style={{ cursor: 'pointer', ...(n.unread ? { background: 'var(--primary-container)', margin: '0 -16px', padding: '12px 16px' } : {}) }}
                onClick={() => void open(n)}
              >
                <Icon name={n.type === 'MEMO_MENTION' ? 'person' : 'comment'} />
                <div class="grow">
                  <div>
                    <strong>{n.sender || 'Someone'}</strong> {describe(n.type)}
                    {n.unread && <span class="badge" style={{ marginLeft: 8 }} aria-label="Unread">New</span>}
                  </div>
                  {n.relatedSnippet && <div class="small" style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{n.relatedSnippet}</div>}
                  {n.memoSnippet && <div class="small muted" style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{n.memoSnippet}</div>}
                  <div class="small muted">{n.dateLabel}</div>
                </div>
                {n.unread && (
                  <button
                    class="icon-btn"
                    title="Mark as read"
                    aria-label="Mark as read"
                    onClick={(e) => {
                      e.stopPropagation()
                      void markRead(n)
                    }}
                  >
                    <Icon name="check" />
                  </button>
                )}
                <button
                  class="icon-btn"
                  title="Delete"
                  aria-label="Delete"
                  onClick={(e) => {
                    e.stopPropagation()
                    void remove(n)
                  }}
                >
                  <Icon name="trash" />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  )
}
