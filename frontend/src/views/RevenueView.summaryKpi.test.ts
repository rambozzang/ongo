import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import RevenueView from './RevenueView.vue'
import { useRevenueStore } from '@/stores/revenue'
import koMessages from '@/locales/ko/common.json'
import enMessages from '@/locales/en/common.json'
import type { RevenueSummaryLocal } from '@/stores/revenue'

/**
 * 수익 요약 KPI 카드가 **측정 불가를 숫자처럼 그리지 않는지** 고정한다.
 *
 * 스토어가 `null` 을 줘도 화면이 `?? 0` 이나 `?? 'YOUTUBE'` 한 줄만 넣으면 스토어
 * 수정이 통째로 무의미해진다. 게다가 `null.toLocaleString()` 은 그대로 두면
 * **TypeError 로 화면 전체가 사라진다.**
 */
describe('수익 요약 KPI 표시', () => {
  let store: ReturnType<typeof useRevenueStore>

  async function renderWithSummary(summary: Partial<RevenueSummaryLocal>) {
    const pinia = createPinia()
    setActivePinia(pinia)
    store = useRevenueStore()

    store.fetchRevenue = vi.fn().mockResolvedValue(undefined) as never
    store.fetchInsights = vi.fn().mockResolvedValue(undefined) as never
    store.fetchCpmRpm = vi.fn().mockResolvedValue(undefined) as never
    store.fetchBrandDealRevenue = vi.fn().mockResolvedValue(undefined) as never

    const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', component: { template: '<div />' } }],
    })
    await router.push('/')
    await router.isReady()

    const wrapper = mount(RevenueView, { global: { plugins: [pinia, i18n, router] } })
    await flushPromises()

    store.summary = {
      totalRevenue: 1000,
      monthlyGrowth: null,
      averageRPM: null,
      averageRpmUnavailableReason: 'noViewSample',
      topPlatform: null,
      topPlatformRevenue: null,
      ...summary,
    }
    await flushPromises()
    return wrapper
  }

  const rpmCard = (w: Awaited<ReturnType<typeof renderWithSummary>>) =>
    w.find('[data-testid="revenue-rpm-kpi"]')
  const platformCard = (w: Awaited<ReturnType<typeof renderWithSummary>>) =>
    w.find('[data-testid="revenue-top-platform-kpi"]')

  beforeEach(() => vi.clearAllMocks())

  // ── 평균 RPM ─────────────────────────────────────────────────────────────

  /** **이 케이스가 임의 분모로 만든 "₩1,234" 를 그리던 자리다.** */
  it('RPM이 null이면 금액이 아니라 측정 불가를 보여준다', async () => {
    const wrapper = await renderWithSummary({ averageRPM: null, averageRpmUnavailableReason: 'noViewSample' })

    const card = rpmCard(wrapper)
    expect(card.text()).toContain(koMessages.revenue.avgRpmUnavailableShort)
    expect(card.text()).toContain(koMessages.revenue.avgRpmNoViewSample)
    expect(card.text()).not.toContain('₩')
  })

  it('불러오지 못한 경우와 표본이 없는 경우를 구분해 말한다', async () => {
    const failed = await renderWithSummary({ averageRPM: null, averageRpmUnavailableReason: 'loadFailed' })
    expect(rpmCard(failed).text()).toContain(koMessages.revenue.avgRpmLoadFailed)

    const noSample = await renderWithSummary({ averageRPM: null, averageRpmUnavailableReason: 'noViewSample' })
    expect(rpmCard(noSample).text()).toContain(koMessages.revenue.avgRpmNoViewSample)
  })

  it('측정된 RPM은 금액으로 보여준다', async () => {
    const wrapper = await renderWithSummary({ averageRPM: 1234, averageRpmUnavailableReason: null })

    expect(rpmCard(wrapper).text()).toContain('₩1,234')
  })

  /** 조회는 있는데 수익이 0 이면 그 0 은 실측이다. 측정 불가로 감추면 관찰을 잃는다. */
  it('측정된 0원 RPM은 숫자로 보여준다', async () => {
    const wrapper = await renderWithSummary({ averageRPM: 0, averageRpmUnavailableReason: null })

    const card = rpmCard(wrapper)
    expect(card.text()).toContain('₩0')
    expect(card.text()).not.toContain(koMessages.revenue.avgRpmUnavailableShort)
  })

  /** `toLocaleString` 은 `NaN`·`Infinity` 를 문자열로 성실히 그려낸다. */
  it('NaN·Infinity가 흘러들어와도 숫자로 그리지 않는다', async () => {
    const nan = await renderWithSummary({ averageRPM: Number.NaN, averageRpmUnavailableReason: null })
    expect(rpmCard(nan).text()).not.toContain('NaN')
    expect(rpmCard(nan).text()).toContain(koMessages.revenue.avgRpmUnavailableShort)

    const inf = await renderWithSummary({ averageRPM: Number.POSITIVE_INFINITY, averageRpmUnavailableReason: null })
    expect(inf.text()).not.toContain('∞')
    expect(inf.text()).not.toContain('Infinity')
  })

  // ── 최고 수익 플랫폼 ─────────────────────────────────────────────────────

  /** **이 케이스가 "YouTube · ₩0" 을 그리던 자리다.** */
  it('플랫폼이 null이면 YouTube를 지어내지 않는다', async () => {
    const wrapper = await renderWithSummary({ topPlatform: null, topPlatformRevenue: null })

    const card = platformCard(wrapper)
    expect(card.text()).not.toContain('YouTube')
    expect(card.text()).toContain(koMessages.revenue.topPlatformUnavailable)
  })

  it('집계된 플랫폼은 라벨과 금액으로 보여준다', async () => {
    const wrapper = await renderWithSummary({ topPlatform: 'TIKTOK', topPlatformRevenue: 5000 })

    const card = platformCard(wrapper)
    expect(card.text()).toContain('TikTok')
    expect(card.text()).toContain('5,000')
  })

  /** 라벨이 없는 것과 값이 틀린 것은 다르다. 원문을 그대로 보여준다. */
  it('라벨이 없는 플랫폼은 원문 그대로 보여준다', async () => {
    const wrapper = await renderWithSummary({ topPlatform: 'SOME_NEW_PLATFORM', topPlatformRevenue: 100 })

    const card = platformCard(wrapper)
    expect(card.text()).toContain('SOME_NEW_PLATFORM')
    expect(card.text()).not.toContain('YouTube')
  })

  it('ko/en 두 로케일에 새 문구가 모두 있다', () => {
    const keys = [
      'avgRpmUnavailableShort', 'avgRpmNoViewSample', 'avgRpmLoadFailed',
      'avgRpmViewBased', 'topPlatformUnavailableShort', 'topPlatformUnavailable',
    ] as const
    const en = enMessages as { revenue: Record<string, unknown> }
    for (const key of keys) {
      expect(koMessages.revenue[key], `ko.${key}`).toBeTruthy()
      expect(en.revenue[key], `en.${key}`).toBeTruthy()
    }
  })
})
