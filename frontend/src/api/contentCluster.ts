import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  ContentCluster,
  ClusterContent,
  ContentClusterSummary,
} from '@/types/contentCluster'

export const contentClusterApi = {
  getClusters() {
    return apiClient
      .get<ResData<ContentCluster[]>>('/content-clusters')
      .then(unwrapResponse)
  },

  getCluster(id: number) {
    return apiClient
      .get<ResData<ContentCluster>>(`/content-clusters/${id}`)
      .then(unwrapResponse)
  },

  getContents(clusterId: number) {
    return apiClient
      .get<ResData<ClusterContent[]>>(`/content-clusters/${clusterId}/contents`)
      .then(unwrapResponse)
  },

  getSummary() {
    return apiClient
      .get<ResData<ContentClusterSummary>>('/content-clusters/summary')
      .then(unwrapResponse)
  },
}
