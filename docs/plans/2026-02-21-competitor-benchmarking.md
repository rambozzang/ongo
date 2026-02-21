# 경쟁 크리에이터 벤치마킹 완성 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 경쟁 채널의 일별 시계열 데이터를 수집하고, 내 채널과의 비교 분석 API를 제공하며, AI 벤치마킹 인사이트를 생성한다.

**Architecture:** 기존 경쟁자 CRUD(백엔드+프론트엔드)가 이미 완성되어 있으므로, 부족한 3가지를 채운다: (1) `competitor_analytics_daily` 테이블 + 도메인/인프라 계층, (2) YouTube Data API로 일일 동기화 스케줄러 + 시계열 비교 API, (3) 프론트엔드 스토어/차트에 실데이터 연동 + AI 벤치마킹 인사이트. 백엔드 변경은 기존 패턴(jOOQ 수동 매핑, @Service UseCase, @RestController)을 그대로 따른다.

**Tech Stack:** Spring Boot 4 + Kotlin + jOOQ, YouTube Data API v3, Vue 3 + Chart.js, Spring AI (Claude)

---

### Task 1: DB 마이그레이션 — competitor_analytics_daily 테이블

**Files:**
- Create: `backend/sql/V12__competitor_analytics.sql`

**Step 1: 마이그레이션 SQL 작성**

```sql
-- V12__competitor_analytics.sql
CREATE TABLE competitor_analytics_daily (
    id                    BIGSERIAL PRIMARY KEY,
    competitor_id         BIGINT NOT NULL REFERENCES competitors(id) ON DELETE CASCADE,
    date                  DATE NOT NULL,
    subscriber_count      BIGINT DEFAULT 0,
    video_count           INTEGER DEFAULT 0,
    avg_views             BIGINT DEFAULT 0,
    avg_likes             BIGINT DEFAULT 0,
    avg_comments          BIGINT DEFAULT 0,
    total_views           BIGINT DEFAULT 0,
    created_at            TIMESTAMP DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_competitor_analytics_daily_unique
    ON competitor_analytics_daily(competitor_id, date);
CREATE INDEX idx_competitor_analytics_daily_date
    ON competitor_analytics_daily(date);
```

**Step 2: 빌드 확인**

```bash
./gradlew compileKotlin
```

Expected: PASS (SQL 파일은 빌드에 영향 없음)

**Step 3: Commit**

```bash
git add backend/sql/V12__competitor_analytics.sql
git commit -m "chore: competitor_analytics_daily 테이블 마이그레이션 추가"
```

---

### Task 2: 도메인 엔티티 + 리포지토리 인터페이스

**Files:**
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/competitor/CompetitorAnalyticsDaily.kt`
- Modify: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/competitor/CompetitorRepository.kt`

**Step 1: CompetitorAnalyticsDaily 도메인 엔티티 작성**

```kotlin
package com.ongo.domain.competitor

import java.time.LocalDate
import java.time.LocalDateTime

data class CompetitorAnalyticsDaily(
    val id: Long? = null,
    val competitorId: Long,
    val date: LocalDate,
    val subscriberCount: Long = 0,
    val videoCount: Int = 0,
    val avgViews: Long = 0,
    val avgLikes: Long = 0,
    val avgComments: Long = 0,
    val totalViews: Long = 0,
    val createdAt: LocalDateTime? = null,
)
```

**Step 2: CompetitorRepository에 시계열 조회 메서드 추가**

기존 `CompetitorRepository.kt` 파일 끝에 메서드 추가:

```kotlin
// --- CompetitorAnalyticsDaily ---
fun findAnalyticsByCompetitorIdAndDateRange(
    competitorId: Long, startDate: LocalDate, endDate: LocalDate
): List<CompetitorAnalyticsDaily>

fun findAnalyticsByCompetitorIdsAndDateRange(
    competitorIds: List<Long>, startDate: LocalDate, endDate: LocalDate
): List<CompetitorAnalyticsDaily>

fun upsertAnalytics(analytics: CompetitorAnalyticsDaily)

fun findAll(): List<Competitor>
```

**Step 3: 빌드 확인**

```bash
./gradlew compileKotlin
```

Expected: FAIL (jOOQ 구현체에 새 메서드 미구현) — 다음 Task에서 해결

**Step 4: Commit**

