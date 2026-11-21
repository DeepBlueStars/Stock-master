/**
 * 轻量 Markdown 渲染器（报告正文用）。
 *
 * 为什么不装 marked/markdown-it：这一页只渲染自己系统里的报告，
 * 支持的语法就是标题、段落、列表、表格、引用、代码、加粗/斜体/链接这几样，
 * 自己写一份不带 HTML 透传的渲染器，既省一个依赖，也保证不会把外部正文里的 HTML 直接塞进页面。
 */

const escapeHtml = (text) =>
  String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')

/** 行内语法：先转义，再替换，避免正文里的标签被执行 */
function inline(text) {
  let html = escapeHtml(text)
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/__([^_]+)__/g, '<strong>$1</strong>')
  html = html.replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, '$1<em>$2</em>')
  html = html.replace(/~~([^~]+)~~/g, '<del>$1</del>')
  html = html.replace(
    /\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g,
    '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>'
  )
  return html
}

const isTableSeparator = (line) =>
  !!line && /^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)+\|?\s*$/.test(line)

const cellsOf = (row) => {
  let text = row.trim()
  if (text.startsWith('|')) text = text.slice(1)
  if (text.endsWith('|')) text = text.slice(0, -1)
  return text.split('|').map((cell) => cell.trim())
}

function renderList(items) {
  let pos = 0
  const build = (indent) => {
    const ordered = items[pos].ordered
    let html = ordered ? '<ol>' : '<ul>'
    while (pos < items.length && items[pos].indent >= indent) {
      if (items[pos].indent > indent) {
        const sub = build(items[pos].indent)
        html = html.replace(/<\/li>$/, `${sub}</li>`)
        continue
      }
      const text = inline(items[pos].text)
      pos += 1
      let children = ''
      if (pos < items.length && items[pos].indent > indent) {
        children = build(items[pos].indent)
      }
      html += `<li>${text}${children}</li>`
    }
    html += ordered ? '</ol>' : '</ul>'
    return html
  }
  return build(items[0].indent)
}

export function renderMarkdown(markdown) {
  if (!markdown) return ''
  const lines = String(markdown).replace(/\r\n?/g, '\n').split('\n')
  const out = []
  let i = 0

  while (i < lines.length) {
    const raw = lines[i]
    const line = raw.trim()

    if (!line) {
      i += 1
      continue
    }

    // 代码块
    if (/^```/.test(line)) {
      i += 1
      const buffer = []
      while (i < lines.length && !/^```/.test(lines[i].trim())) {
        buffer.push(lines[i])
        i += 1
      }
      i += 1
      out.push(`<pre><code>${escapeHtml(buffer.join('\n'))}</code></pre>`)
      continue
    }

    // 分隔线
    if (/^([-*_])\s*(\1\s*){2,}$/.test(line)) {
      out.push('<hr />')
      i += 1
      continue
    }

    // 标题
    const heading = /^(#{1,6})\s+(.*)$/.exec(line)
    if (heading) {
      const level = heading[1].length
      out.push(`<h${level}>${inline(heading[2].replace(/\s+#+\s*$/, ''))}</h${level}>`)
      i += 1
      continue
    }

    // 表格
    if (line.includes('|') && isTableSeparator(lines[i + 1])) {
      const header = cellsOf(lines[i])
      const aligns = cellsOf(lines[i + 1]).map((cell) => {
        const left = cell.startsWith(':')
        const right = cell.endsWith(':')
        if (left && right) return 'center'
        if (right) return 'right'
        if (left) return 'left'
        return ''
      })
      const alignAttr = (idx) => (aligns[idx] ? ` style="text-align:${aligns[idx]}"` : '')
      let html = '<div class="md-table"><table><thead><tr>'
      header.forEach((cell, idx) => {
        html += `<th${alignAttr(idx)}>${inline(cell)}</th>`
      })
      html += '</tr></thead><tbody>'
      i += 2
      while (i < lines.length && lines[i].trim() && lines[i].includes('|')) {
        const cells = cellsOf(lines[i])
        html += `<tr>${header
          .map((_, idx) => `<td${alignAttr(idx)}>${inline(cells[idx] || '')}</td>`)
          .join('')}</tr>`
        i += 1
      }
      html += '</tbody></table></div>'
      out.push(html)
      continue
    }

    // 引用
    if (/^>\s?/.test(line)) {
      const buffer = []
      while (i < lines.length && /^\s*>\s?/.test(lines[i])) {
        buffer.push(lines[i].replace(/^\s*>\s?/, ''))
        i += 1
      }
      out.push(`<blockquote>${renderMarkdown(buffer.join('\n'))}</blockquote>`)
      continue
    }

    // 列表
    const listMatch = /^(\s*)([-*+]|\d+[.)])\s+(.*)$/.exec(raw)
    if (listMatch) {
      const items = []
      const firstOrdered = /^\d/.test(listMatch[2])
      const firstIndent = listMatch[1].replace(/\t/g, '  ').length
      while (i < lines.length) {
        const match = /^(\s*)([-*+]|\d+[.)])\s+(.*)$/.exec(lines[i])
        if (!match) {
          // 缩进的续行并进上一条，别让它变成独立段落
          if (/^\s{2,}\S/.test(lines[i]) && items.length > 0) {
            items[items.length - 1].text += ` ${lines[i].trim()}`
            i += 1
            continue
          }
          break
        }
        const indent = match[1].replace(/\t/g, '  ').length
        const ordered = /^\d/.test(match[2])
        // 同一层换了列表类型（有序 <-> 无序）就断开，别把「1. 2.」并进「- 」里
        if (indent === firstIndent && ordered !== firstOrdered) break
        items.push({ indent, ordered, text: match[3] })
        i += 1
      }
      out.push(renderList(items))
      continue
    }

    // 段落
    const buffer = [line]
    i += 1
    while (i < lines.length) {
      const next = lines[i]
      if (!next.trim()) break
      if (/^(#{1,6}\s|```|>\s?)/.test(next.trim())) break
      if (/^(\s*)([-*+]|\d+[.)])\s+/.test(next)) break
      if (next.includes('|') && isTableSeparator(lines[i + 1])) break
      buffer.push(next.trim())
      i += 1
    }
    out.push(`<p>${buffer.map(inline).join('<br />')}</p>`)
  }

  return out.join('\n')
}

export default renderMarkdown
