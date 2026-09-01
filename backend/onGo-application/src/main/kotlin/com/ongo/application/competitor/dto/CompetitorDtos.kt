package com.ongo.application.competitor.dto

import java.time.LocalDateTime

data class CompetitorResponse(
    val id: Long,
    val platform: String,
    val platformChannelId: String,
    val channelName: String,
    val channelUrl: String?,
    /** 구독자 수. **재지 못했으면 `null`.** 저장 컬럼이 NULL 이거나 조회가 값을 못 준 경우다. */
    val subscriberCount: Long?,
    /** 총 조회수. **재지 못했으면 `null`** — [avgViews] 의 분자이기도 하다. */
    val totalViews: Long?,
    /** 영상 수. **재지 못했으면 `null`** — [avgViews] 의 분모이기도 하다. */
    val videoCount: Int?,
    /**
     * 영상당 평균 조회수. **[totalViews] 나 [videoCount] 를 모르거나 [videoCount] 가 0 이면
     * `null`** — 분자나 분모가 없으면 평균이 성립하지 않는다.
     *
     * 저장 모델(`Competitor.avgViews`)은 `Long` non-null 이라 0 이 들어 있다. 그 `0` 은
     * "평균 0회" 라는 관측이 아니라 **분모가 없어 계산하지 못한 자리**다. 응답에서
     * [videoCount] 를 근거로 갈라낸다(스키마는 그대로 둔다).
     *
     * 영상이 있고 총 조회수가 실제로 0 이면 그 `0` 은 관측이므로 그대로 `0` 이다.
     */
    val avgViews: Long?,
    val profileImageUrl: String?,
    val lastSyncedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
)

data class CompetitorListResponse(
    val competitors: List<CompetitorResponse>,
    val totalCount: Int,
)

/** 경쟁 채널 한 건의 동기화 결과. status 는 SYNCED / UNSUPPORTED / FAILED. */
data class CompetitorSyncItemResponse(
    val competitorId: Long?,
    val channelName: String,
    val platform: String,
    val status: String,
    val message: String? = null,
)

/**
 * 수동 동기화 결과.
 *
 * 건수를 그대로 노출한다. 예전에는 저장된 목록을 그대로 돌려주면서 "동기화가
 * 완료되었습니다"라고만 답해서, 실제로 아무것도 갱신되지 않아도 성공으로 보였다.
 */
data class CompetitorSyncResponse(
    val requested: Int,
    val synced: Int,
    val unsupported: Int,
    val failed: Int,
    val results: List<CompetitorSyncItemResponse>,
    val competitors: List<CompetitorResponse>,
    val totalCount: Int,
)

data class CreateCompetitorRequest(
    val platform: String,
    val platformChannelId: String,
    val channelName: String,
    val channelUrl: String? = null,
    /** 구독자 수. **재지 못했으면 `null`** — 0 은 실제 0 명이라는 관측이다. */
    val subscriberCount: Long? = null,
    /** 총 조회수. **모르면 `null`** — 0 은 조회가 없었다는 주장이다. */
    val totalViews: Long? = null,
    /** 영상 수. **모르면 `null`** — 0 은 실제로 영상이 없다는 주장이다. */
    val videoCount: Int? = null,
    val avgViews: Long = 0,
    val profileImageUrl: String? = null,
)

data class UpdateCompetitorRequest(
    val channelName: String? = null,
    val channelUrl: String? = null,
    val subscriberCount: Long? = null,
    val totalViews: Long? = null,
    val videoCount: Int? = null,
    val avgViews: Long? = null,
    val profileImageUrl: String? = null,
)

data class ChannelLookupRequest(
    val platform: String,
    val query: String,
)

data class ChannelLookupResponse(
    val found: Boolean,
    val platformChannelId: String? = null,
    val channelName: String? = null,
    val channelUrl: String? = null,
    /** 구독자 수. **재지 못했으면 `null`** — 0 은 실제 0 명이라는 관측이다. */
    val subscriberCount: Long? = null,
    /** 총 조회수. **조회가 값을 주지 못했으면 `null`.** */
    val totalViews: Long? = null,
    /** 영상 수. **조회가 값을 주지 못했으면 `null`.** */
    val videoCount: Int? = null,
    val profileImageUrl: String? = null,
    val platform: String? = null,
    val requiresManualInput: Boolean = false,
    val message: String? = null,
)

data class CompetitorTrendRequest(
    val competitorIds: List<Long> = emptyList(),
    val days: Int = 30,
)

data class CompetitorTrendPoint(
    val date: String,
    /**
     * 그날 스냅샷의 구독자 수. **그날 조회가 값을 주지 못했으면 `null`.**
     *
     * 구독자 수를 숨긴 채널은 이 값이 비어 있다. 화면이 그 자리에 0 을 찍으면 추이
     * 그래프가 **바닥까지 떨어졌다가 돌아오는 가짜 폭락**을 그린다.
     */
    val subscriberCount: Long?,
    /**
     * 그날의 영상당 평균 조회수. **그 스냅샷의 `videoCount` 가 0 이면 `null`.**
     *
     * `CompetitorResponse.avgViews` 와 같은 계약이다 — 분모가 없으면 평균이 없다.
     * 화면이 추이 그래프에 0 점을 찍으면 "그날 평균 0회" 라는 관측이 된다.
     */
    val avgViews: Long?,
    /** 그날 관측한 총 조회수. **조회가 값을 주지 못했으면 `null`.** */
    val totalViews: Long?,
)

data class CompetitorTrendResponse(
    val competitorId: Long,
    val channelName: String,
    val data: List<CompetitorTrendPoint>,
)