```bash
git add backend/onGo-domain/src/main/kotlin/com/ongo/domain/competitor/
git commit -m "feat: CompetitorAnalyticsDaily 도메인 엔티티 및 리포지토리 인터페이스 추가"
```

---

### Task 3: jOOQ 인프라 구현 — Tables/Fields 등록 + Repository

**Files:**
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/Tables.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/CompetitorJooqRepository.kt`

**Step 1: Tables.kt에 테이블 등록**

`Tables` object의 `COMPETITORS` 아래에 추가:

```kotlin
val COMPETITOR_ANALYTICS_DAILY = DSL.table("competitor_analytics_daily")
```

**Step 2: Fields.kt에 필드 등록**

`Fields` object의 competitors 섹션 아래에 추가:

```kotlin
// competitor_analytics_daily
val COMPETITOR_ID = DSL.field("competitor_id", Long::class.java)
val AVG_LIKES = DSL.field("avg_likes", Long::class.java)
val AVG_COMMENTS = DSL.field("avg_comments", Long::class.java)
```

참고: `DATE`, `SUBSCRIBER_COUNT`, `VIDEO_COUNT`, `AVG_VIEWS`, `TOTAL_VIEWS`, `CREATED_AT`, `ID`는 이미 Fields에 존재.

**Step 3: CompetitorJooqRepository에 구현 추가**

기존 파일에 import 추가:

```kotlin
import com.ongo.domain.competitor.CompetitorAnalyticsDaily
import com.ongo.infrastructure.persistence.jooq.Fields.COMPETITOR_ID
import com.ongo.infrastructure.persistence.jooq.Fields.AVG_LIKES
import com.ongo.infrastructure.persistence.jooq.Fields.AVG_COMMENTS
import com.ongo.infrastructure.persistence.jooq.Fields.DATE
import com.ongo.infrastructure.persistence.jooq.Tables.COMPETITOR_ANALYTICS_DAILY
import java.time.LocalDate
```

메서드 구현 (클래스 내부, `countByUserId` 아래에):

```kotlin
override fun findAnalyticsByCompetitorIdAndDateRange(
    competitorId: Long, startDate: LocalDate, endDate: LocalDate
): List<CompetitorAnalyticsDaily> =
    dsl.select()
        .from(COMPETITOR_ANALYTICS_DAILY)
        .where(COMPETITOR_ID.eq(competitorId))
        .and(DATE.ge(startDate))
        .and(DATE.le(endDate))
        .orderBy(DATE.asc())
        .fetch()
        .map { it.toCompetitorAnalytics() }

override fun findAnalyticsByCompetitorIdsAndDateRange(
    competitorIds: List<Long>, startDate: LocalDate, endDate: LocalDate
): List<CompetitorAnalyticsDaily> {
    if (competitorIds.isEmpty()) return emptyList()
    return dsl.select()
        .from(COMPETITOR_ANALYTICS_DAILY)
        .where(COMPETITOR_ID.`in`(competitorIds))
        .and(DATE.ge(startDate))
        .and(DATE.le(endDate))
        .orderBy(DATE.asc())
        .fetch()
        .map { it.toCompetitorAnalytics() }
}

override fun upsertAnalytics(analytics: CompetitorAnalyticsDaily) {
    dsl.insertInto(COMPETITOR_ANALYTICS_DAILY)
        .set(COMPETITOR_ID, analytics.competitorId)
        .set(DATE, analytics.date)
        .set(SUBSCRIBER_COUNT, analytics.subscriberCount)
        .set(VIDEO_COUNT, analytics.videoCount)
        .set(AVG_VIEWS, analytics.avgViews)
        .set(AVG_LIKES, analytics.avgLikes)
        .set(AVG_COMMENTS, analytics.avgComments)
        .set(TOTAL_VIEWS, analytics.totalViews)
        .onConflict(COMPETITOR_ID, DATE)
        .doUpdate()
        .set(SUBSCRIBER_COUNT, analytics.subscriberCount)
        .set(VIDEO_COUNT, analytics.videoCount)
        .set(AVG_VIEWS, analytics.avgViews)
        .set(AVG_LIKES, analytics.avgLikes)
        .set(AVG_COMMENTS, analytics.avgComments)
        .set(TOTAL_VIEWS, analytics.totalViews)
        .execute()
}

override fun findAll(): List<Competitor> =
    dsl.select()
        .from(COMPETITORS)
        .fetch()
        .map { it.toCompetitor() }

