import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import RetentionCurveChart from './RetentionCurveChart.vue'
import { analyticsApi } from '@/api/analytics'
import koMessages from '@/locales/ko/common.json'
import enMessages from '@/locales/en/common.json'

/**
 * 구간별 유지율이 **없다는 사실**을 정직하게 보여주는지.
 *
 * 서버는 곡선을 지어내지 않고 `available: false` + 사유를 내려준다. 화면이 그걸 기존
 * "데이터 없음" 문구로 뭉개면, 기다리면 생기는 줄 알고 계속 기다리게 된다.
 */
vi.mock('@/api/analytics', () => ({
  analyticsApi: { retentionCurve: vi.fn(), videoList: vi.fn() },
}))

const REASON = '구간별 시청 유지율은 현재 플랫폼 분석 연동에서 제공하지 않아 표시할 수 없습니다.'

function mountChart(locale: 'ko' | 'en' = 'ko') {
  const i18n = createI18n({
    legacy: false,
    locale,
    fallbackLocale: 'ko',
    messages: { ko: koMessages, en: enMessages },
  })
  return mount(RetentionCurveChart, {
    props: { videoId: 42 },
    global: {
      plugins: [i18n],
      stubs: {
        Line: true,
        AsyncState: {
          props: ['loading', 'empty', 'emptyTitle', 'emptyDescription'],
          template:
            '<div><span data-testid="title">{{ emptyTitle }}</span>' +
            '<span data-testid="desc">{{ emptyDescription }}</span>' +
            '<slot v-if="!empty" /></div>',
        },
      },
    },
  })
}

describe('RetentionCurveChart', () => {
  beforeEach(() => vi.clearAllMocks())

  it('미지원 응답은 데이터 없음과 다른 문구로 알린다', async () => {
    vi.mocked(analyticsApi.retentionCurve).mockResolvedValue({
      videoId: 42,
      retentionPoints: [],
      avgRetention: [],
      dropOffPoints: [],
      available: false,
      unavailableReason: REASON,
    } as never)

    const wrapper = mountChart()
    await flushPromises()

    expect(wrapper.get('[data-testid="title"]').text()).toContain('연결되지 않았습니다')
    expect(wrapper.get('[data-testid="desc"]').text()).toBe(REASON)
    // 기다리면 생긴다는 오해를 주는 문구를 쓰지 않는다.
    expect(wrapper.get('[data-testid="title"]').text()).not.toContain('데이터가 없습니다')
  })

  /** 필드가 없는 옛 응답은 기존 문구를 그대로 써야 한다 — 하위 호환. */
  it('available 필드가 없으면 기존 데이터 없음 문구를 쓴다', async () => {
    vi.mocked(analyticsApi.retentionCurve).mockResolvedValue({
      videoId: 42,
      retentionPoints: [],
      avgRetention: [],
      dropOffPoints: [],
    } as never)

    const wrapper = mountChart()
    await flushPromises()

    expect(wrapper.get('[data-testid="title"]').text()).toContain('리텐션 데이터가 없습니다')
    expect(wrapper.get('[data-testid="desc"]').text()).toBe('')
  })

  /** 사유 없이 available=false 만 오면 단정하지 않는다. */
  it('사유가 없으면 미지원으로 단정하지 않는다', async () => {
    vi.mocked(analyticsApi.retentionCurve).mockResolvedValue({
      videoId: 42,
      retentionPoints: [],
      avgRetention: [],
      dropOffPoints: [],
      available: false,
      unavailableReason: null,
    } as never)

    const wrapper = mountChart()
    await flushPromises()

    expect(wrapper.get('[data-testid="title"]').text()).toContain('리텐션 데이터가 없습니다')
  })

  /**
   * 영어 UI 에서도 자연스러워야 한다. 하드코딩 한국어를 남기면 영어 사용자는 제목만
   * 한글로 보게 된다.
   */
  it('영어 로케일에서 미지원 제목이 영어로 나온다', async () => {
    vi.mocked(analyticsApi.retentionCurve).mockResolvedValue({
      videoId: 42,
      retentionPoints: [],
      avgRetention: [],
      dropOffPoints: [],
      available: false,
      unavailableReason: REASON,
    } as never)

    const wrapper = mountChart('en')
    await flushPromises()

    const title = wrapper.get('[data-testid="title"]').text()
    expect(title).toBe(enMessages.analyticsView.retention.unsupportedTitle)
    // 한글이 섞이면 i18n 을 타지 않은 것이다.
    expect(title).not.toMatch(/[가-힣]/)
  })

  it('영어 로케일의 데이터 없음 문구도 영어다', async () => {
    vi.mocked(analyticsApi.retentionCurve).mockResolvedValue({
      videoId: 42,
      retentionPoints: [],
      avgRetention: [],
      dropOffPoints: [],
    } as never)

    const wrapper = mountChart('en')
    await flushPromises()

    const title = wrapper.get('[data-testid="title"]').text()
    expect(title).toBe(enMessages.analyticsView.retention.emptyNoData)
    expect(title).not.toMatch(/[가-힣]/)
  })

  /** ko/en 양쪽에 키가 다 있어야 fallback 으로 한글이 새지 않는다. */
  it('세 상태 문구가 ko/en 모두 정의돼 있다', () => {
    for (const key of ['emptySelect', 'emptyNoData', 'unsupportedTitle'] as const) {
      expect(koMessages.analyticsView.retention[key], `ko.${key}`).toBeTruthy()
      expect(enMessages.analyticsView.retention[key], `en.${key}`).toBeTruthy()
      expect(enMessages.analyticsView.retention[key]).not.toMatch(/[가-힣]/)
    }
  })
})
