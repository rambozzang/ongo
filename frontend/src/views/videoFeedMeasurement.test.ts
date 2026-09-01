import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import VideoDetailPanel from '@/components/video/VideoDetailPanel.vue'
import koMessages from '@/locales/ko/common.json'
import type { VideoFeedItem } from '@/types/video'

/**
 * 영상 피드 화면이 **재지 못한 지표를 0 으로 그리지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 서버의 `FeedItem` 네 지표가 `Long = 0` 이었다. Instagram 미디어 목록 API 는 조회수를
 * 아예 주지 않는데 그 자리가 `0` 이 되어, 화면이 **"조회수 0"** 을 측정 결과처럼 그렸다.
 *
 * 이 값은 저장되지 않고 응답으로 바로 오므로 서버가 이제 `null` 을 준다.
 * `?? 0` 으로 되살리면 안 된다.
 */
const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  fallbackLocale: 'ko',
  messages: { ko: koMessages },
})

const notMeasured = koMessages.analyticsView.notMeasured

describe('영상 피드 지표 미측정 표시', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const item = (overrides: Partial<VideoFeedItem> = {}): VideoFeedItem => ({
    videoId: null,
    platformVideoId: 'v1',
    platform: 'YOUTUBE',
    channelName: '내 채널',
    title: '영상',
    description: null,
    thumbnailUrl: null,
    platformUrl: null,
    viewCount: 1000,
    likeCount: 10,
    commentCount: 5,
    shareCount: 2,
    publishedAt: '2026-08-01T00:00:00Z',
    ...overrides,
  })

  const mountPanel = (data: VideoFeedItem) =>
    mount(VideoDetailPanel, {
      props: { item: data, isOpen: true },
      global: { plugins: [i18n] },
    })

  /** **이 케이스가 "조회수 0" 을 측정 결과처럼 그리던 자리다.** */
  it('상세 패널이 미측정 조회수를 0 으로 그리지 않는다', () => {
    const text = mountPanel(item({ viewCount: null })).text()

    expect(text).toContain(notMeasured)
  })

  /** **측정된 0 은 관측이다.** */
  it('상세 패널이 측정된 0 을 숫자로 그린다', () => {
    const text = mountPanel(item({ viewCount: 0, likeCount: 0, commentCount: 0, shareCount: 0 })).text()

    expect(text).toContain('0')
    expect(text).not.toContain(notMeasured)
  })

  /** 측정값 표시는 그대로다 — 과도한 변경 회귀를 막는다. */
  it('상세 패널이 측정된 지표를 그대로 그린다', () => {
    const text = mountPanel(item()).text()

    expect(text).toContain('1.0K')
    expect(text).not.toContain(notMeasured)
  })

  /** 지표마다 따로 판정한다 — 하나가 미측정이어도 나머지는 숫자로 남는다. */
  it('일부만 미측정이면 나머지는 숫자로 그린다', () => {
    const text = mountPanel(item({ viewCount: null, likeCount: 10 })).text()

    expect(text).toContain(notMeasured)
    expect(text).toContain('10')
  })
})
