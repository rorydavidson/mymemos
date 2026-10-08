import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { attempt, errorText, session, toast } from '../core'
import { Icon } from './Icon'
import { confirmDialog } from './ui'

export interface EditorProps {
  /** Editing an existing memo; absent for a new one. */
  localId?: string
  initial: string
  /** The memo is locked: [initial] is its decrypted text and saving encrypts it again. */
  locked?: boolean
  onClose: (savedId?: string) => void
}

const DRAFT_KEY = 'mymemos.draft.new'
const VIS_KEY = 'mymemos.defaultVisibility'

/**
 * The editor: a plain textarea with the apps' formatting bar, Return carrying list markers
 * on (MarkdownContinuation), `#` completing tags and `@` completing due dates from the same
 * parser the tasks screen reads. Ctrl or Cmd + Enter saves; Escape leaves.
 */
export function Editor(props: EditorProps) {
  const isNew = !props.localId
  const [text, setText] = useState(() => (isNew ? readDraft() ?? props.initial : props.initial))
  const [visibility, setVisibility] = useState(() => storage(VIS_KEY) ?? 'PRIVATE')
  const [pinned, setPinned] = useState(false)
  const [files, setFiles] = useState<File[]>([])
  const [cursor, setCursor] = useState(0)
  const [showTemplates, setShowTemplates] = useState(false)
  const [saving, setSaving] = useState(false)
  const area = useRef<HTMLTextAreaElement>(null)
  const filePicker = useRef<HTMLInputElement>(null)
  const datePicker = useRef<HTMLInputElement>(null)
  const dirty = text !== props.initial || files.length > 0

  useEffect(() => {
    area.current?.focus()
    if (!isNew) return
    // The server's default visibility, remembered for when it cannot be asked.
    session.defaultVisibility().then(
      (v) => {
        setVisibility(v)
        store(VIS_KEY, v)
      },
      () => undefined,
    )
  }, [])

  useEffect(() => {
    if (isNew) store(DRAFT_KEY, text.trim() ? text : null)
  }, [text])

  const token = useMemo(() => currentToken(text, cursor), [text, cursor])
  const suggestions = useMemo(() => {
    if (!token) return []
    if (token.kind === '#') return session.tagSuggestions(token.prefix).map((t) => ({ label: `#${t}`, hint: '', insert: `#${t} ` }))
    return [
      ...session.dateSuggestions(token.prefix).map((d) => ({ label: `@${d.token}`, hint: d.hint, insert: `@${d.token} ` })),
      { label: 'Pick a date…', hint: '', insert: '' },
    ]
  }, [token])

  const apply = (next: string, caret: number) => {
    setText(next)
    setCursor(caret)
    requestAnimationFrame(() => {
      const el = area.current
      if (!el) return
      el.focus()
      el.setSelectionRange(caret, caret)
    })
  }

  const accept = (s: { insert: string }) => {
    if (!token) return
    if (!s.insert) {
      datePicker.current?.showPicker?.()
      datePicker.current?.click()
      return
    }
    const before = text.slice(0, token.start)
    const after = text.slice(cursor)
    apply(before + s.insert + after, before.length + s.insert.length)
  }

  /** Wraps the selection, or inserts the markers around the caret. */
  const wrap = (left: string, right = left) => {
    const el = area.current!
    const { selectionStart: a, selectionEnd: b } = el
    const next = text.slice(0, a) + left + text.slice(a, b) + right + text.slice(b)
    apply(next, b + left.length + (a === b ? 0 : right.length))
  }

  /** Puts [prefix] at the start of each selected line, or the caret's line. */
  const linePrefix = (prefix: string) => {
    const el = area.current!
    const a = text.lastIndexOf('\n', el.selectionStart - 1) + 1
    let b = text.indexOf('\n', el.selectionEnd)
    if (b < 0) b = text.length
    const lines = text.slice(a, b).split('\n').map((l) => (l.startsWith(prefix) ? l.slice(prefix.length) : prefix + l))
    const block = lines.join('\n')
    apply(text.slice(0, a) + block + text.slice(b), a + block.length)
  }

  const insert = (s: string) => {
    const el = area.current!
    const a = el.selectionStart
    apply(text.slice(0, a) + s + text.slice(el.selectionEnd), a + s.length)
  }

  const save = async () => {
    if (saving) return
    if (!text.trim() && files.length === 0) {
      props.onClose()
      return
    }
    setSaving(true)
    try {
      let id = props.localId
      if (!id) {
        id = await session.create(text, visibility, pinned)
        store(DRAFT_KEY, null)
      } else if (props.locked) {
        if (!(await session.saveLocked(id, text))) throw new Error('The memo password is needed to save a locked memo')
      } else {
        await session.updateContent(id, text)
      }
      for (const f of files) await session.attach(id, f.name, f.type, new Uint8Array(await f.arrayBuffer()))
      props.onClose(id)
    } catch (e) {
      toast(`Could not save: ${errorText(e)}`)
      setSaving(false)
    }
  }

  const close = async () => {
    if (dirty && !isNew && !(await confirmDialog('Discard changes?', 'What you changed here will be lost.', 'Discard', true))) return
    // A new memo's draft is kept, so leaving is never a loss.
    props.onClose()
  }

  const onKeyDown = (e: KeyboardEvent) => {
    if ((e.metaKey || e.ctrlKey) && e.key === 'Enter') {
      e.preventDefault()
      void save()
      return
    }
    if (e.key === 'Escape') {
      e.preventDefault()
      void close()
      return
    }
    if (suggestions.length > 0 && (e.key === 'Tab' || (e.key === 'Enter' && token && token.prefix.length > 0))) {
      e.preventDefault()
      accept(suggestions[0])
      return
    }
    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
      const el = e.target as HTMLTextAreaElement
      if (el.selectionStart !== el.selectionEnd) return
      const pos = el.selectionStart
      const withBreak = text.slice(0, pos) + '\n' + text.slice(pos)
      const result = session.continueList(withBreak, pos + 1)
      if (result) {
        e.preventDefault()
        apply(result.text, result.cursor)
      }
    }
  }

  return (
    <>
      <div class="sheet-backdrop" onClick={() => void close()} />
      <div class="sheet-full editor" role="dialog" aria-label={isNew ? 'New memo' : 'Edit memo'}>
        <div class="pane-header">
          <button class="icon-btn" onClick={() => void close()} aria-label="Close">
            <Icon name="close" />
          </button>
          <h1>{isNew ? 'New memo' : props.locked ? 'Edit locked memo' : 'Edit memo'}</h1>
          <button class="btn" onClick={() => void save()} disabled={saving}>
            {saving ? 'Saving…' : 'Save'}
          </button>
        </div>
        <textarea
          ref={area}
          class="editor-text"
          value={text}
          placeholder="Write something. #tags, - [ ] tasks and @tomorrow all work."
          spellcheck
          onInput={(e) => {
            const el = e.target as HTMLTextAreaElement
            setText(el.value)
            setCursor(el.selectionStart)
          }}
          onKeyDown={onKeyDown}
          onKeyUp={(e) => setCursor((e.target as HTMLTextAreaElement).selectionStart)}
          onClick={(e) => setCursor((e.target as HTMLTextAreaElement).selectionStart)}
        />
        {files.length > 0 && (
          <div class="toolbar" style={{ borderTop: 0 }}>
            {files.map((f, i) => (
              <span class="chip" key={i}>
                <Icon name="attach" size={14} /> {f.name}
                <button class="icon-btn" style={{ width: 22, height: 22 }} aria-label={`Remove ${f.name}`} onClick={() => setFiles(files.filter((_, j) => j !== i))}>
                  <Icon name="close" size={14} />
                </button>
              </span>
            ))}
          </div>
        )}
        {suggestions.length > 0 && (
          <div class="toolbar" style={{ gap: 6 }} aria-label="Suggestions">
            {suggestions.map((s, i) => (
              <button key={s.label} class={`chip${i === 0 ? ' selected' : ''}`} onMouseDown={(e) => e.preventDefault()} onClick={() => accept(s)}>
                {s.label}
                {s.hint && <span class="muted" style={{ marginLeft: 4 }}>{s.hint}</span>}
              </button>
            ))}
          </div>
        )}
        <div class="toolbar">
          <button class="icon-btn" title="Task" onClick={() => linePrefix('- [ ] ')}>
            <Icon name="checkbox" />
          </button>
          <button class="icon-btn" title="Bullet list" onClick={() => linePrefix('- ')}>
            <Icon name="list" />
          </button>
          <button class="icon-btn" title="Heading" onClick={() => linePrefix('# ')}>
            <Icon name="heading" />
          </button>
          <button class="icon-btn" title="Bold" onClick={() => wrap('**')}>
            <Icon name="bold" />
          </button>
          <button class="icon-btn" title="Italic" onClick={() => wrap('*')}>
            <Icon name="italic" />
          </button>
          <button class="icon-btn" title="Code" onClick={() => wrap('`')}>
            <Icon name="code" />
          </button>
          <button class="icon-btn" title="Quote" onClick={() => linePrefix('> ')}>
            <Icon name="quote" />
          </button>
          <button class="icon-btn" title="Link" onClick={() => wrap('[', '](https://)')}>
            <Icon name="link" />
          </button>
          <button class="icon-btn" title="Tag" onClick={() => insert('#')}>
            <Icon name="tag" />
          </button>
          {!props.locked && (
            <button class="icon-btn" title="Attach files or images" onClick={() => filePicker.current?.click()}>
              <Icon name="image" />
            </button>
          )}
          <button class="icon-btn" title="Templates" onClick={() => setShowTemplates(!showTemplates)}>
            <Icon name="template" />
          </button>
          <span class="spacer" />
          {isNew && (
            <>
              <button class={`icon-btn${pinned ? ' on' : ''}`} title={pinned ? 'Pinned' : 'Pin'} onClick={() => setPinned(!pinned)} aria-pressed={pinned}>
                <Icon name="pin" />
              </button>
              <select value={visibility} onChange={(e) => setVisibility((e.target as HTMLSelectElement).value)} aria-label="Visibility">
                <option value="PRIVATE">Private</option>
                <option value="PROTECTED">Workspace</option>
                <option value="PUBLIC">Public</option>
              </select>
            </>
          )}
        </div>
        {showTemplates && (
          <div class="menu" style={{ top: 'auto', bottom: '56px', right: '60px' }}>
            {session.templates().map((t) => (
              <button
                key={t.id}
                onClick={() => {
                  setShowTemplates(false)
                  insert(session.expandTemplate(t.body))
                }}
              >
                <Icon name="template" size={18} />
                {t.title}
              </button>
            ))}
            {session.templates().length === 0 && <div class="muted small" style={{ padding: 10 }}>No templates. Add some in Settings.</div>}
          </div>
        )}
        <input
          ref={filePicker}
          type="file"
          multiple
          hidden
          onChange={(e) => {
            const picked = Array.from((e.target as HTMLInputElement).files ?? [])
            ;(e.target as HTMLInputElement).value = ''
            if (props.localId) {
              for (const f of picked) {
                void attempt(async () => session.attach(props.localId!, f.name, f.type, new Uint8Array(await f.arrayBuffer())), 'Could not attach')
              }
              toast(picked.length === 1 ? 'Attached' : `${picked.length} files attached`)
            } else {
              setFiles([...files, ...picked])
            }
          }}
        />
        <input
          ref={datePicker}
          type="date"
          style={{ position: 'absolute', opacity: 0, pointerEvents: 'none', bottom: 0 }}
          onChange={(e) => {
            const v = (e.target as HTMLInputElement).value
            if (v && token) {
              const before = text.slice(0, token.start)
              apply(`${before}@${v} ${text.slice(cursor)}`, before.length + v.length + 2)
            }
          }}
        />
      </div>
    </>
  )
}

/** The `#tag` or `@date` being typed at the caret, if any. */
function currentToken(text: string, cursor: number): { kind: '#' | '@'; prefix: string; start: number } | null {
  const before = text.slice(0, cursor)
  const m = before.match(/(^|[\s(])([#@])([\p{L}\p{N}_/-]*)$/u)
  if (!m) return null
  const start = cursor - m[2].length - m[3].length
  // A `#` at the start of a line followed by nothing yet could be a heading; wait for a letter.
  if (m[2] === '#' && m[3].length === 0) return null
  return { kind: m[2] as '#' | '@', prefix: m[3], start }
}

function storage(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function store(key: string, value: string | null): void {
  try {
    if (value == null) localStorage.removeItem(key)
    else localStorage.setItem(key, value)
  } catch {
    // Private windows can refuse storage; a lost draft is the only cost.
  }
}

function readDraft(): string | null {
  return storage(DRAFT_KEY)
}
