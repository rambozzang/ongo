import type { Schedule } from '@/types/schedule'
import { parseKstLocalDateTime } from '@/utils/kst'

/** User-provided titles remain text when opened in a spreadsheet. */
export function creatorCsv(rows: unknown[][]): string {
  return (
    '\uFEFF' +
    rows
      .map((row) =>
        row
          .map((value) => {
            let cell = String(value ?? '')
        if (typeof value === 'string' && /^[\s]*[=+@-]/.test(cell)) cell = `'${cell}`
            return `"${cell.replace(/"/g, '""')}"`
          })
          .join(','),
      )
      .join('\r\n')
  )
}

export function downloadCreatorFile(content: string, filename: string, type: string): void {
  const url = URL.createObjectURL(new Blob([content], { type }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

function escapeIcs(value: string): string {
  return value
    .replace(/\\/g, '\\\\')
    .replace(/\r\n|\r|\n/g, '\\n')
    .replace(/;/g, '\\;')
    .replace(/,/g, '\\,')
}

function instant(value: string): string {
  return parseKstLocalDateTime(value)
    .toISOString()
    .replace(/[-:]/g, '')
    .replace(/\.\d{3}/, '')
}

/** RFC 5545 limits a content line to 75 octets, including Korean titles. */
function foldLine(line: string): string {
  const encoder = new TextEncoder()
  let part = '',
    bytes = 0
  const parts: string[] = []
  for (const character of line) {
    const size = encoder.encode(character).length
    if (bytes + size > 75) {
      parts.push(part)
      part = ' '
      bytes = 1
    }
    part += character
    bytes += size
  }
  parts.push(part)
  return parts.join('\r\n')
}

/** One event per target account, using the actual platform-specific KST time. */
export function schedulesIcs(schedules: Schedule[], now = new Date()): string {
  const lines = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//onGo//Creator Calendar//KO',
    'CALSCALE:GREGORIAN',
  ]
  const stamp = now
    .toISOString()
    .replace(/[-:]/g, '')
    .replace(/\.\d{3}/, '')
  for (const schedule of schedules) {
    for (const target of schedule.platforms) {
      const at = target.scheduledAt || schedule.scheduledAt
      if (!Number.isFinite(parseKstLocalDateTime(at).getTime())) continue
      lines.push(
        'BEGIN:VEVENT',
        `UID:schedule-${schedule.id}-${target.platform}-${target.channelId ?? 'default'}@ongo`,
        `DTSTAMP:${stamp}`,
        `DTSTART:${instant(at)}`,
        `DURATION:PT15M`,
        `SUMMARY:${escapeIcs(`${schedule.videoTitle ?? ''} · ${target.platform}`)}`,
        `DESCRIPTION:${escapeIcs(`${target.status ?? schedule.status} | Asia/Seoul`)}`,
        `STATUS:${schedule.status === 'CANCELLED' || target.status === 'CANCELLED' ? 'CANCELLED' : 'CONFIRMED'}`,
        'END:VEVENT',
      )
    }
  }
  lines.push('END:VCALENDAR')
  return lines.map(foldLine).join('\r\n') + '\r\n'
}
