export type CompetitorPlatform = 'YOUTUBE' | 'TIKTOK' | 'INSTAGRAM' | 'NAVER_CLIP'

export interface Competitor {
  id: number
  name: string
  channelUrl: string
  platform: CompetitorPlatform
  /**
   * 채널 프로필 이미지 URL. **없으면 `null`** — 지어내지 않는다.
   *
   * 예전에는 외부 서비스의 무작위 인물 사진을 넣어, 화면이 그것을 실제 채널
   * 이미지처럼 보여줬다. `null` 이면 화면이 로컬 placeholder 를 그린다 —
   * **`src=''` 로 두면 안 된다**(브라우저가 현재 페이지를 다시 요청한다).
   */
  avatarUrl: string | null
  /**
   * 구독자 수. **재지 못했으면 `null`.**
   *
   * YouTube 채널은 구독자 수를 숨길 수 있고, 그때 조회 응답에 그 값이 아예 없다.
   * `?? 0` 으로 채우면 순위·평균·비교에서 **"구독자 0명"** 이 측정 결과가 된다.
   */
  subscriberCount: number | null
  /**
   * 영상 수. **재지 못했으면 `null`** — `0` 은 실제로 영상이 없다는 관측이다.
   *
   * `avgViews` 의 분모이기도 하다. `?? 0` 으로 채우면 "영상 0개" 가 관측처럼 보이고
   * 평균도 계산되지 않은 채 사라진다.
   */
  videoCount: number | null
  /**
   * 영상당 평균 조회수. **`videoCount` 를 모르거나 0 이면 `null`** — 나눌 대상이 없다.
   *
   * 서버 저장 모델은 `Long` non-null 이라 그 자리에 `0` 이 들어 있지만, 그것은 분모가
   * 없어 계산하지 못한 자리이지 "평균 0회" 라는 관측이 아니다. 서버가 `videoCount` 를
   * 근거로 갈라 준다. **`?? 0` 을 하지 말 것** — 영상이 있고 조회수가 실제 0 인 경우와
   * 같아진다.
   */
  avgViews: number | null
  /**
   * 참여율(%). **`null` 은 "측정할 수 없다"** 이지 0 이 아니다.
   *
   * 공개 API 로 남의 채널의 좋아요·댓글을 얻을 수 없어 분자가 없다. 예전에는 0 을 넣어
   * 비교표가 "나 4.2% vs 경쟁자 0.0%" 를 그렸고, 없는 경쟁 우위를 사실처럼 보여줬다.
   */
  avgEngagement: number | null
  /**
   * 성장률(%). **`null` 은 "측정할 수 없다"** 이지 0 이 아니다.
   *
   * 관측된 두 시점이 없거나 기준일 구독자가 0 이면 비율이 성립하지 않는다. 예전에는
   * 그 자리에 0 을 넣어 수집 이력이 없는 경쟁사가 "정체 중" 으로 보였다.
   */
  growthRate: number | null  // monthly percentage
  lastVideoAt: string
  addedAt: string
  isTracking: boolean
}

export interface CompetitorComparison {
  metric: string
  myValue: number | null
  /** `null` 이면 측정 불가. 0 과 구분해야 한다. */
  competitorValue: number | null
  /** 양쪽 다 측정값일 때만 계산된다. 아니면 `null`. */
  difference: number | null
  /** 기준값이 0 이거나 측정 불가면 `null` — 비율의 기준이 없다. */
  differencePercent: number | null
  /**
   * 이 지표를 비교할 수 있는가. `false` 면 화면은 숫자·우열 대신 안내를 보여야 한다.
   */
  comparable: boolean
  /** 비교할 수 없는 이유. `comparable` 이 false 일 때만 채워진다. */
  unavailableReason?: string
}

export interface CompetitorVideo {
  id: number
  competitorId: number
  title: string
  views: number
  likes: number
  comments: number
  publishedAt: string
  duration: string
  thumbnailUrl: string
}

