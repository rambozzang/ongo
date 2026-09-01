import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { competitorApi } from '@/api/competitor'
import { useCompetitorStore } from '@/stores/competitor'
import CompetitorCard from './CompetitorCard.vue'
import TrendingVideoList from './TrendingVideoList.vue'
import koMessages from '@/locales/ko/common.json'
import type { Competitor, CompetitorVideo } from '@/types/competitor'

vi.mock('@/api/competitor', () => ({
  competitorApi: {
    list: vi.fn(),
    benchmark: vi.fn(),
    add: vi.fn(),
    remove: vi.fn(),
    sync: vi.fn(),
    trends: vi.fn(),
  },
}))

/**
 * 경쟁 채널 아바타에서 **가짜 외부 이미지**를 제거한 계약을 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * avatarUrl: resp.profileImageUrl ?? <외부 아바타 서비스 URL + id>
 * return competitor?.avatarUrl || <외부 아바타 서비스 고정 URL>
 * ```
 *
 * 그 외부 서비스는 **무작위 인물 사진**을 돌려준다. 화면에서는 그 경쟁
 * 채널의 실제 프로필 이미지와 구분되지 않아, 사용자는 남의 얼굴을 그 채널의 것으로
 * 읽었다. 목록 쪽은 파라미터가 없어 프로필이 없는 **모든** 채널이 똑같은 얼굴이 됐다.
 *
 * 이제 없으면 `null` 이고, 화면은 **로컬 아이콘**을 그린다.
 * `src=''` 로 두지 않는다 — 브라우저가 현재 페이지를 다시 요청한다.
 */
