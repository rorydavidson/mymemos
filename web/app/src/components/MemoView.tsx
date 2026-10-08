import { useEffect, useState } from 'preact/hooks'
import { attempt, errorText, hex, isDark, session, shareUrl, tint, toast, useData, useOnline, type AttachmentRow, type Reveal } from '../core'
import { go, openMemo, useRoute } from '../router'
import { Editor } from './Editor'
import { Icon } from './Icon'
import { Markdown } from './Markdown'
import { MemoRows } from './MemoList'
import { mapsLink, TileMap } from './TileMap'
import { askPassword, confirmDialog, Dialog, Menu, promptDialog, TagChip, useTagStyles } from './ui'

/**
 * One memo, everything about it: the rendered text with live checkboxes, attachments,
 * reactions, references both ways, comments, its place, and the actions the apps put in
 * a memo's menu.
 */
export function MemoView(props: { localId: string }) {
  const v = useData()
  const route = useRoute()
  const id = props.localId
  const detail = session.memo(id)
  const tagStyle = useTagStyles()
  const [menu, setMenu] = useState(false)
  const [editing, setEditing] = useState<{ text: string; locked: boolean } | null>(null)
  const [dialog, setDialog] = useState<null | 'colour' | 'share' | 'reference' | 'reaction'>(null)
  const [reveal, setReveal] = useState<Reveal | null>(null)
  const dark = isDark()

  useEffect(() => setReveal(null), [id])
  useEffect(() => {
    if (detail?.row.locked) void session.reveal(id).then(setReveal)
  }, [id, v, detail?.row.locked])

  if (!detail) {
    return (
      <div class="empty">
        This memo is not here any more.
        <div style={{ marginTop: 12 }}>
          <button class="btn tonal" onClick={() => openMemo(null, route)}>
            Close
          </button>
        </div>
      </div>
    )
  }
  const row = detail.row
  const locked = row.locked

  const unlockText = async (): Promise<string | null> => {
    let r = await session.reveal(id)
    while (r.needsPassword) {
      if (!(await askPassword(r.wrongPassword))) return null
      r = await session.reveal(id)
    }
    setReveal(r)
    return r.text ?? null
  }

  const edit = async () => {
    if (!locked) return setEditing({ text: detail.content, locked: false })
    const text = await unlockText()
    if (text != null) setEditing({ text, locked: true })
  }

  const remove = async () => {
    const undoable = await session.deleteMemo(id)
    openMemo(null, route)
    if (undoable) toast('Memo deleted', { label: 'Undo', run: () => void session.undoDelete(id).then((ok) => ok && openMemo(id)) })
    else toast('Memo deleted')
  }

  const archive = async () => {
    await session.setArchived(id, !detail.archived)
    toast(detail.archived ? 'Moved back to memos' : 'Archived', { label: 'Undo', run: () => void session.setArchived(id, detail.archived) })
  }

  const lock = async () => {
    if (!session.hasPassword() && !(await askPassword())) return
    const answer = await promptDialog('Lock memo', [{ name: 'title', label: 'Title shown while locked (optional)', placeholder: 'Leave blank to show nothing', value: row.title }], 'Lock', 'The text is encrypted here before it is sent. The server sees only the title, if you give one.')
    if (!answer) return
    if (await session.lock(id, answer.title.trim() || null)) toast('Locked')
  }

  const unlockForGood = async () => {
    if ((await unlockText()) == null) return
    if (await session.unlockForGood(id)) toast('Unlocked: stored as plain text again')
  }

  const setPlace = () => {
    if (!navigator.geolocation) return toast('This browser cannot tell where you are')
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const answer = await promptDialog('Add place', [{ name: 'name', label: 'Name this place', placeholder: 'Home, the office, a café…' }], 'Add')
        if (!answer) return
        await attempt(() => session.setLocation(id, pos.coords.latitude, pos.coords.longitude, answer.name.trim()))
      },
      (e) => toast(`No location: ${e.message}`),
      { enableHighAccuracy: true, timeout: 15000 },
    )
  }

  const remind = async () => {
    if (!detail.onServer) return toast('Reminders need the memo on the server first; try again after it syncs')
    const soon = new Date(Date.now() + 3600_000)
    soon.setMinutes(0, 0, 0)
    const local = new Date(soon.getTime() - soon.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
    const answer = await promptDialog('Remind me', [
      { name: 'at', label: 'When', type: 'datetime-local', value: local },
      { name: 'note', label: 'Note (optional)' },
    ], 'Set reminder', 'Reminders sync to every device. In a browser they appear while MyMemos is open in a tab.')
    if (!answer?.at) return
    if (await session.addReminder(id, new Date(answer.at).getTime(), answer.note)) {
      toast('Reminder set')
      if ('Notification' in window && Notification.permission === 'default') void Notification.requestPermission()
    }
  }

  const copy = async () => {
    const text = locked ? await unlockText() : detail.content
    if (text != null) await navigator.clipboard.writeText(text).then(() => toast('Copied'), () => toast('Could not copy'))
  }

  const body = locked ? reveal?.text ?? null : detail.content

  return (
    <div>
      <div class="pane-header">
        <button class="icon-btn" onClick={() => openMemo(null, route)} aria-label="Back">
          <Icon name="back" />
        </button>
        <h1>{row.title || 'Memo'}</h1>
        <button class={`icon-btn${row.pinned ? ' on' : ''}`} aria-label={row.pinned ? 'Unpin' : 'Pin'} aria-pressed={row.pinned} onClick={() => void session.setPinned(id, !row.pinned)}>
          <Icon name="pin" />
        </button>
        <button class="icon-btn" aria-label="Edit" onClick={() => void edit()}>
          <Icon name="edit" />
        </button>
        <button class="icon-btn" aria-label="More" aria-haspopup="menu" onClick={() => setMenu(!menu)}>
          <Icon name="more" />
        </button>
        {menu && (
          <Menu onClose={() => setMenu(false)}>
            <button onClick={() => setDialog('colour')}>
              <Icon name="palette" size={18} /> Colour
            </button>
            <VisibilityItems id={id} current={row.visibility} />
            <hr />
            {locked ? (
              <>
                <button onClick={async () => {
                  const a = await promptDialog('Title shown while locked', [{ name: 'title', label: 'Title', value: row.lockedTitle ?? '' }], 'Save')
                  if (a) await session.setLockedTitle(id, a.title.trim() || null)
                }}>
                  <Icon name="edit" size={18} /> Rename locked memo
                </button>
                <button onClick={() => void unlockForGood()}>
                  <Icon name="unlock" size={18} /> Unlock for good
                </button>
              </>
            ) : (
              <button onClick={() => void lock()}>
                <Icon name="lock" size={18} /> Lock with password
              </button>
            )}
            <button onClick={() => void remind()}>
              <Icon name="alarm" size={18} /> Remind me
            </button>
            {detail.hasPlace ? (
              <button onClick={() => void session.clearLocation(id)}>
                <Icon name="place" size={18} /> Remove place
              </button>
            ) : (
              <button onClick={setPlace}>
                <Icon name="place" size={18} /> Add current place
              </button>
            )}
            <button onClick={() => (detail.onServer ? setDialog('reference') : toast('Sync first: references need the memo on the server'))}>
              <Icon name="link" size={18} /> Add reference
            </button>
            <button onClick={() => (detail.onServer ? setDialog('share') : toast('Sync first: share links need the memo on the server'))}>
              <Icon name="share" size={18} /> Share links
            </button>
            <button onClick={() => void copy()}>
              <Icon name="template" size={18} /> Copy text
            </button>
            <hr />
            <button onClick={() => void archive()}>
              <Icon name="archive" size={18} /> {detail.archived ? 'Unarchive' : 'Archive'}
            </button>
            <button onClick={() => void remove()} style={{ color: 'var(--error)' }}>
              <Icon name="trash" size={18} /> Delete
            </button>
          </Menu>
        )}
      </div>

      <div class="detail" style={row.colourHex >= 0 ? { background: tint(row.colourHex, dark), borderRadius: 18, margin: '0 12px', paddingTop: 16 } : undefined}>
        {row.conflict && (
          <div class="banner">
            <span style={{ flex: 1 }}>This is your copy from an edit that clashed with another device. Both versions were kept.</span>
            <button class="btn text" onClick={() => void session.keepConflictCopy(id)}>
              Keep as memo
            </button>
          </div>
        )}
        {locked && body == null ? (
          <div class="empty">
            <Icon name="lock" size={36} />
            <p>{row.lockedTitle ? <strong>{row.lockedTitle}</strong> : 'Locked memo'}</p>
            <button class="btn" onClick={() => void unlockText()}>
              Unlock to read
            </button>
          </div>
        ) : (
          <Markdown
            source={body ?? ''}
            tagStyle={tagStyle}
            onTag={(t) => go('tag', t)}
            onToggleTask={locked ? undefined : (line, checked) => void attempt(() => session.toggleTask(id, line, checked))}
          />
        )}

        {row.tags.length > 0 && !locked && (
          <div class="memo-card" style={{ background: 'none', boxShadow: 'none', padding: 0, margin: '12px 0 0' }}>
            <div class="tags" style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {row.tags.map((t) => (
                <TagChip key={t} tag={t} style={tagStyle(t)} onClick={() => go('tag', t)} />
              ))}
            </div>
          </div>
        )}

        <Attachments id={id} />

        {detail.hasPlace && (
          <div style={{ margin: '14px 0' }}>
            {session.mapTilesEnabled() ? (
              <TileMap points={[{ lat: detail.latitude, lon: detail.longitude }]} onOpen={() => open(mapsLink(detail.latitude, detail.longitude), '_blank', 'noopener')} />
            ) : null}
            <a class="small" href={mapsLink(detail.latitude, detail.longitude)} target="_blank" rel="noopener noreferrer">
              <Icon name="place" size={14} /> {detail.placeName || `${detail.latitude.toFixed(4)}, ${detail.longitude.toFixed(4)}`}
            </a>
          </div>
        )}

        <Reactions id={id} onAdd={() => setDialog('reaction')} />
        <References id={id} />
        <div class="detail-meta">
          <span>Written {detail.created}</span>
          {detail.updated !== detail.created && <span>Changed {detail.updated}</span>}
          <span>{detail.visibility}</span>
          {!locked && <span>{detail.wordCount} words</span>}
          {row.pending && <span>Waiting to sync</span>}
        </div>
        {detail.onServer && <Comments id={id} />}
      </div>

      {editing && <Editor localId={id} initial={editing.text} locked={editing.locked} onClose={() => setEditing(null)} />}
      {dialog === 'colour' && <ColourDialog id={id} current={detail.colourName ?? null} onClose={() => setDialog(null)} />}
      {dialog === 'share' && <ShareDialog id={id} onClose={() => setDialog(null)} />}
      {dialog === 'reference' && <ReferenceDialog id={id} onClose={() => setDialog(null)} />}
      {dialog === 'reaction' && (
        <Dialog title="React" onClose={() => setDialog(null)}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, fontSize: 26 }}>
            {session.quickReactions.map((r) => (
              <button
                key={r}
                class="icon-btn"
                style={{ width: 52, height: 52, fontSize: 26 }}
                onClick={() => {
                  setDialog(null)
                  void session.toggleReaction(id, r)
                }}
              >
                {r}
              </button>
            ))}
          </div>
        </Dialog>
      )}
    </div>
  )
}

