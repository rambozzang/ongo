import { describe, expect, it } from 'vitest'
import { buildSegmentOptions, scoreTier } from './shortsSegmentOptions'

describe('buildSegmentOptions', () => {
  it('모두 비우면 조건을 보내지 않는다 — 서버 기본값을 따른다', () => {
    expect(buildSegmentOptions({ clipCount: '', minDurationSec: null, maxDurationSec: '' })).toEqual({ ok: true, value: null })
  })

  it('입력한 값만 보낸다', () => {
    expect(buildSegmentOptions({ clipCount: '3', minDurationSec: '', maxDurationSec: 45 })).toEqual({
      ok: true,
      value: { clipCount: 3, maxDurationSec: 45 },
    })
  })

  it('개수는 1~10개, 정수만', () => {
    expect(buildSegmentOptions({ clipCount: 0, minDurationSec: null, maxDurationSec: null })).toEqual({ ok: false, error: 'invalidCount' })
    expect(buildSegmentOptions({ clipCount: 11, minDurationSec: null, maxDurationSec: null })).toEqual({ ok: false, error: 'invalidCount' })
    expect(buildSegmentOptions({ clipCount: '2.5', minDurationSec: null, maxDurationSec: null })).toEqual({ ok: false, error: 'invalidCount' })
    expect(buildSegmentOptions({ clipCount: 10, minDurationSec: null, maxDurationSec: null }).ok).toBe(true)
  })

  it('길이는 5~180초', () => {
    expect(buildSegmentOptions({ clipCount: null, minDurationSec: 4, maxDurationSec: null })).toEqual({ ok: false, error: 'invalidDuration' })
    expect(buildSegmentOptions({ clipCount: null, minDurationSec: null, maxDurationSec: 181 })).toEqual({ ok: false, error: 'invalidDuration' })
    expect(buildSegmentOptions({ clipCount: null, minDurationSec: 'abc', maxDurationSec: null })).toEqual({ ok: false, error: 'invalidDuration' })
  })

  it('최소가 최대보다 길면 막는다', () => {
    expect(buildSegmentOptions({ clipCount: null, minDurationSec: 60, maxDurationSec: 30 })).toEqual({ ok: false, error: 'invalidRange' })
    expect(buildSegmentOptions({ clipCount: null, minDurationSec: 30, maxDurationSec: 30 }).ok).toBe(true)
  })
})

describe('scoreTier', () => {
  it('점수가 없으면 0점과 구분한다', () => {
    expect(scoreTier(null)).toBe('none')
    expect(scoreTier(undefined)).toBe('none')
    expect(scoreTier(0)).toBe('low')
  })

  it('경계값', () => {
    expect(scoreTier(75)).toBe('high')
    expect(scoreTier(74)).toBe('mid')
    expect(scoreTier(50)).toBe('mid')
    expect(scoreTier(49)).toBe('low')
  })
})
