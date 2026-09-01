import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { analyticsApi } from '@/api/analytics'
import BestTimeCard from './BestTimeCard.vue'
import koMessages from '@/locales/ko/common.json'
import enMessages from '@/locales/en/common.json'
import type { HeatmapData } from '@/types/analytics'

vi.mock('@/api/analytics', () => ({
  analyticsApi: { getOptimalTimes: vi.fn() },
}))

/**
 * 최적 게시 시간 카드가 **근거 없는 슬롯을 지어내지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * API 가 빈 결과를 주면 히트맵에서 상위 5개를 뽑아 슬롯을 만들고
 * `engagementRate: 0, confidenceScore: 0` 을 채웠다. 화면은 그것을
 * **`참여율 0% / 신뢰도 0%`** 로 측정값처럼 보여줬다.
 *
 * 게다가 그 폴백은 **서버가 게시 시각을 확인하지 못해 추천을 만들지 못한** 상황에서
 * 나온다. 근거가 없어서 못 만든 자리를 다른 근거 없는 숫자로 채우는 셈이다.
 */
describe('최적 게시 시간 추천 폴백 제거', () => {
  const heatmap: HeatmapData[] = [
    { dayOfWeek: 1, hour: 9, value: 5000 },
    { dayOfWeek: 3, hour: 20, value: 3000 },
  ]

  function mountCard() {
    const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
    return mount(BestTimeCard, {
      props: { data: heatmap },
      global: { plugins: [i18n], stubs: { AsyncState: false } },
    })
  }

  beforeEach(() => vi.clearAllMocks())

  /** **이 케이스가 "12:00 · 참여율 0% · 신뢰도 0%" 를 만들던 자리다.** */
  it('API 슬롯이 없으면 히트맵으로 가짜 추천을 만들지 않는다', async () => {
    vi.mocked(analyticsApi.getOptimalTimes).mockResolvedValue({
      slots: [],
      unavailableReason: '게시 시각이 확인된 성과 데이터가 없어 추천 시간을 계산할 수 없습니다',
    } as never)

    const wrapper = mountCard()
    await flushPromises()

    // 히트맵에는 데이터가 있지만 추천 슬롯을 만들면 안 된다.
    expect(wrapper.text()).not.toContain('참여율: 0%')
    expect(wrapper.text()).not.toContain('신뢰도: 0%')
    expect(wrapper.text()).not.toContain('12:00')
  })

  it('빈 상태에 서버가 준 사유를 보여준다', async () => {
    vi.mocked(analyticsApi.getOptimalTimes).mockResolvedValue({
      slots: [],
      unavailableReason: '게시 시각이 확인된 성과 데이터가 없어 추천 시간을 계산할 수 없습니다',
    } as never)

    const wrapper = mountCard()
    await flushPromises()

    expect(wrapper.text()).toContain('게시 시각이 확인된 성과 데이터가 없어')
  })

  it('사유가 없으면 기본 빈 상태 문구를 보여준다', async () => {
    vi.mocked(analyticsApi.getOptimalTimes).mockResolvedValue({ slots: [] } as never)

    const wrapper = mountCard()
    await flushPromises()

    expect(wrapper.text()).toContain(koMessages.analyticsView.optimalTimesEmptyTitle)
  })

  it('API 호출이 실패해도 가짜 슬롯을 만들지 않는다', async () => {
    vi.mocked(analyticsApi.getOptimalTimes).mockRejectedValue(new Error('조회 실패'))

    const wrapper = mountCard()
    await flushPromises()

    expect(wrapper.text()).not.toContain('참여율: 0%')
    expect(wrapper.text()).not.toContain('신뢰도: 0%')
  })

  // ── 측정된 슬롯은 그대로 ─────────────────────────────────────────────────

  it('서버가 준 슬롯은 기존대로 값과 함께 보여준다', async () => {
    vi.mocked(analyticsApi.getOptimalTimes).mockResolvedValue({
      slots: [{
        dayOfWeek: 1, dayLabel: '월요일', hour: 9, timeLabel: '09:00',
        expectedViews: 12000, engagementRate: 4.2, confidenceScore: 60, score: 88.5,
      }],
      unavailableReason: null,
    } as never)

    const wrapper = mountCard()
    await flushPromises()

    expect(wrapper.text()).toContain('09:00')
    expect(wrapper.text()).toContain('4.2')
    expect(wrapper.text()).toContain('60')
  })

  // ── 슬롯 단위 참여율 미측정 ──────────────────────────────────────────────
  //
  // 슬롯 자체는 만들어졌지만(조회수는 잼) 그 슬롯의 게시물이 전부 참여 지표를
  // 보고하지 않는 플랫폼이면 서버가 `engagementRate: null` 을 준다. 예전 서버는
  // 빈 표본의 중앙값 `0.0` 을 채웠고 화면은 "참여율: 0%" 를 그렸다.

  function slot(engagementRate: number | null) {
    return {
      dayOfWeek: 1,
      dayLabel: '월요일',
      hour: 9,
      timeLabel: '09:00',
      expectedViews: 12000,
      engagementRate,
      confidenceScore: 60,
      score: 88.5,
    }
  }

  async function mountWithSlot(engagementRate: number | null) {
    vi.mocked(analyticsApi.getOptimalTimes).mockResolvedValue({
      slots: [slot(engagementRate)],
      unavailableReason: null,
    } as never)
    const wrapper = mountCard()
    await flushPromises()
    return wrapper
  }

  /** **이 케이스가 "참여율: 0%" 를 관측처럼 그리던 자리다.** */
  it('참여율이 null이면 0%가 아니라 측정 불가로 보여준다', async () => {
    const wrapper = await mountWithSlot(null)

    expect(wrapper.text()).toContain(koMessages.analyticsView.notMeasured)
    expect(wrapper.text()).not.toContain('참여율: 0%')
    // 조회수·신뢰도는 잰 값이다 — 함께 사라지면 안 된다.
    // `formatNumber` 는 한국어 축약을 쓴다(12,000 → "1.2만").
    expect(wrapper.text()).toContain('1.2만')
    expect(wrapper.text()).toContain('신뢰도: 60%')
  })

  /** 단위는 값이 있을 때만 붙는다. 밖에 두면 "측정 불가%" 가 된다. */
  it('미측정 문구 뒤에 퍼센트 기호가 붙지 않는다', async () => {
    const wrapper = await mountWithSlot(null)

    expect(wrapper.text()).not.toContain(`${koMessages.analyticsView.notMeasured}%`)
    expect(wrapper.text()).not.toContain('null')
  })

  /** **측정된 0 은 관측이다.** 감추면 실제 관찰을 잃는다. */
  it('측정된 참여율 0은 0%로 그대로 보여준다', async () => {
    const wrapper = await mountWithSlot(0)

    expect(wrapper.text()).toContain('참여율: 0%')
    expect(wrapper.text()).not.toContain(koMessages.analyticsView.notMeasured)
  })

  it('ko/en 두 로케일에 빈 상태 문구가 있다', () => {
    expect(koMessages.analyticsView.optimalTimesEmptyTitle).toBeTruthy()
    expect(koMessages.analyticsView.optimalTimesEmptyDescription).toBeTruthy()
    const en = enMessages as { analyticsView: { optimalTimesEmptyTitle?: string; optimalTimesEmptyDescription?: string } }
    expect(en.analyticsView.optimalTimesEmptyTitle).toBeTruthy()
    expect(en.analyticsView.optimalTimesEmptyDescription).toBeTruthy()
  })
})
