import { Parser, type Node } from 'commonmark'
import type { ComponentChildren, JSX } from 'preact'
import { useMemo } from 'preact/hooks'

/**
 * CommonMark as the apps render it. commonmark.js is the reference parser for the spec the
 * Android app's commonmark-java implements; the GFM extensions that app adds (task lists,
 * tables, strikethrough, autolinks) are done here over the parsed tree, plus `#tags`.
 *
 * Raw HTML is shown as text, never interpreted, and links only open http, https, mailto and
 * geo: text from a server or a shared note cannot talk the page into anything else.
 */
export interface MarkdownProps {
  source: string
  /** Lines dropped from the front of [source], so a task's line index maps back to the memo. */
  lineOffset?: number
  onToggleTask?: (lineIndex: number, checked: boolean) => void
  onTag?: (tag: string) => void
  tagStyle?: (tag: string) => { emoji?: string | null; colour?: string | null } | undefined
}

const parser = new Parser({ smart: false })
const SAFE_SCHEMES = /^(https?:|mailto:|geo:)/i
const TAG = /(?<![\p{L}\p{N}_/])#([\p{L}\p{N}_/-]+)/gu
const URL_RE = /\bhttps?:\/\/[^\s<>()]+[^\s<>().,;:!?'"\]]/g
const STRIKE = /~~(?=\S)([\s\S]*?\S)~~/g
const TABLE_RULE = /^\s*\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)*\|?\s*$/
const TASK_LINE = /^\s*(?:[-*+]|\d+[.)])\s+\[([ xX])\](\s|$)/

export function Markdown(props: MarkdownProps) {
  const tree = useMemo(() => parser.parse(props.source), [props.source])
  const lines = useMemo(() => props.source.split('\n'), [props.source])
  return <div class="md">{children(tree, { ...props, lines })}</div>
}

type Ctx = MarkdownProps & { lines: string[] }

function children(node: Node, ctx: Ctx): ComponentChildren[] {
  const out: ComponentChildren[] = []
  let i = 0
  for (let c = node.firstChild; c; c = c.next) out.push(render(c, ctx, i++))
  return out
}

function render(node: Node, ctx: Ctx, key: number): ComponentChildren {
  switch (node.type) {
    case 'paragraph': {
      const table = tableFrom(node, ctx, key)
      if (table) return table
      // A tight list's paragraph is just its text, as in commonmark's own HTML renderer.
      const grand = node.parent?.parent
      if (grand?.type === 'list' && grand.listTight) return <>{children(node, ctx)}</>
      return <p key={key}>{children(node, ctx)}</p>
    }
    case 'heading': {
      const H = `h${node.level}` as keyof JSX.IntrinsicElements
      return <H key={key}>{children(node, ctx)}</H>
    }
    case 'text':
      return <>{inline(node.literal ?? '', ctx, key)}</>
    case 'softbreak':
      return '\n'
    case 'linebreak':
      return <br key={key} />
    case 'emph':
      return <em key={key}>{children(node, ctx)}</em>
    case 'strong':
      return <strong key={key}>{children(node, ctx)}</strong>
    case 'code':
      return <code key={key}>{node.literal}</code>
    case 'code_block':
      return (
        <pre key={key}>
          <code>{node.literal}</code>
        </pre>
      )
    case 'html_block':
      return <p key={key}>{node.literal}</p>
    case 'html_inline':
      return <>{node.literal}</>
    case 'thematic_break':
      return <hr key={key} />
    case 'block_quote':
      return <blockquote key={key}>{children(node, ctx)}</blockquote>
    case 'link': {
      const dest = node.destination ?? ''
      if (!SAFE_SCHEMES.test(dest)) return <>{children(node, ctx)}</>
      return (
        <a key={key} href={dest} title={node.title ?? undefined} target="_blank" rel="noopener noreferrer" onClick={(e) => e.stopPropagation()}>
          {children(node, ctx)}
        </a>
      )
    }
    case 'image': {
      const dest = node.destination ?? ''
      const alt = textOf(node)
      if (!/^https?:/i.test(dest)) return <>{alt}</>
      return <img key={key} src={dest} alt={alt} loading="lazy" referrerpolicy="no-referrer" />
    }
    case 'list': {
      const items = children(node, ctx)
      if (node.listType === 'ordered') {
        return (
          <ol key={key} start={node.listStart ?? 1}>
            {items}
          </ol>
        )
      }
      return <ul key={key}>{items}</ul>
    }
    case 'item':
      return taskItem(node, ctx, key) ?? <li key={key}>{children(node, ctx)}</li>
    default:
      return <>{children(node, ctx)}</>
  }
}

/**
 * A list item starting `[ ]` or `[x]` becomes a checkbox that edits its own line. The source
 * line is what is checked: commonmark.js splits `[` into a text node of its own, since it may
 * open a link, so the marker can span several nodes.
 */
