import { describe, expect, it } from 'vitest'
import { creatorCsv, schedulesIcs } from './creatorExport'
import type { Schedule } from '@/types/schedule'

describe('creator exports', () => {
  it('escapes spreadsheet formulas, quotes and multiline creator titles', () => {
    const csv = creatorCsv([['=HYPERLINK("https://example.test")', '한글\n제목', 0, null]])
    expect(csv).toContain('"\'=HYPERLINK(""https://example.test"")"')
    expect(csv).toContain('"한글\n제목"')
    expect(csv).toContain('"0",""')
    expect(csv.charCodeAt(0)).toBe(0xfeff)
  })

  it('exports actual per-account publishing times in UTC and folds Korean text', () => {
    const schedule = {
      id: 1,
      videoTitle: '한글 제목,'.repeat(20),
      scheduledAt: '2026-10-09T09:00',
      status: 'SCHEDULED',
      platforms: [
        { platform: 'YOUTUBE', channelId: 10, scheduledAt: '2026-10-09T09:00' },
        { platform: 'YOUTUBE', channelId: 11, scheduledAt: '2026-10-09T13:30' },
      ],
    } as Schedule
    const ics = schedulesIcs([schedule], new Date('2026-10-01T00:00:00Z'))
    expect(ics).toContain('DTSTART:20261009T000000Z')
    expect(ics).toContain('DTSTART:20261009T043000Z')
    expect(ics).toContain('UID:schedule-1-YOUTUBE-10@ongo')
    expect(ics).toContain('UID:schedule-1-YOUTUBE-11@ongo')
    expect(ics).toContain('\\,')
    for (const line of ics.split('\r\n'))
      expect(new TextEncoder().encode(line).length).toBeLessThanOrEqual(75)
  })
})
