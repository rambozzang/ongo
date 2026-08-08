import { describe, expect, it } from 'vitest'
import { escapeHtml, highlightTemplateVariables } from './html'

describe('html escaping', () => {
  it('escapes markup before it reaches v-html', () => {
    expect(escapeHtml('<img src=x onerror=alert(1)>')).toBe(
      '&lt;img src=x onerror=alert(1)&gt;',
    )
  })

  it('highlights only the template syntax owned by the preview', () => {
    expect(highlightTemplateVariables('<script>{{title}}</script>')).toContain(
      '&lt;script&gt;<span class="variable-highlight">{{title}}</span>&lt;/script&gt;',
    )
  })
})
