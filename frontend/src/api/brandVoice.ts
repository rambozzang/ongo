import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  BrandVoiceProfile,
  TrainVoiceRequest,
  GenerateWithVoiceRequest,
  GenerateWithVoiceResponse,
  VoiceAnalysis,
} from '@/types/brandVoice'

export const brandVoiceApi = {
  getProfiles() {
    return apiClient
      .get<ResData<{ profiles: BrandVoiceProfile[] }>>('/brand-voice/profiles')
      .then(unwrapResponse)
      .then((res) => res.profiles)
  },

  getProfile(id: number) {
    return apiClient
      .get<ResData<BrandVoiceProfile>>(`/brand-voice/profiles/${id}`)
      .then(unwrapResponse)
  },

  trainVoice(request: TrainVoiceRequest) {
    return apiClient
      .post<ResData<BrandVoiceProfile>>('/brand-voice/train', request)
      .then(unwrapResponse)
  },

  generateWithVoice(request: GenerateWithVoiceRequest) {
    return apiClient
      .post<ResData<GenerateWithVoiceResponse>>('/brand-voice/generate', request)
      .then(unwrapResponse)
  },

  analyzeText(text: string) {
    return apiClient
      .post<ResData<VoiceAnalysis>>('/brand-voice/analyze', { text })
      .then(unwrapResponse)
  },

  deleteProfile(id: number) {
    return apiClient
      .delete<ResData<void>>(`/brand-voice/profiles/${id}`)
      .then(unwrapResponse)
  },
}
