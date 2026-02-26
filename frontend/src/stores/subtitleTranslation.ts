import { defineStore } from 'pinia'
import { ref } from 'vue'
import { subtitleTranslationApi } from '@/api/subtitleTranslation'
import type {
  SubtitleTranslation,
  TranslationLine,
  SupportedLanguage,
  SubtitleTranslationSummary,
  CreateTranslationRequest,
} from '@/types/subtitleTranslation'

export const useSubtitleTranslationStore = defineStore('subtitleTranslation', () => {
  const translations = ref<SubtitleTranslation[]>([])
  const currentLines = ref<TranslationLine[]>([])
  const supportedLanguages = ref<SupportedLanguage[]>([])
  const summary = ref<SubtitleTranslationSummary>({
    totalTranslations: 0,
    completedCount: 0,
    inProgressCount: 0,
    totalCreditsUsed: 0,
    avgQuality: 0,
  })
  const isLoading = ref(false)

  const mockTranslations: SubtitleTranslation[] = [
    {
      id: 1, videoTitle: '주간 브이로그 #15', sourceLanguage: 'ko', targetLanguage: 'en',
      status: 'COMPLETED', progress: 100, sourceLineCount: 120, translatedLineCount: 120,
      quality: 94, cost: 50, createdAt: '2025-03-08T10:00:00Z', completedAt: '2025-03-08T10:15:00Z',
    },
    {
      id: 2, videoTitle: '쿠킹 챌린지 에피소드 8', sourceLanguage: 'ko', targetLanguage: 'ja',
      status: 'TRANSLATING', progress: 65, sourceLineCount: 200, translatedLineCount: 130,
      quality: 0, cost: 80, createdAt: '2025-03-10T14:00:00Z',
    },
    {
      id: 3, videoTitle: '여행 브이로그 Day 3', sourceLanguage: 'ko', targetLanguage: 'zh',
      status: 'COMPLETED', progress: 100, sourceLineCount: 95, translatedLineCount: 95,
      quality: 88, cost: 40, createdAt: '2025-03-07T08:00:00Z', completedAt: '2025-03-07T08:12:00Z',
    },
    {
      id: 4, videoTitle: 'OOTD 코디 추천', sourceLanguage: 'ko', targetLanguage: 'en',
      status: 'REVIEWING', progress: 100, sourceLineCount: 45, translatedLineCount: 45,
      quality: 91, cost: 20, createdAt: '2025-03-09T16:00:00Z',
    },
    {
      id: 5, videoTitle: '제품 리뷰 #22', sourceLanguage: 'ko', targetLanguage: 'es',
      status: 'PENDING', progress: 0, sourceLineCount: 150, translatedLineCount: 0,
      quality: 0, cost: 60, createdAt: '2025-03-10T09:00:00Z',
    },
  ]

  const mockLines: TranslationLine[] = [
    { id: 1, translationId: 1, lineNumber: 1, startTime: '00:00:01', endTime: '00:00:04', sourceText: '안녕하세요 여러분!', translatedText: 'Hello everyone!', isEdited: false },
    { id: 2, translationId: 1, lineNumber: 2, startTime: '00:00:05', endTime: '00:00:09', sourceText: '오늘은 일주일간의 일상을 보여드릴게요', translatedText: "Today I'll show you my week", isEdited: false },
    { id: 3, translationId: 1, lineNumber: 3, startTime: '00:00:10', endTime: '00:00:14', sourceText: '먼저 아침 루틴부터 시작할게요', translatedText: "Let's start with my morning routine", isEdited: true },
    { id: 4, translationId: 1, lineNumber: 4, startTime: '00:00:15', endTime: '00:00:19', sourceText: '저는 보통 7시에 일어나요', translatedText: 'I usually wake up at 7 AM', isEdited: false },
    { id: 5, translationId: 1, lineNumber: 5, startTime: '00:00:20', endTime: '00:00:24', sourceText: '간단하게 스트레칭을 하고', translatedText: 'I do some light stretching', isEdited: false },
  ]

  const mockLanguages: SupportedLanguage[] = [
    { code: 'en', name: 'English', nativeName: 'English' },
    { code: 'ja', name: 'Japanese', nativeName: '日本語' },
    { code: 'zh', name: 'Chinese', nativeName: '中文' },
    { code: 'es', name: 'Spanish', nativeName: 'Español' },
    { code: 'fr', name: 'French', nativeName: 'Français' },
    { code: 'de', name: 'German', nativeName: 'Deutsch' },
    { code: 'pt', name: 'Portuguese', nativeName: 'Português' },
    { code: 'vi', name: 'Vietnamese', nativeName: 'Tiếng Việt' },
    { code: 'th', name: 'Thai', nativeName: 'ไทย' },
    { code: 'id', name: 'Indonesian', nativeName: 'Bahasa Indonesia' },
  ]

  const mockSummary: SubtitleTranslationSummary = {
    totalTranslations: 42,
    completedCount: 35,
    inProgressCount: 4,
    totalCreditsUsed: 1850,
    avgQuality: 91.2,
  }

  async function fetchTranslations(status?: string) {
    isLoading.value = true
    try {
      translations.value = await subtitleTranslationApi.getTranslations(status)
    } catch {
      translations.value = status
        ? mockTranslations.filter((t) => t.status === status)
        : mockTranslations
    } finally {
      isLoading.value = false
    }
  }

  async function createTranslation(request: CreateTranslationRequest) {
    isLoading.value = true
    try {
      const translation = await subtitleTranslationApi.createTranslation(request)
      translations.value.unshift(translation)
    } catch {
      translations.value.unshift({
        id: Date.now(),
        videoTitle: request.videoTitle,
        sourceLanguage: request.sourceLanguage,
        targetLanguage: request.targetLanguage,
        status: 'PENDING',
        progress: 0,
        sourceLineCount: 0,
        translatedLineCount: 0,
        quality: 0,
        cost: 0,
        createdAt: new Date().toISOString(),
      })
    } finally {
      isLoading.value = false
    }
  }

  async function fetchLines(translationId: number) {
    try {
      currentLines.value = await subtitleTranslationApi.getLines(translationId)
    } catch {
      currentLines.value = mockLines.filter((l) => l.translationId === translationId)
      if (currentLines.value.length === 0) currentLines.value = mockLines
    }
  }

  async function updateLine(translationId: number, lineId: number, translatedText: string) {
    try {
      await subtitleTranslationApi.updateLine(translationId, lineId, translatedText)
    } catch {
      // mock fallback
    }
    const line = currentLines.value.find((l) => l.id === lineId)
    if (line) {
      line.translatedText = translatedText
      line.isEdited = true
    }
  }

  async function fetchSupportedLanguages() {
    try {
      supportedLanguages.value = await subtitleTranslationApi.getSupportedLanguages()
    } catch {
      supportedLanguages.value = mockLanguages
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await subtitleTranslationApi.getSummary()
    } catch {
      summary.value = mockSummary
    }
  }

  return {
    translations,
    currentLines,
    supportedLanguages,
    summary,
    isLoading,
    fetchTranslations,
    createTranslation,
    fetchLines,
    updateLine,
    fetchSupportedLanguages,
    fetchSummary,
  }
})
