import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createRouter, createMemoryHistory } from 'vue-router'
import PerformanceScore from './PerformanceScore.vue'
import koMessages from '@/locales/ko/common.json'
import enMessages from '@/locales/en/common.json'

const video = {
  id: 42,
  userId: 7,
  title: '테스트 영상',
  description: null,
  tags: [],
  category: null,
  mediaType: 'VIDEO',
  fileUrl: 'https://storage.test/video.mp4',
  thumbnailUrl: null,
  thumbnailCandidates: [],
  fileSize: 4,
  status: 'PUBLISHED',
  visibility: 'PUBLIC',
  createdAt: '2026-08-10T00:00:00Z',
  updatedAt: '2026-08-10T00:00:00Z',
  uploads: [{
    id: 101,
    videoId: 42,
    platform: 'YOUTUBE',
    channelId: 9,
    channelName: '테스트 채널',
    status: 'PUBLISHED',
    platformVideoId: 'yt-1',
    platformUrl: 'https://youtube.test/watch/yt-1',
  }],
}

const analytics = [{
  platform: 'YOUTUBE',
  views: 4200,
  likes: 100,
  comments: 5,
  shares: 2,
  hasData: true,
  dailyTrend: [{ date: '2026-08-10', totalViews: 4200, platformViews: {} }],
}]

async function mountScore(locale: 'ko' | 'en', analyticsData = analytics) {
  const i18n = createI18n({
    legacy: false,
    locale,
    // fallback 을 끄고 **요청한 로케일만** 준다. 키가 빠져 있으면 fallback 이 가려 주지
    // 못하므로, 렌더된 문자열이 곧 그 로케일에 키가 있다는 증거가 된다.
    fallbackLocale: locale,
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })
  await router.push('/')
  await router.isReady()

  return mount(PerformanceScore, {
    props: { video, analytics: analyticsData } as never,
    global: { plugins: [i18n, router] },
  })
}

/**
 * 카드 부제가 **두 로케일 모두에서** 실제로 번역되는지 고정한다.
 *
 * 이 부제는 위아래 두 점수 카드의 계산 근거가 다르다는 것을 알리는 유일한 표시다.
 * 한국어가 박혀 있으면 영어 사용자에게 그대로 나가고, 키만 있고 값이 없으면 화면에
 * `videoDetail.selfScoreBasis` 라는 키 문자열이 그대로 노출된다.
 */
describe('PerformanceScore i18n', () => {
  it('자체 점수 부제를 로케일별로 번역해 보여준다', async () => {
    const ko = await mountScore('ko')
    expect(ko.text()).toContain(koMessages.videoDetail.selfScoreBasis)

    const en = await mountScore('en')
    expect(en.text()).toContain(enMessages.videoDetail.selfScoreBasis)
    // 키 문자열이 그대로 나오면 번역이 없는 것이다.
    expect(en.text()).not.toContain('videoDetail.selfScoreBasis')
  })

  it('추세 표본이 없으면 성장 점수를 중립 숫자로 채우지 않는다', async () => {
    const wrapper = await mountScore('ko')

    expect(wrapper.find('[data-testid="growth-score-unavailable"]').exists()).toBe(true)
  })

  it('미수집 지표는 0점으로 그리지 않는다', async () => {
    const wrapper = await mountScore('ko', [{
      platform: 'TUMBLR',
      views: null,
      likes: null,
      comments: null,
      shares: null,
      dailyTrend: [],
    }] as never)

    expect(wrapper.find('[data-testid="overall-score-unavailable"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="reach-score-unavailable"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="engagement-score-unavailable"]').exists()).toBe(true)
  })
})
