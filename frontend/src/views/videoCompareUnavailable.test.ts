import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { ChartBarIcon } from '@heroicons/vue/24/outline'
import MetricDifferenceCard from '@/components/analytics/MetricDifferenceCard.vue'
import PlatformPieChart from '@/components/dashboard/PlatformPieChart.vue'
import { createPinia, setActivePinia } from 'pinia'
import koMessages from '@/locales/ko/common.json'
import type { PlatformComparison } from '@/types/analytics'

/**
 * 비교 화면이 **미수집 지표를 0 으로 그리지 않는지** 고정한다.
 *
 * `MetricDifferenceCard` 는 이미 `valueA: number | null` 을 받아 `null` 을 미측정으로
 * 처리한다. 그런데 `VideoCompareView` 가 `comparisonA?.totalShares ?? 0` 으로 넘겨
 * **카드의 판정을 무력화**하고 있었다 — 서버가 `null` 을 줘도 카드는 0 을 받았다.
 */
describe('비교 화면 미수집 표시', () => {
  const i18n = createI18n({ legacy: false, locale: 'ko', fallbackLocale: 'ko', messages: { ko: koMessages } })

  function mountCard(valueA: number | null, valueB: number | null) {
    return mount(MetricDifferenceCard, {
      props: {
        title: '공유',
        icon: ChartBarIcon,
        unavailableLabel: koMessages.videoCompare.metricUnavailable,
        valueA,
        valueB,
      },
      global: { plugins: [i18n] },
    })
  }

  /** **이 케이스가 `?? 0` 으로 0 이 되던 자리다.** */
  it('값이 null이면 0이 아니라 측정 불가를 보여준다', () => {
    const wrapper = mountCard(null, null)

    expect(wrapper.text()).toContain(koMessages.videoCompare.metricUnavailable)
    expect(wrapper.text()).not.toContain('0')
  })

  it('한쪽만 null이어도 증감을 지어내지 않는다', () => {
    const wrapper = mountCard(100, null)

    expect(wrapper.text()).toContain(koMessages.videoCompare.metricUnavailable)
    // 비교 대상이 없으므로 % 증감을 주장하면 안 된다.
    expect(wrapper.text()).not.toContain('%')
  })

  /** **측정된 0 은 관측이다.** */
  it('측정된 0은 숫자로 보여준다', () => {
    const wrapper = mountCard(0, 0)

    expect(wrapper.text()).toContain('0')
    expect(wrapper.text()).not.toContain(koMessages.videoCompare.metricUnavailable)
  })

  it('측정된 값은 그대로 보여준다', () => {
    const wrapper = mountCard(120, 100)

    expect(wrapper.text()).toContain('120')
    expect(wrapper.text()).not.toContain(koMessages.videoCompare.metricUnavailable)
  })

  // ── 플랫폼 도넛 ──────────────────────────────────────────────────────────

  /**
   * 도넛 조각 하나는 "전체 조회 중 이만큼" 이라는 주장이다. 조회수를 수집하지 않는
   * 플랫폼(Tumblr 의 `views` 는 노트 총합)에 조각을 주면 그 주장을 지어내는 것이고,
   * 분모까지 오염된다.
   */
  it('조회수 미수집 플랫폼은 도넛 비중에서 빠지고 측정 불가로 표시된다', () => {
    setActivePinia(createPinia())
    // jsdom 에는 matchMedia 가 없다. 테마 스토어가 setup 에서 부른다.
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: (query: string) => ({
        matches: false, media: query, onchange: null,
        addEventListener: () => {}, removeEventListener: () => {}, dispatchEvent: () => false,
        addListener: () => {}, removeListener: () => {},
      }),
    })

    const data: PlatformComparison[] = [
      { platform: 'YOUTUBE', views: 900, likes: 10, comments: 5, shares: 2, unavailableMetrics: [] },
      { platform: 'TUMBLR', views: null, likes: 60, comments: 20, shares: 20, unavailableMetrics: ['views'] },
    ]

    const wrapper = mount(PlatformPieChart, { props: { data }, global: { plugins: [i18n] } })

    const text = wrapper.text()
    expect(text).toContain(koMessages.analyticsView.notMeasured)
    // 분모가 오염되면 YouTube 비중이 100% 가 아니게 된다.
    expect(text).toContain('100.0%')
    expect(text).not.toContain('NaN')
  })
})