function VisibilityItems(props: { id: string; current: string }) {
  const labels: Record<string, string> = { PRIVATE: 'Private', PROTECTED: 'Workspace', PUBLIC: 'Public' }
  return (
    <>
      {Object.entries(labels).map(([k, label]) => (
        <button key={k} onClick={() => void session.setVisibility(props.id, k)} aria-checked={props.current === k} role="menuitemradio">
          <Icon name={props.current === k ? 'check' : 'visibility'} size={18} /> {label}
        </button>
      ))}
    </>
  )
}

function Attachments(props: { id: string }) {
  useData()
  const list = session.attachments(props.id)
  if (list.length === 0) return null
  return (
    <div class="attachments">
      {list.map((a) => (
        <AttachmentTile key={a.localId} memo={props.id} a={a} />
      ))}
    </div>
  )
}

function AttachmentTile(props: { memo: string; a: AttachmentRow }) {
  const [url, setUrl] = useState<string | null>(null)
  const a = props.a
  useEffect(() => {
    let live = true
    session.attachmentUrl(props.memo, a.localId).then((u) => live && setUrl(u ?? null), () => undefined)
    return () => {
      live = false
    }
  }, [a.localId])
  const remove = async (e: Event) => {
    e.preventDefault()
    if (await confirmDialog('Remove attachment?', a.filename, 'Remove', true)) await session.removeAttachment(props.memo, a.localId)
  }
  return (
    <a class="attachment" href={url ?? undefined} target="_blank" rel="noopener noreferrer" download={a.isImage ? undefined : a.filename}>
      {a.isImage && url ? <img src={url} alt={a.filename} /> : <Icon name="attach" size={28} />}
      <span class="small" style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '100%' }}>
        {a.filename}
      </span>
      {!a.uploaded && <span class="small muted">Waiting to upload</span>}
      <button class="icon-btn remove" aria-label={`Remove ${a.filename}`} onClick={(e) => void remove(e)}>
        <Icon name="close" size={16} />
      </button>
    </a>
  )
}

