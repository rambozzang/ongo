import { describe, expect, it, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import PostingHeatmap from './PostingHeatmap.vue'
import type { HeatmapData } from '@/types/analytics'

/**
 * 게시 시간 히트맵의 **문구 계약**.
 *
 * ## 무엇이 거짓이었나
 *
 * 안내 문구와 툴팁이 "평균 참여율" 이라고 말했지만, 서버가 주는 값은
 * `getHeatmapData` 의 **조회수 합계**다. 툴팁의 `X%` 는 참여율도 아니었다 —
 * 그 칸의 값을 **최대값으로 나눈 비율**이었다. 화면이 재지도 않은 지표를 주장했다.
 *
 * 값 자체의 축(게시 시각)과 플랫폼 필터는 서버가 지킨다
 * (`HeatmapQueryContractTest`·`HeatmapPublishedAtIT`). 여기서는 화면이 그 값을
 * 무엇이라고 부르는지만 고정한다.
 */
describe('PostingHeatmap 문구', () => {
  beforeEach(() => {
    // `useThemeStore` 가 색상 결정을 위해 읽는다. jsdom 에는 없다.
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: () => ({
        matches: false,
        addEventListener: () => {},
        removeEventListener: () => {},
      }),
    })
    setActivePinia(createPinia())
  })

  function render(data: HeatmapData[]) {
    return mount(PostingHeatmap, { props: { data } })
  }

  /** 수요일(3) 14시에 1,000 회. */
  const measured: HeatmapData[] = [{ dayOfWeek: 3, hour: 14, value: 1000 }]

  it('참여율이 아니라 조회수라고 안내한다', () => {
    const text = render(measured).text()

    expect(text).toContain('조회수')
    expect(text).not.toContain('참여율')
  })

  /** **이 케이스가 최대값 대비 비율을 참여율이라 부르던 자리다.** */
  it('툴팁에 참여율 퍼센트를 만들지 않는다', () => {
    const titles = render(measured)
      .findAll('[title]')
      .map((el) => el.attributes('title') ?? '')

    expect(titles.some((t) => t.includes('참여율'))).toBe(false)
    expect(titles.some((t) => t.includes('조회수 1,000회'))).toBe(true)
  })

  /** 재지 않은 칸과 **실측 0 회** 칸은 다른 문장이어야 한다. */
  it('측정 없는 칸과 실측 0 칸을 구분한다', () => {
    const titles = render([{ dayOfWeek: 3, hour: 14, value: 0 }])
      .findAll('[title]')
      .map((el) => el.attributes('title') ?? '')

    const measuredZero = titles.filter((t) => t.includes('조회수 0회'))
    const notMeasured = titles.filter((t) => t.includes('게시 기록 없음'))

    expect(measuredZero.length).toBe(1)
    // 24시간 × 7일 중 나머지는 전부 미측정이다.
    expect(notMeasured.length).toBe(24 * 7 - 1)
  })

  it('데이터가 없으면 모든 칸이 게시 기록 없음이다', () => {
    const titles = render([])
      .findAll('[title]')
      .map((el) => el.attributes('title') ?? '')

    expect(titles.every((t) => t.includes('게시 기록 없음'))).toBe(true)
    expect(titles.some((t) => t.includes('0%'))).toBe(false)
  })
})
