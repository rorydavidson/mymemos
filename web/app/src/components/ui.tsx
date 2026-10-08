import type { ComponentChildren } from 'preact'
import { render } from 'preact'
import { useEffect, useRef, useState } from 'preact/hooks'
import { hex, session, useData, useToasts } from '../core'
import { Icon } from './Icon'

export function Dialog(props: { title: string; onClose: () => void; children: ComponentChildren; actions?: ComponentChildren; wide?: boolean }) {
  useEffect(() => {
    const key = (e: KeyboardEvent) => e.key === 'Escape' && props.onClose()
    addEventListener('keydown', key)
    return () => removeEventListener('keydown', key)
  }, [props.onClose])
  return (
    <div class="scrim" onClick={(e) => e.target === e.currentTarget && props.onClose()}>
      <div class={`dialog${props.wide ? ' wide' : ''}`} role="dialog" aria-modal="true" aria-label={props.title}>
        <h2>{props.title}</h2>
        {props.children}
        {props.actions && <div class="actions">{props.actions}</div>}
      </div>
    </div>
  )
}

export function Switch(props: { label: string; hint?: string; checked: boolean; onChange: (v: boolean) => void; disabled?: boolean }) {
  return (
    <label class="switch">
      <span>
        {props.label}
        {props.hint && <span class="hint">{props.hint}</span>}
      </span>
      <input type="checkbox" role="switch" checked={props.checked} disabled={props.disabled} onChange={(e) => props.onChange((e.target as HTMLInputElement).checked)} />
    </label>
  )
}

export function Toasts() {
  const list = useToasts()
  return (
    <div class="toasts" aria-live="polite">
      {list.map((t) => (
        <div class="toast" key={t.id}>
          <span class="grow" style={{ flex: 1 }}>
            {t.text}
          </span>
          {t.action && (
            <button class="btn text" onClick={t.action.run}>
              {t.action.label}
            </button>
          )}
        </div>
      ))}
    </div>
  )
}

/** A small popup menu anchored to its trigger. */
export function Menu(props: { onClose: () => void; children: ComponentChildren; style?: Record<string, string> }) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const away = (e: MouseEvent) => ref.current && !ref.current.contains(e.target as Node) && props.onClose()
    const key = (e: KeyboardEvent) => e.key === 'Escape' && props.onClose()
    setTimeout(() => addEventListener('click', away))
    addEventListener('keydown', key)
    return () => {
      removeEventListener('click', away)
      removeEventListener('keydown', key)
    }
  }, [])
  return (
    <div class="menu" ref={ref} role="menu" style={props.style} onClick={() => props.onClose()}>
      {props.children}
    </div>
  )
}

// ---- promise dialogs ------------------------------------------------------------------------

function mount<T>(build: (done: (value: T) => void) => ComponentChildren): Promise<T> {
  return new Promise((resolve) => {
    const host = document.createElement('div')
    document.body.appendChild(host)
    const done = (value: T) => {
      render(null, host)
      host.remove()
      resolve(value)
    }
    render(<>{build(done)}</>, host)
  })
}

export function confirmDialog(title: string, body: string, confirm = 'OK', danger = false): Promise<boolean> {
  return mount<boolean>((done) => (
    <Dialog
      title={title}
      onClose={() => done(false)}
      actions={
        <>
          <button class="btn text" onClick={() => done(false)}>
            Cancel
          </button>
          <button class={`btn${danger ? ' danger' : ''}`} onClick={() => done(true)}>
            {confirm}
          </button>
        </>
      }
    >
      <p class="muted">{body}</p>
    </Dialog>
  ))
}

export interface PromptField {
  name: string
  label: string
  type?: 'text' | 'password' | 'number' | 'datetime-local' | 'textarea' | 'url' | 'email'
  value?: string
  placeholder?: string
  hint?: string
}

/** Asks for one or more values; resolves null on cancel. */
export function promptDialog(title: string, fields: PromptField[], submit = 'OK', note?: string): Promise<Record<string, string> | null> {
  return mount<Record<string, string> | null>((done) => <PromptForm title={title} fields={fields} submit={submit} note={note} done={done} />)
}