function Reactions(props: { id: string; onAdd: () => void }) {
  useData()
  const list = session.reactions(props.id)
  return (
    <div class="reactions">
      {list.map((r) => (
        <button key={r.type} class={`reaction${r.mine ? ' mine' : ''}`} onClick={() => void session.toggleReaction(props.id, r.type)} aria-pressed={r.mine}>
          {r.type} <span class="small">{r.count}</span>
        </button>
      ))}
      <button class="reaction" onClick={props.onAdd} aria-label="Add reaction">
        <Icon name="smile" size={18} />
      </button>
    </div>
  )
}

function References(props: { id: string }) {
  useData()
  const refs = session.references(props.id)
  const back = session.backlinks(props.id)
  if (refs.length === 0 && back.length === 0) return null
  return (
    <div>
      {refs.length > 0 && (
        <>
          <div class="section-title">References</div>
          <div class="card-box">
            {refs.map((r) => (
              <div class="row" key={r.remoteName}>
                <Icon name="link" size={18} />
                <a
                  class="grow"
                  style={{ cursor: 'pointer' }}
                  onClick={async () => {
                    const local = r.localId ?? (await session.localIdForRemote(r.remoteName))
                    if (local) openMemo(local)
                    else toast('That memo could not be fetched')
                  }}
                >
                  {r.snippet || r.remoteName}
                </a>
                <button class="icon-btn" aria-label="Remove reference" onClick={() => void session.removeReference(props.id, r.remoteName)}>
                  <Icon name="close" size={18} />
                </button>
              </div>
            ))}
          </div>
        </>
      )}
      {back.length > 0 && (
        <>
          <div class="section-title">Referenced by</div>
          <MemoRows memos={back} />
        </>
      )}
    </div>
  )
}

