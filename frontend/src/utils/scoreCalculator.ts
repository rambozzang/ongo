import type { Video } from '@/types/video'
import type { VideoAnalytics } from '@/types/analytics'

export interface VideoScoreResult {
  overall: number
  reach: number
  engagement: number
  growth: number
  coverage: number
  suggestions: Suggestion[]
}

export interface Suggestion {
  text: string
  priority: 'high' | 'medium' | 'low'
  actionLabel?: string
  actionRoute?: string
}

/**
 * Calculate a performance score for a video based on its metadata and analytics
 */
export function calculateVideoScore(
  video: Video,
  analytics?: VideoAnalytics[]
): VideoScoreResult {
  // Calculate sub-scores
  const reach = calculateReachScore(video, analytics)
  const engagement = calculateEngagementScore(analytics)
  const growth = calculateGrowthScore(analytics)
  const coverage = calculateCoverageScore(video)

  // Calculate overall score (weighted average)
  const overall = Math.round(
    reach * 0.3 +
    engagement * 0.35 +
    growth * 0.2 +
    coverage * 0.15
  )

  // Generate suggestions based on scores
  const suggestions = generateSuggestions(
    { reach, engagement, growth, coverage },
    video
  )

  return {
    overall,
    reach,
    engagement,
    growth,
    coverage,
    suggestions,
  }
}

/**
 * Calculate reach score (0-100)
 * Based on total views across all platforms relative to expected performance
 */
function calculateReachScore(_video: Video, analytics?: VideoAnalytics[]): number {
  if (!analytics || analytics.length === 0) {
    return 0
  }

  const totalViews = analytics.reduce((sum, a) => sum + a.views, 0)

  // Scoring logic: logarithmic scale
  // 0-100 views: 0-20
  // 100-1000 views: 20-40
  // 1000-10000 views: 40-60
  // 10000-100000 views: 60-80
  // 100000+ views: 80-100

  if (totalViews === 0) return 0
  if (totalViews < 100) return Math.min(20, (totalViews / 100) * 20)
  if (totalViews < 1000) return 20 + ((totalViews - 100) / 900) * 20
  if (totalViews < 10000) return 40 + ((totalViews - 1000) / 9000) * 20
  if (totalViews < 100000) return 60 + ((totalViews - 10000) / 90000) * 20
  return Math.min(100, 80 + ((totalViews - 100000) / 100000) * 20)
}

/**
 * Calculate engagement score (0-100)
 * Based on likes, comments, shares relative to views
 */
function calculateEngagementScore(analytics?: VideoAnalytics[]): number {
  if (!analytics || analytics.length === 0) {
    return 0
  }

  const totalViews = analytics.reduce((sum, a) => sum + a.views, 0)
  const totalLikes = analytics.reduce((sum, a) => sum + a.likes, 0)
  const totalComments = analytics.reduce((sum, a) => sum + a.comments, 0)
  const totalShares = analytics.reduce((sum, a) => sum + a.shares, 0)

  if (totalViews === 0) return 0

  // Calculate engagement rate (weighted sum / views)
  // Weight: likes=1, comments=2, shares=3
  const engagementScore = (totalLikes + totalComments * 2 + totalShares * 3) / totalViews

  // Convert to 0-100 scale
  // 0-2% engagement: 0-30
  // 2-5% engagement: 30-60
  // 5-10% engagement: 60-80
  // 10%+ engagement: 80-100

  const engagementPercent = engagementScore * 100

  if (engagementPercent === 0) return 0
  if (engagementPercent < 2) return (engagementPercent / 2) * 30
  if (engagementPercent < 5) return 30 + ((engagementPercent - 2) / 3) * 30
  if (engagementPercent < 10) return 60 + ((engagementPercent - 5) / 5) * 20
  return Math.min(100, 80 + ((engagementPercent - 10) / 10) * 20)
}

/**
 * Calculate growth score (0-100)
 * Based on view trends over time
 */
