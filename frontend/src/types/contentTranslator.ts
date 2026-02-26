export interface TranslationJob {
  id: number
  videoId: number
  videoTitle: string
  sourceLanguage: string
  targetLanguage: string
  contentType: 'TITLE' | 'DESCRIPTION' | 'TAGS' | 'SUBTITLE' | 'ALL'
  originalText: string
  translatedText: string | null
  quality: number | null
  status: 'PENDING' | 'TRANSLATING' | 'COMPLETED' | 'FAILED'
  createdAt: string
  completedAt: string | null
}

export interface TranslationGlossary {
  id: number
  sourceWord: string
  targetWord: string
  sourceLanguage: string
  targetLanguage: string
  context: string | null
  createdAt: string
}

export interface ContentTranslatorSummary {
  totalTranslations: number
  completedTranslations: number
  avgQuality: number
  topLanguagePair: string
  glossarySize: number
}