// API types
export interface CompetitorResponse {
  id: number
  platform: string
  platformChannelId: string
  channelName: string
  channelUrl: string | null
  /** 구독자 수. **재지 못했으면 `null`** — 서버가 저장된 NULL 을 그대로 준다. */
  subscriberCount: number | null
  /** 총 조회수. **재지 못했으면 `null`** — `avgViews` 의 분자이기도 하다. */
  totalViews: number | null
  /** 영상 수. **재지 못했으면 `null`** — `avgViews` 의 분모이기도 하다. */
  videoCount: number | null
  /** 영상당 평균 조회수. **`videoCount` 를 모르거나 0 이면 `null`** — 나눌 대상이 없다. */
  avgViews: number | null
  profileImageUrl: string | null
  lastSyncedAt: string | null
  createdAt: string | null
}

export interface CompetitorListResponse {
  competitors: CompetitorResponse[]
  totalCount: number
}

export interface CompetitorSyncItemResponse {
  competitorId: number | null
  channelName: string
  platform: string
  status: 'SYNCED' | 'UNSUPPORTED' | 'FAILED' | string
  message: string | null
}

/** 백엔드 CompetitorController.POST /competitors/sync의 실제 응답. */
export interface CompetitorSyncResponse {
  requested: number
  synced: number
  unsupported: number
  failed: number
  results: CompetitorSyncItemResponse[]
  competitors: CompetitorResponse[]
  totalCount: number
}

export interface CreateCompetitorRequest {
  platform: string
  platformChannelId: string
  channelName: string
  channelUrl?: string
  subscriberCount?: number
  totalViews?: number
  videoCount?: number
  avgViews?: number
  profileImageUrl?: string
}

export interface UpdateCompetitorRequest {
  channelName?: string
  channelUrl?: string
  subscriberCount?: number
  totalViews?: number
  videoCount?: number
  avgViews?: number
  profileImageUrl?: string
}

export interface ChannelLookupRequest {
  platform: string
  query: string
}

export interface ChannelLookupResponse {
  found: boolean
  platformChannelId?: string
  channelName?: string
  channelUrl?: string
  /**
   * 구독자 수. **조회가 값을 주지 못했으면 `null`.**
   *
   * 구독자 수를 숨긴 YouTube 채널이 그렇다. 미리보기가 이 자리에 0 을 그리면
   * 사용자는 그것을 실제 구독자 수로 읽고 그대로 저장한다.
   */
  subscriberCount: number | null
  /** 총 조회수. **조회가 값을 주지 못했으면 `null`.** */
  totalViews: number | null
  /** 영상 수. **조회가 값을 주지 못했으면 `null`.** */
  videoCount: number | null
  profileImageUrl?: string
  platform?: string
  requiresManualInput: boolean
  message?: string
}

// Trend API types
export interface CompetitorTrendPoint {
  date: string
  /** 그날 관측한 구독자 수. **그날 조회가 값을 주지 못했으면 `null`.** */
  subscriberCount: number | null
  /** 그날의 영상당 평균 조회수. **그 스냅샷 영상 수가 0 이면 `null`.** */
  avgViews: number | null
  /** 그날 관측한 총 조회수. **조회가 값을 주지 못했으면 `null`.** */
  totalViews: number | null
}

export interface CompetitorTrendResponse {
  competitorId: number
  channelName: string
  data: CompetitorTrendPoint[]
}

// Benchmark API types
/**
 * 경쟁사와 나란히 놓이는 **내 채널 기준값**. 오염되면 비교 결과가 통째로 틀린다.
 *
 * 집계 행에는 `videoUploadId` 만 있어 플랫폼을 알 수 없다. 예전 서버는 필터 없이 더해
 * Tumblr 의 `total_notes`(노트 총합)를 조회수로, Pinterest 의 `SAVE`(저장)·
 * `PIN_CLICK`(클릭)을 참여 수로 썼다. 이제 수집하는 행이 없으면 `null` 이 온다.
 *
 * 스토어의 `MyStats` 는 이미 nullable 이고 `getComparison` 이 `comparable: false` 로
 * 처리한다 — 여기서 `?? 0` 을 하면 그 처리가 무력화된다.
 */
