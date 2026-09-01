import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import RevenueView from './RevenueView.vue'
import { useRevenueStore } from '@/stores/revenue'
import koMessages from '@/locales/ko/common.json'
import enMessages from '@/locales/en/common.json'
import type { CpmRpmItem } from '@/types/revenue'

/**
 * CPM/RPM 표가 **계산할 수 없는 단가를 0원으로 그리지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * ₩{{ item.cpm.toLocaleString('ko-KR', { minimumFractionDigits: 2 }) }}
 * ```
 *
 * 서버가 분모 0 인 자리에 `0.0` 을 보냈고 표는 **"₩0.00"** 을 그렸다 — 재지 않았을
 * 뿐인데 "이 플랫폼은 수익성이 0" 이라는 관측이 된다. 서버가 이제 `null` 을 주므로,
 * 여기서 `?? 0` 한 줄만 넣어도 서버 수정이 통째로 무의미해진다.
 *
 * 게다가 `null.toLocaleString()` 은 그대로 두면 **TypeError 로 표 전체가 사라진다.**
 */
describe('CPM/RPM 단가 측정 불가 표시', () => {
  let store: ReturnType<typeof useRevenueStore>

  async function renderCpmRpmTab(platforms: CpmRpmItem[]) {
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

    store.cpmRpmData = { platforms }
    const cpmRpmTab = wrapper.findAll('[role="tab"]').find((t) => t.text().includes('CPM'))
    expect(cpmRpmTab, 'CPM/RPM 탭을 찾지 못했다').toBeTruthy()
    await cpmRpmTab!.trigger('click')
    await flushPromises()

    return wrapper
  }

  function row(overrides: Partial<CpmRpmItem>): CpmRpmItem {
    return {
      platform: 'YOUTUBE',
      cpm: 250,
      rpm: 500,
      impressions: 4000,
      views: 2000,
      revenueMicro: 1_000_000_000,
      unavailableMetrics: {},
      ...overrides,
    }
  }

  beforeEach(() => vi.clearAllMocks())

  // ── 계산 불가는 명시적으로 ───────────────────────────────────────────────

  /** **이 케이스가 "₩0.00" 을 그리던 자리다.** */
  it('CPM이 null이면 0원이 아니라 측정 불가를 보여준다', async () => {
    const wrapper = await renderCpmRpmTab([
      row({ cpm: null, impressions: 0, unavailableMetrics: { cpm: '노출수가 집계되지 않아 CPM을 계산할 수 없습니다' } }),
    ])

    expect(wrapper.find('[data-testid="cpm-unavailable"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="cpm-value"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="cpm-unavailable"]').text()).toBe(koMessages.revenue.unitPriceUnavailable)
    // RPM 은 살아 있어야 한다. 한쪽 분모가 비었다고 다른 쪽까지 버리면 안 된다.
    expect(wrapper.find('[data-testid="rpm-value"]').text()).toContain('500')
  })

  it('RPM이 null이면 RPM만 측정 불가로 표시한다', async () => {
    const wrapper = await renderCpmRpmTab([
      row({ rpm: null, views: 0, unavailableMetrics: { rpm: '조회수가 집계되지 않아 RPM을 계산할 수 없습니다' } }),
    ])

    expect(wrapper.find('[data-testid="rpm-unavailable"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="cpm-value"]').text()).toContain('250')
  })

  it('서버가 준 사유를 칸에 붙여 둔다', async () => {
    const reason = '노출수가 집계되지 않아 CPM을 계산할 수 없습니다'
    const wrapper = await renderCpmRpmTab([
      row({ cpm: null, impressions: 0, unavailableMetrics: { cpm: reason } }),
    ])

    expect(wrapper.find('[data-testid="cpm-unavailable"]').attributes('title')).toBe(reason)
  })

  // ── 측정된 0 은 보존 ─────────────────────────────────────────────────────

  /** **분모가 양수인데 수익이 0 이면 그 0 은 실측이다.** 감추면 실제 관찰을 잃는다. */
  it('측정된 0원 단가는 숫자로 보여준다', async () => {
    const wrapper = await renderCpmRpmTab([row({ cpm: 0, rpm: 0, revenueMicro: 0 })])

    expect(wrapper.find('[data-testid="cpm-value"]').text()).toBe('₩0.00')
    expect(wrapper.find('[data-testid="rpm-value"]').text()).toBe('₩0.00')
    expect(wrapper.find('[data-testid="cpm-unavailable"]').exists()).toBe(false)
  })

  // ── 깨진 숫자를 그리지 않는다 ────────────────────────────────────────────

  /**
   * `toLocaleString` 은 `NaN`·`Infinity` 를 **"NaN"·"∞" 라는 문자열로 성실히 그려낸다.**
   * 어딘가에서 0 으로 나눈 값이 새어 들어와도 표에는 나오지 않아야 한다.
   */
  it('NaN·Infinity가 흘러들어와도 숫자로 그리지 않는다', async () => {
    const wrapper = await renderCpmRpmTab([
      row({ platform: 'YOUTUBE', cpm: Number.NaN, rpm: Number.POSITIVE_INFINITY }),
    ])

    const text = wrapper.text()
    expect(text).not.toContain('NaN')
    expect(text).not.toContain('∞')
    expect(text).not.toContain('Infinity')
    expect(wrapper.find('[data-testid="cpm-unavailable"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="rpm-unavailable"]').exists()).toBe(true)
  })

  /** 필드 자체가 없는 옛 응답이 와도 표가 죽지 않아야 한다. */
  it('단가 필드가 없어도 표가 깨지지 않는다', async () => {
    const wrapper = await renderCpmRpmTab([
      { platform: 'TIKTOK', impressions: 0, views: 0, revenueMicro: 0 } as unknown as CpmRpmItem,
    ])

    expect(wrapper.find('[data-testid="cpm-unavailable"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('undefined')
  })

  // ── 행이 없는 경우와 구분 ────────────────────────────────────────────────

  /**
   * 측정된 수익 행이 하나도 없는 것과 단가 하나를 못 낸 것은 다른 상태다.
   * 전자는 표 대신 빈 상태를, 후자는 표 안의 칸을 "측정 불가" 로 그린다.
   */
  it('플랫폼 행이 없으면 표 대신 빈 상태를 보여준다', async () => {
    const wrapper = await renderCpmRpmTab([])

    expect(wrapper.text()).toContain(koMessages.revenue.cpmRpmEmpty)
    expect(wrapper.find('[data-testid="cpm-unavailable"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="cpm-value"]').exists()).toBe(false)
  })

  it('ko/en 두 로케일에 측정 불가 문구가 있다', () => {
    expect(koMessages.revenue.unitPriceUnavailable).toBeTruthy()
    const en = enMessages as { revenue: { unitPriceUnavailable?: string } }
    expect(en.revenue.unitPriceUnavailable).toBeTruthy()
  })
})
