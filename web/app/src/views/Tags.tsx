import { useState } from 'preact/hooks'
import { attempt, hex, session, useData } from '../core'
import { Dialog } from '../components/ui'
import { Icon } from '../components/Icon'
import { go, type Route } from '../router'

interface Style {
  emoji: string | null
  colourName: string | null
  colourHex: number
}

/** Every tag with its memo count, and the emoji and colour each one is shown with. */
export function TagsView(_: { route: Route }) {
  useData()
  const [filter, setFilter] = useState('')
  const [editing, setEditing] = useState<string | null>(null)

  const styles = new Map<string, Style>()
  for (const s of session.tagStyles()) styles.set(s.tag, { emoji: s.emoji ?? null, colourName: s.colourName ?? null, colourHex: s.colourHex })

  const all = session.tagCounts()
  const needle = filter.trim().replace(/^#/, '').toLowerCase()
  const shown = needle ? all.filter((t) => t.tag.toLowerCase().includes(needle)) : all

  return (
    <>
      <div class="pane-header">
        <h1>Tags</h1>
        <span class="muted small">{all.length}</span>
      </div>
      <div class="pane-body">
        {all.length === 0 ? (
          <div class="empty">No tags yet. Write #something in any memo.</div>
        ) : (
          <>
            <div class="field">
              <input class="input" type="search" placeholder="Filter tags" aria-label="Filter tags" value={filter} onInput={(e) => setFilter((e.target as HTMLInputElement).value)} />
            </div>
            {shown.length === 0 && <div class="empty">No tags match.</div>}
            {shown.length > 0 && (
              <div class="card-box">
                {shown.map((t) => {
                  const s = styles.get(t.tag)
                  return (
                    <div class="row" key={t.tag}>
                      <span style={{ width: 24, textAlign: 'center' }}>{s?.emoji || '#'}</span>
                      <button class="grow" style={{ textAlign: 'left', background: 'none', border: 0, padding: 0, color: 'inherit', font: 'inherit', cursor: 'pointer' }} onClick={() => go('tag', t.tag)}>
                        {t.tag}
                      </button>
                      {s && s.colourHex >= 0 && <span class="swatch" style={{ width: 18, height: 18, cursor: 'default', background: swatchFill(s.colourHex) }} aria-label={s.colourName ?? undefined} />}
                      <span class="muted small">{t.count}</span>
                      <button class="icon-btn" title="Style" aria-label={`Style ${t.tag}`} onClick={() => setEditing(t.tag)}>
                        <Icon name="palette" size={20} />
                      </button>
                    </div>
                  )
                })}
              </div>
            )}
          </>
        )}
      </div>
      {editing !== null && <StyleDialog tag={editing} current={styles.get(editing)} onClose={() => setEditing(null)} />}
    </>
  )
}

function swatchFill(colour: number): string {
  return `color-mix(in srgb, ${hex(colour)} 30%, var(--card))`
}

function StyleDialog(props: { tag: string; current: Style | undefined; onClose: () => void }) {
  const [emoji, setEmoji] = useState<string | null>(props.current?.emoji ?? null)
  const [colour, setColour] = useState<string | null>(props.current?.colourName ?? null)
  const groups = session.emojiCatalogue()
  const colours = session.colours()

  const save = async () => {
    const e = emoji?.trim() || null
    await attempt(() => session.setTagStyle(props.tag, e, colour), 'Could not save the style')
    props.onClose()
  }

  return (
    <Dialog
      title={`#${props.tag}`}
      wide
      onClose={props.onClose}
      actions={
        <>
          <button class="btn text" onClick={props.onClose}>
            Cancel
          </button>
          <button class="btn" onClick={() => void save()}>
            Save
          </button>
        </>
      }
    >
      <div class="section-title">Emoji</div>
      <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
        <input
          class="input"
          style={{ width: 120 }}
          aria-label="Any emoji"
          placeholder="Any emoji"
          value={emoji ?? ''}
          onInput={(e) => setEmoji((e.target as HTMLInputElement).value || null)}
        />
        <button class={`chip${emoji === null ? ' selected' : ''}`} onClick={() => setEmoji(null)}>
          None
        </button>
      </div>
      <div style={{ maxHeight: 260, overflowY: 'auto', marginTop: 8 }}>
        {groups.map((g) => (
          <div key={g.name}>
            <div class="muted small" style={{ margin: '8px 0 4px' }}>
              {g.name}
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 2 }}>
              {g.emoji.map((em) => (
                <button
                  key={em}
                  class={`icon-btn${emoji === em ? ' on' : ''}`}
                  style={{ fontSize: 20, outline: emoji === em ? '2px solid var(--primary)' : undefined }}
                  aria-label={em}
                  onClick={() => setEmoji(em)}
                >
                  {em}
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>

      <div class="section-title">Colour</div>
      <div class="swatches">
        <button class={`swatch${colour === null ? ' selected' : ''}`} style={{ background: 'var(--card)' }} title="None" aria-label="No colour" onClick={() => setColour(null)} />
        {colours.map((c) => (
          <button key={c.name} class={`swatch${colour === c.name ? ' selected' : ''}`} style={{ background: swatchFill(c.hex) }} title={c.name} aria-label={c.name} onClick={() => setColour(c.name)} />
        ))}
      </div>
      <p class="muted small">The colour is also saved to the server's tag setting, so Memos' own web page shows it too.</p>
    </Dialog>
  )
}
