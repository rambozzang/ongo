import type { SegmentOptionsRequest } from '@/api/ugcShortsPipeline'

/**
 * 실행 생성 폼의 "클립 고르기 조건" 입력을 서버 요청으로 바꾼다.
 *
 * 범위는 서버(`ShortsSegmentPolicy.Options.of`)와 같다. 서버가 최종 판정하지만, 여기서 먼저 막아야
 * 사용자가 원본 길이 측정·크레딧 확인을 기다린 뒤에야 "입력이 틀렸다"를 보지 않는다.
 *
 * 빈 칸은 "지정 안 함"이다 — 0 이나 기본값으로 채워 보내지 않는다. 서버 기본값이 바뀌면 그 값을
 * 따라가야 하기 때문이다.
 */
export const SEGMENT_LIMITS = {
  minClipCount: 1,
  maxClipCount: 10,
  minDurationSec: 5,
  maxDurationSec: 180,
} as const

export interface SegmentOptionsForm {
  clipCount: number | string | null
  minDurationSec: number | string | null
  maxDurationSec: number | string | null
}

export type SegmentOptionsError = 'invalidCount' | 'invalidDuration' | 'invalidRange'

export type SegmentOptionsResult =
  | { ok: true; value: SegmentOptionsRequest | null }
  | { ok: false; error: SegmentOptionsError }

/** 빈 칸이면 null, 정수가 아니면 NaN. `<input type="number">` 는 빈 칸을 '' 로 준다. */
function parse(raw: number | string | null): number | null {
  if (raw === null || raw === '') return null
  const n = typeof raw === 'number' ? raw : Number(raw.trim())
  return Number.isInteger(n) ? n : Number.NaN
}

export function buildSegmentOptions(form: SegmentOptionsForm): SegmentOptionsResult {
  const clipCount = parse(form.clipCount)
  const minSec = parse(form.minDurationSec)
  const maxSec = parse(form.maxDurationSec)

  if (clipCount !== null && !(clipCount >= SEGMENT_LIMITS.minClipCount && clipCount <= SEGMENT_LIMITS.maxClipCount)) {
    return { ok: false, error: 'invalidCount' }
  }
  for (const sec of [minSec, maxSec]) {
    if (sec !== null && !(sec >= SEGMENT_LIMITS.minDurationSec && sec <= SEGMENT_LIMITS.maxDurationSec)) {
      return { ok: false, error: 'invalidDuration' }
    }
  }
  if (minSec !== null && maxSec !== null && minSec > maxSec) {
    return { ok: false, error: 'invalidRange' }
  }

  if (clipCount === null && minSec === null && maxSec === null) return { ok: true, value: null }
  const value: SegmentOptionsRequest = {}
  if (clipCount !== null) value.clipCount = clipCount
  if (minSec !== null) value.minDurationSec = minSec
  if (maxSec !== null) value.maxDurationSec = maxSec
  return { ok: true, value }
}

/** 점수 색 구분. 점수가 없으면 'none' — 0점과 구분한다. */
export type ScoreTier = 'high' | 'mid' | 'low' | 'none'

export function scoreTier(score: number | null | undefined): ScoreTier {
  if (score === null || score === undefined) return 'none'
  if (score >= 75) return 'high'
  if (score >= 50) return 'mid'
  return 'low'
}