function Comments(props: { id: string }) {
  const [list, loading, , reload] = useOnline(() => session.comments(props.id), [props.id], [])
  const [text, setText] = useState('')
  const send = async () => {
    if (!text.trim()) return
    if (await attempt(() => session.addComment(props.id, text.trim()), 'Could not comment')) {
      setText('')
      reload()
    }
  }
  return (
    <div>
      <div class="section-title">Comments</div>
      {loading && list.length === 0 && <div class="muted small">Loading…</div>}
      {list.map((c) => (
        <div class="comment" key={c.localId}>
          <div class="small muted">
            {c.mine ? 'You' : c.creator} · {c.dateLabel}
            {c.pending && ' · waiting to sync'}
          </div>
          <Markdown source={c.body} />
        </div>
      ))}
      <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
        <input class="input" placeholder="Add a comment" value={text} onInput={(e) => setText((e.target as HTMLInputElement).value)} onKeyDown={(e) => e.key === 'Enter' && void send()} />
        <button class="btn tonal" onClick={() => void send()} disabled={!text.trim()}>
          Send
        </button>
      </div>
    </div>
  )
}

function ColourDialog(props: { id: string; current: string | null; onClose: () => void }) {
  const set = async (name: string | null) => {
    props.onClose()
    await attempt(() => session.setColour(props.id, name))
  }
  return (
    <Dialog title="Note colour" onClose={props.onClose} actions={<button class="btn text" onClick={() => void set(null)}>No colour</button>}>
      <p class="muted small">Plain memos carry the colour to your other devices. Locked ones keep it in this browser only.</p>
      <div class="swatches">
        {session.colours().map((c) => (
          <button
            key={c.name}
            class={`swatch${props.current === c.name ? ' selected' : ''}`}
            title={c.name.toLowerCase()}
            aria-label={c.name.toLowerCase()}
            style={{ background: `color-mix(in srgb, ${hex(c.hex)} 30%, var(--card))` }}
            onClick={() => void set(c.name)}
          />
        ))}
      </div>
    </Dialog>
  )
}

