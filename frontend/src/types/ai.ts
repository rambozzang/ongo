import type { Platform } from './channel'

export type AiTone = 'FRIENDLY' | 'PROFESSIONAL' | 'HUMOROUS'

export interface GenerateMetaRequest {
  script?: string
  videoId?: number
  useStt: boolean
  platforms: Platform[]
  tone: AiTone
  category: string
}

export interface GenerateMetaResponse {
  platformResults: PlatformMetaResult[]
  hashtags: string[]
  creditsUsed: number
  creditsRemaining: number
}

export interface PlatformMetaResult {
  platform: Platform
  titleCandidates: string[]
  description: string
  tags: string[]
}

export interface GenerateHashtagsRequest {
  title: string
  category: string
  platforms: Platform[]
}

export interface GenerateHashtagsResponse {
  platformHashtags: Record<string, string[]>
  creditsUsed: number
  creditsRemaining: number
}

export interface SttRequest {
  videoId: number
}

export interface SttResponse {
  transcript: string
  segments: SttSegment[]
  creditsUsed: number
  creditsRemaining: number
}

export interface SttSegment {
  startTime: number
  endTime: number
  text: string
}

export interface AnalyzeScriptRequest {
  script: string
}

export interface AnalyzeScriptResponse {
  keywords: string[]
  targetAudience: string
  suggestedCategory: string
  summary: string
  creditsUsed: number
  creditsRemaining: number
}

export interface GenerateReplyRequest {
  commentId: number
  commentContent: string
  channelTone: AiTone
}

export interface GenerateReplyResponse {
  replies: { tone: AiTone; content: string }[]
  creditsUsed: number
  creditsRemaining: number
}

export interface SuggestScheduleRequest {
  channelId: number
}

export interface SuggestScheduleResponse {
  suggestions: ScheduleSuggestion[]
  creditsUsed: number
  creditsRemaining: number
}

export interface ScheduleSuggestion {
  dayOfWeek: string
  time: string
  reason: string
  expectedImprovement: number
}

export interface GenerateIdeasRequest {
  category: string
  recentVideoIds?: number[]
}

export interface GenerateIdeasResponse {
  ideas: ContentIdea[]
  creditsUsed: number
  creditsRemaining: number
}

export interface ContentIdea {
  title: string
  description: string
  expectedReaction: string
  trendScore: number
}

export interface GenerateReportRequest {
  period: '7d' | '30d'
}

export interface GenerateReportResponse {
  reportMarkdown: string
  creditsUsed: number
  creditsRemaining: number
}

// AI Pipeline types
export type PipelineStepType = 'STT' | 'ANALYZE_SCRIPT' | 'GENERATE_META' | 'GENERATE_HASHTAGS' | 'SUGGEST_SCHEDULE'
export type PipelineStepStatusType = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED'
export type PipelineStatusType = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

export interface PipelineStepInfo {
  key: PipelineStepType
  displayName: string
  creditCost: number
  description: string
}

export const PIPELINE_STEPS: PipelineStepInfo[] = [
  { key: 'STT', displayName: '음성 텍스트 변환', creditCost: 10, description: '영상의 음성을 텍스트로 변환합니다' },
  { key: 'ANALYZE_SCRIPT', displayName: '대본 분석', creditCost: 5, description: '키워드, 타겟 오디언스, 카테고리를 분석합니다' },
  { key: 'GENERATE_META', displayName: '메타데이터 생성', creditCost: 5, description: '제목과 설명을 자동 생성합니다' },
  { key: 'GENERATE_HASHTAGS', displayName: '해시태그 생성', creditCost: 3, description: '최적의 해시태그를 추천합니다' },
  { key: 'SUGGEST_SCHEDULE', displayName: '업로드 시간 추천', creditCost: 3, description: '최적의 업로드 시간을 추천합니다' },
]

export interface AiPipelineRequest {
  videoId: number
  steps: string[]
  channelId?: number
}

export interface AiPipelineResponse {
  pipelineId: string
  videoId: number
  steps: PipelineStepStatusDto[]
  totalCredits: number
  discountApplied: boolean
  status: PipelineStatusType
  results: Record<string, unknown>
}

export interface PipelineStepStatusDto {
  step: string
  displayName: string
  creditCost: number
  status: PipelineStepStatusType
  result?: unknown
  error?: string
}

// AI Batch Processing types
export type AiBatchOperation = 'GENERATE_META' | 'GENERATE_HASHTAGS' | 'STT' | 'ALL'
export type BatchStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'PARTIALLY_FAILED'
export type BatchItemStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface AiBatchRequest {
  videoIds: number[]
  operation: AiBatchOperation
  platform?: string
}

export interface AiBatchResponse {
  batchId: string
  totalItems: number
  status: BatchStatus
  items: AiBatchItemStatus[]
}

export interface AiBatchItemStatus {
  videoId: number
  videoTitle: string | null
  status: BatchItemStatus
  result?: unknown
  error: string | null
}

export const BATCH_OPERATIONS: { key: AiBatchOperation; label: string; creditCost: number }[] = [
  { key: 'GENERATE_META', label: '메타데이터 생성', creditCost: 5 },
  { key: 'GENERATE_HASHTAGS', label: '해시태그 생성', creditCost: 3 },
  { key: 'STT', label: '음성 텍스트 변환', creditCost: 10 },
  { key: 'ALL', label: '전체 (메타 + 해시태그 + STT)', creditCost: 18 },
]

// Weekly Digest types
export interface WeeklyDigestResponse {
  id: number
  weekRange: string
  summary: string
  topVideos: string[]
  anomalies: string[]
  actionItems: string[]
  generatedAt: string
}

// Content Gap Analysis types
export interface ContentGapRequest {
  channelIds?: number[]
  includeCompetitors: boolean
}

export interface ContentGapResponse {
  opportunities: ContentOpportunity[]
  oversaturated: OversaturatedTopic[]
  analyzedAt: string
}

export interface ContentOpportunity {
  topic: string
  estimatedDemand: 'HIGH' | 'MEDIUM' | 'LOW'
  competitionLevel: string
  suggestedAngle: string
  relevanceScore: number
}

export interface OversaturatedTopic {
  topic: string
  saturationLevel: 'HIGH' | 'MEDIUM' | 'LOW'
  recommendation: string
}
