import { ref } from 'vue'
import { defineStore } from 'pinia'
import type { CommentSummaryResult, TopComment, CommentSummarySummary } from '@/types/commentSummary'
import { commentSummaryApi } from '@/api/commentSummary'

export const useCommentSummaryStore = defineStore('commentSummary', () => {
  const results = ref<CommentSummaryResult[]>([])
  const topComments = ref<TopComment[]>([])
  const summary = ref<CommentSummarySummary>({
    totalAnalyzed: 0,
    avgPositive: 0,
    avgNegative: 0,
    mostDiscussedTopic: '-',
    totalComments: 0,
  })
  const isLoading = ref(false)

  async function fetchResults(platform?: string) {
    isLoading.value = true
    try {
      results.value = await commentSummaryApi.getResults(platform)
    } catch {
      results.value = [
        { id: 1, videoTitle: '맥북 프로 M4 리뷰', platform: 'YOUTUBE', totalComments: 245, positivePct: 72, negativePct: 8, neutralPct: 20, topTopics: ['성능', '가격', '디자인'], aiSummary: '대부분의 댓글이 M4 칩의 성능 향상에 긍정적이며, 가격 대비 만족도가 높습니다.', analyzedAt: '2025-01-28T10:00:00' },
        { id: 2, videoTitle: '서울 카페 투어', platform: 'YOUTUBE', totalComments: 189, positivePct: 85, negativePct: 3, neutralPct: 12, topTopics: ['위치', '분위기', '메뉴'], aiSummary: '시청자들이 카페 위치와 분위기에 대해 매우 긍정적인 반응을 보이고 있습니다.', analyzedAt: '2025-01-27T10:00:00' },
        { id: 3, videoTitle: '홈트레이닝 루틴', platform: 'YOUTUBE', totalComments: 312, positivePct: 68, negativePct: 12, neutralPct: 20, topTopics: ['효과', '난이도', '시간'], aiSummary: '운동 효과에 대한 긍정적 의견이 많으나, 일부 시청자는 난이도가 높다고 느끼고 있습니다.', analyzedAt: '2025-01-26T10:00:00' },
        { id: 4, videoTitle: '일본 여행 꿀팁', platform: 'YOUTUBE', totalComments: 156, positivePct: 90, negativePct: 2, neutralPct: 8, topTopics: ['숙소', '맛집', '교통'], aiSummary: '여행 정보의 실용성에 대한 매우 높은 만족도를 보이며, 추가 정보를 요청하는 댓글이 많습니다.', analyzedAt: '2025-01-25T10:00:00' },
      ]
    } finally {
      isLoading.value = false
    }
  }

  async function fetchTopComments(summaryId: number) {
    try {
      topComments.value = await commentSummaryApi.getTopComments(summaryId)
    } catch {
      topComments.value = [
        { id: 1, summaryId, author: '테크팬123', text: 'M4 칩 성능이 정말 대단하네요!', likes: 45, sentiment: 'POSITIVE', isHighlighted: true },
        { id: 2, summaryId, author: '개발자Kim', text: '이 가격에 이 성능이면 갓성비', likes: 38, sentiment: 'POSITIVE', isHighlighted: true },
        { id: 3, summaryId, author: '비교맨', text: '윈도우 노트북과 비교 영상도 해주세요', likes: 22, sentiment: 'NEUTRAL', isHighlighted: false },
        { id: 4, summaryId, author: '학생유저', text: '학생에게는 좀 비싸네요...', likes: 15, sentiment: 'NEGATIVE', isHighlighted: false },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await commentSummaryApi.getSummary()
    } catch {
      summary.value = { totalAnalyzed: 25, avgPositive: 78.8, avgNegative: 6.3, mostDiscussedTopic: '성능', totalComments: 4520 }
    }
  }

  return { results, topComments, summary, isLoading, fetchResults, fetchTopComments, fetchSummary }
})
