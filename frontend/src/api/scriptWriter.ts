import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  Script,
  ScriptTemplate,
  ScriptSummary,
  GenerateScriptRequest,
} from '@/types/scriptWriter'

export const scriptWriterApi = {
  getScripts: (status?: string) =>
    apiClient.get<ResData<Script[]>>('/scripts', { params: { status } }).then(unwrapResponse),

  getScript: (id: number) =>
    apiClient.get<ResData<Script>>(`/scripts/${id}`).then(unwrapResponse),

  generateScript: (request: GenerateScriptRequest) =>
    apiClient.post<ResData<Script>>('/scripts/generate', request).then(unwrapResponse),

  updateScript: (id: number, data: Partial<Script>) =>
    apiClient.put<ResData<Script>>(`/scripts/${id}`, data).then(unwrapResponse),

  deleteScript: (id: number) =>
    apiClient.delete<ResData<void>>(`/scripts/${id}`).then(unwrapResponse),

  getTemplates: () =>
    apiClient.get<ResData<ScriptTemplate[]>>('/scripts/templates').then(unwrapResponse),

  getSummary: () =>
    apiClient.get<ResData<ScriptSummary>>('/scripts/summary').then(unwrapResponse),

  regenerateSection: (scriptId: number, sectionId: number) =>
    apiClient.post<ResData<Script>>(`/scripts/${scriptId}/sections/${sectionId}/regenerate`).then(unwrapResponse),
}
