import { ref } from 'vue'
import { defineStore } from 'pinia'
import type {
  ContentCluster,
  ClusterContent,
  ContentClusterSummary,
} from '@/types/contentCluster'
import { contentClusterApi } from '@/api/contentCluster'

export const useContentClusterStore = defineStore('contentCluster', () => {
  const clusters = ref<ContentCluster[]>([])
  const clusterContents = ref<ClusterContent[]>([])
  const summary = ref<ContentClusterSummary>({
    totalClusters: 0,
    totalContents: 0,
    avgClusterSize: 0,
    bestCluster: '-',
    mostCommonTag: '-',
  })
  const isLoading = ref(false)

  async function fetchClusters() {
    isLoading.value = true
    try {
      clusters.value = await contentClusterApi.getClusters()
    } catch {
      clusters.value = [
        { id: 1, name: '테크 리뷰 시리즈', description: 'IT 제품 리뷰 관련 콘텐츠 클러스터', contentCount: 12, avgViews: 15200, avgEngagement: 4.5, topContent: '맥북 프로 M4 리뷰', tags: ['테크', '리뷰', '가젯'], platform: 'YOUTUBE', createdAt: '2025-01-20T10:00:00' },
        { id: 2, name: '일상 브이로그', description: '일상 브이로그 관련 콘텐츠', contentCount: 8, avgViews: 8500, avgEngagement: 6.2, topContent: '서울 카페 투어', tags: ['브이로그', '일상', '카페'], platform: 'YOUTUBE', createdAt: '2025-01-18T10:00:00' },
        { id: 3, name: '쇼츠 레시피', description: '짧은 요리 레시피 콘텐츠', contentCount: 15, avgViews: 42000, avgEngagement: 8.1, topContent: '3분 파스타', tags: ['요리', '레시피', '쇼츠'], platform: 'YOUTUBE', createdAt: '2025-01-15T10:00:00' },
        { id: 4, name: 'TikTok 챌린지', description: '트렌드 챌린지 참여 콘텐츠', contentCount: 6, avgViews: 85000, avgEngagement: 12.5, topContent: '댄스 챌린지 #trending', tags: ['챌린지', '댄스', '트렌드'], platform: 'TIKTOK', createdAt: '2025-01-12T10:00:00' },
      ]
    } finally {
      isLoading.value = false
    }
  }

  async function fetchClusterContents(clusterId: number) {
    try {
      clusterContents.value = await contentClusterApi.getContents(clusterId)
    } catch {
      clusterContents.value = [
        { id: 1, title: '맥북 프로 M4 리뷰', platform: 'YOUTUBE', views: 25000, likes: 1200, engagement: 5.8, publishedAt: '2025-01-25T10:00:00', similarity: 0.95 },
        { id: 2, title: '갤럭시 S25 첫인상', platform: 'YOUTUBE', views: 18000, likes: 890, engagement: 4.2, publishedAt: '2025-01-22T10:00:00', similarity: 0.88 },
        { id: 3, title: '아이패드 프로 vs 갤럭시탭', platform: 'YOUTUBE', views: 12000, likes: 650, engagement: 3.9, publishedAt: '2025-01-18T10:00:00', similarity: 0.82 },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await contentClusterApi.getSummary()
    } catch {
      summary.value = { totalClusters: 4, totalContents: 41, avgClusterSize: 10.3, bestCluster: '쇼츠 레시피', mostCommonTag: '리뷰' }
    }
  }

  return { clusters, clusterContents, summary, isLoading, fetchClusters, fetchClusterContents, fetchSummary }
})
