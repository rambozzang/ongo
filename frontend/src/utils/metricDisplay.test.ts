import { describe, expect, it } from 'vitest'
import { resolveMetricDisplay, shouldWarnStaleMetrics } from './metricDisplay'
import koMessages from '@/locales/ko/common.json'
import enMessages from '@/locales/en/common.json'

/**
 * KPI 칸이 **0 과 "모름" 을 구분**하는지.
 *
 * 예전에는 `currentAnalytics?.views ?? 0` 이라 로딩·오류·미집계가 전부 "0" 으로 나왔다.
 * 크리에이터는 그걸 "조회수가 0" 으로 읽는다 — 특히 오류 배너와 "0" 이 같이 뜨면
 * 어느 쪽이 사실인지 알 수 없다.
 */
describe('resolveMetricDisplay', () => {
  const idle = { loading: false, hasError: false, hasData: true }

  describe('실제 값은 그대로 보여준다', () => {
    /**
     * **(b) 집계 행이 있고 실제 0 이면 0 이다.** 이것까지 "미측정" 으로 가리면 반대
     * 거짓말이 된다.
     */
    it('집계가 있는 실제 0 은 값으로 유지한다', () => {
      expect(resolveMetricDisplay(0, idle)).toEqual({ kind: 'value', value: 0 })
    })

    it('양수는 값으로 유지한다', () => {
      expect(resolveMetricDisplay(1234, idle)).toEqual({ kind: 'value', value: 1234 })
    })

    /**
     * **(c) 재시도 실패 정책**: 마지막으로 성공한 값을 보존한다. 이미 확인한 숫자를
     * 오류 하나로 지우면 사용자는 성과가 사라진 줄 안다. 신선도는 배너가 따로 알린다.
     */
    it('오류 중이어도 남아 있는 값은 계속 보여준다', () => {
      expect(resolveMetricDisplay(4200, { loading: false, hasError: true, hasData: true }))
        .toEqual({ kind: 'value', value: 4200 })
    })

    it('재조회 중이어도 이전 값을 보여준다', () => {
      expect(resolveMetricDisplay(4200, { loading: true, hasError: false, hasData: true }))
        .toEqual({ kind: 'value', value: 4200 })
    })

    /** hasData 를 안 주는 옛 응답은 값이 있으면 값으로 본다 — 하위 호환. */
    it('hasData 를 모르면 값을 그대로 쓴다', () => {
      expect(resolveMetricDisplay(7, { loading: false, hasError: false }))
        .toEqual({ kind: 'value', value: 7 })
    })
  })

  /**
   * **(a) 이 블록이 이번 수정의 핵심이다.**
   *
   * 서버(`AnalyticsUseCase`)는 `uploads.map { }` 으로 업로드마다 행을 만들고, 집계가
   * 없으면 `dailyData = []` 에 합계 0 을 넣는다. 그래서 `currentAnalytics` 는 null 이
   * 아니고 값도 숫자 0 이다 — 값 존재만 보면 "0회" 로 보여 주게 된다.
   */
  describe('집계 행이 없는 플랫폼은 0 이라고 하지 않는다', () => {
    it('행 없음 + 합계 0 은 미측정이다', () => {
      expect(resolveMetricDisplay(0, { loading: false, hasError: false, hasData: false }))
        .toEqual({ kind: 'unavailable', reason: 'noData' })
    })

    /** 재조회가 실패해도 "수집된 것이 없다"는 사실이 더 정확한 안내다. */
    it('행 없음은 오류보다 먼저 판정한다', () => {
      expect(resolveMetricDisplay(0, { loading: false, hasError: true, hasData: false }))
        .toEqual({ kind: 'unavailable', reason: 'noData' })
    })

    /**
     * 재조회 중에도 **이미 아는 사실**을 유지한다. 값이 있을 때 이전 값을 계속 보여주는
     * 것과 같은 원칙이다 — 조회 중이라고 아는 것을 숨기고 "로딩"으로 되돌리면 화면이
     * 깜빡이기만 하고 정보는 줄어든다.
     */
    it('행 없음이 확인된 상태는 재조회 중에도 유지한다', () => {
      expect(resolveMetricDisplay(0, { loading: true, hasError: false, hasData: false }))
        .toEqual({ kind: 'unavailable', reason: 'noData' })
    })

    /** 아직 아무것도 모르는 첫 조회는 로딩이다. */
    it('아무 정보도 없는 첫 조회는 로딩이다', () => {
      expect(resolveMetricDisplay(undefined, { loading: true, hasError: false }))
        .toEqual({ kind: 'loading' })
    })
  })

  describe('값이 없으면 0 이라고 하지 않는다', () => {
    /** **(d) 로딩/오류** */
    it('로딩 중에는 로딩으로 본다', () => {
      expect(resolveMetricDisplay(undefined, { loading: true, hasError: false }))
        .toEqual({ kind: 'loading' })
      expect(resolveMetricDisplay(null, { loading: true, hasError: true }))
        .toEqual({ kind: 'loading' })
    })

    it('오류로 못 불러온 것과 집계가 없는 것을 구분한다', () => {
      expect(resolveMetricDisplay(undefined, { loading: false, hasError: true }))
        .toEqual({ kind: 'unavailable', reason: 'error' })
      expect(resolveMetricDisplay(undefined, { loading: false, hasError: false }))
        .toEqual({ kind: 'unavailable', reason: 'noData' })
    })

    /** 어떤 조합에서도 0 이 새어 나오면 안 된다. */
    it('값이 없을 때 value 를 만들지 않는다', () => {
      for (const loading of [true, false]) {
        for (const hasError of [true, false]) {
          for (const hasData of [true, false, undefined]) {
            for (const value of [undefined, null, Number.NaN, Number.POSITIVE_INFINITY]) {
              const result = resolveMetricDisplay(value, { loading, hasError, hasData })
              expect(result.kind, `${value}/${loading}/${hasError}/${hasData}`).not.toBe('value')
            }
          }
        }
      }
    })

    /** hasData=false 면 어떤 숫자가 와도 값으로 보여주지 않는다. */
    it('집계가 없다고 확인되면 숫자가 있어도 값으로 쓰지 않는다', () => {
      for (const value of [0, 5, 1234]) {
        const result = resolveMetricDisplay(value, {
          loading: false,
          hasError: false,
          hasData: false,
        })
        expect(result.kind, `${value}`).toBe('unavailable')
      }
    })
  })

  /** **(c) stale 정책**: 값을 보존하되 최신이 아님을 함께 알린다. */
  describe('shouldWarnStaleMetrics', () => {
    it('오류인데 이전 값이 남아 있으면 알린다', () => {
      expect(shouldWarnStaleMetrics({ hasError: true, hasLoadedValue: true })).toBe(true)
    })

    it('값이 없는 오류에는 오래된 값 안내를 붙이지 않는다', () => {
      expect(shouldWarnStaleMetrics({ hasError: true, hasLoadedValue: false })).toBe(false)
    })

    it('오류가 없으면 알리지 않는다', () => {
      expect(shouldWarnStaleMetrics({ hasError: false, hasLoadedValue: true })).toBe(false)
      expect(shouldWarnStaleMetrics({ hasError: false, hasLoadedValue: false })).toBe(false)
    })
  })

  /** 문구는 ko/en 양쪽에 있어야 영어 UI 에 한글이 새지 않는다. */
  describe('상태 문구 i18n', () => {
    const keys = [
      'metricLoading',
      'metricUnavailableError',
      'metricNoData',
      'analyticsStaleNotice',
    ] as const

    it('ko/en 모두 정의돼 있다', () => {
      for (const key of keys) {
        expect(koMessages.videoDetail[key], `ko.${key}`).toBeTruthy()
        expect(enMessages.videoDetail[key], `en.${key}`).toBeTruthy()
      }
    })

    it('영어 문구에 한글이 섞이지 않는다', () => {
      for (const key of keys) {
        expect(enMessages.videoDetail[key]).not.toMatch(/[가-힣]/)
      }
    })

    /** "미측정" 과 "0" 이 같은 뜻으로 읽히면 고친 의미가 없다. */
    it('미측정 문구가 숫자 0 이 아니다', () => {
      expect(koMessages.videoDetail.metricNoData).not.toBe('0')
      expect(enMessages.videoDetail.metricNoData).not.toBe('0')
    })
  })
})