data class BenchmarkResponse(
    val myStats: MyChannelStats,
    val competitors: List<CompetitorBenchmark>,
)

/**
 * 경쟁사와 나란히 놓이는 **내 채널 기준값**. 오염되면 비교 결과가 통째로 틀린다.
 *
 * 집계 행에는 `videoUploadId` 만 있어 플랫폼을 알 수 없다. 그래서 예전에는 필터 없이
 * 더했고, Tumblr 의 `total_notes`(노트 총합)가 조회수로, Pinterest 의 `SAVE`(저장)·
 * `PIN_CLICK`(클릭)이 참여 수로 들어갔다.
 */
data class MyChannelStats(
    /**
     * 구독자 수를 조회하는 채널만 합산한 값. **그런 채널이 하나도 없으면 `null`.**
     *
     * `ThreadsClient.kt:205` 와 `LinkedInClient.kt:264` 는 팔로워 수를 묻지도 않고
     * `subscriberCount = 0` 을 박아 넣는다. 그 `0` 을 그대로 내보내면 비교표가
     * **"구독자 0명"** 을 측정 결과로 그린다.
     *
     * [totalViews]·[avgViews] 와 같은 계약이다 — 조회하는 채널이 있고 합이 0 이면
     * 그 0 은 실측이다.
     */
    val subscriberCount: Long?,
    /**
     * 조회수를 수집하는 행만 합산한 값. **측정 행이 하나도 없으면 `null`.**
     *
     * [videoCount]·[avgViews] 와 같은 계약이다 — 행이 있고 합이 0 이면 그 0 은 실측이다.
     */
    val totalViews: Long?,
    /** 조회수가 측정된 업로드 수. */
    val videoCount: Int,
    /** 영상당 평균 조회수. 측정된 영상이 없으면 `null`. */
    val avgViews: Long?,
    /** 좋아요·댓글·공유를 모두 수집하는 행에서만 계산한다. 분모가 없으면 `null`. */
    val engagementRate: Double?,
    /**
     * 최근 30일 구독 증가율(%). **구독 증가를 수집하는 행이 없으면 `null`.**
     *
     * `subscriber_gained` 를 조회하는 어댑터는 `YouTubeClient` 하나뿐이다. `0.0` 은
     * "성장하지 않았다" 는 관측이 된다.
     */
    val growthRate: Double?,
)

data class CompetitorBenchmark(
    val id: Long,
    val channelName: String,
    val platform: String,
    /** 구독자 수. **재지 못했으면 `null`.** 저장 컬럼이 NULL 이거나 조회가 값을 못 준 경우다. */
    val subscriberCount: Long?,
    /** 총 조회수. **재지 못했으면 `null`** — `CompetitorResponse` 와 같은 계약. */
    val totalViews: Long?,
    /** 영상 수. **재지 못했으면 `null`** — `CompetitorResponse` 와 같은 계약. */
    val videoCount: Int?,
    /** 영상당 평균 조회수. **[videoCount] 가 `null` 이거나 0 이면 `null`** — 나눌 대상이 없다. */
    val avgViews: Long?,
    /**
     * 경쟁자 참여율(%). **항상 `null` 이다 — 측정할 수단이 없다.**
     *
     * 참여율은 좋아요·댓글·공유를 조회수로 나눈 값인데, 우리가 남의 채널에 대해 공개
     * API 로 얻는 것은 구독자 수·총 조회수·영상 수뿐이다. 분자에 해당하는 지표가 없다.
     *
     * 예전에는 이 자리에 `0.0` 을 넣었다. 주석으로는 "정확히 알 수 없음"이라고 적혀
     * 있었지만 **값은 측정값과 구분되지 않는 모양으로 나갔다.** 프론트 비교표가 그대로
     * "참여율: 나 4.2% vs 경쟁자 0.0%" 를 그렸고, 크리에이터는 추적하는 모든 경쟁사를
     * 참여율에서 압도한다고 믿게 됐다. 있지도 않은 경쟁 우위를 근거로 전략을 세운다.
     *
     * `null` 은 "0 이다" 가 아니라 **"모른다"** 다. 소비자는 평균·차이·우위 판정에
     * 섞지 말고 "측정 불가"로 표시해야 한다. 대시보드 증감률([MetricChange])과
     * 플랫폼 지표 가용성([com.ongo.application.analytics.PlatformMetricAvailability])이
     * 쓰는 것과 같은 정책이다.
     *
     * 언젠가 산출 수단이 생기면 그때 실제 값을 넣는다. 그 전까지 이 자리는 비어 있다.
     */
    val engagementRate: Double?,
    /**
     * [engagementRate] 가 `null` 인 이유. 화면이 그대로 보여줄 수 있는 문장이다.
     *
     * 값만 `null` 로 두면 "아직 안 불러왔다" 와 "구할 수 없다" 가 구분되지 않는다.
     */
    val engagementRateUnavailableReason: String?,
    /**
     * 30일 구독자 성장률(%). **관측된 두 시점이 없거나 기준일 구독자가 0 이면 `null`.**
     *
     * 예전에는 기간 내 수집 이력이 없어도 `0.0` 을 만들었다. 화면은 그것을 "성장률 0%" 로
     * 그렸고, 한 번도 수집한 적 없는 경쟁사가 "정체 중" 으로 보였다.
     *
     * 두 시점이 실제로 관측됐고 값이 같을 때만 `0.0` 이 관측 결과다. 소비자는 `null` 을
     * 평균·차이·우위 판정에 섞지 말아야 한다 — [engagementRate] 와 같은 정책이다.
     */
    val growthRate: Double?,
    val profileImageUrl: String?,
)
