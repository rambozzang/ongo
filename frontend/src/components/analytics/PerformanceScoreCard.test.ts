import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import PerformanceScoreCard from './PerformanceScoreCard.vue'
import { analyticsApi } from '@/api/analytics'
import koMessages from '@/locales/ko/common.json'
import enMessages from '@/locales/en/common.json'

vi.mock('@/api/analytics', () => ({ analyticsApi: { performanceScore: vi.fn() } }))

const unavailable = (reason: string) => ({
  videoId: 42,
  overallScore: 0,
  breakdown: { viewVelocity: 0, engagement: 0, watchTime: 0, conversion: 0, share: 0 },
  percentileRank: 0,
  trend: 'stable',
  isAnomaly: false,
  anomalyDescription: null,
  prediction7d: 0,
  dataAvailable: false,
  unavailableReason: reason,
})

async function mountCard(locale: 'ko' | 'en') {
  const i18n = createI18n({
    legacy: false,
    locale,
    // fallback 을 끄고 **요청한 로케일만** 준다. 키가 빠져 있으면 fallback 이 가려 주지
    // 못하므로, 렌더된 문자열이 곧 그 로케일에 키가 있다는 증거가 된다.
    fallbackLocale: locale,
    messages: { ko: koMessages, en: enMessages },
  })
  const wrapper = mount(PerformanceScoreCard, {
    props: { videoId: 42 },
    global: { plugins: [i18n] },
  })
  await flushPromises()
  return wrapper
}

const measured = (percentileRank: number | null) => ({
  videoId: 42,
  overallScore: 62.5,
  breakdown: { viewVelocity: 60, engagement: 70, watchTime: 50, conversion: 40, share: 30 },
  percentileRank,
  trend: 'up',
  isAnomaly: false,
  anomalyDescription: null,
  prediction7d: 1234,
  dataAvailable: true,
  unavailableReason: null,
})

/**
 * 순위 배지는 **낮을수록 좋다**(상위 %). 값이 없을 수도 있다.
 *
 * `percentileRank` 가 null 인데 `.toFixed()` 를 부르면 카드 전체가 런타임 오류로 죽는다.
 * 0 으로 대체하면 "Top 0%"(최상위)라는 **없는 사실**을 만든다.
 */
describe('PerformanceScoreCard 순위 배지', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('순위가 없으면 배지를 그리지 않고 비교 부족을 알린다', async () => {
    vi.mocked(analyticsApi.performanceScore).mockResolvedValue(measured(null) as never)

    const wrapper = await mountCard('ko')

    expect(wrapper.find('[data-testid="percentile-badge"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="percentile-unavailable"]').exists()).toBe(true)
    expect(wrapper.text()).toContain(koMessages.videoDetail.percentileUnavailable)
    // 없는 순위를 0 으로 채우지 않는다.
    expect(wrapper.text()).not.toContain('Top 0%')
  })

  /** 필드가 없는 옛 응답도 같은 취급이어야 한다 — undefined 에 `.toFixed()` 는 터진다. */
  it('순위 필드가 아예 없는 응답에서도 죽지 않는다', async () => {
    const legacy = { ...measured(null) } as Record<string, unknown>
    delete legacy.percentileRank
    vi.mocked(analyticsApi.performanceScore).mockResolvedValue(legacy as never)

    const wrapper = await mountCard('ko')

    expect(wrapper.find('[data-testid="percentile-badge"]').exists()).toBe(false)
    // 배지만 빠지고 카드 본문(점수 62.5 → 반올림 63)은 정상 렌더링돼야 한다.
    expect(wrapper.text()).toContain('63')
  })

  it('최고 성과는 작은 상위 %로 표시한다', async () => {
    vi.mocked(analyticsApi.performanceScore).mockResolvedValue(measured(5) as never)

    const wrapper = await mountCard('ko')

    const badge = wrapper.find('[data-testid="percentile-badge"]')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toBe('Top 5%')
    // 낮은 값이 좋은 것이다. 색 기준이 예전 방향이면 최고 성과가 회색이 된다.
    expect(badge.classes().join(' ')).toContain('success')
  })

  it('최저 성과는 큰 상위 %로 표시하고 강조하지 않는다', async () => {
    vi.mocked(analyticsApi.performanceScore).mockResolvedValue(measured(100) as never)

    const wrapper = await mountCard('ko')

    const badge = wrapper.find('[data-testid="percentile-badge"]')
    expect(badge.text()).toBe('Top 100%')
    expect(badge.classes().join(' ')).not.toContain('success')
  })
})

/**
 * 새로 추가한 문구가 **두 로케일 모두에서** 실제로 번역되는지 고정한다.
 *
 * 컴포넌트에 한국어를 그대로 박아 두면 영어 사용자에게 한국어가 그대로 나가고,
 * 반대로 키만 있고 로케일 파일에 값이 없으면 화면에 `videoDetail.aiScoreBasis` 같은
 * **키 문자열이 그대로 노출된다.** 둘 다 배포 전에는 눈에 띄지 않는다.
 */
describe('PerformanceScoreCard i18n', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('부제를 로케일별로 번역해 보여준다', async () => {
    vi.mocked(analyticsApi.performanceScore).mockResolvedValue(unavailable('NO_ANALYTICS') as never)

    const ko = await mountCard('ko')
    expect(ko.text()).toContain(koMessages.videoDetail.aiScoreBasis)

    const en = await mountCard('en')
    expect(en.text()).toContain(enMessages.videoDetail.aiScoreBasis)
    // 키 문자열이 그대로 나오면 번역이 없는 것이다.
    expect(en.text()).not.toContain('videoDetail.aiScoreBasis')
  })

  it('수집 전 빈 상태 문구를 로케일별로 번역해 보여준다', async () => {
    vi.mocked(analyticsApi.performanceScore).mockResolvedValue(unavailable('NO_ANALYTICS') as never)

    const ko = await mountCard('ko')
    expect(ko.text()).toContain(koMessages.videoDetail.aiScoreEmptyNoAnalytics)

    const en = await mountCard('en')
    expect(en.text()).toContain(enMessages.videoDetail.aiScoreEmptyNoAnalytics)
    expect(en.text()).not.toContain('videoDetail.aiScoreEmpty')
  })

  it('게시 전 빈 상태 문구를 로케일별로 번역해 보여준다', async () => {
    vi.mocked(analyticsApi.performanceScore).mockResolvedValue(unavailable('NO_UPLOADS') as never)

    const ko = await mountCard('ko')
    expect(ko.text()).toContain(koMessages.videoDetail.aiScoreEmptyNoUploads)

    const en = await mountCard('en')
    expect(en.text()).toContain(enMessages.videoDetail.aiScoreEmptyNoUploads)
  })

  /** 사유를 모르는 경우(옛 응답·조회 실패)에도 키가 아니라 문구가 나와야 한다. */
  it('사유가 없으면 기본 빈 상태 문구를 보여준다', async () => {
    vi.mocked(analyticsApi.performanceScore).mockRejectedValue(new Error('조회 실패'))

    const en = await mountCard('en')

    expect(en.text()).toContain(enMessages.videoDetail.aiScoreEmptyDefault)
    expect(en.text()).not.toContain('videoDetail.aiScoreEmptyDefault')
  })
})
