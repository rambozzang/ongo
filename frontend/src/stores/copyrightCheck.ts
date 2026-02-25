import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  CopyrightCheckResult,
  RunCheckRequest,
  VideoForCheck,
} from '@/types/copyrightCheck'
import { copyrightCheckApi } from '@/api/copyrightCheck'

// ── Mock data ────────────────────────────────────────────────────────

function generateMockVideos(): VideoForCheck[] {
  return [
    { id: 1, title: '서울 맛집 TOP 5', thumbnailUrl: '', duration: 620, hasBeenChecked: true, lastCheckStatus: 'PASSED' },
    { id: 2, title: '주말 카페 브이로그', thumbnailUrl: '', duration: 485, hasBeenChecked: true, lastCheckStatus: 'WARNING' },
    { id: 3, title: '운동 루틴 공유', thumbnailUrl: '', duration: 380, hasBeenChecked: false },
    { id: 4, title: '신상 맛집 리뷰', thumbnailUrl: '', duration: 720, hasBeenChecked: false },
    { id: 5, title: '여행 브이로그 제주도편', thumbnailUrl: '', duration: 900, hasBeenChecked: true, lastCheckStatus: 'BLOCKED' },
  ]
}

function generateMockResults(): CopyrightCheckResult[] {
  return [
    {
      id: 1, videoId: 1, videoTitle: '서울 맛집 TOP 5', status: 'PASSED',
      issues: [],
      musicDetected: [
        { title: 'Sunny Day', artist: 'Free Music Archive', licensed: true, timestamp: { start: 0, end: 120 } },
      ],
      monetizationEligible: true,
      platformChecks: [
        { platform: 'YouTube', status: 'PASSED', issues: [] },
        { platform: 'TikTok', status: 'PASSED', issues: [] },
        { platform: 'Instagram', status: 'PASSED', issues: [] },
      ],
      checkedAt: new Date(Date.now() - 86400000 * 2).toISOString(),
    },
    {
      id: 2, videoId: 2, videoTitle: '주말 카페 브이로그', status: 'WARNING',
      issues: [
        { id: 'iss-1', type: 'MUSIC', severity: 'WARNING', title: '저작권 음악 감지', description: 'BGM에서 저작권 보호 음악이 감지되었습니다 (00:45-01:32).', timestamp: { start: 45, end: 92 }, suggestion: '무료 음원으로 교체하거나 라이선스를 구매하세요.', autoFixAvailable: true },
        { id: 'iss-2', type: 'BRAND', severity: 'INFO', title: '브랜드 로고 노출', description: '카페 간판의 브랜드 로고가 감지되었습니다.', suggestion: '일반적으로 문제가 되지 않지만, 광고 수익에 영향을 줄 수 있습니다.', autoFixAvailable: false },
      ],
      musicDetected: [
        { title: 'Coffee Vibes', artist: 'Unknown Artist', licensed: false, timestamp: { start: 45, end: 92 } },
        { title: 'Morning Jazz', artist: 'Free BGM', licensed: true, timestamp: { start: 120, end: 300 } },
      ],
      monetizationEligible: true,
      platformChecks: [
        { platform: 'YouTube', status: 'WARNING', issues: ['저작권 음악 감지 - 수익화 일부 제한 가능'] },
        { platform: 'TikTok', status: 'PASSED', issues: [] },
        { platform: 'Instagram', status: 'PASSED', issues: [] },
      ],
      checkedAt: new Date(Date.now() - 86400000).toISOString(),
    },
    {
      id: 3, videoId: 5, videoTitle: '여행 브이로그 제주도편', status: 'BLOCKED',
      issues: [
        { id: 'iss-3', type: 'MUSIC', severity: 'CRITICAL', title: '저작권 위반 음악', description: '전체 BGM이 저작권 보호 음악입니다. 업로드 시 즉시 차단될 수 있습니다.', timestamp: { start: 0, end: 900 }, suggestion: '무료 음원으로 전체 교체가 필요합니다.', autoFixAvailable: true },
        { id: 'iss-4', type: 'CONTENT_POLICY', severity: 'WARNING', title: '타인 얼굴 노출', description: '영상 내 불특정 다수의 얼굴이 노출되었습니다.', suggestion: '모자이크 처리를 권장합니다.', autoFixAvailable: false },
      ],
      musicDetected: [
        { title: 'Summer Hits 2024', artist: 'Major Label', licensed: false, timestamp: { start: 0, end: 900 } },
      ],
      monetizationEligible: false,
      platformChecks: [
        { platform: 'YouTube', status: 'BLOCKED', issues: ['저작권 위반 - 업로드 차단'] },
        { platform: 'TikTok', status: 'WARNING', issues: ['음원 저작권 이슈'] },
        { platform: 'Instagram', status: 'WARNING', issues: ['음원 저작권 이슈'] },
      ],
      checkedAt: new Date(Date.now() - 3600000).toISOString(),
    },
  ]
}

