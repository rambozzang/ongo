export type SeriesStatus = 'ACTIVE' | 'PAUSED' | 'COMPLETED' | 'ARCHIVED'

export interface SeriesEpisode {
  id: number
  seriesId: number
  episodeNumber: number
  title: string
  videoId?: string
  platform: string
  status: 'PLANNED' | 'DRAFTED' | 'PUBLISHED' | 'SKIPPED'
  scheduledDate?: string
  publishedDate?: string
  views?: number
  likes?: number
  comments?: number
}

export interface ContentSeries {
  id: number
  title: string
  description: string
  coverImageUrl?: string
  status: SeriesStatus
  platform: string
  frequency: 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'CUSTOM'
  customFrequencyDays?: number
  totalEpisodes: number
  publishedEpisodes: number
  avgViews: number
  avgRetention: number
  totalViews: number
  episodes: SeriesEpisode[]
  tags: string[]
  createdAt: string
  updatedAt: string
}

export interface SeriesAnalytics {
  seriesId: number
  viewsTrend: { episode: number; views: number; retention: number }[]
  subscriberGrowth: number
  avgEngagement: number
  bestPerformingEpisode: SeriesEpisode | null
  dropOffRate: number
  audienceReturnRate: number
}

export interface CreateSeriesRequest {
  title: string
  description: string
  platform: string
  frequency: ContentSeries['frequency']
  plannedEpisodes?: number
  tags?: string[]
}
