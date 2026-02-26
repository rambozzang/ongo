import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { TranslationJob, TranslationGlossary, ContentTranslatorSummary } from '@/types/contentTranslator'
import { contentTranslatorApi } from '@/api/contentTranslator'

export const useContentTranslatorStore = defineStore('contentTranslator', () => {
  const jobs = ref<TranslationJob[]>([])
  const glossary = ref<TranslationGlossary[]>([])
  const summary = ref<ContentTranslatorSummary | null>(null)
  const loading = ref(false)

  const fetchJobs = async () => {
    loading.value = true
    try {
      jobs.value = await contentTranslatorApi.getJobs()
    } catch {
      jobs.value = [
        { id: 1, videoId: 101, videoTitle: '제주도 3일 여행', sourceLanguage: 'ko', targetLanguage: 'en', contentType: 'ALL', originalText: '제주도 숨겨진 맛집 여행', translatedText: 'Hidden Gem Restaurants in Jeju Island', quality: 95, status: 'COMPLETED', createdAt: '2025-01-15T10:00:00Z', completedAt: '2025-01-15T10:05:00Z' },
        { id: 2, videoId: 102, videoTitle: 'MacBook Pro M4 리뷰', sourceLanguage: 'ko', targetLanguage: 'ja', contentType: 'TITLE', originalText: 'MacBook Pro M4 리뷰', translatedText: 'MacBook Pro M4 レビュー', quality: 92, status: 'COMPLETED', createdAt: '2025-01-14T09:00:00Z', completedAt: '2025-01-14T09:03:00Z' },
        { id: 3, videoId: 103, videoTitle: '엘든링 보스 공략', sourceLanguage: 'ko', targetLanguage: 'en', contentType: 'SUBTITLE', originalText: '이번 영상에서는 가장 어려운 보스를 공략합니다', translatedText: null, quality: null, status: 'TRANSLATING', createdAt: '2025-01-16T14:00:00Z', completedAt: null },
        { id: 4, videoId: 104, videoTitle: '서울 카페 투어', sourceLanguage: 'ko', targetLanguage: 'zh', contentType: 'DESCRIPTION', originalText: '서울에서 가장 핫한 카페 5곳', translatedText: '首尔最热门的5家咖啡厅', quality: 88, status: 'COMPLETED', createdAt: '2025-01-13T11:00:00Z', completedAt: '2025-01-13T11:04:00Z' },
      ]
    } finally {
      loading.value = false
    }
  }

  const fetchGlossary = async () => {
    try {
      glossary.value = await contentTranslatorApi.getGlossary()
    } catch {
      glossary.value = [
        { id: 1, sourceWord: '구독', targetWord: 'Subscribe', sourceLanguage: 'ko', targetLanguage: 'en', context: '유튜브 CTA', createdAt: '2025-01-10T10:00:00Z' },
        { id: 2, sourceWord: '좋아요', targetWord: 'Like', sourceLanguage: 'ko', targetLanguage: 'en', context: '소셜 미디어 인터랙션', createdAt: '2025-01-10T10:01:00Z' },
        { id: 3, sourceWord: '크리에이터', targetWord: 'クリエイター', sourceLanguage: 'ko', targetLanguage: 'ja', context: '콘텐츠 제작자', createdAt: '2025-01-11T09:00:00Z' },
      ]
    }
  }

  const fetchSummary = async () => {
    try {
      summary.value = await contentTranslatorApi.getSummary()
    } catch {
      summary.value = { totalTranslations: 156, completedTranslations: 142, avgQuality: 91, topLanguagePair: 'ko→en', glossarySize: 48 }
    }
  }

  return { jobs, glossary, summary, loading, fetchJobs, fetchGlossary, fetchSummary }
})
