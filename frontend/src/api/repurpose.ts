import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { RepurposeJob, RepurposeDetail } from '@/types/repurpose'

export const repurposeApi = {
  analyzeForRepurpose(videoId: number) {
    return apiClient
      .post<ResData<RepurposeJob>>(`/videos/${videoId}/repurpose`)
      .then(unwrapResponse)
  },

  getRepurposeJobs() {
    return apiClient
      .get<ResData<RepurposeJob[]>>('/repurpose')
      .then(unwrapResponse)
  },

  getRepurposeDetail(jobId: number) {
    return apiClient
      .get<ResData<RepurposeDetail>>(`/repurpose/${jobId}`)
      .then(unwrapResponse)
  },
}