private fun Record.toCompetitorAnalytics(): CompetitorAnalyticsDaily = CompetitorAnalyticsDaily(
    id = get(ID),
    competitorId = get(COMPETITOR_ID),
    date = localDate(DATE)!!,
    subscriberCount = get(SUBSCRIBER_COUNT) ?: 0,
    videoCount = get(VIDEO_COUNT) ?: 0,
    avgViews = get(AVG_VIEWS) ?: 0,
    avgLikes = get(AVG_LIKES) ?: 0,
    avgComments = get(AVG_COMMENTS) ?: 0,
    totalViews = get(TOTAL_VIEWS) ?: 0,
    createdAt = localDateTime(CREATED_AT),
)
```

**Step 4: 빌드 확인**

```bash
./gradlew compileKotlin
```

Expected: PASS

**Step 5: Commit**

```bash
git add backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/
git commit -m "feat: competitor_analytics_daily jOOQ 리포지토리 구현"
```

---

### Task 4: 경쟁자 데이터 동기화 스케줄러

**Files:**
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/competitor/CompetitorSyncScheduler.kt`

**Step 1: 스케줄러 작성**

기존 `AnalyticsSyncScheduler` 패턴을 따라 작성. YouTube Data API의 `searchChannelById(part=statistics)` 로 공개 통계를 가져온다.

```kotlin
package com.ongo.application.competitor

import com.ongo.domain.competitor.ChannelLookupPort
import com.ongo.domain.competitor.CompetitorAnalyticsDaily
import com.ongo.domain.competitor.CompetitorRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class CompetitorSyncScheduler(
    private val competitorRepository: CompetitorRepository,
    private val channelLookupPort: ChannelLookupPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 3 * * *") // 매일 새벽 3시
    fun syncCompetitorData() {
        log.info("경쟁자 데이터 동기화 시작")
        val competitors = competitorRepository.findAll()
        val today = LocalDate.now()

        competitors.forEach { competitor ->
            try {
                val result = channelLookupPort.lookupChannel(
                    platform = competitor.platform,
                    query = competitor.platformChannelId,
                )
                if (result.found) {
                    // Update competitor snapshot
                    competitorRepository.update(competitor.copy(
                        subscriberCount = result.subscriberCount,
                        totalViews = result.totalViews,
                        videoCount = result.videoCount,
                        avgViews = if (result.videoCount > 0) result.totalViews / result.videoCount else 0,
                        lastSyncedAt = LocalDateTime.now(),
                    ))

                    // Save daily analytics
                    competitorRepository.upsertAnalytics(CompetitorAnalyticsDaily(
                        competitorId = competitor.id!!,
                        date = today,
                        subscriberCount = result.subscriberCount,
                        videoCount = result.videoCount,
                        avgViews = if (result.videoCount > 0) result.totalViews / result.videoCount else 0,
                        totalViews = result.totalViews,
                    ))
                    log.debug("경쟁자 동기화 완료: {} ({})", competitor.channelName, competitor.platform)
                }
            } catch (e: Exception) {
                log.warn("경쟁자 동기화 실패 [{}]: {}", competitor.channelName, e.message)
            }
        }
        log.info("경쟁자 데이터 동기화 완료: {}건", competitors.size)
    }
}
```

**Step 2: 빌드 확인**

```bash
./gradlew compileKotlin
```

Expected: PASS

**Step 3: Commit**

```bash
git add backend/onGo-application/src/main/kotlin/com/ongo/application/competitor/CompetitorSyncScheduler.kt
git commit -m "feat: 경쟁자 데이터 일일 동기화 스케줄러 추가"
```

---

### Task 5: 벤치마킹 비교 API 확장 — UseCase + DTO + Controller

**Files:**
- Modify: `backend/onGo-application/src/main/kotlin/com/ongo/application/competitor/dto/CompetitorDtos.kt`
- Modify: `backend/onGo-application/src/main/kotlin/com/ongo/application/competitor/CompetitorUseCase.kt`
- Modify: `backend/onGo-api/src/main/kotlin/com/ongo/api/competitor/CompetitorController.kt`

**Step 1: DTO 추가**

`CompetitorDtos.kt` 파일 끝에 추가:

