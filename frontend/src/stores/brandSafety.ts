import { defineStore } from 'pinia'
import { ref } from 'vue'
import { brandSafetyApi } from '@/api/brandSafety'
import type {
  BrandSafetyCheck,
  BrandSafetyRule,
  BrandSafetySummary,
  RunSafetyCheckRequest,
} from '@/types/brandSafety'

export const useBrandSafetyStore = defineStore('brandSafety', () => {
  const checks = ref<BrandSafetyCheck[]>([])
  const rules = ref<BrandSafetyRule[]>([])
  const summary = ref<BrandSafetySummary>({
    totalChecks: 0,
    safeCount: 0,
    warningCount: 0,
    violationCount: 0,
    avgScore: 0,
  })
  const isLoading = ref(false)

  const mockChecks: BrandSafetyCheck[] = [
    {
      id: 1, contentTitle: '주간 브이로그 #15', contentType: 'VIDEO', platform: 'YOUTUBE',
      status: 'SAFE', overallScore: 95,
      checks: [
        { id: 1, category: '언어', name: '비속어 검사', status: 'PASS', severity: 'HIGH', description: '비속어가 감지되지 않았습니다.' },
        { id: 2, category: '저작권', name: '배경음악 검사', status: 'PASS', severity: 'HIGH', description: '저작권 위반 음악이 없습니다.' },
        { id: 3, category: '광고', name: '광고 표시 검사', status: 'PASS', severity: 'MEDIUM', description: '광고 고지가 올바르게 포함되어 있습니다.' },
      ],
      checkedAt: '2025-03-10T14:00:00Z', createdAt: '2025-03-10T13:55:00Z',
    },
    {
      id: 2, contentTitle: '먹방 챌린지', contentType: 'VIDEO', platform: 'YOUTUBE',
      status: 'WARNING', overallScore: 72,
      checks: [
        { id: 4, category: '건강', name: '위험 행위 검사', status: 'WARN', severity: 'MEDIUM', description: '과도한 음식 섭취 장면이 포함될 수 있습니다.', recommendation: '적절한 주의 문구를 추가하세요.' },
        { id: 5, category: '저작권', name: '배경음악 검사', status: 'PASS', severity: 'HIGH', description: '저작권 위반 음악이 없습니다.' },
        { id: 6, category: '언어', name: '비속어 검사', status: 'PASS', severity: 'HIGH', description: '비속어가 감지되지 않았습니다.' },
      ],
      checkedAt: '2025-03-09T16:00:00Z', createdAt: '2025-03-09T15:50:00Z',
    },
    {
      id: 3, contentTitle: '제품 리뷰 #22', contentType: 'VIDEO', platform: 'INSTAGRAM',
      status: 'VIOLATION', overallScore: 45,
      checks: [
        { id: 7, category: '광고', name: '광고 표시 검사', status: 'FAIL', severity: 'CRITICAL', description: '유료 광고 콘텐츠이나 광고 표시가 없습니다.', recommendation: '공정거래위원회 기준에 따라 광고 표시를 추가하세요.' },
        { id: 8, category: '저작권', name: '이미지 검사', status: 'WARN', severity: 'MEDIUM', description: '출처 미확인 이미지가 포함되어 있습니다.', recommendation: '이미지 출처를 확인하고 라이선스를 확보하세요.' },
        { id: 9, category: '언어', name: '비속어 검사', status: 'PASS', severity: 'HIGH', description: '비속어가 감지되지 않았습니다.' },
      ],
      checkedAt: '2025-03-08T10:00:00Z', createdAt: '2025-03-08T09:55:00Z',
    },
    {
      id: 4, contentTitle: 'OOTD 코디 추천', contentType: 'SHORT', platform: 'TIKTOK',
      status: 'SAFE', overallScore: 98,
      checks: [
        { id: 10, category: '언어', name: '비속어 검사', status: 'PASS', severity: 'HIGH', description: '비속어가 감지되지 않았습니다.' },
        { id: 11, category: '저작권', name: '음악 검사', status: 'PASS', severity: 'HIGH', description: '라이선스 확인된 음악입니다.' },
      ],
      checkedAt: '2025-03-10T09:00:00Z', createdAt: '2025-03-10T08:55:00Z',
    },
  ]

  const mockRules: BrandSafetyRule[] = [
    { id: 1, name: '비속어 자동 감지', category: '언어', description: '콘텐츠 내 비속어 및 부적절한 표현 자동 감지', isEnabled: true, severity: 'HIGH', createdAt: '2024-01-01T00:00:00Z' },
    { id: 2, name: '저작권 음악 검사', category: '저작권', description: '배경음악의 저작권 라이선스 검증', isEnabled: true, severity: 'HIGH', createdAt: '2024-01-01T00:00:00Z' },
    { id: 3, name: '광고 표시 확인', category: '광고', description: '유료 광고 콘텐츠의 적절한 광고 표시 여부 확인', isEnabled: true, severity: 'CRITICAL', createdAt: '2024-01-01T00:00:00Z' },
    { id: 4, name: '위험 행위 감지', category: '건강', description: '위험하거나 모방 가능한 행위 감지', isEnabled: true, severity: 'MEDIUM', createdAt: '2024-01-01T00:00:00Z' },
    { id: 5, name: '민감한 주제 필터', category: '정치/종교', description: '정치적, 종교적으로 민감한 주제 감지', isEnabled: false, severity: 'LOW', createdAt: '2024-01-01T00:00:00Z' },
  ]

  const mockSummary: BrandSafetySummary = {
    totalChecks: 127,
    safeCount: 98,
    warningCount: 22,
    violationCount: 7,
    avgScore: 86.3,
  }

  async function fetchChecks(status?: string) {
    isLoading.value = true
    try {
      checks.value = await brandSafetyApi.getChecks(status)
    } catch {
      checks.value = status
        ? mockChecks.filter((c) => c.status === status)
        : mockChecks
    } finally {
      isLoading.value = false
    }
  }

  async function runCheck(request: RunSafetyCheckRequest) {
    isLoading.value = true
    try {
      const check = await brandSafetyApi.runCheck(request)
      checks.value.unshift(check)
    } catch {
      checks.value.unshift({
        id: Date.now(),
        contentTitle: request.contentTitle,
        contentType: request.contentType,
        platform: request.platform,
        status: 'PENDING',
        overallScore: 0,
        checks: [],
        checkedAt: new Date().toISOString(),
        createdAt: new Date().toISOString(),
      })
    } finally {
      isLoading.value = false
    }
  }

  async function fetchRules() {
    try {
      rules.value = await brandSafetyApi.getRules()
    } catch {
      rules.value = mockRules
    }
  }

  async function toggleRule(id: number, isEnabled: boolean) {
    try {
      await brandSafetyApi.toggleRule(id, isEnabled)
    } catch {
      // mock fallback
    }
    const rule = rules.value.find((r) => r.id === id)
    if (rule) rule.isEnabled = isEnabled
  }

  async function fetchSummary() {
    try {
      summary.value = await brandSafetyApi.getSummary()
    } catch {
      summary.value = mockSummary
    }
  }

  return {
    checks,
    rules,
    summary,
    isLoading,
    fetchChecks,
    runCheck,
    fetchRules,
    toggleRule,
    fetchSummary,
  }
})
