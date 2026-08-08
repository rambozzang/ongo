import { describe, expect, it } from 'vitest'
import { toScheduleRangeParams } from './schedule'
import { toDateTimeLocal } from '@/utils/schedule'

describe('schedule API range mapping', () => {
  it('maps an inclusive UI date range to the backend LocalDateTime contract', () => {
    expect(
      toScheduleRangeParams({
        startDate: '2026-08-10',
        endDate: '2026-08-16',
        status: 'SCHEDULED',
      }),
    ).toEqual({
      from: '2026-08-10T00:00:00',
      to: '2026-08-16T23:59:59',
      status: 'SCHEDULED',
    })
  })

  it('does not invent a range when the caller only filters by status', () => {
    expect(toScheduleRangeParams({ status: 'SCHEDULED' })).toEqual({
      from: undefined,
      to: undefined,
      status: 'SCHEDULED',
    })
  })

  it('serializes a local wall-clock time without a UTC suffix', () => {
    const date = new Date(2026, 7, 10, 9, 5)
    expect(toDateTimeLocal(date)).toBe('2026-08-10T09:05')
  })
})