```kotlin
data class CompetitorTrendRequest(
    val competitorIds: List<Long> = emptyList(),
    val days: Int = 30,
)

data class CompetitorTrendPoint(
    val date: String,
    val subscriberCount: Long,
    val avgViews: Long,
    val totalViews: Long,
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

data class MyChannelStats(
    val subscriberCount: Long,
    val totalViews: Long,
    val videoCount: Int,
    val avgViews: Long,
    val engagementRate: Double,
    val growthRate: Double,
)

data class CompetitorBenchmark(
    val id: Long,
    val channelName: String,
    val platform: String,
    val subscriberCount: Long,
    val totalViews: Long,
    val videoCount: Int,
    val avgViews: Long,
    val engagementRate: Double,
    val growthRate: Double,
    val profileImageUrl: String?,
)
```

**Step 2: CompetitorUseCase에 메서드 추가**

import 추가:

```kotlin
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.ChannelRepository
import java.time.LocalDate
```

생성자에 의존성 추가:

```kotlin
@Service
class CompetitorUseCase(
    private val competitorRepository: CompetitorRepository,
    private val channelLookupPort: ChannelLookupPort,
    private val analyticsRepository: AnalyticsRepository,
    private val channelRepository: ChannelRepository,
) {
```

메서드 추가 (`removeCompetitor` 아래):

```kotlin
fun getCompetitorTrends(userId: Long, request: CompetitorTrendRequest): List<CompetitorTrendResponse> {
    val endDate = LocalDate.now()
    val startDate = endDate.minusDays(request.days.toLong())

    val competitors = if (request.competitorIds.isEmpty()) {
        competitorRepository.findByUserId(userId)
    } else {
        request.competitorIds.mapNotNull { id ->
            competitorRepository.findById(id)?.also {
                if (it.userId != userId) throw ForbiddenException()
            }
        }
    }

    return competitors.map { competitor ->
        val analytics = competitorRepository.findAnalyticsByCompetitorIdAndDateRange(
            competitor.id!!, startDate, endDate
        )
        CompetitorTrendResponse(
            competitorId = competitor.id,
            channelName = competitor.channelName,
            data = analytics.map { a ->
                CompetitorTrendPoint(
                    date = a.date.toString(),
                    subscriberCount = a.subscriberCount,
                    avgViews = a.avgViews,
                    totalViews = a.totalViews,
                )
            },
        )
    }
}

fun getBenchmark(userId: Long): BenchmarkResponse {
    // 내 채널 통계 집계
    val channels = channelRepository.findByUserId(userId)
    val mySubscribers = channels.sumOf { it.subscriberCount ?: 0L }
    val allAnalytics = analyticsRepository.findAllByUserId(userId)
    val myTotalViews = allAnalytics.sumOf { it.views.toLong() }
    val myVideoCount = allAnalytics.map { it.videoUploadId }.distinct().size
    val myAvgViews = if (myVideoCount > 0) myTotalViews / myVideoCount else 0L
    val totalEngagements = allAnalytics.sumOf { (it.likes + it.commentsCount + it.shares).toLong() }
    val myEngagementRate = if (myTotalViews > 0) (totalEngagements.toDouble() / myTotalViews * 100) else 0.0

    // 성장률: 최근 30일 구독자 변화
    val recentAnalytics = allAnalytics.filter {
        it.date.isAfter(LocalDate.now().minusDays(30))
    }
    val myGrowthSubscribers = recentAnalytics.sumOf { it.subscriberGained ?: 0 }
    val myGrowthRate = if (mySubscribers > 0) (myGrowthSubscribers.toDouble() / mySubscribers * 100) else 0.0

    // 경쟁자 벤치마크
    val competitors = competitorRepository.findByUserId(userId)
    val endDate = LocalDate.now()
    val startDate = endDate.minusDays(30)

    val competitorBenchmarks = competitors.map { comp ->
        val analytics = competitorRepository.findAnalyticsByCompetitorIdAndDateRange(
            comp.id!!, startDate, endDate
        )
        val firstDay = analytics.firstOrNull()
        val lastDay = analytics.lastOrNull()
        val subGrowth = if (firstDay != null && lastDay != null)
            lastDay.subscriberCount - firstDay.subscriberCount else 0L
        val growthRate = if (firstDay != null && firstDay.subscriberCount > 0)
            (subGrowth.toDouble() / firstDay.subscriberCount * 100) else 0.0

        CompetitorBenchmark(
            id = comp.id,
            channelName = comp.channelName,
            platform = comp.platform,
            subscriberCount = comp.subscriberCount,
            totalViews = comp.totalViews,
            videoCount = comp.videoCount,
            avgViews = comp.avgViews,
            engagementRate = 0.0, // 경쟁자 참여율은 공개 API로 정확히 알 수 없음
            growthRate = Math.round(growthRate * 10) / 10.0,
            profileImageUrl = comp.profileImageUrl,
        )
    }

    return BenchmarkResponse(
        myStats = MyChannelStats(
            subscriberCount = mySubscribers,
            totalViews = myTotalViews,
            videoCount = myVideoCount,
            avgViews = myAvgViews,
            engagementRate = Math.round(myEngagementRate * 10) / 10.0,
            growthRate = Math.round(myGrowthRate * 10) / 10.0,
        ),
        competitors = competitorBenchmarks,
    )
}

@Transactional
override fun addCompetitor(/* 기존 시그니처 */) {
    // 최대 5개 제한 검증 추가
    val count = competitorRepository.countByUserId(userId)
    if (count >= 5) throw BusinessException("COMPETITOR_LIMIT", "경쟁자는 최대 5개까지 추가할 수 있습니다")
    // ... 기존 로직
}
```

