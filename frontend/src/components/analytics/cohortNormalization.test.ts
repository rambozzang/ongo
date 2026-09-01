import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia, setActivePinia } from 'pinia'
import CohortAnalysisChart from './CohortAnalysisChart.vue'
import { analyticsApi } from '@/api/analytics'
import koMessages from '@/locales/ko/common.json'
import type { CohortGroupData } from '@/types/analytics'

vi.mock('@/api/analytics', () => ({ analyticsApi: { cohortAnalysis: vi.fn() } }))

/**
 * 코호트 표가 **정규화 기준 없는 구간을 0% 로 그리지 않는지** 고정한다.
 *
 * 서버는 코호트 전체 조회수가 0 이면 `normalizedPercent = null` 을 준다 —
 * `maxViews.coerceAtLeast(1)` 로 분모를 1 로 세우던 것을 걷어냈기 때문이다. 화면이
 * `?? 0` 을 하면 **평평한 0% 유지 곡선**이 그대로 되살아난다.
 */
describe('코호트 유지율 미측정 표시', () => {
  const i18n = createI18n({ legacy: false, locale: 'ko', fallbackLocale: 'ko', messages: { ko: koMessages } })

  function cohort(overrides: Partial<CohortGroupData> = {}): CohortGroupData {
    return {
      name: '브이로그',
      videoCount: 3,
      avgViews: 500,
      cumulativeViewCurve: [
        { day: 7, value: 300, normalizedPercent: 60 },
        { day: 30, value: 500, normalizedPercent: 100 },
      ],
      unavailableReason: null,
      ...overrides,
    }
  }

  async function render(cohorts: CohortGroupData[]) {
    setActivePinia(createPinia())
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: (query: string) => ({
        matches: false, media: query, onchange: null,
        addEventListener: () => {}, removeEventListener: () => {}, dispatchEvent: () => false,
        addListener: () => {}, removeListener: () => {},
      }),
    })
    vi.mocked(analyticsApi.cohortAnalysis).mockResolvedValue({
      groupBy: 'CATEGORY',
      cohorts,
      dateRange: { from: '2026-07-01', to: '2026-08-28' },
    } as never)

    const wrapper = mount(CohortAnalysisChart, { global: { plugins: [i18n] } })
    await flushPromises()
    return wrapper
  }

  beforeEach(() => vi.clearAllMocks())

  /** **이 케이스가 "0.0%" 를 그리던 자리다.** */
  it('정규화 기준이 없으면 0%가 아니라 측정 불가를 보여준다', async () => {
    const wrapper = await render([cohort({
      avgViews: null,
      cumulativeViewCurve: [
        { day: 7, value: 0, normalizedPercent: null },
        { day: 30, value: 0, normalizedPercent: null },
      ],
      unavailableReason: '조회수가 집계되지 않아 유지 곡선을 계산할 수 없습니다',
    })])

    expect(wrapper.find('[data-testid="cohort-day7"]').text()).toBe(koMessages.analyticsView.notMeasured)
    expect(wrapper.find('[data-testid="cohort-day30"]').text()).toBe(koMessages.analyticsView.notMeasured)
    expect(wrapper.find('[data-testid="cohort-day7"]').text()).not.toContain('0.0%')
  })

  it('평균 조회수가 null이면 측정 불가를 보여준다', async () => {
    const wrapper = await render([cohort({ avgViews: null })])

    expect(wrapper.text()).toContain(koMessages.analyticsView.notMeasured)
  })

  // ── 측정된 값은 그대로 ───────────────────────────────────────────────────

  it('측정된 유지율은 퍼센트로 보여준다', async () => {
    const wrapper = await render([cohort()])

    expect(wrapper.find('[data-testid="cohort-day7"]').text()).toBe('60.0%')
    expect(wrapper.find('[data-testid="cohort-day30"]').text()).toBe('100.0%')
  })

  /**
   * **기준이 있는 상태의 0% 는 관측이다.** "그 구간까지 조회가 없었다" 는 사실이므로
   * 측정 불가로 감추면 실제 관찰을 잃는다.
   */
  it('기준이 있는 상태의 0%는 숫자로 보여준다', async () => {
    const wrapper = await render([cohort({
      cumulativeViewCurve: [
        { day: 7, value: 0, normalizedPercent: 0 },
        { day: 30, value: 500, normalizedPercent: 100 },
      ],
    })])

    expect(wrapper.find('[data-testid="cohort-day7"]').text()).toBe('0.0%')
    expect(wrapper.find('[data-testid="cohort-day7"]').text())
      .not.toBe(koMessages.analyticsView.notMeasured)
  })

  it('구간이 아예 없으면 측정 불가를 보여준다', async () => {
    const wrapper = await render([cohort({ cumulativeViewCurve: [] })])

    expect(wrapper.find('[data-testid="cohort-day7"]').text()).toBe(koMessages.analyticsView.notMeasured)
  })
})
