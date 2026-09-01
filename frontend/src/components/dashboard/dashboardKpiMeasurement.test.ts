import { describe, expect, it, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { EyeIcon } from '@heroicons/vue/24/outline'
import SummaryCard from './SummaryCard.vue'
import MobileDashboard from './MobileDashboard.vue'
import koMessages from '@/locales/ko/common.json'
import type { DashboardKpi } from '@/types/analytics'

/**
 * 첫 화면 KPI 가 **미수집과 실측 0 을 구분해서 그리는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 서버가 `SUM(views)`·`SUM(likes)` 를 플랫폼 필터 없이 냈다. `TumblrClient.kt:141` 은
 * `views = total_notes`(노트 총합), `PinterestClient.kt:158` 은 `likes = SAVE`(저장 수)를
 * 같은 컬럼에 넣으므로 **다른 뜻의 큰 숫자**가 KPI 에 섞였다.
 *
 * 서버가 그 행을 빼면서 "수집 플랫폼 없음" 은 `null` 이 됐는데, 화면은 `?? 0` 을 하고
 * 있었다. 그러면 **재지 않은 것과 실제로 0 이었던 것이 다시 같아진다.**
 *
 * 값 자체의 집계 계약은 서버가 지킨다
 * (`DashboardKpiMetricQueryContractTest`·`DashboardKpiMeasurementIT`).
 * 여기서는 화면이 `null` 을 어떻게 그리는지만 고정한다.
 */
describe('대시보드 KPI 미측정 표시', () => {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'ko',
    messages: { ko: koMessages },
  })

  const notMeasured = koMessages.analyticsView.notMeasured

  // ── SummaryCard (데스크톱 카드) ─────────────────────────────────────────

  function mountCard(value: number | null, change?: number | null) {
    return mount(SummaryCard, {
      props: {
        title: '총 조회수',
        value,
        change,
        changeType: 'percent' as const,
        icon: EyeIcon,
        color: 'blue' as const,
      },
      global: { plugins: [i18n] },
    })
  }

  /** **이 케이스가 "총 조회수 0" 을 성과로 그리던 자리다.** */
  it('조회수가 null이면 0이 아니라 측정 불가를 보여준다', () => {
    const wrapper = mountCard(null)

    expect(wrapper.text()).toContain(notMeasured)
    expect(wrapper.text()).not.toContain('0')
  })

  /** **측정된 0 은 관측이다.** 감추면 실제 관찰을 잃는다. */
  it('측정된 0 조회수는 숫자로 보여준다', () => {
    const wrapper = mountCard(0)

    expect(wrapper.text()).toContain('0')
    expect(wrapper.text()).not.toContain(notMeasured)
  })

  /** 미측정 상태에서 증감 배지가 뜨면 "변화 없음" 이라는 사실을 주장하게 된다. */
  it('미측정이면 증감 배지도 그리지 않는다', () => {
    const wrapper = mountCard(null, null)

    expect(wrapper.text()).not.toContain('%')
    expect(wrapper.text()).not.toContain('↑')
    expect(wrapper.text()).not.toContain('↓')
  })

  /** 성장색(초록)이 뜨면 미측정이 상승으로 보인다. */
  it('미측정에 성장색을 입히지 않는다', () => {
    const html = mountCard(null, null).html()

    expect(html).not.toContain('text-green')
    expect(html).not.toContain('text-red')
  })

  // ── MobileDashboard (모바일 카드) ───────────────────────────────────────

  beforeEach(() => {
    // 하위 `TrendChart` 가 `useThemeStore` 를 통해 읽는다. jsdom 에는 없다.
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: () => ({
        matches: false,
        addEventListener: () => {},
        removeEventListener: () => {},
      }),
    })
  })

  function mountMobile(kpi: Partial<DashboardKpi>) {
    setActivePinia(createPinia())
    return mount(MobileDashboard, {
      props: {
        loading: false,
        kpi: kpi as DashboardKpi,
        greeting: '안녕하세요',
        userName: '크리에이터',
        currentDate: '2026-08-28',
        creditPercentage: 50,
        recentVideos: [],
        trendData: [],
        period: '7d' as const,
        todayAndTomorrowSchedules: [],
        groupedSchedules: [],
      },
      global: {
        plugins: [i18n],
        // `TrendChart` 는 Chart.js 라 jsdom 캔버스에서 터진다. KPI 문구와 무관하다.
        stubs: { RouterLink: true, 'router-link': true, TrendChart: true },
        mocks: { $router: { push: () => {} } },
      },
    })
  }

  const measured: Partial<DashboardKpi> = {
    totalViews: 1234,
    viewsChangePercent: 12,
    totalSubscribers: 5,
    subscribersChange: 1,
    totalLikes: 4321,
    likesChangePercent: 3,
    creditBalance: 10,
    creditTotal: 100,
  }

  it('모바일에서 조회수 null을 측정 불가로 그린다', () => {
    const text = mountMobile({ ...measured, totalViews: null, viewsChangePercent: null }).text()

    expect(text).toContain(notMeasured)
    // 좋아요는 여전히 측정값이다 — 함께 죽으면 안 된다.
    expect(text).toContain('4.3K')
  })

  it('모바일에서 좋아요 null을 측정 불가로 그린다', () => {
    const text = mountMobile({ ...measured, totalLikes: null, likesChangePercent: null }).text()

    expect(text).toContain(notMeasured)
    expect(text).toContain('1.2K')
  })

  /** 측정된 0 은 `formatCompact` 를 그대로 통과해야 한다. */
  it('모바일에서 측정된 0은 숫자로 그린다', () => {
    const text = mountMobile({ ...measured, totalViews: 0, viewsChangePercent: null }).text()

    expect(text).toContain('0')
  })

  /** 네 지표가 모두 미측정인 신규 사용자. 어떤 칸에도 0 이 뜨면 안 된다. */
  it('모든 지표가 미측정이면 조회수·좋아요 자리에 0을 그리지 않는다', () => {
    const wrapper = mountMobile({
      totalViews: null,
      viewsChangePercent: null,
      totalSubscribers: null,
      subscribersChange: null,
      totalLikes: null,
      likesChangePercent: null,
      creditBalance: 0,
      creditTotal: 0,
    })
    const text = wrapper.text()

    // 세 지표 카드가 모두 측정 불가 문구를 쓴다.
    expect(text.split(notMeasured).length - 1).toBe(3)
    expect(text).not.toContain('↑')
    expect(text).not.toContain('↓')
  })
})