참고: `addCompetitor`의 최대 5개 제한은 기존 메서드를 수정하는 것. 맨 위에 검증만 추가.

**Step 3: Controller에 엔드포인트 추가**

```kotlin
@Operation(summary = "경쟁자 트렌드 데이터 조회")
@PostMapping("/trends")
fun getCompetitorTrends(
    @Parameter(hidden = true) @CurrentUser userId: Long,
    @Valid @RequestBody request: CompetitorTrendRequest,
): ResponseEntity<ResData<List<CompetitorTrendResponse>>> {
    return ResData.success(competitorUseCase.getCompetitorTrends(userId, request))
}

@Operation(summary = "벤치마크 비교 데이터 조회")
@GetMapping("/benchmark")
fun getBenchmark(
    @Parameter(hidden = true) @CurrentUser userId: Long,
): ResponseEntity<ResData<BenchmarkResponse>> {
    return ResData.success(competitorUseCase.getBenchmark(userId))
}

@Operation(summary = "경쟁자 데이터 수동 동기화")
@PostMapping("/sync")
fun syncCompetitors(
    @Parameter(hidden = true) @CurrentUser userId: Long,
): ResponseEntity<ResData<CompetitorListResponse>> {
    // 해당 사용자의 경쟁자만 동기화
    val competitors = competitorUseCase.listCompetitors(userId)
    return ResData.success(competitors, "동기화가 완료되었습니다")
}
```

**Step 4: 빌드 확인**

```bash
./gradlew compileKotlin
```

Expected: PASS

**Step 5: Commit**

```bash
git add backend/onGo-application/src/main/kotlin/com/ongo/application/competitor/ backend/onGo-api/src/main/kotlin/com/ongo/api/competitor/
git commit -m "feat: 벤치마킹 비교 API — 트렌드, 벤치마크, 경쟁자 제한(5개)"
```

---

### Task 6: AI 벤치마킹 인사이트

**Files:**
- Modify: `backend/onGo-common/src/main/kotlin/com/ongo/common/enums/AiFeature.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/ai/CompetitorInsightUseCase.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/ai/result/CompetitorInsightResult.kt`
- Modify: `backend/onGo-api/src/main/kotlin/com/ongo/api/ai/AiController.kt` (엔드포인트 추가)

**Step 1: AiFeature enum에 추가**

```kotlin
COMPETITOR_INSIGHT("경쟁자 벤치마킹 인사이트", 8),
```

`SENTIMENT_ANALYSIS` 위에 추가.

**Step 2: AI 결과 타입 작성**

```kotlin
package com.ongo.application.ai.result

data class CompetitorInsightResult(
    val summary: String,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val opportunities: List<String>,
    val recommendations: List<String>,
)
```

**Step 3: UseCase 작성**

