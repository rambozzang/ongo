import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from './client'
import { videoApi } from './video'
import { scheduleApi } from './schedule'
import { scheduleOptimizerApi } from './scheduleOptimizer'
import { capabilitiesApi } from './capabilities'
import { analyticsApi } from './analytics'
import { aiApi } from './ai'
import { channelApi } from './channel'
import { ugcCampaignApi } from './ugcCampaign'
import { ugcPublishingApi } from './ugcPublishing'
import { ugcShortsPipelineApi } from './ugcShortsPipeline'
import { settingsApi } from './settings'

const response = (data: unknown = {}) => ({ data: { success: true, data } })

describe('publish core API contracts', () => {
  const get = vi.spyOn(apiClient, 'get')
  const post = vi.spyOn(apiClient, 'post')
  const put = vi.spyOn(apiClient, 'put')
  const patch = vi.spyOn(apiClient, 'patch')
  const del = vi.spyOn(apiClient, 'delete')

  beforeEach(() => {
    vi.clearAllMocks()
    get.mockResolvedValue(response() as never)
    post.mockResolvedValue(response() as never)
    put.mockResolvedValue(response() as never)
    patch.mockResolvedValue(response() as never)
    del.mockResolvedValue(response() as never)
  })

  it('loads channel capabilities and URL-import availability', async () => {
    await videoApi.getUploadCapabilities()
    expect(get).toHaveBeenCalledWith('/videos/stream-publish/capabilities')

    await videoApi.getImportAvailability()
    expect(get).toHaveBeenCalledWith('/videos/import-url/availability')
    await capabilitiesApi.list()
    expect(get).toHaveBeenCalledWith('/capabilities')
  })

  it('keeps automation API key lifecycle server-backed and never invents a local token', async () => {
    await settingsApi.listApiKeys()
    await settingsApi.createApiKey({ name: 'content automation', expiresAt: '2026-12-31T23:59' })
    await settingsApi.revokeApiKey(12)

    expect(get).toHaveBeenCalledWith('/settings/api-keys')
    expect(post).toHaveBeenCalledWith('/settings/api-keys', {
      name: 'content automation',
      expiresAt: '2026-12-31T23:59',
    })
    expect(del).toHaveBeenCalledWith('/settings/api-keys/12')
  })

  it('creates, updates, publishes, retries, rechecks, and completes a video', async () => {
    const createRequest = { title: 'title', tags: ['one'], visibility: 'PUBLIC' as const, mediaType: 'VIDEO' as const }
    await videoApi.create(createRequest)
    expect(post).toHaveBeenCalledWith('/videos', createRequest)

    await videoApi.update(7, { title: 'updated' })
    expect(put).toHaveBeenCalledWith('/videos/7', { title: 'updated' })

    const publishRequest = { platforms: [{ platform: 'YOUTUBE' as const, title: 'title', description: '', tags: [], visibility: 'PUBLIC' as const }] }
    await videoApi.publish(7, publishRequest)
    expect(post).toHaveBeenCalledWith('/videos/7/publish', publishRequest)

    await videoApi.retry(7, 'YOUTUBE')
    await videoApi.recheck(7, 'YOUTUBE')
    await videoApi.confirmUpload(7)
    expect(post).toHaveBeenCalledWith('/videos/7/retry/YOUTUBE')
    expect(post).toHaveBeenCalledWith('/videos/7/recheck/YOUTUBE')
    expect(post).toHaveBeenCalledWith('/videos/7/upload/complete')
  })

  it('keeps list, detail, feed, and translation paths server-backed', async () => {
    await videoApi.list({ page: 0, size: 20, sort: 'createdAt,desc' })
    expect(get).toHaveBeenCalledWith('/videos', { params: { page: 0, size: 20, sort: 'createdAt,desc' } })
    await videoApi.get(8)
    await videoApi.feed({ platform: 'YOUTUBE', page: 1, size: 10 })
    await videoApi.getTranslations(8)
    await videoApi.requestTranslation(8, ['en'])
    await videoApi.updateTranslation(8, 2, { title: 'English title' })
    await videoApi.deleteTranslation(8, 2)
    expect(get).toHaveBeenCalledWith('/videos/8')
    expect(get).toHaveBeenCalledWith('/videos/feed', { params: { platform: 'YOUTUBE', page: 1, size: 10 } })
    expect(post).toHaveBeenCalledWith('/videos/8/translations', { languages: ['en'] })
    expect(put).toHaveBeenCalledWith('/videos/8/translations/2', { title: 'English title' })
    expect(del).toHaveBeenCalledWith('/videos/8/translations/2')
  })

  it('maps schedule ranges to inclusive LocalDateTime and persists actions', async () => {
    await scheduleApi.list({ startDate: '2026-08-10', endDate: '2026-08-16' })
    expect(get).toHaveBeenCalledWith('/schedules', {
      params: { from: '2026-08-10T00:00:00', to: '2026-08-16T23:59:59', status: undefined },
    })
    await scheduleApi.get(3)
    await scheduleApi.create({ videoId: 7, scheduledAt: '2026-08-10T09:00', platforms: [] })
    await scheduleApi.update(3, { scheduledAt: '2026-08-11T09:00' })
    await scheduleApi.cancel(3)
    expect(put).toHaveBeenCalledWith('/schedules/3', { scheduledAt: '2026-08-11T09:00' })
    expect(del).toHaveBeenCalledWith('/schedules/3')
  })

  it('uses the schedule optimizer endpoints without local recommendations', async () => {
    await scheduleOptimizerApi.generateSlots('YOUTUBE')
    await scheduleOptimizerApi.getSlots('YOUTUBE')
    await scheduleOptimizerApi.getRecommendations()
    await scheduleOptimizerApi.applyRecommendation(11)
    await scheduleOptimizerApi.getSummary()
    expect(post).toHaveBeenCalledWith('/schedule-optimizer/generate', null, { params: { platform: 'YOUTUBE' } })
    expect(get).toHaveBeenCalledWith('/schedule-optimizer/slots?platform=YOUTUBE')
    expect(get).toHaveBeenCalledWith('/schedule-optimizer/recommendations')
    expect(post).toHaveBeenCalledWith('/schedule-optimizer/recommendations/11/apply')
    expect(get).toHaveBeenCalledWith('/schedule-optimizer/summary')
  })

  it('loads server analytics used by the Compose best-time mode', async () => {
    await analyticsApi.getOptimalTimes('YOUTUBE')
    expect(get).toHaveBeenCalledWith('/analytics/optimal-times', { params: { platform: 'YOUTUBE' } })
  })

  it('keeps channel, AI, UGC campaign, and Shorts pipeline flows on the server', async () => {
    await channelApi.list()
    await channelApi.connect('YOUTUBE', { code: 'code', redirectUri: 'http://localhost/callback' } as never)
    await channelApi.sync(7)
    await channelApi.disconnect(7)
    expect(get).toHaveBeenCalledWith('/channels')
    expect(post).toHaveBeenCalledWith('/channels/connect/youtube', { code: 'code', redirectUri: 'http://localhost/callback' })
    expect(post).toHaveBeenCalledWith('/channels/7/sync')
    expect(del).toHaveBeenCalledWith('/channels/7')

    await aiApi.generateMeta({ script: 'script', platforms: ['YOUTUBE'] } as never)
    await aiApi.getPipelineStatus('pipeline-1')
    await aiApi.cancelPipeline('pipeline-1')
    expect(post).toHaveBeenCalledWith('/ai/generate-meta', { script: 'script', platforms: ['YOUTUBE'] })
    expect(get).toHaveBeenCalledWith('/ai/pipeline/pipeline-1')
    expect(del).toHaveBeenCalledWith('/ai/pipeline/pipeline-1')

    await ugcCampaignApi.create(3, { name: '캠페인' })
    await ugcCampaignApi.update(3, 4, { description: '수정' })
    await ugcCampaignApi.publish(3, 4)
    expect(post).toHaveBeenCalledWith('/workspaces/3/ugc/campaigns', { name: '캠페인' })
    expect(patch).toHaveBeenCalledWith('/workspaces/3/ugc/campaigns/4', { description: '수정' })
    expect(post).toHaveBeenCalledWith('/workspaces/3/ugc/campaigns/4/publish')

    await ugcPublishingApi.publish(3, 8, { platforms: ['YOUTUBE'] })
    await ugcPublishingApi.registerExternal(8, { platform: 'YOUTUBE', externalPostUrl: 'https://example.com/post' })
    expect(post).toHaveBeenCalledWith('/workspaces/3/ugc/submissions/8/publish', { platforms: ['YOUTUBE'] })
    expect(post).toHaveBeenCalledWith('/ugc/me/submissions/8/external-posts', {
      platform: 'YOUTUBE',
      externalPostUrl: 'https://example.com/post',
    })

    await ugcShortsPipelineApi.create(3, { sourceVideoId: 12 })
    await ugcShortsPipelineApi.selectHooks(3, 9, { selections: [], discardClipIds: [] })
    await ugcShortsPipelineApi.confirmSchedule(3, 9, { startAt: '2026-08-10T09:00', intervalHours: 24, platforms: ['YOUTUBE'] })
    await ugcShortsPipelineApi.startRender(3, 9, 1)
    expect(post).toHaveBeenCalledWith('/workspaces/3/ugc/shorts/runs', { sourceVideoId: 12 })
    expect(post).toHaveBeenCalledWith('/workspaces/3/ugc/shorts/runs/9/hooks', { selections: [], discardClipIds: [] })
    expect(post).toHaveBeenCalledWith('/workspaces/3/ugc/shorts/runs/9/schedule', {
      startAt: '2026-08-10T09:00',
      intervalHours: 24,
      platforms: ['YOUTUBE'],
    })
    expect(post).toHaveBeenCalledWith('/workspaces/3/ugc/shorts/runs/9/clips/1/render', {})
  })
})
