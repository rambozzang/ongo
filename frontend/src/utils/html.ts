/** Escape user/AI supplied text before inserting the small markup we own. */
export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

export function highlightTemplateVariables(value: string): string {
  return escapeHtml(value).replace(
    /\{\{([^}]+)\}\}/g,
    '<span class="variable-highlight">{{$1}}</span>',
  )
}
