import { describe, expect, it } from 'vitest'
import { fallbackOptimalSlot, kstWallClockToInstant, nextOptimalDateTime } from './optimalSchedule'

describe('optimal schedule conversion', () => {
  it('converts a server weekday/hour into the next local wall-clock time', () => {
    const now = new Date(2026, 7, 9, 8, 0) // Sunday
    expect(nextOptimalDateTime(now, { dayOfWeek: 1, hour: 9 })).toBe('2026-08-10T09:00')
  })

  it('moves an already-passed slot to the following week', () => {
    const now = new Date(2026, 7, 9, 10, 0) // Sunday
    expect(nextOptimalDateTime(now, { dayOfWeek: 0, hour: 9 })).toBe('2026-08-16T09:00')
  })

  it('clamps malformed server values instead of producing an invalid date', () => {
    const result = nextOptimalDateTime(new Date(2026, 7, 9, 8, 0), {
      dayOfWeek: 99,
      hour: -4,
    })
    expect(result).toMatch(/^2026-08-15T00:00$/)
  })

  it('uses safe defaults for non-finite analytics values', () => {
    expect(nextOptimalDateTime(new Date(2026, 7, 9, 8, 0), {
      dayOfWeek: Number.NaN,
      hour: Number.POSITIVE_INFINITY,
    })).toBe('2026-08-16T00:00')
  })

  it('provides a visible generic fallback when analytics has no history', () => {
    expect(fallbackOptimalSlot(0).hour).toBe(9)
    expect(fallbackOptimalSlot(4).hour).toBe(12)
  })

  it('converts KST wall-clock time to an instant without browser timezone drift', () => {
    expect(kstWallClockToInstant('2026-08-10T09:00').toISOString()).toBe('2026-08-10T00:00:00.000Z')
  })
})