function ShareDialog(props: { id: string; onClose: () => void }) {
  const [list, loading, error, reload] = useOnline(() => session.shares(props.id), [props.id], [])
  const [days, setDays] = useState('7')
  const create = async () => {
    const share = await attempt(() => session.createShare(props.id, Number(days)), 'Could not create a link')
    if (share) {
      await navigator.clipboard?.writeText(shareUrl(share.url)).catch(() => undefined)
      toast('Link created and copied')
      reload()
    }
  }
  return (
    <Dialog title="Share links" onClose={props.onClose} wide actions={<button class="btn text" onClick={props.onClose}>Done</button>}>
      <p class="muted small">Anyone with a link can read this memo until it expires or you revoke it.</p>
      {error && <p class="error-text">{error}</p>}
      {loading && <p class="muted small">Loading…</p>}
      <div class="card-box">
        {list.map((s) => (
          <div class="row" key={s.name}>
            <div class="grow">
              <a href={shareUrl(s.url)} target="_blank" rel="noopener noreferrer" style={{ wordBreak: 'break-all' }}>
                {shareUrl(s.url)}
              </a>
              <div class="small muted">
                Created {s.createdLabel}
                {s.expiresLabel ? ` · expires ${s.expiresLabel}` : ' · never expires'}
              </div>
            </div>
            <button class="btn text danger" onClick={async () => { await attempt(() => session.revokeShare(s.name)); reload() }}>
              Revoke
            </button>
          </div>
        ))}
        {list.length === 0 && !loading && <div class="row muted">No links yet.</div>}
      </div>
      <div style={{ display: 'flex', gap: 8, marginTop: 12, alignItems: 'center' }}>
        <select class="input" style={{ width: 'auto' }} value={days} onChange={(e) => setDays((e.target as HTMLSelectElement).value)}>
          <option value="1">Expires in a day</option>
          <option value="7">Expires in a week</option>
          <option value="30">Expires in a month</option>
          <option value="0">Never expires</option>
        </select>
        <button class="btn" onClick={() => void create()}>
          New link
        </button>
      </div>
    </Dialog>
  )
}

function ReferenceDialog(props: { id: string; onClose: () => void }) {
  const [q, setQ] = useState('')
  const list = session.referenceCandidates(q).filter((m) => m.localId !== props.id)
  return (
    <Dialog title="Reference a memo" onClose={props.onClose} wide>
      <input class="search" style={{ width: '100%' }} autoFocus placeholder="Search memos" value={q} onInput={(e) => setQ((e.target as HTMLInputElement).value)} />
      <div style={{ marginTop: 8, maxHeight: '50vh', overflowY: 'auto' }}>
        {list.map((m) => (
          <div
            key={m.localId}
            class="compact-row"
            onClick={async () => {
              props.onClose()
              await attempt(() => session.addReference(props.id, m.localId))
              toast('Reference added')
            }}
          >
            <span class="title">{m.title || 'Untitled'}</span>
            <span class="time">{m.dateLabel}</span>
          </div>
        ))}
        {list.length === 0 && <div class="empty">No synced memos match.</div>}
      </div>
    </Dialog>
  )
}

export { errorText }