function taskItem(node: Node, ctx: Ctx, key: number): ComponentChildren | null {
  const para = node.firstChild
  if (!para || para.type !== 'paragraph') return null
  const lineNo = (node.sourcepos?.[0]?.[0] ?? 1) - 1
  const m = (ctx.lines[lineNo] ?? '').match(TASK_LINE)
  if (!m) return null
  const checked = m[1] !== ' '
  // Drop the marker's characters from the front of the paragraph's text nodes.
  let skip = 3
  const body: ComponentChildren[] = []
  let i = 0
  for (let c = para.firstChild; c; c = c.next) {
    if (skip > 0 && c.type === 'text') {
      const lit = c.literal ?? ''
      if (lit.length <= skip) {
        skip -= lit.length
        continue
      }
      body.push(<>{inline(lit.slice(skip).replace(/^\s+/, ''), ctx, i++)}</>)
      skip = 0
      continue
    }
    if (skip > 0 && c.type === 'softbreak') continue
    skip = 0
    body.push(render(c, ctx, i++))
  }
  for (let c = para.next; c; c = c.next) body.push(render(c, ctx, i++))
  const line = lineNo + (ctx.lineOffset ?? 0)
  return (
    <li key={key} class={`task${checked ? ' done' : ''}`}>
      <input
        type="checkbox"
        checked={checked}
        disabled={!ctx.onToggleTask}
        aria-label={checked ? 'Untick task' : 'Tick task'}
        onClick={(e) => {
          e.preventDefault()
          e.stopPropagation()
          ctx.onToggleTask?.(line, !checked)
        }}
      />
      <span class="task-body">{body}</span>
    </li>
  )
}

/** Tags, bare links and ~~strikethrough~~ inside one run of text. */
function inline(text: string, ctx: Ctx, key: number): ComponentChildren[] {
  type Hit = { start: number; end: number; node: ComponentChildren }
  const hits: Hit[] = []
  for (const m of text.matchAll(STRIKE)) {
    hits.push({ start: m.index!, end: m.index! + m[0].length, node: <del>{inline(m[1], ctx, key)}</del> })
  }
  for (const m of text.matchAll(URL_RE)) {
    hits.push({
      start: m.index!,
      end: m.index! + m[0].length,
      node: (
        <a href={m[0]} target="_blank" rel="noopener noreferrer" onClick={(e) => e.stopPropagation()}>
          {m[0]}
        </a>
      ),
    })
  }
  for (const m of text.matchAll(TAG)) {
    const tag = m[1].replace(/[/-]+$/, '')
    if (!tag || tag.startsWith('colour/')) continue
    const style = ctx.tagStyle?.(tag)
    hits.push({
      start: m.index!,
      end: m.index! + 1 + tag.length,
      node: (
        <a
          class="tag"
          role="link"
          style={style?.colour ? { color: style.colour } : undefined}
          onClick={(e) => {
            e.stopPropagation()
            ctx.onTag?.(tag)
          }}
        >
          {style?.emoji ? `${style.emoji} ` : ''}#{tag}
        </a>
      ),
    })
  }
  hits.sort((a, b) => a.start - b.start)
  const parts: ComponentChildren[] = []
  let at = 0
  let k = 0
  for (const h of hits) {
    if (h.start < at) continue
    if (h.start > at) parts.push(text.slice(at, h.start))
    parts.push(<span key={`${key}-${k++}`}>{h.node}</span>)
    at = h.end
  }
  if (at < text.length) parts.push(text.slice(at))
  return parts
}

/** A paragraph whose source is a GFM table: a header row, a rule, then rows. */
function tableFrom(node: Node, ctx: Ctx, key: number): ComponentChildren | null {
  const pos = node.sourcepos
  if (!pos) return null
  const src = ctx.lines.slice(pos[0][0] - 1, pos[1][0])
  if (src.length < 2 || !src[0].includes('|') || !TABLE_RULE.test(src[1])) return null
  const cells = (line: string) =>
    line.trim().replace(/^\|/, '').replace(/\|$/, '').split(/(?<!\\)\|/).map((c) => c.trim())
  const align = cells(src[1]).map((c) => (c.startsWith(':') && c.endsWith(':') ? 'center' : c.endsWith(':') ? 'right' : undefined))
  const cell = (text: string) => {
    const p = parser.parse(text).firstChild
    return p ? children(p, ctx) : null
  }
  return (
    <table key={key}>
      <thead>
        <tr>
          {cells(src[0]).map((c, i) => (
            <th style={align[i] ? { textAlign: align[i] } : undefined}>{cell(c)}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {src.slice(2).map((row) => (
          <tr>
            {cells(row).map((c, i) => (
              <td style={align[i] ? { textAlign: align[i] } : undefined}>{cell(c)}</td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function textOf(node: Node): string {
  let s = ''
  const w = node.walker()
  for (let e = w.next(); e; e = w.next()) if (e.entering && e.node.literal) s += e.node.literal
  return s
}