function calculateGrowthScore(analytics?: VideoAnalytics[]): number {
  if (!analytics || analytics.length === 0) {
    return 0
  }

  // Calculate average views change across platforms
  const validChanges = analytics.filter(a => a.viewsChange != null)
  if (validChanges.length === 0) {
    return 50 // Neutral score if no trend data
  }

  const avgChange = validChanges.reduce((sum, a) => sum + (a.viewsChange ?? 0), 0) / validChanges.length

  // Convert change percentage to score
  // -50% or worse: 0-20
  // -50% to 0%: 20-50
  // 0%: 50 (neutral)
  // 0% to +50%: 50-80
  // +50% or more: 80-100

  if (avgChange <= -50) return Math.max(0, 20 + (avgChange + 50) / 2.5)
  if (avgChange < 0) return 50 + (avgChange / 50) * 30
  if (avgChange === 0) return 50
  if (avgChange <= 50) return 50 + (avgChange / 50) * 30
  return Math.min(100, 80 + ((avgChange - 50) / 50) * 20)
}

/**
 * Calculate platform coverage score (0-100)
 * Based on how many platforms the video is published to
 */
function calculateCoverageScore(video: Video): number {
  const publishedUploads = video.uploads.filter(
    u => u.status === 'PUBLISHED' || u.status === 'PROCESSING'
  )

  const platformCount = publishedUploads.length

  // Max 4 platforms (YouTube, TikTok, Instagram, Naver Clip)
  // 0 platforms: 0
  // 1 platform: 25
  // 2 platforms: 50
  // 3 platforms: 75
  // 4 platforms: 100

  return Math.min(100, platformCount * 25)
}

/**
 * Generate actionable suggestions based on scores
 */
function generateSuggestions(
  scores: { reach: number; engagement: number; growth: number; coverage: number },
  video: Video
): Suggestion[] {
  const suggestions: Suggestion[] = []

  // Generate suggestions based on weakest scores
  if (scores.coverage < 75) {
    suggestions.push({
      text: '더 많은 플랫폼에 게시하면 도달률을 높일 수 있어요',
      priority: scores.coverage < 50 ? 'high' : 'medium',
      actionLabel: '재업로드',
      actionRoute: `/upload?reupload=${video.id}`,
    })
  }

  if (scores.engagement < 50) {
    suggestions.push({
      text: '설명에 키워드를 추가하면 검색 노출이 올라갈 수 있어요',
      priority: scores.engagement < 30 ? 'high' : 'medium',
      actionLabel: '수정하기',
      actionRoute: `/upload?edit=${video.id}`,
    })
  }

  if (scores.reach < 50 && video.tags.length < 5) {
    suggestions.push({
      text: '태그를 추가하면 더 많은 사람들에게 도달할 수 있어요',
      priority: 'medium',
      actionLabel: '태그 추가',
      actionRoute: `/upload?edit=${video.id}`,
    })
  }

  if (scores.growth < 40 && video.uploads.length > 0) {
    suggestions.push({
      text: '이 영상을 재활용하면 추가 조회수를 얻을 수 있어요',
      priority: 'medium',
      actionLabel: '재활용',
    })
  }

  if (scores.engagement < 60 && (!video.thumbnailUrl || video.thumbnailCandidates.length === 0)) {
    suggestions.push({
      text: '더 매력적인 썸네일을 사용하면 클릭률이 높아질 수 있어요',
      priority: 'low',
      actionLabel: '썸네일 변경',
      actionRoute: `/upload?edit=${video.id}`,
    })
  }

  // If performance is already good, give positive reinforcement
  const overall = Math.round(
    scores.reach * 0.3 +
    scores.engagement * 0.35 +
    scores.growth * 0.2 +
    scores.coverage * 0.15
  )

  if (overall >= 70) {
    suggestions.unshift({
      text: '훌륭한 성과입니다! 이 전략을 다른 영상에도 적용해보세요',
      priority: 'low',
    })
  }

  // Limit to 4 suggestions
  return suggestions.slice(0, 4)
}
