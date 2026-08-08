import { describe, expect, it } from 'vitest'
import { kstDateString, kstTimeString, parseKstLocalDateTime } from './kst'

describe('KST date helpers', () => {
  it('uses KST calendar date rather than UTC date', () => {
    expect(kstDateString(new Date('2026-08-09T15:30:00.000Z'))).toBe('2026-08-10')
  })

  it('parses server wall-clock values without browser timezone drift', () => {
    expect(parseKstLocalDateTime('2026-08-10T09:00').toISOString()).toBe('2026-08-10T00:00:00.000Z')
    expect(kstTimeString('2026-08-10T09:00')).toBe('09:00')
  })
})