describe('경쟁 채널 아바타', () => {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'ko',
    messages: { ko: koMessages },
  })

  const competitor = (overrides: Partial<Competitor> = {}): Competitor => ({
    id: 1,
    name: '경쟁 채널',
    channelUrl: '',
    platform: 'YOUTUBE',
    avatarUrl: null,
    subscriberCount: 10000,
    videoCount: 12,
    avgViews: 1500,
    avgEngagement: null,
    growthRate: null,
    lastVideoAt: '',
    addedAt: '',
    isTracking: true,
    ...overrides,
  })

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  // ── CompetitorCard ──────────────────────────────────────────────────────

  function mountCard(data: Competitor) {
    return mount(CompetitorCard, { props: { competitor: data }, global: { plugins: [i18n] } })
  }

  /** **이 케이스가 남의 얼굴을 그 채널 프로필로 보여주던 자리다.** */
  it('카드: 프로필 이미지가 없으면 img 를 그리지 않는다', () => {
    const wrapper = mountCard(competitor({ avatarUrl: null }))

    expect(wrapper.find('img').exists()).toBe(false)
    // 외부 아바타 서비스로 나가는 요청이 하나도 없어야 한다.
    expect(wrapper.findAll('img')).toHaveLength(0)
  })

  /** `src=''` 는 브라우저가 현재 페이지를 다시 내려받게 만든다. */
  it('카드: 빈 src 를 가진 img 를 남기지 않는다', () => {
    const imgs = mountCard(competitor({ avatarUrl: null })).findAll('img')

    expect(imgs.every(img => (img.attributes('src') ?? '') !== '')).toBe(true)
  })

  it('카드: 프로필 이미지가 없으면 로컬 placeholder 를 그린다', () => {
    const wrapper = mountCard(competitor({ avatarUrl: null }))

    // 로컬 SVG 아이콘 — 외부 요청이 없다. 라벨은 감싸는 요소가 들고 있다
    // (heroicons 는 자체적으로 `aria-hidden` 을 붙여 장식 요소로 둔다).
    const placeholder = wrapper.find('[role="img"]')
    expect(placeholder.exists()).toBe(true)
    expect(placeholder.attributes('aria-label')).toBe('경쟁 채널')
    expect(placeholder.find('svg').exists()).toBe(true)
  })

  /** **실제 URL 은 그대로 통과한다.** 과도한 차단 회귀를 막는다. */
  it('카드: 실제 프로필 이미지는 그대로 렌더링한다', () => {
    const url = 'https://yt3.example.com/channel-avatar.jpg'
    const wrapper = mountCard(competitor({ avatarUrl: url }))

    const img = wrapper.find('img')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe(url)
    expect(img.attributes('alt')).toBe('경쟁 채널')
  })

  // ── TrendingVideoList ───────────────────────────────────────────────────

  const video: CompetitorVideo = {
    id: 1,
    competitorId: 1,
    title: '인기 영상',
    views: 1000,
    likes: 10,
    comments: 2,
    publishedAt: '2026-08-20T00:00:00Z',
    duration: '10:00',
    thumbnailUrl: 'https://yt3.example.com/thumb.jpg',
  }

  function mountList(data: Competitor) {
    return mount(TrendingVideoList, {
      props: { videos: [video], competitors: [data] },
      global: { plugins: [i18n] },
    })
  }

  /** **이 케이스가 프로필 없는 모든 채널을 같은 얼굴로 보여주던 자리다.** */
  it('목록: 프로필 이미지가 없으면 가짜 아바타를 그리지 않는다', () => {
    const wrapper = mountList(competitor({ avatarUrl: null }))

    // 썸네일 img 만 남아야 한다 — 아바타 img 는 빠진다.
    const srcs = wrapper.findAll('img').map(img => img.attributes('src'))
    expect(srcs).toEqual([video.thumbnailUrl])
  })

  it('목록: 빈 src 를 가진 img 를 남기지 않는다', () => {
    const imgs = mountList(competitor({ avatarUrl: null })).findAll('img')

    expect(imgs.every(img => (img.attributes('src') ?? '') !== '')).toBe(true)
  })

  it('목록: 프로필 이미지가 없으면 로컬 placeholder 를 그린다', () => {
    const wrapper = mountList(competitor({ avatarUrl: null }))

    expect(wrapper.find('[role="img"]').attributes('aria-label')).toBe('경쟁 채널')
  })

  /** **실제 URL 은 그대로 통과한다.** */
  it('목록: 실제 프로필 이미지는 그대로 렌더링한다', () => {
    const url = 'https://yt3.example.com/channel-avatar.jpg'
    const wrapper = mountList(competitor({ avatarUrl: url }))

    const srcs = wrapper.findAll('img').map(img => img.attributes('src'))
    expect(srcs).toContain(url)
  })

  // ── 응답 매퍼 ───────────────────────────────────────────────────────────

  const listResponse = (profileImageUrl: string | null) => ({
    competitors: [
      {
        id: 7,
        platform: 'YOUTUBE',
        platformChannelId: 'rival',
        channelName: '경쟁 채널',
        channelUrl: null,
        subscriberCount: 10000,
        totalViews: 18000,
        videoCount: 12,
        avgViews: 1500,
        profileImageUrl,
        lastSyncedAt: null,
        createdAt: null,
      },
    ],
    totalCount: 1,
  })

  /** **매퍼가 `null` 을 가짜 URL 로 바꾸지 않는다.** */
  it('매퍼: 프로필 이미지가 없으면 null 을 유지한다', async () => {
    vi.mocked(competitorApi.list).mockResolvedValue(listResponse(null) as never)
    vi.mocked(competitorApi.benchmark).mockRejectedValue(new Error('benchmark down'))
    const store = useCompetitorStore()

    await store.fetchCompetitors()

    expect(store.competitors[0].avatarUrl).toBeNull()
  })

  /** 매퍼가 실제 URL 을 훼손하지 않는다. */
  it('매퍼: 실제 프로필 이미지 URL 을 그대로 통과시킨다', async () => {
    const url = 'https://yt3.example.com/channel-avatar.jpg'
    vi.mocked(competitorApi.list).mockResolvedValue(listResponse(url) as never)
    vi.mocked(competitorApi.benchmark).mockRejectedValue(new Error('benchmark down'))
    const store = useCompetitorStore()

    await store.fetchCompetitors()

    expect(store.competitors[0].avatarUrl).toBe(url)
  })
})
