import { beforeEach, describe, expect, it, vi } from 'vitest'
import apiClient from './client'
import { aiApi } from './ai'
import { analyticsApi } from './analytics'
import { ugcCampaignApi } from './ugcCampaign'
import { ugcParticipationApi } from './ugcParticipation'
import { ugcPublishingApi } from './ugcPublishing'
import { ugcRewardApi } from './ugcReward'
import { ugcShortsPipelineApi } from './ugcShortsPipeline'
import { ugcShortsPromptApi } from './ugcShortsPrompt'
import { ugcShortsSheetApi } from './ugcShortsSheet'
import { ugcShortsTemplateApi } from './ugcShortsTemplate'
import { ugcSubmissionApi } from './ugcSubmission'

const response = (data: unknown = {}) => ({ data: { success: true, data } })
const anyRequest = {} as never
const file = new File(['content'], 'reference.png', { type: 'image/png' })

describe('creator and UGC server contracts', () => {
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

  it('keeps every AI metadata and creator-assistance action server-backed', async () => {
    await aiApi.generateHashtags(anyRequest)
    await aiApi.stt(anyRequest)
    await aiApi.analyzeScript(anyRequest)
    await aiApi.generateReply(anyRequest)
    await aiApi.suggestSchedule(anyRequest)
    await aiApi.generateReport(anyRequest)
    await aiApi.startPipeline(anyRequest)
    await aiApi.getLatestWeeklyDigest()
    await aiApi.listWeeklyDigests()
    await aiApi.contentGapAnalysis(anyRequest)
    await aiApi.startBatch(anyRequest)
    await aiApi.getBatchStatus('batch-1')
    await aiApi.strategyCoach(anyRequest)
    await aiApi.revenueReport(anyRequest)
    await aiApi.demoGenerate('education')

    expect(post).toHaveBeenCalledWith('/ai/generate-hashtags', anyRequest)
    expect(post).toHaveBeenCalledWith('/ai/stt', anyRequest)
    expect(post).toHaveBeenCalledWith('/ai/analyze-script', anyRequest)
    expect(post).toHaveBeenCalledWith('/ai/batch', anyRequest)
    expect(get).toHaveBeenCalledWith('/ai/weekly-digests', { params: { page: 0, size: 10 } })
    expect(get).toHaveBeenCalledWith('/ai/batch/batch-1')
  })

  it('covers the analytics panels used for creator decisions', async () => {
    get.mockResolvedValue(response({ platforms: [], data: {}, videos: [], tags: [] }) as never)
    await analyticsApi.dashboard()
    await analyticsApi.trends()
    await analyticsApi.platformComparison()
    await analyticsApi.videoAnalytics(7)
    await analyticsApi.heatmap()
    await analyticsApi.topVideos('30d', 5)
    await analyticsApi.videoCompare([7, 8])
    await analyticsApi.performanceScore(7)
    await analyticsApi.anomalies()
    await analyticsApi.cohortAnalysis('CATEGORY', '2026-01-01', '2026-01-31')
    await analyticsApi.retentionCurve(7)
    await analyticsApi.tagPerformance()
    await analyticsApi.trafficSources(14)
    await analyticsApi.demographics(14)
    await analyticsApi.ctr(14)
    await analyticsApi.avgViewDuration(14)
    await analyticsApi.subscriberConversion(14)
    await analyticsApi.crossPlatformComparison(14)

    expect(get).toHaveBeenCalledWith('/analytics/dashboard', { params: { days: 7 } })
    expect(get).toHaveBeenCalledWith('/analytics/videos/7')
    expect(get).toHaveBeenCalledWith('/analytics/compare', { params: { videoIds: '7,8', days: 30 } })
  })

  it('keeps the campaign lifecycle, playbook, and submission review on the API', async () => {
    await ugcCampaignApi.list(3, { status: 'DRAFT', page: 0, size: 20 })
    await ugcCampaignApi.get(3, 4)
    await ugcCampaignApi.pause(3, 4)
    await ugcCampaignApi.complete(3, 4)
    await ugcCampaignApi.upsertPlaybook(3, 4, anyRequest)

    await ugcParticipationApi.createInvite(3, 4, anyRequest)
    await ugcParticipationApi.listApplications(3, 4, { status: 'PENDING', page: 0, size: 20 })
    await ugcParticipationApi.accept(3, 8)
    await ugcParticipationApi.reject(3, 9)
    await ugcParticipationApi.viewInvite('invite-token')
    await ugcParticipationApi.apply('invite-token', anyRequest)
    await ugcParticipationApi.myApplications()

    await ugcSubmissionApi.saveDraft(4, anyRequest)
    await ugcSubmissionApi.listMine(4)
    await ugcSubmissionApi.submit(8)
    await ugcSubmissionApi.list(3, 4, { status: 'SUBMITTED', page: 0, size: 20 })
    await ugcSubmissionApi.detail(3, 8)
    await ugcSubmissionApi.requestChanges(3, 8, anyRequest)
    await ugcSubmissionApi.approve(3, 8, anyRequest)

    expect(get).toHaveBeenCalledWith('/workspaces/3/ugc/campaigns', { params: { status: 'DRAFT', page: 0, size: 20 } })
    expect(post).toHaveBeenCalledWith('/workspaces/3/ugc/campaigns/4/pause')
    expect(post).toHaveBeenCalledWith('/ugc/invites/invite-token/applications', anyRequest)
    expect(post).toHaveBeenCalledWith('/workspaces/3/ugc/submissions/8/approve', anyRequest)
  })

  it('keeps UGC publishing, rewards, and audit data linked to the workspace', async () => {
    await ugcPublishingApi.listCampaignPosts(3, 4)
    await ugcPublishingApi.myPosts(8)

    await ugcRewardApi.getAnalytics(3, 4)
    await ugcRewardApi.recordMetric(3, 8, { views: 100, likes: 10, comments: 2, shares: 1 })
    await ugcRewardApi.listParticipants(3, 4)
    await ugcRewardApi.updateReward(3, 8, anyRequest)
    await ugcRewardApi.confirmReward(3, 8)
    await ugcRewardApi.markPaid(3, 8)
    await ugcRewardApi.downloadCsv(3, 4)
    await ugcRewardApi.listAuditEvents(3, 4)

    expect(get).toHaveBeenCalledWith('/workspaces/3/ugc/campaigns/4/posts')
    expect(post).toHaveBeenCalledWith('/workspaces/3/ugc/campaign-posts/8/metrics', {
      views: 100,
      likes: 10,
      comments: 2,
      shares: 1,
    })
    expect(get).toHaveBeenCalledWith('/workspaces/3/ugc/campaigns/4/rewards.csv', { responseType: 'blob' })
  })

  it('covers Shorts run stages, render handoff, prompts, sheets, and templates', async () => {
    await ugcShortsPipelineApi.list(3, 0, 20)
    await ugcShortsPipelineApi.get(3, 9)
    await ugcShortsPipelineApi.rerunStage(3, 9, 'TRANSCRIBE')
    await ugcShortsPipelineApi.downloadRenderSpec(3, 9, 1)
    await ugcShortsPipelineApi.downloadRenderBundle(3, 9)
    await ugcShortsPipelineApi.attachRenderedVideo(3, 9, 1, 77)
    await ugcShortsPipelineApi.remove(3, 9)
    await ugcShortsPipelineApi.getRenderAvailability()
    await ugcShortsPipelineApi.getRenderStatus(3, 9, 1)

    await ugcShortsPromptApi.list(3)
    await ugcShortsPromptApi.get(3, 'HOOK')
    await ugcShortsPromptApi.update(3, 'HOOK', anyRequest)
    await ugcShortsPromptApi.resetToDefault(3, 'HOOK')
    await ugcShortsPromptApi.revisions(3, 'HOOK')
    await ugcShortsPromptApi.restoreRevision(3, 'HOOK', 2)

    await ugcShortsSheetApi.downloadSheet(3, 9)
    await ugcShortsSheetApi.previewSheet(3, 9, file)
    await ugcShortsSheetApi.applySheet(3, 9, file)

    await ugcShortsTemplateApi.list(3)
    await ugcShortsTemplateApi.get(3, 2)
    await ugcShortsTemplateApi.create(3, anyRequest)
    await ugcShortsTemplateApi.update(3, 2, anyRequest)
    await ugcShortsTemplateApi.remove(3, 2)
    await ugcShortsTemplateApi.uploadReferenceImage(3, 2, file)

    expect(get).toHaveBeenCalledWith('/workspaces/3/ugc/shorts/runs/9/render-bundle', { responseType: 'blob' })
    expect(post).toHaveBeenCalledWith('/workspaces/3/ugc/shorts/runs/9/clips/1/rendered-video', { videoId: 77 })
    expect(post).toHaveBeenCalledWith('/workspaces/3/ugc/shorts/prompts/HOOK/revisions/2/restore')
  })
})
