import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  GenerateMetaRequest,
  GenerateMetaResponse,
  GenerateHashtagsRequest,
  GenerateHashtagsResponse,
  SttRequest,
  SttResponse,
  AnalyzeScriptRequest,
  AnalyzeScriptResponse,
  GenerateReplyRequest,
  GenerateReplyResponse,
  SuggestScheduleRequest,
  SuggestScheduleResponse,
  GenerateIdeasRequest,
  GenerateIdeasResponse,
  GenerateReportRequest,
  GenerateReportResponse,
  AiPipelineRequest,
  AiPipelineResponse,
  AiBatchRequest,
  AiBatchResponse,
  WeeklyDigestResponse,
  ContentGapRequest,
  ContentGapResponse,
} from '@/types/ai'

export const aiApi = {
  generateMeta(request: GenerateMetaRequest) {
    return apiClient
      .post<ResData<GenerateMetaResponse>>('/ai/generate-meta', request)
      .then(unwrapResponse)
  },

  generateHashtags(request: GenerateHashtagsRequest) {
    return apiClient
      .post<ResData<GenerateHashtagsResponse>>('/ai/generate-hashtags', request)
      .then(unwrapResponse)
  },

  stt(request: SttRequest) {
    return apiClient.post<ResData<SttResponse>>('/ai/stt', request).then(unwrapResponse)
  },

  analyzeScript(request: AnalyzeScriptRequest) {
    return apiClient
      .post<ResData<AnalyzeScriptResponse>>('/ai/analyze-script', request)
      .then(unwrapResponse)
  },

  generateReply(request: GenerateReplyRequest) {
    return apiClient
      .post<ResData<GenerateReplyResponse>>('/ai/generate-reply', request)
      .then(unwrapResponse)
  },

  suggestSchedule(request: SuggestScheduleRequest) {
    return apiClient
      .post<ResData<SuggestScheduleResponse>>('/ai/suggest-schedule', request)
      .then(unwrapResponse)
  },

  generateIdeas(request: GenerateIdeasRequest) {
    return apiClient
      .post<ResData<GenerateIdeasResponse>>('/ai/generate-ideas', request)
      .then(unwrapResponse)
  },

  generateReport(request: GenerateReportRequest) {
    return apiClient
      .post<ResData<GenerateReportResponse>>('/ai/generate-report', request)
      .then(unwrapResponse)
  },

  startPipeline(request: AiPipelineRequest) {
    return apiClient
      .post<ResData<AiPipelineResponse>>('/ai/pipeline', request)
      .then(unwrapResponse)
  },

  getPipelineStatus(pipelineId: string) {
    return apiClient
      .get<ResData<AiPipelineResponse>>(`/ai/pipeline/${pipelineId}`)
      .then(unwrapResponse)
  },

  cancelPipeline(pipelineId: string) {
    return apiClient
      .delete<ResData<AiPipelineResponse>>(`/ai/pipeline/${pipelineId}`)
      .then(unwrapResponse)
  },

  getLatestWeeklyDigest() {
    return apiClient
      .get<ResData<WeeklyDigestResponse>>('/ai/weekly-digest')
      .then(unwrapResponse)
  },

  listWeeklyDigests(page = 0, size = 10) {
    return apiClient
      .get<ResData<WeeklyDigestResponse[]>>('/ai/weekly-digests', { params: { page, size } })
      .then(unwrapResponse)
  },

  contentGapAnalysis(request: ContentGapRequest) {
    return apiClient
      .post<ResData<ContentGapResponse>>('/ai/content-gap-analysis', request)
      .then(unwrapResponse)
  },

  startBatch(request: AiBatchRequest) {
    return apiClient
      .post<ResData<AiBatchResponse>>('/ai/batch', request)
      .then(unwrapResponse)
  },

  getBatchStatus(batchId: string) {
    return apiClient
      .get<ResData<AiBatchResponse>>(`/ai/batch/${batchId}`)
      .then(unwrapResponse)
  },
}
