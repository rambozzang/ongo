package com.ongo.application.competitor

import com.ongo.domain.competitor.ChannelLookupPort
import com.ongo.domain.competitor.ChannelLookupResult
import com.ongo.domain.competitor.Competitor
import com.ongo.domain.competitor.CompetitorAnalyticsDaily
import com.ongo.domain.competitor.CompetitorRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

/** 경쟁 채널 한 건을 갱신한 결과. 성공/미지원/실패를 구분한다. */
enum class CompetitorRefreshStatus {
    /** 제공자에서 값을 받아 저장했다. */
    SYNCED,

    /** 제공자가 자동 조회를 지원하지 않거나 API 키가 없다. 재시도해도 같다. */
    UNSUPPORTED,

    /** 조회나 저장이 예외로 끝났다. 재시도할 여지가 있다. */
    FAILED,
}

data class CompetitorRefreshOutcome(
    val competitorId: Long?,
    val channelName: String,
    val platform: String,
    val status: CompetitorRefreshStatus,
    val message: String? = null,
)

data class CompetitorRefreshSummary(
    val requested: Int,
    val synced: Int,
    val unsupported: Int,
    val failed: Int,
    val outcomes: List<CompetitorRefreshOutcome>,
)

/**
 * 경쟁 채널 갱신 로직의 단일 출처.
 *
 * 스케줄러(배치)와 사용자 요청(HTTP)이 같은 동작을 해야 하는데, 양쪽에 같은 코드를
 * 복사하면 한쪽만 고쳐지는 순간 갈라진다. 특히 `avgViews` 계산과 일별 스냅샷 저장은
 * 빠뜨리기 쉽다.
 *
 * **조회(lookup)와 저장(persist)을 일부러 분리해 둔다.** 스케줄러는 HTTP 필터를 지나지
 * 않아 계정 동결 가드를 직접 보는데, 그 재확인이 **외부 조회와 저장 사이**에 들어가야
 * 하기 때문이다. 하나로 합치면 스케줄러가 가드를 끼워 넣을 자리를 잃는다.
 */
@Service
class CompetitorRefreshService(
    private val competitorRepository: CompetitorRepository,
    private val channelLookupPort: ChannelLookupPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 외부 제공자 조회만 한다. 쓰기는 하지 않는다. */
    fun lookup(competitor: Competitor): ChannelLookupResult =
        channelLookupPort.lookupChannel(
            platform = competitor.platform,
            query = competitor.platformChannelId,
        )

    /** 조회 결과를 저장한다. 외부 I/O 가 없어 호출자의 가드 재확인 직후에 두기 안전하다. */
    fun persist(competitor: Competitor, result: ChannelLookupResult, today: LocalDate = LocalDate.now()) {
        /*
         * **분모를 모르면 평균을 만들 수 없다.**
         *
         * 저장 컬럼(`avg_views`)은 non-null 이라 계산하지 못한 자리에 0 이 남지만,
         * 응답 경계의 `measuredAvgViews` 가 `videoCount` 를 근거로 그 0 을 걸러낸다.
         * 그래서 여기서는 계산 가능할 때만 실제 평균을 넣는다.
         */
        val videoCount = result.videoCount
        val totalViews = result.totalViews
        val avgViews = if (videoCount != null && videoCount > 0 && totalViews != null) {
            totalViews / videoCount
        } else {
            0
        }

        competitorRepository.update(
            competitor.copy(
                subscriberCount = result.subscriberCount,
                totalViews = result.totalViews,
                videoCount = result.videoCount,
                avgViews = avgViews,
                lastSyncedAt = LocalDateTime.now(),
            )
        )

        competitorRepository.upsertAnalytics(
            CompetitorAnalyticsDaily(
                competitorId = competitor.id!!,
                date = today,
                subscriberCount = result.subscriberCount,
                videoCount = result.videoCount,
                avgViews = avgViews,
                totalViews = result.totalViews,
            )
        )
    }

    /**
     * 조회와 저장을 한 번에 수행한다.
     *
     * 계정 동결 가드를 이미 통과한 경로(HTTP 요청)에서만 쓴다. 스케줄러는 사이에 가드
     * 재확인이 필요하므로 lookup/persist 를 직접 호출한다.
     */
    fun refresh(competitor: Competitor, today: LocalDate = LocalDate.now()): CompetitorRefreshOutcome {
        val base = { status: CompetitorRefreshStatus, message: String? ->
            CompetitorRefreshOutcome(
                competitorId = competitor.id,
                channelName = competitor.channelName,
                platform = competitor.platform,
                status = status,
                message = message,
            )
        }

        return try {
            val result = lookup(competitor)
            if (!result.found) {
                // 미지원 플랫폼이나 API 키 누락은 재시도해도 같은 답이 온다.
                // 실패와 섞으면 사용자가 "다시 눌러보라"는 잘못된 안내를 받는다.
                return base(CompetitorRefreshStatus.UNSUPPORTED, result.message)
            }
            persist(competitor, result, today)
            base(CompetitorRefreshStatus.SYNCED, null)
        } catch (e: Exception) {
            log.warn("경쟁자 동기화 실패 [{}]: {}", competitor.channelName, e.message)
            base(CompetitorRefreshStatus.FAILED, e.message)
        }
    }

    /** 여러 건을 갱신하고 건별 결과를 집계한다. 한 건이 실패해도 나머지는 계속한다. */
    fun refreshAll(competitors: List<Competitor>, today: LocalDate = LocalDate.now()): CompetitorRefreshSummary {
        val outcomes = competitors.map { refresh(it, today) }
        return CompetitorRefreshSummary(
            requested = competitors.size,
            synced = outcomes.count { it.status == CompetitorRefreshStatus.SYNCED },
            unsupported = outcomes.count { it.status == CompetitorRefreshStatus.UNSUPPORTED },
            failed = outcomes.count { it.status == CompetitorRefreshStatus.FAILED },
            outcomes = outcomes,
        )
    }
}
