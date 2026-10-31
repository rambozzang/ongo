import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { AiEstimate } from '@/types/aiEstimate'

/**
 * SEO 점검 결과. 백엔드 `VideoSeoScoreResponse` 와 필드명이 같다.
 *
 * 점수는 **규칙 점수**(실측)다 — 항목(`checks`)마다 통과 여부와 실제 값이 있다. AI 가 만든 것은
 * `suggestions`·`relatedKeywords` 뿐이고 `aiEstimate` 로 표시한다.
 */
export interface SeoCheck {
  key: string
  label: string
  passed: boolean
  points: number
  maxPoints: number
  /** 실제 값(글자 수·개수 등) */
  detail: string
}

export interface SeoScoreCategory {
  key: string
  name: string
  score: number
  maxScore: number
  checks: SeoCheck[]
}

export interface SeoScoreResponse {
  videoId: number
  overallScore: number
  /** 점수가 무엇으로 매겨졌는지(규칙 점수, AI 가 매기지 않음) */
  scoreBasis: string
  categories: SeoScoreCategory[]
  suggestions: string[]
  relatedKeywords: string[]
  aiEstimate: AiEstimate
  creditsUsed: number
}

export const videoSeoApi = {
  analyze(videoId: number) {
    return apiClient
      .post<ResData<SeoScoreResponse>>(`/videos/${videoId}/seo-score`)
      .then(unwrapResponse)
  },
}
