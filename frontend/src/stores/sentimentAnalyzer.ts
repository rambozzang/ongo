import { defineStore } from 'pinia'
import { ref } from 'vue'
import { sentimentAnalyzerApi } from '@/api/sentimentAnalyzer'
import type {
  SentimentResult,
  CommentSentiment,
  SentimentTrend,
  SentimentAnalyzerSummary,
  AnalyzeSentimentRequest,
} from '@/types/sentimentAnalyzer'

export const useSentimentAnalyzerStore = defineStore('sentimentAnalyzer', () => {
  const results = ref<SentimentResult[]>([])
  const currentComments = ref<CommentSentiment[]>([])
  const trends = ref<SentimentTrend[]>([])
  const summary = ref<SentimentAnalyzerSummary>({
    totalAnalyzed: 0,
    avgPositiveRate: 0,
    avgSentimentScore: 0,
    mostPositiveContent: '',
    mostNegativeContent: '',
  })
  const isLoading = ref(false)

  const mockResults: SentimentResult[] = [
    {
      id: 1, contentTitle: '주간 브이로그 #15', platform: 'YOUTUBE',
      totalComments: 245, positiveCount: 180, neutralCount: 45, negativeCount: 20,
      positiveRate: 73.5, avgSentimentScore: 78, topPositiveKeywords: ['재밌어요', '최고', '응원해요'],
      topNegativeKeywords: ['아쉬워요', '짧아요'], analyzedAt: '2025-03-10T14:00:00Z', createdAt: '2025-03-10T14:00:00Z',
    },
    {
      id: 2, contentTitle: '쿠킹 챌린지 에피소드 8', platform: 'YOUTUBE',
      totalComments: 189, positiveCount: 150, neutralCount: 28, negativeCount: 11,
      positiveRate: 79.4, avgSentimentScore: 82, topPositiveKeywords: ['맛있겠다', '레시피 공유', '꿀조합'],
      topNegativeKeywords: ['양이 적어요'], analyzedAt: '2025-03-09T10:00:00Z', createdAt: '2025-03-09T10:00:00Z',
    },
    {
      id: 3, contentTitle: 'OOTD 코디 추천', platform: 'INSTAGRAM',
      totalComments: 312, positiveCount: 260, neutralCount: 32, negativeCount: 20,
      positiveRate: 83.3, avgSentimentScore: 85, topPositiveKeywords: ['센스 있다', '예쁘다', '따라하고 싶어요'],
      topNegativeKeywords: ['비싸요', '어디서 사요'], analyzedAt: '2025-03-08T16:00:00Z', createdAt: '2025-03-08T16:00:00Z',
    },
    {
      id: 4, contentTitle: '제품 리뷰 #22', platform: 'YOUTUBE',
      totalComments: 156, positiveCount: 85, neutralCount: 42, negativeCount: 29,
      positiveRate: 54.5, avgSentimentScore: 58, topPositiveKeywords: ['솔직한 리뷰', '도움 돼요'],
      topNegativeKeywords: ['광고 아닌가요', '비추', '별로'], analyzedAt: '2025-03-07T12:00:00Z', createdAt: '2025-03-07T12:00:00Z',
    },
  ]

  const mockComments: CommentSentiment[] = [
    { id: 1, resultId: 1, commentText: '오늘도 재밌게 봤습니다!', authorName: '하늘별', sentiment: 'POSITIVE', score: 92, keywords: ['재밌게'], createdAt: '2025-03-10T10:00:00Z' },
    { id: 2, resultId: 1, commentText: '브이로그 퀄리티가 점점 올라가네요', authorName: '뷰티러버', sentiment: 'POSITIVE', score: 88, keywords: ['퀄리티'], createdAt: '2025-03-10T09:00:00Z' },
    { id: 3, resultId: 1, commentText: '영상이 좀 짧은 것 같아요', authorName: '시청자A', sentiment: 'NEGATIVE', score: 35, keywords: ['짧은'], createdAt: '2025-03-10T08:00:00Z' },
    { id: 4, resultId: 1, commentText: '좋아요 누르고 갑니다~', authorName: '구독자123', sentiment: 'POSITIVE', score: 80, keywords: ['좋아요'], createdAt: '2025-03-10T07:00:00Z' },
    { id: 5, resultId: 1, commentText: '그냥 그래요', authorName: '패스바이', sentiment: 'NEUTRAL', score: 50, keywords: [], createdAt: '2025-03-10T06:00:00Z' },
  ]

  const mockSummary: SentimentAnalyzerSummary = {
    totalAnalyzed: 28,
    avgPositiveRate: 72.7,
    avgSentimentScore: 75.8,
    mostPositiveContent: 'OOTD 코디 추천',
    mostNegativeContent: '제품 리뷰 #22',
  }

  async function fetchResults() {
    isLoading.value = true
    try {
      results.value = await sentimentAnalyzerApi.getResults()
    } catch {
      results.value = mockResults
    } finally {
      isLoading.value = false
    }
  }

  async function analyze(request: AnalyzeSentimentRequest) {
    isLoading.value = true
    try {
      const result = await sentimentAnalyzerApi.analyze(request)
      results.value.unshift(result)
    } catch {
      results.value.unshift({
        id: Date.now(), contentTitle: request.contentTitle, platform: request.platform,
        totalComments: 0, positiveCount: 0, neutralCount: 0, negativeCount: 0,
        positiveRate: 0, avgSentimentScore: 0, topPositiveKeywords: [], topNegativeKeywords: [],
        analyzedAt: new Date().toISOString(), createdAt: new Date().toISOString(),
      })
    } finally {
      isLoading.value = false
    }
  }

  async function fetchComments(resultId: number, sentiment?: string) {
    try {
      currentComments.value = await sentimentAnalyzerApi.getComments(resultId, sentiment)
    } catch {
      currentComments.value = sentiment
        ? mockComments.filter((c) => c.sentiment === sentiment)
        : mockComments
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await sentimentAnalyzerApi.getSummary()
    } catch {
      summary.value = mockSummary
    }
  }

  return {
    results, currentComments, trends, summary, isLoading,
    fetchResults, analyze, fetchComments, fetchSummary,
  }
})
