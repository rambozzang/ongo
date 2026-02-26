export interface ContentCluster {
  id: number
  name: string
  description: string
  contentCount: number
  avgViews: number
  avgEngagement: number
  topContent: string
  tags: string[]
  platform: string
  createdAt: string
}

export interface ClusterContent {
  id: number
  title: string
  platform: string
  views: number
  likes: number
  engagement: number
  publishedAt: string
  similarity: number
}

export interface ContentClusterSummary {
  totalClusters: number
  totalContents: number
  avgClusterSize: number
  bestCluster: string
  mostCommonTag: string
}
