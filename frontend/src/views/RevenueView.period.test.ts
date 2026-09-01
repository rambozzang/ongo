import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import RevenueView from './RevenueView.vue'
import { useRevenueStore } from '@/stores/revenue'
import koMessages from '@/locales/ko/common.json'

/**
 * 기간 선택이 **실제 조회와 라벨에 그대로 이어지는지** 고정한다.
 *
 * 예전에는 화면이 `'1','3','6','12'`(개월)를 들고 있는데 스토어는 항상 `'30d'` 만 불렀고,
 * 기간 버튼에 감시자도 없었다. "1년"을 골라도 30일치의 마지막 12행(=12일)이 보였다.
 */
describe('RevenueView 기간 전달', () => {
  let store: ReturnType<typeof useRevenueStore>

  async function renderView() {
    const pinia = createPinia()
    setActivePinia(pinia)
    store = useRevenueStore()

    // 스토어 동작 자체는 revenue.period.test.ts 가 검증한다. 여기서는 **뷰가 무엇을
    // 어떤 기간으로 부르는지**만 본다.
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
    return wrapper
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  /**
   * **i18n 키 누락은 조용히 지나간다.** vue-i18n 은 콘솔 경고만 내고 화면에는 키 경로를
   * 그대로 찍는다(`revenue.platformBreakdown`). 실제로 그 키는 `{ totalRevenue }` 객체라
   * 문자열로 쓸 수 없었는데, 테스트가 전부 초록이라 아무도 몰랐다.
   *
   * 경고를 오류로 승격해 이 화면이 렌더링하는 모든 키를 강제한다. 임의 fallback 을 넣어
   * 가리면 사용자에게 키 경로가 보이는 것을 막지 못한다.
   */
  it('렌더링 중 해석되지 않는 i18n 키가 없다', async () => {
    const missing: string[] = []
    const warn = vi.spyOn(console, 'warn').mockImplementation((...args: unknown[]) => {
      const message = args.map(String).join(' ')
      if (message.includes('Not found') && message.includes('key')) missing.push(message)
    })

    try {
      await renderView()
      // 기간을 바꿔 조건부 문구(AI 30일 안내 등)까지 렌더링시킨다.
      store.loadedPeriod = '365d'
      await flushPromises()
    } finally {
      warn.mockRestore()
    }

    expect(missing, `해석되지 않은 i18n 키:\n${missing.join('\n')}`).toEqual([])
  })

  /**
   * 위 감지 장치가 **실제로 누락을 잡는지** 확인한다. vue-i18n 이 경고를 다른 경로로
   * 보내면 위 테스트는 아무것도 검사하지 않으면서 항상 통과한다 — 그런 테스트는
   * 있으나 마나다. 소스를 건드리지 않고 여기서 일부러 없는 키를 그려 본다.
   */
  it('i18n 누락 감지 장치가 실제로 동작한다', async () => {
    const missing: string[] = []
    const warn = vi.spyOn(console, 'warn').mockImplementation((...args: unknown[]) => {
      const message = args.map(String).join(' ')
      if (message.includes('Not found') && message.includes('key')) missing.push(message)
    })

    try {
      const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
      mount({ template: `<p>{{ $t('revenue.__definitely_missing__') }}</p>` }, {
        global: { plugins: [i18n] },
      })
      await flushPromises()
    } finally {
      warn.mockRestore()
    }

    expect(missing.length).toBeGreaterThan(0)
  })

  // ── 성장률 비교 불가 ─────────────────────────────────────────────────────
  //
  // 서버는 이전 기간 수익이 0 이면 `growthPercent = null` 을 준다. 그것을 퍼센트 숫자로
  // 그리면 첫 수익이 발생한 크리에이터가 "+100%" 를 실제 성장률로 읽는다.

  it('성장률이 null이면 퍼센트 숫자를 만들어내지 않는다', async () => {
    const wrapper = await renderView()

    store.summary = { ...store.summary, monthlyGrowth: null }
    await flushPromises()

    const kpi = wrapper.find('[data-testid="revenue-growth-kpi"]')
    expect(kpi.exists()).toBe(true)
    expect(kpi.text()).toContain(koMessages.revenue.growthUnavailableShort)
    // 0% / 100% / null / NaN 어느 것도 노출되면 안 된다.
    expect(kpi.text()).not.toContain('%')
    expect(kpi.text()).not.toContain('null')
    expect(kpi.text()).not.toContain('NaN')
  })

  it('성장률이 null이면 증감 색상으로 좋고 나쁨을 주장하지 않는다', async () => {
    const wrapper = await renderView()

    store.summary = { ...store.summary, monthlyGrowth: null }
    await flushPromises()

    const html = wrapper.find('[data-testid="revenue-growth-kpi"]').html()
    expect(html).not.toContain('success')
    expect(html).not.toContain('error')
  })

  it('실제 성장률은 기존대로 부호와 퍼센트로 보여준다', async () => {
    const wrapper = await renderView()

    store.summary = { ...store.summary, monthlyGrowth: 12.34 }
    await flushPromises()
    expect(wrapper.find('[data-testid="revenue-growth-kpi"]').text()).toContain('+12.3%')

    store.summary = { ...store.summary, monthlyGrowth: -8 }
    await flushPromises()
    expect(wrapper.find('[data-testid="revenue-growth-kpi"]').text()).toContain('-8.0%')
  })

  /** 실제로 0% 인 변화는 측정된 사실이므로 그대로 보여준다. */
  it('측정된 0% 변화는 비교 불가로 감추지 않는다', async () => {
    const wrapper = await renderView()

    store.summary = { ...store.summary, monthlyGrowth: 0 }
    await flushPromises()

    const kpi = wrapper.find('[data-testid="revenue-growth-kpi"]')
    expect(kpi.text()).toContain('+0.0%')
    expect(kpi.text()).not.toContain(koMessages.revenue.growthUnavailableShort)
  })

  it('플랫폼 분해 제목은 객체 키가 아니라 문자열 키를 쓴다', async () => {
    const wrapper = await renderView()

    expect(wrapper.text()).toContain(koMessages.revenue.platformBreakdownTitle)
    // 키 경로가 그대로 보이면 번역이 해석되지 않은 것이다.
    expect(wrapper.text()).not.toContain('revenue.platformBreakdown')
  })

  it('첫 진입은 기본 기간(30일)으로 부른다', async () => {
    await renderView()

    expect(store.fetchRevenue).toHaveBeenCalledWith('30d')
  })

  /** 값이 곧 API 일수다. 개월 값('1','3','6','12')이 되살아나면 여기서 걸린다. */
  it.each([
    ['최근 90일', '90d'],
    ['최근 180일', '180d'],
    ['최근 365일', '365d'],
  ])('%s 버튼을 누르면 %s 로 다시 부른다', async (label, period) => {
    const wrapper = await renderView()

    const button = wrapper.findAll('button').find((b) => b.text() === label)
    expect(button, `기간 버튼을 찾지 못했다: ${label}`).toBeDefined()
    await button!.trigger('click')
    await flushPromises()

    expect(store.fetchRevenue).toHaveBeenLastCalledWith(period)
  })

  /**
   * AI 인사이트는 백엔드가 **최근 30일로 고정**돼 있다
   * (`RevenueInsightUseCase:51` → `getRevenueSummary(userId, 30)`).
   * 헤더에서 365일을 골랐는데 아무 말이 없으면 그 기간의 분석으로 읽는다.
   */
  it('선택 기간이 30일이 아니면 AI가 30일 기준임을 알린다', async () => {
    const wrapper = await renderView()
    expect(wrapper.find('[data-testid="insights-fixed-period-notice"]').exists()).toBe(false)

    store.loadedPeriod = '365d'
    await flushPromises()

    const notice = wrapper.find('[data-testid="insights-fixed-period-notice"]')
    expect(notice.exists()).toBe(true)
    expect(notice.text()).toContain('최근 30일')
  })

  it('AI 생성 버튼과 안내 문구도 30일 기준임을 밝힌다', async () => {
    const wrapper = await renderView()

    expect(wrapper.text()).toContain(koMessages.revenue.insights.generate)
    expect(koMessages.revenue.insights.generate).toContain('30일')
    expect(koMessages.revenue.insights.generateHint).toContain('30일')
    expect(koMessages.revenue.insights.emptyHint).toContain('30일')
  })
})