export const useCopyrightCheckStore = defineStore('copyrightCheck', () => {
  const videos = ref<VideoForCheck[]>([])
  const results = ref<CopyrightCheckResult[]>([])
  const selectedResult = ref<CopyrightCheckResult | null>(null)
  const loading = ref(false)
  const checking = ref(false)

  const passedCount = computed(() => results.value.filter((r) => r.status === 'PASSED').length)
  const warningCount = computed(() => results.value.filter((r) => r.status === 'WARNING').length)
  const blockedCount = computed(() => results.value.filter((r) => r.status === 'BLOCKED').length)
  const uncheckedVideos = computed(() => videos.value.filter((v) => !v.hasBeenChecked))

  async function fetchVideos() {
    loading.value = true
    try {
      videos.value = await copyrightCheckApi.getVideos()
    } catch {
      videos.value = generateMockVideos()
    } finally {
      loading.value = false
    }
  }

  async function fetchResults() {
    loading.value = true
    try {
      results.value = await copyrightCheckApi.getResults()
    } catch {
      results.value = generateMockResults()
    } finally {
      loading.value = false
    }
  }

  async function runCheck(request: RunCheckRequest) {
    checking.value = true
    try {
      const response = await copyrightCheckApi.runCheck(request)
      results.value.unshift(response.result)
      const video = videos.value.find((v) => v.id === request.videoId)
      if (video) {
        video.hasBeenChecked = true
        video.lastCheckStatus = response.result.status
      }
    } catch {
      // Mock 결과 생성
      const mockResult: CopyrightCheckResult = {
        id: Date.now(),
        videoId: request.videoId,
        videoTitle: videos.value.find((v) => v.id === request.videoId)?.title ?? '영상',
        status: 'PASSED',
        issues: [],
        musicDetected: [],
        monetizationEligible: true,
        platformChecks: request.platforms.map((p) => ({
          platform: p,
          status: 'PASSED' as const,
          issues: [],
        })),
        checkedAt: new Date().toISOString(),
      }
      results.value.unshift(mockResult)
      const video = videos.value.find((v) => v.id === request.videoId)
      if (video) {
        video.hasBeenChecked = true
        video.lastCheckStatus = 'PASSED'
      }
    } finally {
      checking.value = false
    }
  }

  async function autoFix(resultId: number, issueId: string) {
    try {
      const updated = await copyrightCheckApi.autoFix(resultId, issueId)
      const idx = results.value.findIndex((r) => r.id === resultId)
      if (idx !== -1) results.value[idx] = updated
      if (selectedResult.value?.id === resultId) selectedResult.value = updated
    } catch {
      // Mock: 이슈 제거
      const result = results.value.find((r) => r.id === resultId)
      if (result) {
        result.issues = result.issues.filter((i) => i.id !== issueId)
        if (result.issues.length === 0) result.status = 'PASSED'
        else if (result.issues.every((i) => i.severity !== 'CRITICAL')) result.status = 'WARNING'
      }
    }
  }

  return {
    videos,
    results,
    selectedResult,
    loading,
    checking,
    passedCount,
    warningCount,
    blockedCount,
    uncheckedVideos,
    fetchVideos,
    fetchResults,
    runCheck,
    autoFix,
  }
})