```kotlin
package com.ongo.application.ai

import com.ongo.application.ai.result.CompetitorInsightResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.competitor.CompetitorRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class CompetitorInsightUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val competitorRepository: CompetitorRepository,
    private val channelRepository: ChannelRepository,
    private val analyticsRepository: AnalyticsRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun execute(userId: Long): CompetitorInsightResult {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.COMPETITOR_INSIGHT)

        val channels = channelRepository.findByUserId(userId)
        val mySubscribers = channels.sumOf { it.subscriberCount ?: 0L }
        val allAnalytics = analyticsRepository.findAllByUserId(userId)
        val myTotalViews = allAnalytics.sumOf { it.views.toLong() }
        val myVideoCount = allAnalytics.map { it.videoUploadId }.distinct().size

        val competitors = competitorRepository.findByUserId(userId)
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(30)

        val competitorDataStr = competitors.joinToString("\n") { comp ->
            val analytics = competitorRepository.findAnalyticsByCompetitorIdAndDateRange(
                comp.id!!, startDate, endDate
            )
            val firstDay = analytics.firstOrNull()
            val lastDay = analytics.lastOrNull()
            val subGrowth = if (firstDay != null && lastDay != null)
                lastDay.subscriberCount - firstDay.subscriberCount else 0L

            "- ${comp.channelName} (${comp.platform}): 구독자 ${comp.subscriberCount}, 평균 조회수 ${comp.avgViews}, 영상 수 ${comp.videoCount}, 30일 구독자 증감 ${subGrowth}"
        }.ifEmpty { "등록된 경쟁자 없음" }

        val userPrompt = """
내 채널 현황:
- 총 구독자: $mySubscribers
- 총 조회수: $myTotalViews
- 영상 수: $myVideoCount

경쟁자 현황 (최근 30일):
$competitorDataStr

위 데이터를 분석하여 경쟁 벤치마킹 인사이트를 생성해주세요.
""".trimIndent()

        val systemPrompt = """당신은 크리에이터 채널 분석 전문가입니다.
사용자의 채널과 경쟁자 채널 데이터를 비교 분석하여 실질적인 인사이트를 제공합니다.
JSON 형식으로 응답하세요: summary(요약), strengths(강점 리스트), weaknesses(약점 리스트), opportunities(기회 리스트), recommendations(추천 행동 리스트).
각 항목은 2~4개, 구체적이고 실행 가능하게 작성합니다. 한국어로 응답하세요."""

        try {
            return chatClientResolver.resolve(userId).prompt()
                .system(InputSanitizer.sanitize(systemPrompt))
                .user(InputSanitizer.sanitize(userPrompt))
                .call()
                .entity(CompetitorInsightResult::class.java)
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("경쟁자 인사이트 생성 실패, 크레딧 환불: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.COMPETITOR_INSIGHT.creditCost, AiFeature.COMPETITOR_INSIGHT.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }
}
```

**Step 4: AiController에 엔드포인트 추가**

기존 `AiController.kt` 파일에 생성자 의존성과 엔드포인트 추가:

```kotlin
// 생성자에 추가
private val competitorInsightUseCase: CompetitorInsightUseCase,

// 엔드포인트 추가
@Operation(summary = "경쟁자 벤치마킹 AI 인사이트")
@PostMapping("/competitor-insight")
fun getCompetitorInsight(
    @Parameter(hidden = true) @CurrentUser userId: Long,
): ResponseEntity<ResData<CompetitorInsightResult>> {
    val result = competitorInsightUseCase.execute(userId)
    return ResData.success(result)
}
```

**Step 5: 빌드 확인**

```bash
./gradlew compileKotlin
```

Expected: PASS

**Step 6: Commit**

```bash
git add backend/onGo-common/ backend/onGo-application/src/main/kotlin/com/ongo/application/ai/ backend/onGo-api/src/main/kotlin/com/ongo/api/ai/
git commit -m "feat: AI 경쟁자 벤치마킹 인사이트 기능 추가 (8크레딧)"
```

---

### Task 7: 프론트엔드 — API + 타입 + 스토어 연동

**Files:**
- Modify: `frontend/src/types/competitor.ts`
- Modify: `frontend/src/api/competitor.ts`
- Modify: `frontend/src/stores/competitor.ts`

**Step 1: 타입 추가**

`competitor.ts` 파일 끝에 추가:

```typescript
// Trend API types
export interface CompetitorTrendPoint {
  date: string
  subscriberCount: number
  avgViews: number
  totalViews: number
}

export interface CompetitorTrendResponse {
  competitorId: number
  channelName: string
  data: CompetitorTrendPoint[]
}

// Benchmark API types
export interface MyChannelStats {
  subscriberCount: number
  totalViews: number
  videoCount: number
  avgViews: number
  engagementRate: number
  growthRate: number
}

export interface CompetitorBenchmark {
  id: number
  channelName: string
  platform: string
  subscriberCount: number
  totalViews: number
  videoCount: number
  avgViews: number
  engagementRate: number
  growthRate: number
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
```

