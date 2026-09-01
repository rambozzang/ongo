import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { UsersIcon } from '@heroicons/vue/24/outline'
import SummaryCard from './SummaryCard.vue'
import AudienceGrowthWidget from './AudienceGrowthWidget.vue'
import koMessages from '@/locales/ko/common.json'
import type { TrendDataPoint } from '@/types/analytics'

/**
 * 구독 지표가 **미수집과 실측 0 을 구분해서 그리는지** 고정한다.
 *
 * `subscriber_gained` 를 조회하는 어댑터는 YouTube 하나뿐이라, 서버는 수집 플랫폼이
 * 없으면 `null` 을 준다. 화면이 `?? 0` 을 하면 **세 상황이 다시 "0" 으로 합쳐진다** —
 * 실제로 0명이 늘어난 경우, TikTok 만 쓰는 경우, 업로드가 아예 없는 경우.
 */
describe('구독 지표 미측정 표시', () => {
  const i18n = createI18n({ legacy: false, locale: 'ko', fallbackLocale: 'ko', messages: { ko: koMessages } })

  function mountCard(value: number | null, change?: number | null) {
    return mount(SummaryCard, {
      props: { title: '신규 구독', value, change, changeType: 'number' as const, icon: UsersIcon, color: 'green' as const },
      global: { plugins: [i18n] },
    })
  }

  // ── SummaryCard ─────────────────────────────────────────────────────────

  /** **이 케이스가 "신규 구독 0명" 을 그리던 자리다.** */
  it('값이 null이면 0이 아니라 측정 불가를 보여준다', () => {
    const wrapper = mountCard(null)

    expect(wrapper.text()).toContain(koMessages.analyticsView.notMeasured)
    expect(wrapper.text()).not.toContain('0')
  })

  /** **측정된 0 은 관측이다.** 감추면 실제 관찰을 잃는다. */
  it('측정된 0은 숫자로 보여준다', () => {
    const wrapper = mountCard(0)

    expect(wrapper.text()).toContain('0')
    expect(wrapper.text()).not.toContain(koMessages.analyticsView.notMeasured)
  })

  /** 기본 포맷이 compact 라 1,234 는 "1.2K" 로 나온다. */
  it('측정된 값은 그대로 보여준다', () => {
    expect(mountCard(1234).text()).toContain('1.2K')
  })

  /**
   * `Math.abs(null) === 0` 이라 `!== undefined` 만 검사하면 "↑0" 이 뜬다.
   */
  it('증감이 null이면 배지를 그리지 않는다', () => {
    const wrapper = mountCard(42, null)

    expect(wrapper.text()).toContain('42')
    // 증감 배지가 없어야 한다 — 있으면 "0" 이 보인다.
    expect(wrapper.text()).not.toContain('↑')
    expect(wrapper.text()).not.toContain('↓')
  })

  it('NaN·Infinity가 흘러들어와도 숫자로 그리지 않는다', () => {
    expect(mountCard(Number.NaN).text()).toContain(koMessages.analyticsView.notMeasured)
    expect(mountCard(Number.POSITIVE_INFINITY).text()).not.toContain('∞')
  })

  // ── AudienceGrowthWidget ────────────────────────────────────────────────

  const trendData: TrendDataPoint[] = [
    { date: '2026-08-20', totalViews: 100, platformViews: {} },
    { date: '2026-08-21', totalViews: 200, platformViews: {} },
  ]

  function mountWidget(totalSubscribers: number | null, change?: number | null) {
    // 위젯이 setup 에서 테마 스토어를 쓴다. jsdom 에는 matchMedia 도 없다.
    const pinia = createPinia()
    setActivePinia(pinia)
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: (query: string) => ({
        matches: false, media: query, onchange: null,
        addEventListener: () => {}, removeEventListener: () => {}, dispatchEvent: () => false,
        addListener: () => {}, removeListener: () => {},
      }),
    })

    return mount(AudienceGrowthWidget, {
      props: { totalSubscribers, change, trendData },
      // 차트는 이 테스트의 관심사가 아니다. vue-chartjs 는 Chart.js 등록이 필요해
      // 스텁하지 않으면 렌더 자체가 실패한다.
      global: { plugins: [i18n, pinia], stubs: { Line: true } },
    })
  }

  it('위젯도 null을 0으로 그리지 않는다', () => {
    const wrapper = mountWidget(null)

    expect(wrapper.text()).toContain(koMessages.analyticsView.notMeasured)
  })

  it('위젯의 측정된 0은 숫자로 보여준다', () => {
    const wrapper = mountWidget(0)

    expect(wrapper.text()).toContain('0')
    expect(wrapper.text()).not.toContain(koMessages.analyticsView.notMeasured)
  })

  it('위젯의 증감이 null이면 전주 대비 배지를 그리지 않는다', () => {
    const wrapper = mountWidget(42, null)

    expect(wrapper.text()).not.toContain('전주 대비')
  })

  it('위젯의 측정된 증감은 보여준다', () => {
    const wrapper = mountWidget(42, 5)

    expect(wrapper.text()).toContain('전주 대비')
    expect(wrapper.text()).toContain('5')
  })
})
