import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  SubtitleTranslation,
  TranslationLine,
  SupportedLanguage,
  SubtitleTranslationSummary,
  CreateTranslationRequest,
} from '@/types/subtitleTranslation'

export const subtitleTranslationApi = {
  getTranslations(status?: string) {
    return apiClient
      .get<ResData<SubtitleTranslation[]>>('/subtitle-translations', { params: { status } })
      .then(unwrapResponse)
  },

  getTranslation(id: number) {
    return apiClient
      .get<ResData<SubtitleTranslation>>(`/subtitle-translations/${id}`)
      .then(unwrapResponse)
  },

  createTranslation(request: CreateTranslationRequest) {
    return apiClient
      .post<ResData<SubtitleTranslation>>('/subtitle-translations', request)
      .then(unwrapResponse)
  },

  getLines(translationId: number) {
    return apiClient
      .get<ResData<TranslationLine[]>>(`/subtitle-translations/${translationId}/lines`)
      .then(unwrapResponse)
  },

  updateLine(translationId: number, lineId: number, translatedText: string) {
    return apiClient
      .patch<ResData<TranslationLine>>(
        `/subtitle-translations/${translationId}/lines/${lineId}`,
        { translatedText },
      )
      .then(unwrapResponse)
  },

  getSupportedLanguages() {
    return apiClient
      .get<ResData<SupportedLanguage[]>>('/subtitle-translations/languages')
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<SubtitleTranslationSummary>>('/subtitle-translations/summary')
      .then(unwrapResponse)
  },
}
