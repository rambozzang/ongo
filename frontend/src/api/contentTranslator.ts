import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { TranslationJob, TranslationGlossary, ContentTranslatorSummary } from '@/types/contentTranslator'

export const contentTranslatorApi = {
  getJobs: () =>
    apiClient.get<ResData<TranslationJob[]>>('/api/v1/content-translator').then(unwrapResponse),

  translate: (data: { videoId: number; targetLanguage: string; contentType: string }) =>
    apiClient.post<ResData<TranslationJob>>('/api/v1/content-translator/translate', data).then(unwrapResponse),

  getGlossary: () =>
    apiClient.get<ResData<TranslationGlossary[]>>('/api/v1/content-translator/glossary').then(unwrapResponse),

  addGlossaryTerm: (data: { sourceWord: string; targetWord: string; sourceLanguage: string; targetLanguage: string }) =>
    apiClient.post<ResData<TranslationGlossary>>('/api/v1/content-translator/glossary', data).then(unwrapResponse),

  getSummary: () =>
    apiClient.get<ResData<ContentTranslatorSummary>>('/api/v1/content-translator/summary').then(unwrapResponse),
}
