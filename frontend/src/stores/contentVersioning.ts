import { defineStore } from 'pinia'
import { ref } from 'vue'
import { contentVersioningApi } from '@/api/contentVersioning'
import type {
  ContentVersionGroup,
  ContentVersioningSummary,
} from '@/types/contentVersioning'

export const useContentVersioningStore = defineStore('contentVersioning', () => {
  const groups = ref<ContentVersionGroup[]>([])
  const summary = ref<ContentVersioningSummary>({
    totalContents: 0,
    totalVersions: 0,
    avgVersionsPerContent: 0,
    mostEditedContent: '',
    bestPerformingChange: '',
  })
  const isLoading = ref(false)

  const mockGroups: ContentVersionGroup[] = [
    {
      contentId: 1, contentTitle: '주간 브이로그 #15', platform: 'YOUTUBE',
      totalVersions: 4, latestVersion: 4, createdAt: '2025-03-05T10:00:00Z',
      versions: [
        { id: 1, contentId: 1, contentTitle: '주간 브이로그 #15', versionNumber: 1, changeType: 'TITLE', changeDescription: '초기 제목 설정', newValue: '주간 브이로그 #15', createdBy: '김민수', createdAt: '2025-03-05T10:00:00Z' },
        { id: 2, contentId: 1, contentTitle: '주간 브이로그 #15', versionNumber: 2, changeType: 'THUMBNAIL', changeDescription: '썸네일 교체 - 밝은 톤으로 변경', createdBy: '박준혁', createdAt: '2025-03-06T14:00:00Z', performanceBefore: { views: 12000, likes: 450, engagement: 6.2, ctr: 3.8 }, performanceAfter: { views: 28000, likes: 1200, engagement: 8.5, ctr: 7.2 } },
        { id: 3, contentId: 1, contentTitle: '주간 브이로그 #15', versionNumber: 3, changeType: 'TAGS', changeDescription: '해시태그 최적화', createdBy: '이서연', createdAt: '2025-03-07T09:00:00Z' },
        { id: 4, contentId: 1, contentTitle: '주간 브이로그 #15', versionNumber: 4, changeType: 'DESCRIPTION', changeDescription: '설명 업데이트 - 타임스탬프 추가', createdBy: '이서연', createdAt: '2025-03-08T11:00:00Z' },
      ],
    },
    {
      contentId: 2, contentTitle: '쿠킹 챌린지 에피소드 8', platform: 'YOUTUBE',
      totalVersions: 3, latestVersion: 3, createdAt: '2025-03-03T08:00:00Z',
      versions: [
        { id: 5, contentId: 2, contentTitle: '쿠킹 챌린지 에피소드 8', versionNumber: 1, changeType: 'TITLE', changeDescription: '초기 업로드', createdBy: '김민수', createdAt: '2025-03-03T08:00:00Z' },
        { id: 6, contentId: 2, contentTitle: '쿠킹 챌린지 에피소드 8', versionNumber: 2, changeType: 'TITLE', changeDescription: '제목 A/B 테스트 - 이모지 추가', previousValue: '쿠킹 챌린지 에피소드 8', newValue: '🍳 쿠킹 챌린지 에피소드 8 | 초간단 레시피', createdBy: '이서연', createdAt: '2025-03-04T15:00:00Z' },
        { id: 7, contentId: 2, contentTitle: '쿠킹 챌린지 에피소드 8', versionNumber: 3, changeType: 'SUBTITLE', changeDescription: '영어 자막 추가', createdBy: '정우진', createdAt: '2025-03-05T12:00:00Z' },
      ],
    },
    {
      contentId: 3, contentTitle: 'OOTD 코디 추천', platform: 'INSTAGRAM',
      totalVersions: 2, latestVersion: 2, createdAt: '2025-03-07T14:00:00Z',
      versions: [
        { id: 8, contentId: 3, contentTitle: 'OOTD 코디 추천', versionNumber: 1, changeType: 'TITLE', changeDescription: '초기 업로드', createdBy: '최수진', createdAt: '2025-03-07T14:00:00Z' },
        { id: 9, contentId: 3, contentTitle: 'OOTD 코디 추천', versionNumber: 2, changeType: 'TAGS', changeDescription: '해시태그 30개 최적화', createdBy: '최수진', createdAt: '2025-03-08T10:00:00Z' },
      ],
    },
  ]

  const mockSummary: ContentVersioningSummary = {
    totalContents: 18,
    totalVersions: 52,
    avgVersionsPerContent: 2.9,
    mostEditedContent: '주간 브이로그 #15',
    bestPerformingChange: '썸네일 변경',
  }

  async function fetchGroups() {
    isLoading.value = true
    try {
      groups.value = await contentVersioningApi.getGroups()
    } catch {
      groups.value = mockGroups
    } finally {
      isLoading.value = false
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await contentVersioningApi.getSummary()
    } catch {
      summary.value = mockSummary
    }
  }

  return {
    groups, summary, isLoading,
    fetchGroups, fetchSummary,
  }
})
