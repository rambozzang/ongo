import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  RepurposeJob,
  RepurposeTemplate,
  RepurposeRequest,
  ContentRepurposerSummary,
} from '@/types/contentRepurposer'

export const contentRepurposerApi = {
  getJobs(status?: string) {
    return apiClient
      .get<ResData<RepurposeJob[]>>('/content-repurposer/jobs', { params: { status } })
      .then(unwrapResponse)
  },

  createJob(request: RepurposeRequest) {
    return apiClient
      .post<ResData<RepurposeJob>>('/content-repurposer/jobs', request)
      .then(unwrapResponse)
  },

  getTemplates() {
    return apiClient
      .get<ResData<RepurposeTemplate[]>>('/content-repurposer/templates')
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<ContentRepurposerSummary>>('/content-repurposer/summary')
      .then(unwrapResponse)
  },
}
