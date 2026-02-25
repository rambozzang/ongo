import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  BrandVoiceProfile,
  TrainVoiceRequest,
  GenerateWithVoiceRequest,
  GenerateWithVoiceResponse,
  VoiceAnalysis,
} from '@/types/brandVoice'
import { brandVoiceApi } from '@/api/brandVoice'

// ── Mock data ────────────────────────────────────────────────────────

function generateMockProfiles(): BrandVoiceProfile[] {
  return [
    {
      id: 1,
      name: '친근한 일상 톤',
      tone: 'CASUAL',
      trainStatus: 'READY',
      sampleCount: 15,
      vocabulary: ['꿀팁', '대박', '찐', '갓생', '인생템'],
      avoidWords: ['사망', '짜증'],
      emojiUsage: 'MODERATE',
      avgSentenceLength: 18,
      signaturePhrase: '오늘도 함께 해요!',
      createdAt: new Date(Date.now() - 86400000 * 10).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 2).toISOString(),
    },
    {
      id: 2,
      name: '교육적 설명 톤',
      tone: 'EDUCATIONAL',
      trainStatus: 'READY',
      sampleCount: 22,
      vocabulary: ['핵심', '포인트', '정리', '분석', '가이드'],
      avoidWords: ['절대', '무조건'],
      emojiUsage: 'MINIMAL',
      avgSentenceLength: 25,
      createdAt: new Date(Date.now() - 86400000 * 30).toISOString(),
      updatedAt: new Date(Date.now() - 86400000 * 5).toISOString(),
    },
    {
      id: 3,
      name: '유머러스 엔터 톤',
      tone: 'HUMOROUS',
      trainStatus: 'TRAINING',
      sampleCount: 8,
      vocabulary: ['ㅋㅋㅋ', '레전드', '미쳤다', '역대급'],
      avoidWords: [],
      emojiUsage: 'HEAVY',
      avgSentenceLength: 12,
      signaturePhrase: '구독 안 하면 손해!',
      createdAt: new Date(Date.now() - 86400000 * 3).toISOString(),
      updatedAt: new Date(Date.now() - 86400000).toISOString(),
    },
  ]
}

export const useBrandVoiceStore = defineStore('brandVoice', () => {
  const profiles = ref<BrandVoiceProfile[]>([])
  const selectedProfile = ref<BrandVoiceProfile | null>(null)
  const generatedResult = ref<GenerateWithVoiceResponse | null>(null)
  const analysis = ref<VoiceAnalysis | null>(null)
  const loading = ref(false)
  const training = ref(false)
  const generating = ref(false)
  const analyzing = ref(false)

  const readyProfiles = computed(() =>
    profiles.value.filter((p) => p.trainStatus === 'READY'),
  )
  const trainingProfiles = computed(() =>
    profiles.value.filter((p) => p.trainStatus === 'TRAINING'),
  )

  async function fetchProfiles() {
    loading.value = true
    try {
      profiles.value = await brandVoiceApi.getProfiles()
    } catch {
      profiles.value = generateMockProfiles()
    } finally {
      loading.value = false
    }
  }

  async function trainVoice(request: TrainVoiceRequest) {
    training.value = true
    try {
      const profile = await brandVoiceApi.trainVoice(request)
      profiles.value.unshift(profile)
    } catch {
      const mockProfile: BrandVoiceProfile = {
        id: Date.now(),
        name: request.name,
        tone: request.tone,
        trainStatus: 'TRAINING',
        sampleCount: request.sampleTexts.length,
        vocabulary: [],
        avoidWords: request.avoidWords ?? [],
        emojiUsage: 'MODERATE',
        avgSentenceLength: 0,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      }
      profiles.value.unshift(mockProfile)
    } finally {
      training.value = false
    }
  }

  async function generateWithVoice(request: GenerateWithVoiceRequest) {
    generating.value = true
    try {
      generatedResult.value = await brandVoiceApi.generateWithVoice(request)
    } catch {
      generatedResult.value = {
        text: `[${selectedProfile.value?.name ?? 'AI'}] ${request.prompt}에 대한 최적화된 텍스트입니다. 브랜드 보이스에 맞춰 자동 생성되었습니다.`,
        hashtags: ['#크리에이터', '#콘텐츠', '#브랜딩'],
        confidenceScore: 0.87,
        creditsUsed: 3,
        creditsRemaining: 47,
      }
    } finally {
      generating.value = false
    }
  }

  async function analyzeText(text: string) {
    analyzing.value = true
    try {
      analysis.value = await brandVoiceApi.analyzeText(text)
    } catch {
      analysis.value = {
        detectedTone: 'CASUAL',
        avgSentenceLength: 16,
        topWords: [
          { word: '좋아요', count: 12 },
          { word: '영상', count: 9 },
          { word: '오늘', count: 7 },
          { word: '추천', count: 5 },
          { word: '꿀팁', count: 4 },
        ],
        emojiFrequency: 0.15,
        formalityScore: 0.3,
        readabilityScore: 0.85,
      }
    } finally {
      analyzing.value = false
    }
  }

  async function deleteProfile(id: number) {
    try {
      await brandVoiceApi.deleteProfile(id)
    } catch {
      // 로컬에서 제거
    }
    profiles.value = profiles.value.filter((p) => p.id !== id)
    if (selectedProfile.value?.id === id) {
      selectedProfile.value = null
    }
  }

  return {
    profiles,
    selectedProfile,
    generatedResult,
    analysis,
    loading,
    training,
    generating,
    analyzing,
    readyProfiles,
    trainingProfiles,
    fetchProfiles,
    trainVoice,
    generateWithVoice,
    analyzeText,
    deleteProfile,
  }
})
