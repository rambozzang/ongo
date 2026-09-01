import type { Video } from '@/types/video'
import type { VideoAnalytics } from '@/types/analytics'

export interface VideoScoreResult {
  /** 측정 가능한 축만 계산한다. 표본이 없으면 null이다. */
  overall: number | null
  reach: number | null
  engagement: number | null
  growth: number | null
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

  // 측정되지 않은 축을 0점이나 중립값으로 섞지 않는다. 남은 측정 축의 가중치만
  // 재정규화해야 분석 표본이 적은 영상의 점수를 임의로 깎거나 올리지 않는다.
  const weightedScores = [
    { value: reach, weight: 0.3 },
    { value: engagement, weight: 0.35 },
    { value: growth, weight: 0.2 },
    { value: coverage, weight: 0.15 },
  ].filter((score): score is { value: number; weight: number } => score.value !== null)
  const totalWeight = weightedScores.reduce((sum, score) => sum + score.weight, 0)
  const hasMeasuredPerformance = reach !== null || engagement !== null || growth !== null
  const overall = !hasMeasuredPerformance || totalWeight === 0
    ? null
    : Math.round(weightedScores.reduce((sum, score) => sum + score.value * score.weight, 0) / totalWeight)

  // Generate suggestions based on scores
  const suggestions = generateSuggestions(
    { reach, engagement, growth, coverage, overall },
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
 * 측정된 값만 더한다. **`null` 은 "그 플랫폼이 이 지표를 주지 않는다" 이며 0 이 아니다.**
 *
 * Facebook·WordPress·Vimeo 는 공유를, Pinterest 는 댓글을 주지 않는다. 그리고 Pinterest 의
 * `shares`(PIN_CLICK)·Dailymotion 의 `shares`(북마크)·Tumblr 의 `views`(노트 총합)는 아예
 * 다른 뜻의 값이라 서버가 `null` 로 내려준다. `?? 0` 을 하면 그 구분이 통째로 사라진다.
 */
function sumMeasured(
  analytics: VideoAnalytics[],
  pick: (a: VideoAnalytics) => number | null | undefined,
): number | null {
  let measured = false
  const sum = analytics.reduce((total, a) => {
    const value = pick(a)
    if (typeof value !== 'number' || !Number.isFinite(value)) return total
    measured = true
    return total + value
  }, 0)
  return measured ? sum : null
}

/**
 * Calculate reach score (0-100)
 * Based on total views across all platforms relative to expected performance
 */
function calculateReachScore(_video: Video, analytics?: VideoAnalytics[]): number | null {
  if (!analytics || analytics.length === 0) {
    return null
  }

  // 미수집 지표는 `null` 이다. 합계에서 빼는 것이 맞다 — `?? 0` 을 하면 그 플랫폼이
  // "조회수 0" 을 기록했다는 관측이 되어 점수가 실제보다 낮아진다.
  const totalViews = sumMeasured(analytics, (a) => a.views)

  // Scoring logic: logarithmic scale
  // 0-100 views: 0-20
  // 100-1000 views: 20-40
  // 1000-10000 views: 40-60
  // 10000-100000 views: 60-80
  // 100000+ views: 80-100

  if (totalViews === null) return null
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
function calculateEngagementScore(analytics?: VideoAnalytics[]): number | null {
  if (!analytics || analytics.length === 0) {
    return null
  }

  const totalViews = sumMeasured(analytics, (a) => a.views)
  const totalLikes = sumMeasured(analytics, (a) => a.likes)
  const totalComments = sumMeasured(analytics, (a) => a.comments)
  const totalShares = sumMeasured(analytics, (a) => a.shares)

  if (totalViews === null || totalViews === 0) return null
  const measuredEngagement = [totalLikes, totalComments, totalShares].some((value) => value !== null)
  if (!measuredEngagement) return null

  // Calculate engagement rate (weighted sum / views)
  // Weight: likes=1, comments=2, shares=3
  const engagementScore = (
    (totalLikes ?? 0) + (totalComments ?? 0) * 2 + (totalShares ?? 0) * 3
  ) / totalViews

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
function calculateGrowthScore(analytics?: VideoAnalytics[]): number | null {
  if (!analytics || analytics.length === 0) {
    return null
  }

  // Calculate average views change across platforms. 변화율 표본이 없으면 중립값을
  // 만들어내지 않고, 호출자가 측정 불가 상태를 표시할 수 있도록 null을 반환한다.
  const validChanges = analytics.filter(
    (a) => typeof a.viewsChange === 'number' && Number.isFinite(a.viewsChange),
  )
  if (validChanges.length === 0) {
    return null
  }

  const avgChange = validChanges.reduce((sum, a) => sum + (a.viewsChange ?? 0), 0) / validChanges.length

  // Convert change percentage to score
  // -50% or worse: 0-20
  // -50% to 0%: 20-50
  // 0%: 50 (neutral, when a real change value exists)
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
    // PROCESSING/UNCONFIRMED는 외부 게시가 확정되지 않았다. 진행 중인 작업을
    // 게시 완료로 세면 실제 도달 범위보다 높은 성과 점수를 보여주게 된다.
    u => u.status === 'PUBLISHED'
  )

  // Several retries or channel variants for the same platform must not inflate
  // the score. Naver Clip is retained in legacy rows but is not a supported
  // publishing destination, so it cannot count toward current coverage.
  const platformCount = new Set(
    publishedUploads
      .map(upload => upload.platform)
      .filter(platform => platform !== 'NAVER_CLIP'),
  ).size

  // The score caps at four distinct supported destinations.
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
  scores: {
    reach: number | null
    engagement: number | null
    growth: number | null
    coverage: number
    overall: number | null
  },
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

  if (scores.engagement !== null && scores.engagement < 50) {
    suggestions.push({
      text: '설명에 키워드를 추가하면 검색 노출이 올라갈 수 있어요',
      priority: scores.engagement < 30 ? 'high' : 'medium',
      actionLabel: '수정하기',
      actionRoute: `/upload?edit=${video.id}`,
    })
  }

  if (scores.reach !== null && scores.reach < 50 && video.tags.length < 5) {
    suggestions.push({
      text: '태그를 추가하면 더 많은 사람들에게 도달할 수 있어요',
      priority: 'medium',
      actionLabel: '태그 추가',
      actionRoute: `/upload?edit=${video.id}`,
    })
  }

  if (scores.growth !== null && scores.growth < 40 && video.uploads.length > 0) {
    suggestions.push({
      text: '이 영상을 재활용하면 추가 조회수를 얻을 수 있어요',
      priority: 'medium',
      actionLabel: '재활용',
    })
  }

  if (
    scores.engagement !== null &&
    scores.engagement < 60 &&
    (!video.thumbnailUrl || video.thumbnailCandidates.length === 0)
  ) {
    suggestions.push({
      text: '더 매력적인 썸네일을 사용하면 클릭률이 높아질 수 있어요',
      priority: 'low',
      actionLabel: '썸네일 변경',
      actionRoute: `/upload?edit=${video.id}`,
    })
  }

  // If performance is already good, give positive reinforcement
  if (scores.overall !== null && scores.overall >= 70) {
    suggestions.unshift({
      text: '훌륭한 성과입니다! 이 전략을 다른 영상에도 적용해보세요',
      priority: 'low',
    })
  }

  // Limit to 4 suggestions
  return suggestions.slice(0, 4)
}
