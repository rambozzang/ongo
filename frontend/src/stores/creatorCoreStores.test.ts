import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useChannelStore } from './channel'
import { useVideoStore } from './video'
import { useUgcCampaignStore } from './ugcCampaign'
import { useUgcShortsStore } from './ugcShorts'
import { useCreditStore } from './credit'
import { useDashboardStore } from './dashboard'
import { useInboxStore } from './inbox'
import { useAiStore } from './ai'
import { useRevenueStore } from './revenue'
import { useTemplatesStore } from './templates'
import { channelApi } from '@/api/channel'
import { videoApi } from '@/api/video'
import { ugcCampaignApi } from '@/api/ugcCampaign'
import { ugcShortsPromptApi } from '@/api/ugcShortsPrompt'
import { ugcShortsTemplateApi } from '@/api/ugcShortsTemplate'
import { creditApi } from '@/api/credit'
import { analyticsApi } from '@/api/analytics'
import { scheduleApi } from '@/api/schedule'
import { inboxApi } from '@/api/inbox'
import { aiApi } from '@/api/ai'
import { revenueApi } from '@/api/revenue'
import { templatesApi } from '@/api/templates'

vi.mock('@/api/channel', () => ({
  channelApi: { list: vi.fn(), disconnect: vi.fn(), sync: vi.fn() },
}))
vi.mock('@/api/video', () => ({
  videoApi: {
    list: vi.fn(), get: vi.fn(), create: vi.fn(), delete: vi.fn(), feed: vi.fn(),
  },
}))
vi.mock('@/api/ugcCampaign', () => ({ ugcCampaignApi: {
  list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(), publish: vi.fn(), pause: vi.fn(), complete: vi.fn(), upsertPlaybook: vi.fn(),
} }))
vi.mock('@/api/ugcShortsPrompt', () => ({ ugcShortsPromptApi: {
  list: vi.fn(), update: vi.fn(), resetToDefault: vi.fn(), revisions: vi.fn(), restoreRevision: vi.fn(),
} }))
vi.mock('@/api/ugcShortsTemplate', () => ({ ugcShortsTemplateApi: {
  list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn(), uploadReferenceImage: vi.fn(),
} }))
vi.mock('@/api/credit', () => ({ creditApi: { getBalance: vi.fn(), getTransactions: vi.fn() } }))
vi.mock('@/api/analytics', () => ({ analyticsApi: {
  dashboard: vi.fn(), trends: vi.fn(), platformComparison: vi.fn(), heatmap: vi.fn(), topVideos: vi.fn(),
  trafficSources: vi.fn(), demographics: vi.fn(), ctr: vi.fn(), avgViewDuration: vi.fn(), subscriberConversion: vi.fn(), crossPlatformComparison: vi.fn(),
} }))
vi.mock('@/api/schedule', () => ({ scheduleApi: { list: vi.fn() } }))
vi.mock('@/api/inbox', () => ({ inboxApi: {
  listMessages: vi.fn(), markAsRead: vi.fn(), markAllAsRead: vi.fn(), toggleStar: vi.fn(),
} }))
vi.mock('@/api/ai', () => ({ aiApi: {
  generateMeta: vi.fn(), generateHashtags: vi.fn(), generateReport: vi.fn(), strategyCoach: vi.fn(), revenueReport: vi.fn(),
} }))
vi.mock('@/api/revenue', () => ({ revenueApi: {
  summary: vi.fn(), trends: vi.fn(), platformRevenue: vi.fn(), cpmRpm: vi.fn(), brandDealRevenue: vi.fn(),
  insights: vi.fn(), generateInsight: vi.fn(), alertConfigs: vi.fn(), saveAlertConfig: vi.fn(), updateAlertConfig: vi.fn(), deleteAlertConfig: vi.fn(),
} }))
vi.mock('@/api/templates', () => ({ templatesApi: {
  list: vi.fn(), create: vi.fn(), update: vi.fn(), delete: vi.fn(), use: vi.fn(),
} }))
vi.mock('@/stores/workspace', () => ({
  useWorkspaceStore: vi.fn(() => ({ ensureActiveWorkspace: vi.fn().mockResolvedValue(3) })),
}))

const page = <T>(content: T[], totalElements = content.length) => ({
  content, totalElements, totalPages: 1, size: 20, number: 0, first: true, last: true,
})

