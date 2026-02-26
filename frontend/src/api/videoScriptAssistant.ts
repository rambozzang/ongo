import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { VideoScript, ScriptSuggestion, VideoScriptAssistantSummary } from '@/types/videoScriptAssistant'

export const videoScriptAssistantApi = {
  getScripts: () =>
    apiClient.get<ResData<VideoScript[]>>('/api/v1/video-script-assistant').then(unwrapResponse),

  generate: (data: { title: string; tone: string; targetLength: number }) =>
    apiClient.post<ResData<VideoScript>>('/api/v1/video-script-assistant/generate', data).then(unwrapResponse),

  getSuggestions: (scriptId: number) =>
    apiClient.get<ResData<ScriptSuggestion[]>>(`/api/v1/video-script-assistant/${scriptId}/suggestions`).then(unwrapResponse),

  applySuggestion: (scriptId: number, suggestionId: number) =>
    apiClient.post<ResData<VideoScript>>(`/api/v1/video-script-assistant/${scriptId}/suggestions/${suggestionId}/apply`).then(unwrapResponse),

  getSummary: () =>
    apiClient.get<ResData<VideoScriptAssistantSummary>>('/api/v1/video-script-assistant/summary').then(unwrapResponse),
}
