import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from './client'
import { videoApi } from './video'
import { scheduleApi } from './schedule'
import { scheduleOptimizerApi } from './scheduleOptimizer'
import { capabilitiesApi } from './capabilities'
import { analyticsApi } from './analytics'

const response = (data: unknown = {}) => ({ data: { success: true, data } })

describe('publish core API contracts', () => {
  const get = vi.spyOn(apiClient, 'get')
  const post = vi.spyOn(apiClient, 'post')
  const put = vi.spyOn(apiClient, 'put')
  const del = vi.spyOn(apiClient, 'delete')

  beforeEach(() => {
    vi.clearAllMocks()
    get.mockResolvedValue(response() as never)
    post.mockResolvedValue(response() as never)
    put.mockResolvedValue(response() as never)
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
})
