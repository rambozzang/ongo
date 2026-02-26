export interface SubtitleTranslation {
  id: number
  videoTitle: string
  sourceLanguage: string
  targetLanguage: string
  status: 'PENDING' | 'TRANSLATING' | 'COMPLETED' | 'FAILED' | 'REVIEWING'
  progress: number
  sourceLineCount: number
  translatedLineCount: number
  quality: number
  cost: number
  createdAt: string
  completedAt?: string
}

export interface TranslationLine {
  id: number
  translationId: number
  lineNumber: number
  startTime: string
  endTime: string
  sourceText: string
  translatedText: string
  isEdited: boolean
}

export interface SupportedLanguage {
  code: string
  name: string
  nativeName: string
}

export interface SubtitleTranslationSummary {
  totalTranslations: number
  completedCount: number
  inProgressCount: number
  totalCreditsUsed: number
  avgQuality: number
}

export interface CreateTranslationRequest {
  videoId: number
  videoTitle: string
  sourceLanguage: string
  targetLanguage: string
}
