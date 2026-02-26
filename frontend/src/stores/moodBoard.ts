import { ref } from 'vue'
import { defineStore } from 'pinia'
import type {
  MoodBoard,
  MoodBoardItem,
  MoodBoardSummary,
} from '@/types/moodBoard'
import { moodBoardApi } from '@/api/moodBoard'

export const useMoodBoardStore = defineStore('moodBoard', () => {
  const boards = ref<MoodBoard[]>([])
  const boardItems = ref<MoodBoardItem[]>([])
  const summary = ref<MoodBoardSummary>({
    totalBoards: 0,
    totalItems: 0,
    topCategory: '-',
    recentBoard: '-',
    avgItemsPerBoard: 0,
  })
  const isLoading = ref(false)

  async function fetchBoards() {
    isLoading.value = true
    try {
      boards.value = await moodBoardApi.getBoards()
    } catch {
      boards.value = [
        { id: 1, name: '봄 시즌 콘텐츠', description: '봄 관련 콘텐츠 아이디어 및 비주얼 참고', category: '시즌', itemCount: 12, coverImage: null, tags: ['봄', '패션', '여행'], isPublic: false, createdAt: '2025-01-28T10:00:00' },
        { id: 2, name: '테크 리뷰 스타일', description: 'IT 제품 리뷰 영상 스타일 참고', category: '테크', itemCount: 8, coverImage: null, tags: ['테크', '리뷰', '미니멀'], isPublic: true, createdAt: '2025-01-25T10:00:00' },
        { id: 3, name: '브이로그 톤앤매너', description: '브이로그 촬영 분위기 및 색감 참고', category: '브이로그', itemCount: 15, coverImage: null, tags: ['브이로그', '색감', '감성'], isPublic: false, createdAt: '2025-01-22T10:00:00' },
        { id: 4, name: '썸네일 디자인', description: '클릭률 높은 썸네일 디자인 참고', category: '디자인', itemCount: 20, coverImage: null, tags: ['썸네일', '디자인', 'CTR'], isPublic: true, createdAt: '2025-01-20T10:00:00' },
      ]
    } finally {
      isLoading.value = false
    }
  }

  async function fetchBoardItems(boardId: number) {
    try {
      boardItems.value = await moodBoardApi.getItems(boardId)
    } catch {
      boardItems.value = [
        { id: 1, boardId, type: 'IMAGE', title: '벚꽃 배경', imageUrl: null, sourceUrl: null, note: '봄 느낌 배경으로 활용', color: '#FFB7C5', position: 1 },
        { id: 2, boardId, type: 'COLOR', title: '파스텔 핑크', imageUrl: null, sourceUrl: null, note: '메인 컬러', color: '#FFD1DC', position: 2 },
        { id: 3, boardId, type: 'IMAGE', title: '카페 인테리어', imageUrl: null, sourceUrl: 'https://example.com', note: '촬영 장소 참고', color: null, position: 3 },
        { id: 4, boardId, type: 'TEXT', title: '캡션 아이디어', imageUrl: null, sourceUrl: null, note: '봄이 왔으니까, 밖으로 나가볼까요?', color: null, position: 4 },
        { id: 5, boardId, type: 'IMAGE', title: '폰트 참고', imageUrl: null, sourceUrl: null, note: '둥근 폰트 사용', color: '#F5F0E8', position: 5 },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await moodBoardApi.getSummary()
    } catch {
      summary.value = { totalBoards: 4, totalItems: 55, topCategory: '디자인', recentBoard: '봄 시즌 콘텐츠', avgItemsPerBoard: 13.8 }
    }
  }

  return { boards, boardItems, summary, isLoading, fetchBoards, fetchBoardItems, fetchSummary }
})