function PromptForm(props: { title: string; fields: PromptField[]; submit: string; note?: string; done: (v: Record<string, string> | null) => void }) {
  const [values, set] = useState<Record<string, string>>(Object.fromEntries(props.fields.map((f) => [f.name, f.value ?? ''])))
  const first = useRef<HTMLInputElement & HTMLTextAreaElement>(null)
  useEffect(() => first.current?.focus(), [])
  return (
    <Dialog
      title={props.title}
      onClose={() => props.done(null)}
      actions={
        <>
          <button class="btn text" onClick={() => props.done(null)}>
            Cancel
          </button>
          <button class="btn" onClick={() => props.done(values)}>
            {props.submit}
          </button>
        </>
      }
    >
      <form
        onSubmit={(e) => {
          e.preventDefault()
          props.done(values)
        }}
      >
        {props.note && <p class="muted small">{props.note}</p>}
        {props.fields.map((f, i) => (
          <div class="field" key={f.name}>
            <label for={`pf-${f.name}`}>{f.label}</label>
            {f.type === 'textarea' ? (
              <textarea
                id={`pf-${f.name}`}
                ref={i === 0 ? first : undefined}
                class="textarea"
                rows={6}
                placeholder={f.placeholder}
                value={values[f.name]}
                onInput={(e) => set({ ...values, [f.name]: (e.target as HTMLTextAreaElement).value })}
              />
            ) : (
              <input
                id={`pf-${f.name}`}
                ref={i === 0 ? first : undefined}
                class="input"
                type={f.type ?? 'text'}
                placeholder={f.placeholder}
                value={values[f.name]}
                autocomplete={f.type === 'password' ? 'current-password' : 'off'}
                onInput={(e) => set({ ...values, [f.name]: (e.target as HTMLInputElement).value })}
              />
            )}
            {f.hint && <span class="muted small">{f.hint}</span>}
          </div>
        ))}
        <button type="submit" hidden />
      </form>
    </Dialog>
  )
}

/** Asks for the memo password, offering to keep it for this browser session. */
export async function askPassword(wrong = false): Promise<boolean> {
  const answer = await mount<{ password: string; remember: boolean } | null>((done) => <PasswordForm wrong={wrong} done={done} />)
  if (!answer || !answer.password) return false
  session.usePassword(answer.password, answer.remember)
  return true
}

function PasswordForm(props: { wrong: boolean; done: (v: { password: string; remember: boolean } | null) => void }) {
  const [password, setPassword] = useState('')
  const [remember, setRemember] = useState(true)
  return (
    <Dialog
      title="Memo password"
      onClose={() => props.done(null)}
      actions={
        <>
          <button class="btn text" onClick={() => props.done(null)}>
            Cancel
          </button>
          <button class="btn" onClick={() => props.done({ password, remember })}>
            Unlock
          </button>
        </>
      }
    >
      <form
        onSubmit={(e) => {
          e.preventDefault()
          props.done({ password, remember })
        }}
      >
        {props.wrong && <p class="error-text">That password does not open this memo.</p>}
        <p class="muted small">One password for all locked memos. The server only ever sees the scrambled text.</p>
        <div class="field">
          <input class="input" type="password" autocomplete="current-password" value={password} autoFocus onInput={(e) => setPassword((e.target as HTMLInputElement).value)} />
        </div>
        <Switch label="Remember until this tab closes" checked={remember} onChange={setRemember} />
        <button type="submit" hidden />
      </form>
    </Dialog>
  )
}

// ---- tag styles -----------------------------------------------------------------------------

/** Tag emoji and colours from the config memo, looked up by tag. */
export function useTagStyles(): (tag: string) => { emoji?: string | null; colour?: string | null } | undefined {
  const v = useData()
  const [map, setMap] = useState(new Map<string, { emoji?: string | null; colour?: string | null }>())
  useEffect(() => {
    const m = new Map<string, { emoji?: string | null; colour?: string | null }>()
    for (const s of session.tagStyles()) m.set(s.tag, { emoji: s.emoji, colour: s.colourHex >= 0 ? hex(s.colourHex) : null })
    setMap(m)
  }, [v])
  return (tag) => map.get(tag) ?? (tag.includes('/') ? map.get(tag.split('/')[0]) : undefined)
}

export function TagChip(props: { tag: string; style?: { emoji?: string | null; colour?: string | null }; onClick?: () => void; selected?: boolean }) {
  const c = props.style?.colour
  return (
    <button
      class={`chip${props.selected ? ' selected' : ''}`}
      style={c && !props.selected ? { background: `color-mix(in srgb, ${c} 22%, var(--card))`, color: 'var(--on-bg)' } : undefined}
      onClick={(e) => {
        e.stopPropagation()
        props.onClick?.()
      }}
    >
      {props.style?.emoji ? `${props.style.emoji} ` : '#'}
      {props.tag}
    </button>
  )
}

export function Spinner() {
  return (
    <div class="empty">
      <Icon name="sync" />
    </div>
  )
}