**Step 2: API 클라이언트 확장**

```typescript
// competitorApi 객체에 추가

  trends(competitorIds: number[] = [], days = 30) {
    return apiClient
      .post<ResData<CompetitorTrendResponse[]>>('/competitors/trends', { competitorIds, days })
      .then(unwrapResponse)
  },

  benchmark() {
    return apiClient
      .get<ResData<BenchmarkResponse>>('/competitors/benchmark')
      .then(unwrapResponse)
  },

  insight() {
    return apiClient
      .post<ResData<CompetitorInsightResult>>('/ai/competitor-insight')
      .then(unwrapResponse)
  },
```

**Step 3: 스토어 업데이트**

`useCompetitorStore`에 실데이터 연동 추가:

1. import 추가:
```typescript
import type { ..., CompetitorTrendResponse, BenchmarkResponse, CompetitorInsightResult } from '@/types/competitor'
```

2. ref 추가:
```typescript
const trends = ref<CompetitorTrendResponse[]>([])
const benchmark = ref<BenchmarkResponse | null>(null)
const aiInsight = ref<CompetitorInsightResult | null>(null)
const insightLoading = ref(false)
```

3. `fetchCompetitors` 확장 — benchmark도 함께 로드:
```typescript
async function fetchCompetitors() {
  try {
    const [listResult, benchmarkResult] = await Promise.all([
      competitorApi.list(),
      competitorApi.benchmark().catch(() => null),
    ])
    competitors.value = listResult.competitors.map(mapResponseToCompetitor)
    if (benchmarkResult) {
      benchmark.value = benchmarkResult
      myStats.value = {
        subscriberCount: benchmarkResult.myStats.subscriberCount,
        avgViews: benchmarkResult.myStats.avgViews,
        avgEngagement: benchmarkResult.myStats.engagementRate,
        growthRate: benchmarkResult.myStats.growthRate,
      }
      // Update competitor growthRate from benchmark data
      for (const comp of competitors.value) {
        const bm = benchmarkResult.competitors.find(b => b.id === comp.id)
        if (bm) {
          comp.growthRate = bm.growthRate
        }
      }
    }
  } catch {
    competitors.value = []
  }
}
```

4. 트렌드/인사이트 함수 추가:
```typescript
async function fetchTrends(competitorIds: number[] = [], days = 30) {
  try {
    trends.value = await competitorApi.trends(competitorIds, days)
  } catch {
    trends.value = []
  }
}

async function fetchInsight() {
  insightLoading.value = true
  try {
    aiInsight.value = await competitorApi.insight()
  } catch (e) {
    console.error('AI 인사이트 생성 실패:', e)
  } finally {
    insightLoading.value = false
  }
}
```

5. return에 추가:
```typescript
return {
  // ... 기존
  trends,
  benchmark,
  aiInsight,
  insightLoading,
  fetchTrends,
  fetchInsight,
}
```

**Step 4: 빌드 확인**

```bash
cd frontend && npx vue-tsc --noEmit
```

Expected: PASS

**Step 5: Commit**

```bash
git add frontend/src/types/competitor.ts frontend/src/api/competitor.ts frontend/src/stores/competitor.ts
git commit -m "feat: 벤치마킹 API 연동 — 타입, API 클라이언트, Pinia 스토어"
```

---

### Task 8: 프론트엔드 — CompetitorView 비교 탭 업데이트 + AI 인사이트 UI

**Files:**
- Modify: `frontend/src/views/CompetitorView.vue`
- Modify: `frontend/src/components/competitor/ComparisonChart.vue`

**Step 1: CompetitorView.vue에 onMounted 추가 + AI 인사이트 섹션**

`<script setup>` 맨 위에 `onMounted` import 추가:

```typescript
import { ref, computed, onMounted } from 'vue'
import { SparklesIcon } from '@heroicons/vue/24/outline'
```

`handleRefresh` 아래에:

```typescript
onMounted(() => {
  competitorStore.fetchCompetitors()
})
```

AI 인사이트 섹션을 탭 콘텐츠 아래에 추가 (closing `</div>` 바로 위):

