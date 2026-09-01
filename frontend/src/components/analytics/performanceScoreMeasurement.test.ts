import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import PerformanceScoreCard from './PerformanceScoreCard.vue'
import { analyticsApi } from '@/api/analytics'
import koMessages from '@/locales/ko/common.json'
import enMessages from '@/locales/en/common.json'
import type { PerformanceScoreResponse } from '@/types/analytics'

vi.mock('@/api/analytics', () => ({ analyticsApi: { performanceScore: vi.fn() } }))

/**
 * 성과 점수 카드가 **미측정 축을 0 점으로 그리지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * { label: '시청 시간 (20%)', value: b.watchTime ?? 0, ... }
 * ```
 *
 * 서버가 `null` 을 줘도 화면은 0 으로 바꿔 **막대를 0% 로 그리고 "0" 을 찍었다.**
 * 시청 시간은 YouTube 만 수집하므로, 다른 플랫폼 크리에이터에게는 늘 "시청 시간 0점"
 * 이 보였다 — 재지 않았을 뿐인데 그 축에서 최하위라는 판정이 된다.
 *
 * 총점과 예측도 같다. `Math.round(null)` 은 `0` 이라 미측정이 "0점"으로 나온다.
 */
describe('성과 점수 미측정 렌더링', () => {
  function response(overrides: Partial<PerformanceScoreResponse> = {}): PerformanceScoreResponse {
    return {
      videoId: 42,
      overallScore: 62.5,
      breakdown: { viewVelocity: 60, engagement: 70, watchTime: 50, conversion: 40, share: 30 },
      percentileRank: 25,
      trend: 'up',
      isAnomaly: false,
      anomalyDescription: null,
      prediction7d: 12_000,
      dataAvailable: true,
      unavailableReason: null,
      unavailableMetrics: {},
      ...overrides,
    }
  }

  async function mountCard(body: PerformanceScoreResponse) {
    vi.mocked(analyticsApi.performanceScore).mockResolvedValue(body as never)
    const i18n = createI18n({ legacy: false, locale: 'ko', fallbackLocale: 'ko', messages: { ko: koMessages } })
    const wrapper = mount(PerformanceScoreCard, { props: { videoId: 42 }, global: { plugins: [i18n] } })
    await flushPromises()
    return wrapper
  }

  beforeEach(() => vi.clearAllMocks())

  // ── 하위 점수 ────────────────────────────────────────────────────────────

  /** **이 케이스가 "시청 시간 0점" 을 그리던 자리다.** */
  it('하위 점수가 null이면 0이 아니라 측정 불가를 보여준다', async () => {
    const wrapper = await mountCard(response({
      breakdown: { viewVelocity: 60, engagement: 70, watchTime: null, conversion: null, share: null },
      unavailableMetrics: { watchTime: '시청 시간이 수집되지 않아 비교 기준을 만들 수 없습니다' },
    }))

    const watchTime = wrapper.find('[data-testid="breakdown-watchTime"]')
    expect(watchTime.find('[data-testid="breakdown-unavailable"]').exists()).toBe(true)
    expect(watchTime.text()).toContain(koMessages.videoDetail.scoreUnavailable)

    // 라벨에 "(20%)" 가 들어 있어 텍스트 검색으로는 "0" 을 가릴 수 없다.
    // 측정된 축과 나란히 두고 **점수 칸이 그려졌는지**로 본다.
    const measured = wrapper.find('[data-testid="breakdown-engagement"]')
    expect(measured.find('[data-testid="breakdown-unavailable"]').exists()).toBe(false)
    expect(measured.text()).toContain('70')
  })

  /** 막대 폭 0% 는 "0점" 과 눈으로 구분되지 않는다. 막대 자체를 그리지 않는다. */
  it('미측정 축은 막대를 그리지 않는다', async () => {
    const wrapper = await mountCard(response({
      breakdown: { viewVelocity: 60, engagement: 70, watchTime: null, conversion: null, share: null },
    }))

    const bars = wrapper.find('[data-testid="breakdown-watchTime"]').findAll('.rounded-full')
    // 배경 트랙만 남고 값 막대는 없어야 한다.
    expect(bars.some((b) => b.attributes('style')?.includes('width'))).toBe(false)
  })

  it('서버가 준 사유를 칸에 붙여 둔다', async () => {
    const reason = '시청 시간이 수집되지 않아 비교 기준을 만들 수 없습니다'
    const wrapper = await mountCard(response({
      breakdown: { viewVelocity: 60, engagement: 70, watchTime: null, conversion: null, share: null },
      unavailableMetrics: { watchTime: reason },
    }))

    expect(
      wrapper.find('[data-testid="breakdown-watchTime"]')
        .find('[data-testid="breakdown-unavailable"]').attributes('title'),
    ).toBe(reason)
  })

  /** **측정된 0 은 관측 결과다.** 감추면 실제 관찰을 잃는다. */
  it('측정된 0점은 숫자로 보여준다', async () => {
    const wrapper = await mountCard(response({
      breakdown: { viewVelocity: 0, engagement: 0, watchTime: 0, conversion: 0, share: 0 },
    }))

    const velocity = wrapper.find('[data-testid="breakdown-viewVelocity"]')
    expect(velocity.find('[data-testid="breakdown-unavailable"]').exists()).toBe(false)
    expect(velocity.text()).toContain('0')
  })

  // ── 총점 ─────────────────────────────────────────────────────────────────

  /** `Math.round(null)` 은 0 이다. 그대로 두면 미측정이 "0점" 이 된다. */
  it('총점이 null이면 0점으로 그리지 않는다', async () => {
    const wrapper = await mountCard(response({
      overallScore: null,
      breakdown: { viewVelocity: null, engagement: null, watchTime: null, conversion: null, share: null },
      unavailableMetrics: { overall: '계산할 수 있는 하위 점수가 하나도 없습니다' },
    }))

    expect(wrapper.find('[data-testid="overall-score"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="overall-unavailable"]').exists()).toBe(true)
  })

  it('측정된 총점은 숫자로 보여준다', async () => {
    const wrapper = await mountCard(response({ overallScore: 62.5 }))

    expect(wrapper.find('[data-testid="overall-score"]').text()).toBe('63')
  })

  it('NaN·Infinity가 흘러들어와도 숫자로 그리지 않는다', async () => {
    const wrapper = await mountCard(response({
      overallScore: Number.NaN,
      breakdown: { viewVelocity: Number.POSITIVE_INFINITY, engagement: 70, watchTime: 50, conversion: 40, share: 30 },
    }))

    expect(wrapper.text()).not.toContain('NaN')
    expect(wrapper.text()).not.toContain('Infinity')
    expect(wrapper.find('[data-testid="overall-unavailable"]').exists()).toBe(true)
  })

  // ── 추세 ─────────────────────────────────────────────────────────────────

  /** **이 케이스가 "안정" 을 지어내던 자리다.** */
  it('추세가 null이면 안정이라고 말하지 않는다', async () => {
    const wrapper = await mountCard(response({ trend: null }))

    const label = wrapper.find('[data-testid="trend-label"]').text()
    expect(label).not.toBe('안정')
    expect(label).toBe(koMessages.videoDetail.trendUnavailable)
  })

  it('측정된 추세는 그대로 보여준다', async () => {
    const up = await mountCard(response({ trend: 'up' }))
    expect(up.find('[data-testid="trend-label"]').text()).toBe('상승세')

    const stable = await mountCard(response({ trend: 'stable' }))
    expect(stable.find('[data-testid="trend-label"]').text()).toBe('안정')
  })

  // ── 7일 예측 ─────────────────────────────────────────────────────────────

  /** **이 케이스가 관측 합계를 "7일 예상" 으로 그리던 자리다.** */
  it('예측이 null이면 숫자를 그리지 않는다', async () => {
    const wrapper = await mountCard(response({ prediction7d: null }))

    const text = wrapper.find('[data-testid="prediction"]').text()
    expect(text).toContain(koMessages.videoDetail.scoreUnavailable)
    expect(text).not.toContain('0')
  })

  it('측정된 예측은 숫자로 보여준다', async () => {
    const wrapper = await mountCard(response({ prediction7d: 12_000 }))

    expect(wrapper.find('[data-testid="prediction"]').text()).toContain('1.2만')
  })

  it('ko/en 두 로케일에 새 문구가 있다', () => {
    const en = enMessages as { videoDetail: Record<string, unknown> }
    for (const key of ['scoreUnavailable', 'trendUnavailable'] as const) {
      expect(koMessages.videoDetail[key], `ko.${key}`).toBeTruthy()
      expect(en.videoDetail[key], `en.${key}`).toBeTruthy()
    }
  })
})