export interface MyChannelStats {
  /**
   * 구독자 수를 조회하는 채널만 합산한 값. **그런 채널이 하나도 없으면 `null`.**
   *
   * Threads·LinkedIn 어댑터는 팔로워 수를 묻지도 않고 `0` 을 박아 넣는다. 서버가 이제
   * 그 자리를 `null` 로 준다 — `?? 0` 으로 채우면 비교표가 **"구독자 0명"** 을 측정
   * 결과로 그리고, 스토어의 `myRanking` 이 나를 항상 꼴찌로 매긴다.
   *
   * 조회하는 채널이 있고 합이 0 이면 그 0 은 실측이다.
   */
  subscriberCount: number | null
  /**
   * 조회수를 수집하는 행만 합산한 값. **측정 행이 하나도 없으면 `null`.**
   *
   * `videoCount`·`avgViews` 와 같은 계약이다 — 행이 있고 합이 0 이면 그 0 은 실측이다.
   */
  totalViews: number | null
  /** 조회수가 측정된 업로드 수. */
  videoCount: number
  /** 영상당 평균 조회수. 측정된 영상이 없으면 `null`. */
  avgViews: number | null
  /** 좋아요·댓글·공유를 모두 수집하는 행에서만 계산한다. 분모가 없으면 `null`. */
  engagementRate: number | null
  /** 구독 증가를 수집하는 행이 없으면 `null` — `0` 은 "성장하지 않았다" 는 관측이 된다. */
  growthRate: number | null
}

export interface CompetitorBenchmark {
  id: number
  channelName: string
  platform: string
  /** 구독자 수. **재지 못했으면 `null`** — `CompetitorResponse` 와 같은 계약. */
  subscriberCount: number | null
  /** 총 조회수. **재지 못했으면 `null`** — `CompetitorResponse` 와 같은 계약. */
  totalViews: number | null
  /** 영상 수. **재지 못했으면 `null`** — `CompetitorResponse` 와 같은 계약. */
  videoCount: number | null
  /** 영상당 평균 조회수. **`videoCount` 를 모르거나 0 이면 `null`.** */
  avgViews: number | null
  /**
   * **항상 `null`** — 경쟁 채널의 참여율은 공개 API 로 산출할 수 없다.
   * 0 이 아니라 "모른다" 이며, 평균·차이·우위 판정에 섞으면 안 된다.
   */
  engagementRate: number | null
  /** [engagementRate] 가 `null` 인 이유. 화면이 그대로 보여줄 수 있는 문장. */
  engagementRateUnavailableReason: string | null
  /**
   * 30일 구독자 성장률(%). **관측된 두 시점이 없거나 기준일 구독자가 0 이면 `null`.**
   *
   * 예전 서버는 기간 내 수집 이력이 없어도 `0.0` 을 줬고, 화면은 그것을 "성장률 0%" 로
   * 그렸다 — 한 번도 수집한 적 없는 경쟁사가 "정체 중" 으로 보였다.
   *
   * `?? 0` 으로 채우지 말 것. 평균·차이·우위 판정에 섞으면 `avgEngagement` 때와 같은
   * 방식으로 없는 우위가 만들어진다.
   */
  growthRate: number | null
  profileImageUrl: string | null
}

export interface BenchmarkResponse {
  myStats: MyChannelStats
  competitors: CompetitorBenchmark[]
}

// AI Insight types
export interface CompetitorInsightResult {
  summary: string
  strengths: string[]
  weaknesses: string[]
   opportunities: string[]
   recommendations: string[]
}