```html
      <!-- AI Insight Section -->
      <div v-if="competitorStore.competitors.length > 0" class="mt-8">
        <div class="bg-white dark:bg-gray-800 rounded-lg p-6 border border-gray-200 dark:border-gray-700">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-xl font-semibold text-gray-900 dark:text-white flex items-center gap-2">
              <SparklesIcon class="w-5 h-5 text-purple-600" />
              AI 벤치마킹 인사이트
            </h2>
            <button
              @click="competitorStore.fetchInsight()"
              :disabled="competitorStore.insightLoading"
              class="btn-primary text-sm"
            >
              {{ competitorStore.insightLoading ? '분석 중...' : 'AI 분석 (8크레딧)' }}
            </button>
          </div>

          <div v-if="competitorStore.aiInsight" class="space-y-4">
            <p class="text-gray-700 dark:text-gray-300">{{ competitorStore.aiInsight.summary }}</p>

            <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div class="bg-green-50 dark:bg-green-900/20 rounded-lg p-4">
                <h3 class="font-medium text-green-800 dark:text-green-300 mb-2">강점</h3>
                <ul class="space-y-1 text-sm text-green-700 dark:text-green-400">
                  <li v-for="(s, i) in competitorStore.aiInsight.strengths" :key="i">• {{ s }}</li>
                </ul>
              </div>
              <div class="bg-red-50 dark:bg-red-900/20 rounded-lg p-4">
                <h3 class="font-medium text-red-800 dark:text-red-300 mb-2">약점</h3>
                <ul class="space-y-1 text-sm text-red-700 dark:text-red-400">
                  <li v-for="(w, i) in competitorStore.aiInsight.weaknesses" :key="i">• {{ w }}</li>
                </ul>
              </div>
              <div class="bg-blue-50 dark:bg-blue-900/20 rounded-lg p-4">
                <h3 class="font-medium text-blue-800 dark:text-blue-300 mb-2">기회</h3>
                <ul class="space-y-1 text-sm text-blue-700 dark:text-blue-400">
                  <li v-for="(o, i) in competitorStore.aiInsight.opportunities" :key="i">• {{ o }}</li>
                </ul>
              </div>
              <div class="bg-purple-50 dark:bg-purple-900/20 rounded-lg p-4">
                <h3 class="font-medium text-purple-800 dark:text-purple-300 mb-2">추천 행동</h3>
                <ul class="space-y-1 text-sm text-purple-700 dark:text-purple-400">
                  <li v-for="(r, i) in competitorStore.aiInsight.recommendations" :key="i">• {{ r }}</li>
                </ul>
              </div>
            </div>
          </div>

          <div v-else class="text-center py-8 text-gray-500 dark:text-gray-400">
            <SparklesIcon class="w-8 h-8 mx-auto mb-2 text-gray-400" />
            <p>AI 분석 버튼을 눌러 경쟁자와의 비교 인사이트를 받아보세요</p>
          </div>
        </div>
      </div>
```

**Step 2: ComparisonChart.vue에 바 차트가 실데이터 사용하도록 확인**

기존 `ComparisonChart.vue`를 읽고, `comparisonData`가 잘 전달되는지 확인. 변경이 필요하면 수정. (기존 코드가 이미 `comparisons` prop으로 Bar chart를 그리고 있으면 변경 불필요)

**Step 3: 빌드 확인**

```bash
cd frontend && npx vue-tsc --noEmit
```

Expected: PASS

**Step 4: Commit**

```bash
git add frontend/src/views/CompetitorView.vue frontend/src/components/competitor/ComparisonChart.vue
git commit -m "feat: CompetitorView AI 인사이트 섹션 추가, 실데이터 연동"
```

---

### Task 9: 전체 빌드 검증

**Step 1: 백엔드 빌드**

```bash
./gradlew compileKotlin
```

Expected: PASS

**Step 2: 프론트엔드 타입 체크**

```bash
cd frontend && npx vue-tsc --noEmit
```

Expected: PASS

**Step 3: Vite 프로덕션 빌드**

```bash
cd frontend && npx vite build 2>&1 | tail -10
```

Expected: 빌드 성공

**Step 4: 최종 커밋 (필요 시)**

```bash
git add -A && git commit -m "chore: 경쟁 크리에이터 벤치마킹 최종 정리"
```