const channel = (id: number, platform: 'YOUTUBE' | 'TIKTOK' = 'YOUTUBE') => ({
  id, userId: 1, platform, platformChannelId: `channel-${id}`, channelName: `채널 ${id}`,
}) as never

const video = (id: number) => ({
  id, userId: 1, title: `영상 ${id}`, status: 'DRAFT', fileUrl: `https://cdn.test/${id}.mp4`,
}) as never

describe('creator-facing core stores', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('preserves connected channels on refresh failure and invalidates video cache on disconnect', async () => {
    vi.mocked(channelApi.list).mockResolvedValue({ channels: [channel(1)], maxAllowed: 7, currentCount: 1 } as never)
    const channels = useChannelStore()
    const videos = useVideoStore()
    videos.videos = page([video(1)]) as never

    await channels.fetchChannels()
    expect(channels.connectedPlatforms).toEqual(['YOUTUBE'])
    vi.mocked(channelApi.list).mockRejectedValueOnce(new Error('채널 서버 장애'))
    await channels.fetchChannels()
    expect(channels.channels).toHaveLength(1)
    expect(channels.loadError).toBe(true)

    vi.mocked(channelApi.disconnect).mockResolvedValue(undefined)
    await channels.disconnectChannel(1)
    expect(channels.channels).toEqual([])
    expect(videos.videos).toBeNull()
  })

  it('keeps video list/feed data on failure and exposes detail errors', async () => {
    const store = useVideoStore()
    vi.mocked(videoApi.list).mockResolvedValue(page([video(1)]) as never)
    await store.fetchVideos()
    expect(store.videos?.content).toHaveLength(1)

    vi.mocked(videoApi.list).mockRejectedValueOnce(new Error('목록 장애'))
    await store.fetchVideos()
    expect(store.videos?.content).toHaveLength(1)
    expect(store.listLoadError).toBe('목록 장애')

    vi.mocked(videoApi.get).mockRejectedValueOnce(new Error('상세 장애'))
    await store.fetchVideo(1)
    expect(store.currentVideo).toBeNull()
    expect(store.detailLoadError).toBe('상세 장애')

    vi.mocked(videoApi.feed).mockResolvedValue({
      items: [video(2)], platforms: ['YOUTUBE'], errors: [],
    } as never)
    await store.fetchFeed()
    expect(store.feedItems).toHaveLength(1)
    vi.mocked(videoApi.feed).mockRejectedValueOnce(new Error('피드 장애'))
    await store.fetchFeed()
    expect(store.feedItems).toHaveLength(1)
    expect(store.feedLoadError).toBe(true)
  })

  it('runs UGC campaign lifecycle against the active workspace', async () => {
    vi.mocked(ugcCampaignApi.list).mockResolvedValue({ items: [{ id: 4, name: '캠페인' }], totalElements: 21 } as never)
    const store = useUgcCampaignStore()
    await store.fetchCampaigns()
    expect(store.campaigns).toHaveLength(1)
    expect(store.totalPages).toBe(2)
    expect(ugcCampaignApi.list).toHaveBeenCalledWith(3, expect.objectContaining({ page: 0 }))

    const updated = {
      campaign: { id: 4, name: '수정된 캠페인', status: 'DRAFT' },
      playbook: null,
    } as any
    vi.mocked(ugcCampaignApi.update).mockResolvedValue(updated)
    await store.updateCampaign(4, { name: '수정된 캠페인' } as never)
    expect(store.current).toMatchObject(updated)
    vi.mocked(ugcCampaignApi.publish).mockResolvedValue({
      ...updated,
      campaign: { ...updated.campaign, status: 'ACTIVE' },
    })
    await store.publish(4)
    expect(store.current?.campaign.status).toBe('ACTIVE')

    vi.mocked(ugcCampaignApi.list).mockRejectedValueOnce(new Error('캠페인 서버 장애'))
    await expect(store.fetchCampaigns()).rejects.toThrow('캠페인 서버 장애')
    expect(store.campaigns).toHaveLength(1)
    expect(store.loadError).toBe('캠페인 서버 장애')
  })

  it('keeps Shorts prompt/template lists in sync after edits and deletion', async () => {
    const prompt = { id: 1, stage: 'HOOK', userPrompt: '후킹', revision: 1 } as any
    const template = { id: 2, name: '기본', isDefault: false } as any
    vi.mocked(ugcShortsPromptApi.list).mockResolvedValue([prompt])
    vi.mocked(ugcShortsTemplateApi.list).mockResolvedValue([template])
    const store = useUgcShortsStore()
    await store.fetchPrompts()
    await store.fetchTemplates()
    expect(store.prompts).toEqual([prompt])
    expect(store.templates).toEqual([template])

    vi.mocked(ugcShortsPromptApi.list).mockRejectedValueOnce(new Error('프롬프트 서버 장애'))
    await expect(store.fetchPrompts()).rejects.toThrow('프롬프트 서버 장애')
    expect(store.prompts).toEqual([prompt])
    expect(store.promptsLoadError).toBe('프롬프트 서버 장애')

    vi.mocked(ugcShortsTemplateApi.list).mockRejectedValueOnce(new Error('템플릿 서버 장애'))
    await expect(store.fetchTemplates()).rejects.toThrow('템플릿 서버 장애')
    expect(store.templates).toEqual([template])
    expect(store.templatesLoadError).toBe('템플릿 서버 장애')

    const revisedPrompt = { ...prompt, revision: 2 } as any
    vi.mocked(ugcShortsPromptApi.update).mockResolvedValue(revisedPrompt)
    await store.updatePrompt('HOOK', {} as never)
    expect(store.prompts[0]).toMatchObject(revisedPrompt)

    const newTemplate = { id: 3, name: '새 템플릿' } as never
    vi.mocked(ugcShortsTemplateApi.create).mockResolvedValue(newTemplate)
    await store.createTemplate({} as never)
    expect(store.templates.map((item) => item.id)).toEqual([2, 3])
    vi.mocked(ugcShortsTemplateApi.remove).mockResolvedValue(undefined)
    await store.deleteTemplate(2)
    expect(store.templates.map((item) => item.id)).toEqual([3])
  })

  it('restores prompt revisions and updates template reference assets through the server', async () => {
    const store = useUgcShortsStore()
    const prompt = { id: 1, stage: 'HOOK', revision: 3, userPrompt: '현재 프롬프트' } as any
    const restored = { ...prompt, revision: 2, userPrompt: '복원된 프롬프트' }
    const template = { id: 9, name: '세로 템플릿' } as any
    const updatedTemplate = { ...template, name: '수정된 템플릿' }
    const restoredDefault = { ...prompt, revision: 4, userPrompt: '기본 프롬프트' }

    vi.mocked(ugcShortsPromptApi.resetToDefault).mockResolvedValue(restoredDefault)
    vi.mocked(ugcShortsPromptApi.revisions).mockResolvedValue([
      { revision: 2, userPrompt: '복원된 프롬프트' },
    ] as never)
    vi.mocked(ugcShortsPromptApi.restoreRevision).mockResolvedValue(restored)
    vi.mocked(ugcShortsTemplateApi.update).mockResolvedValue(updatedTemplate)
    vi.mocked(ugcShortsTemplateApi.uploadReferenceImage).mockResolvedValue({
      ...updatedTemplate,
      referenceImageUrl: 'https://cdn.test/reference.png',
    })

    store.prompts = [prompt]
    store.templates = [template]
    await store.resetPrompt('HOOK')
    expect(store.prompts[0]).toMatchObject(restoredDefault)

    await store.fetchRevisions('HOOK')
    expect(store.revisions).toHaveLength(1)
    await store.restoreRevision('HOOK', 2)
    expect(store.prompts[0]).toMatchObject(restored)

    await store.updateTemplate(9, {} as never)
    expect(store.templates[0]).toMatchObject(updatedTemplate)
    await store.uploadReferenceImage(9, new File(['image'], 'reference.png', { type: 'image/png' }))
    expect(store.templates[0].referenceImageUrl).toBe('https://cdn.test/reference.png')
  })

  it('calculates credit balance, low-balance warning, and today usage from server data', async () => {
    vi.mocked(creditApi.getBalance).mockResolvedValue({ totalBalance: 20, freeMonthly: 100, purchasedBalance: 0 } as never)
    vi.mocked(creditApi.getTransactions).mockResolvedValue(page([
      { type: 'DEDUCT', amount: -20, createdAt: new Date().toISOString() },
      { type: 'CHARGE', amount: 100, createdAt: new Date().toISOString() },
    ]) as never)
    const store = useCreditStore()
    await store.fetchBalance()
    await store.fetchTransactions()
    expect(store.totalBalance).toBe(20)
    expect(store.isLow).toBe(true)
    expect(store.usedToday).toBe(20)
    expect(store.hasEnoughCredits(21)).toBe(false)
  })

  it('loads the dashboard fanout and retains confirmed panels when one API fails', async () => {
    const store = useDashboardStore()
    vi.mocked(analyticsApi.dashboard).mockResolvedValue({ totalViews: 100 } as never)
    vi.mocked(analyticsApi.trends).mockResolvedValue([{ date: '2026-08-01', views: 100 }] as never)
    vi.mocked(analyticsApi.platformComparison).mockRejectedValue(new Error('비교 장애'))
    vi.mocked(videoApi.list).mockResolvedValue(page([video(1)]) as never)
    vi.mocked(scheduleApi.list).mockResolvedValue([{ id: 1, scheduledAt: '2026-08-10T09:00' }] as never)
    vi.mocked(analyticsApi.topVideos).mockResolvedValue([])
    await store.fetchDashboard()

    expect(store.kpi).toMatchObject({ totalViews: 100 })
    expect(store.recentVideos).toHaveLength(1)
    expect(store.loadError).toContain('일부')
    expect(store.isLoadingDashboard).toBe(false)
  })

  it('maps inbox messages, filters them, and keeps local action state after API errors', async () => {
    vi.mocked(inboxApi.listMessages).mockResolvedValue({ messages: [
      {
        id: 1, messageType: 'COMMENT', platform: 'YOUTUBE', senderName: '시청자',
        senderAvatarUrl: null, content: '좋은 영상입니다', videoId: 7, isRead: false,
        isStarred: false, receivedAt: '2026-08-09T09:00:00Z', createdAt: null,
      },
      {
        id: 2, messageType: 'DM', platform: 'TIKTOK', senderName: '팬',
        senderAvatarUrl: null, content: '문의', videoId: null, isRead: true,
        isStarred: true, receivedAt: '2026-08-08T09:00:00Z', createdAt: null,
      },
    ] } as never)
    const store = useInboxStore()
    await store.initMessages()
    expect(store.unreadCount).toBe(1)
    store.setFilters({ platform: 'YOUTUBE', searchText: '좋은' })
    expect(store.filteredMessages.map((message) => message.id)).toEqual([1])
    vi.mocked(inboxApi.markAsRead).mockRejectedValueOnce(new Error('서버 장애'))
    await store.markAsRead(1)
    expect(store.unreadCount).toBe(0)
    store.toggleMessageSelection(1)
    store.toggleMessageSelection(1)
    expect(store.selectedMessageIds.size).toBe(0)
    expect(store.starredMessages.map((message) => message.id)).toEqual([2])
  })

  it('generates and clears creator metadata while exposing AI failures', async () => {
    const store = useAiStore()
    const meta = { platforms: [] } as never
    vi.mocked(aiApi.generateMeta).mockResolvedValue(meta)
    await store.generateMeta({} as never)
    expect(store.metaResult).toMatchObject(meta)
    vi.mocked(aiApi.generateHashtags).mockRejectedValueOnce(new Error('AI 사용량 초과'))
    await expect(store.generateHashtags({} as never)).rejects.toThrow('AI 사용량 초과')
    expect(store.error).toBe('AI 사용량 초과')
    store.clearResults()
    expect(store.metaResult).toBeNull()
    expect(store.error).toBeNull()
  })

  /**
   * 예전에는 이 테스트가 **부분 반영**을 고정했다(`platformRevenue` 실패해도 trends 는
   * 반영). 그 동작은 "라벨은 새 기간, 데이터는 이전 기간"을 만든다 — 기간 계약을
   * 고치면서 없앤 불일치가 부분 반영으로 되살아나는 경로다. 이제 스냅샷은 원자적이며,
   * 하나라도 실패하면 **이전 스냅샷 전체를 그대로 둔다.**
   */
  it('maps revenue trends into platform breakdown and keeps the last complete snapshot on failure', async () => {
    const store = useRevenueStore()
    // 실제 응답 형태를 쓴다. `platformBreakdown` 이 빠진 픽스처는 스토어가 그것을
    // 집계하다 예외를 내므로, 예전 테스트는 "부분 반영"이 아니라 **예외 경로**를
    // 통과하고 있었다.
    vi.mocked(revenueApi.summary).mockResolvedValue({
      totalRevenueKrw: 3000,
      growthPercent: 0,
      platformBreakdown: [
        { platform: 'YOUTUBE', revenueKrw: 1000 },
        { platform: 'TIKTOK', revenueKrw: 2000 },
      ],
    } as never)
    vi.mocked(revenueApi.trends).mockResolvedValue({ data: [
      { date: '2026-08-01', platform: 'YOUTUBE', revenueKrw: 1000 },
      { date: '2026-08-02', platform: 'TIKTOK', revenueKrw: 2000 },
    ] } as never)
    vi.mocked(revenueApi.platformRevenue).mockResolvedValue({ platforms: [] } as never)

    await store.fetchRevenue()

    expect(store.totalAnnualRevenue).toBe(3000)
    expect(store.platformBreakdown).toEqual(expect.arrayContaining([
      expect.objectContaining({ platform: 'YOUTUBE', revenue: 1000 }),
      expect.objectContaining({ platform: 'TIKTOK', revenue: 2000 }),
    ]))
    expect(store.loadError).toBe(false)

    // 한 호출만 실패해도 반쯤 갱신하지 않는다. 직전 완전한 스냅샷이 그대로 남는다.
    vi.mocked(revenueApi.platformRevenue).mockRejectedValueOnce(new Error('수익 API 장애'))

    await store.fetchRevenue()

    expect(store.loadError).toBe(true)
    expect(store.totalAnnualRevenue).toBe(3000)
  })

  /**
   * 예전에는 0 기준선에서 성장률이 `0` 이라고 단언했다. `Infinity`/`NaN` 을 막는다는
   * 의도는 옳았지만, `0` 은 **"변화 없음"이라는 측정한 적 없는 사실**을 주장한다.
   * 이제 비교 불가는 `null` 이며, 그것이 Infinity/NaN 을 막는 더 정직한 방법이다.
   */
  it('does not expose Infinity or NaN when revenue grows from a zero baseline', async () => {
    const store = useRevenueStore()
    vi.mocked(revenueApi.summary).mockResolvedValue({
      totalRevenueKrw: 100,
      // 이전 기간 수익이 0 이면 서버도 null 을 준다(RevenueUseCase).
      growthPercent: null,
      platformBreakdown: [{ platform: 'YOUTUBE', revenueKrw: 100 }],
    } as never)
    vi.mocked(revenueApi.trends).mockResolvedValue({ data: [
      { date: '2026-08-01', platform: 'YOUTUBE', revenueKrw: 0 },
      { date: '2026-08-02', platform: 'YOUTUBE', revenueKrw: 100 },
    ] } as never)
    vi.mocked(revenueApi.platformRevenue).mockResolvedValue({ platforms: [] } as never)

    await store.fetchRevenue()

    expect(store.summary.monthlyGrowth).toBeNull()
    expect(Number.isFinite(store.summary.monthlyGrowth as number)).toBe(false)
    // 첫 행은 비교할 앞 행이 없고, 둘째 행은 기준선이 0 이다. 둘 다 비교 불가다.
    // 예전 값 `0` 은 첫 수익 발생을 "0% 변화"로 위장했다.
    expect(store.growthTrend).toEqual([
      { period: '2026-08-01', growth: null },
      { period: '2026-08-02', growth: null },
    ])
  })

  it('keeps template filtering and server-backed create/update/use/delete behavior', async () => {
    vi.mocked(templatesApi.list).mockResolvedValue({ templates: [
      { id: 1, name: '유튜브 교육', category: 'full', platform: 'YOUTUBE', tags: ['교육'], usageCount: 2, createdAt: '2026-08-01', updatedAt: '2026-08-02' },
    ] } as never)
    const store = useTemplatesStore()
    await store.loadTemplates()
    store.searchText = '교육'
    expect(store.filteredTemplates).toHaveLength(1)
    vi.mocked(templatesApi.use).mockResolvedValue({} as never)
    await store.applyTemplate(1)
    expect(store.templates[0].usageCount).toBe(3)
    vi.mocked(templatesApi.delete).mockResolvedValue(undefined)
    await store.deleteTemplate(1)
    expect(store.templates).toHaveLength(0)
  })

})
