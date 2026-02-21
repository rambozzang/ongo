# 심층 성과 분석 대시보드 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 기존 분석 대시보드에 트래픽 소스, 시청자 인구통계, CTR/노출수, 평균 시청 시간, 구독 전환 차트를 추가하여 심층 분석을 완성한다.

**Architecture:** `analytics_daily` 테이블에 `impressions`, `avg_view_duration_seconds` 컬럼을 추가하고, 채널 수준의 트래픽 소스/인구통계는 별도 `channel_insights_daily` 테이블에 JSONB로 저장. `PlatformAnalyticsResult`를 확장하여 플랫폼 API에서 추가 데이터를 수집. AnalyticsView.vue에 "심층 분석" 서브탭을 추가하여 4개 차트 컴포넌트를 배치.

**Tech Stack:** Spring Boot 4 + Kotlin + jOOQ, YouTube Data/Analytics API, Vue 3 + Chart.js (vue-chartjs), Tailwind CSS

---

### Task 1: DB 마이그레이션 — analytics_daily 확장 + channel_insights_daily 신규

**Files:**
- Create: `backend/sql/V13__deep_analytics.sql`

**Step 1: 마이그레이션 SQL 작성**

```sql
-- V13__deep_analytics.sql

-- analytics_daily에 노출수/평균시청시간 추가
ALTER TABLE analytics_daily ADD COLUMN IF NOT EXISTS impressions INTEGER DEFAULT 0;
ALTER TABLE analytics_daily ADD COLUMN IF NOT EXISTS avg_view_duration_seconds INTEGER DEFAULT 0;

-- 채널 수준 인사이트 (트래픽 소스, 인구통계) - 일별 스냅샷
CREATE TABLE channel_insights_daily (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT NOT NULL REFERENCES users(id),
    platform              VARCHAR(20) NOT NULL,
    date                  DATE NOT NULL,
    traffic_source        JSONB DEFAULT '{}',
    demographics_age      JSONB DEFAULT '{}',
    demographics_gender   JSONB DEFAULT '{}',
    demographics_country  JSONB DEFAULT '{}',
    created_at            TIMESTAMP DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_channel_insights_daily_unique
    ON channel_insights_daily(user_id, platform, date);
CREATE INDEX idx_channel_insights_daily_date
    ON channel_insights_daily(date);
```

**Step 2: Commit**

```bash
git add backend/sql/V13__deep_analytics.sql
git commit -m "chore: 심층 분석용 DB 마이그레이션 — impressions, avg_view_duration, channel_insights_daily"
```

---

### Task 2: 도메인 엔티티 확장 + 리포지토리

**Files:**
- Modify: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/analytics/AnalyticsDaily.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/analytics/ChannelInsightsDaily.kt`
- Modify: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/analytics/AnalyticsRepository.kt`
- Modify: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/channel/PlatformClientPort.kt`

**Step 1: AnalyticsDaily에 필드 추가**

```kotlin
data class AnalyticsDaily(
    // ... 기존 필드 유지
    val impressions: Int = 0,
    val avgViewDurationSeconds: Int = 0,
)
```

**Step 2: ChannelInsightsDaily 엔티티 작성**

```kotlin
package com.ongo.domain.analytics

import com.ongo.common.enums.Platform
import java.time.LocalDate
import java.time.LocalDateTime

data class ChannelInsightsDaily(
    val id: Long? = null,
    val userId: Long,
    val platform: Platform,
    val date: LocalDate,
    val trafficSource: Map<String, Long> = emptyMap(),
    val demographicsAge: Map<String, Double> = emptyMap(),
    val demographicsGender: Map<String, Double> = emptyMap(),
    val demographicsCountry: Map<String, Long> = emptyMap(),
    val createdAt: LocalDateTime? = null,
)
```

**Step 3: AnalyticsRepository에 메서드 추가**

```kotlin
// channel insights
fun upsertChannelInsights(insights: ChannelInsightsDaily)
fun findChannelInsights(userId: Long, platform: Platform?, startDate: LocalDate, endDate: LocalDate): List<ChannelInsightsDaily>
```

**Step 4: PlatformAnalyticsResult 확장**

```kotlin
data class PlatformAnalyticsResult(
    // ... 기존 필드 유지
    val impressions: Long = 0,
    val avgViewDurationSeconds: Long = 0,
)
```

**Step 5: 빌드 확인**

```bash
cd backend && ./gradlew compileKotlin
```

Expected: FAIL (jOOQ 구현체에 새 메서드/필드 미구현) — 다음 Task에서 해결

**Step 6: Commit**

```bash
git add backend/onGo-domain/
git commit -m "feat: 심층 분석 도메인 — AnalyticsDaily 확장, ChannelInsightsDaily 엔티티"
```

---

### Task 3: jOOQ 인프라 — Tables/Fields + Repository 구현

**Files:**
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/Tables.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/AnalyticsJooqRepository.kt`

**Step 1: Tables.kt에 테이블 등록**

```kotlin
val CHANNEL_INSIGHTS_DAILY = DSL.table("channel_insights_daily")
```

**Step 2: Fields.kt에 필드 추가**

```kotlin
// analytics_daily extended
val IMPRESSIONS = DSL.field("impressions", Int::class.java)
val AVG_VIEW_DURATION_SECONDS = DSL.field("avg_view_duration_seconds", Int::class.java)

// channel_insights_daily
val TRAFFIC_SOURCE = DSL.field("traffic_source", Any::class.java)
val DEMOGRAPHICS_AGE = DSL.field("demographics_age", Any::class.java)
val DEMOGRAPHICS_GENDER = DSL.field("demographics_gender", Any::class.java)
val DEMOGRAPHICS_COUNTRY = DSL.field("demographics_country", Any::class.java)
```

**Step 3: AnalyticsJooqRepository 확장**

기존 `toAnalyticsDaily()` 매핑에 새 필드 추가:
```kotlin
impressions = get(IMPRESSIONS) ?: 0,
avgViewDurationSeconds = get(AVG_VIEW_DURATION_SECONDS) ?: 0,
```

기존 `save()`, `upsert()` 메서드에도 새 필드 추가:
```kotlin
.set(IMPRESSIONS, analytics.impressions)
.set(AVG_VIEW_DURATION_SECONDS, analytics.avgViewDurationSeconds)
```

`upsertChannelInsights()` 구현:
```kotlin
override fun upsertChannelInsights(insights: ChannelInsightsDaily) {
    val trafficJson = objectMapper.writeValueAsString(insights.trafficSource)
    val ageJson = objectMapper.writeValueAsString(insights.demographicsAge)
    val genderJson = objectMapper.writeValueAsString(insights.demographicsGender)
    val countryJson = objectMapper.writeValueAsString(insights.demographicsCountry)

    dsl.insertInto(CHANNEL_INSIGHTS_DAILY)
        .set(USER_ID, insights.userId)
        .set(PLATFORM, insights.platform.name)
        .set(DATE, insights.date)
        .set(TRAFFIC_SOURCE, DSL.`val`(trafficJson).cast(Any::class.java))
        .set(DEMOGRAPHICS_AGE, DSL.`val`(ageJson).cast(Any::class.java))
        .set(DEMOGRAPHICS_GENDER, DSL.`val`(genderJson).cast(Any::class.java))
        .set(DEMOGRAPHICS_COUNTRY, DSL.`val`(countryJson).cast(Any::class.java))
        .onConflict(USER_ID, PLATFORM, DATE)
        .doUpdate()
        .set(TRAFFIC_SOURCE, DSL.`val`(trafficJson).cast(Any::class.java))
        .set(DEMOGRAPHICS_AGE, DSL.`val`(ageJson).cast(Any::class.java))
        .set(DEMOGRAPHICS_GENDER, DSL.`val`(genderJson).cast(Any::class.java))
        .set(DEMOGRAPHICS_COUNTRY, DSL.`val`(countryJson).cast(Any::class.java))
        .execute()
}
```

`findChannelInsights()` 구현:
```kotlin
override fun findChannelInsights(
    userId: Long, platform: Platform?, startDate: LocalDate, endDate: LocalDate
): List<ChannelInsightsDaily> {
    var query = dsl.select().from(CHANNEL_INSIGHTS_DAILY)
        .where(USER_ID.eq(userId))
        .and(DATE.ge(startDate))
        .and(DATE.le(endDate))
    if (platform != null) {
        query = query.and(PLATFORM.eq(platform.name))
    }
    return query.orderBy(DATE.asc()).fetch().map { it.toChannelInsights() }
}
```

JSONB → Map 변환을 위한 매핑 함수:
```kotlin
@Suppress("UNCHECKED_CAST")
private fun Record.toChannelInsights(): ChannelInsightsDaily {
    val mapper = ObjectMapper()
    fun <V> parseJsonMap(field: Field<Any>, valueType: Class<V>): Map<String, V> {
        val raw = get(field.name)?.toString() ?: return emptyMap()
        return try { mapper.readValue(raw, mapper.typeFactory.constructMapType(Map::class.java, String::class.java, valueType)) } catch (_: Exception) { emptyMap() }
    }
    return ChannelInsightsDaily(
        id = get(ID),
        userId = get(USER_ID),
        platform = Platform.valueOf(get(PLATFORM)),
        date = localDate(DATE)!!,
        trafficSource = parseJsonMap(TRAFFIC_SOURCE, Long::class.java),
        demographicsAge = parseJsonMap(DEMOGRAPHICS_AGE, Double::class.java),
        demographicsGender = parseJsonMap(DEMOGRAPHICS_GENDER, Double::class.java),
        demographicsCountry = parseJsonMap(DEMOGRAPHICS_COUNTRY, Long::class.java),
        createdAt = localDateTime(CREATED_AT),
    )
}
```

참고: `ObjectMapper`는 `com.fasterxml.jackson.databind.ObjectMapper`. 이미 Spring Boot에 포함.

**Step 4: AnalyticsSyncScheduler 업데이트**

기존 `upsert()` 호출에 새 필드 추가:
```kotlin
analyticsRepository.upsert(AnalyticsDaily(
    // ... 기존 필드
    impressions = analytics.impressions.toInt(),
    avgViewDurationSeconds = analytics.avgViewDurationSeconds.toInt(),
))
```

**Step 5: 빌드 확인**

```bash
cd backend && ./gradlew compileKotlin
```

Expected: PASS

**Step 6: Commit**

```bash
git add backend/onGo-infrastructure/ backend/onGo-application/src/main/kotlin/com/ongo/application/analytics/AnalyticsSyncScheduler.kt
git commit -m "feat: 심층 분석 jOOQ 인프라 — impressions, channel_insights_daily 구현"
```

---

### Task 4: 백엔드 API — 심층 분석 UseCase + DTO + Controller

**Files:**
- Modify: `backend/onGo-application/src/main/kotlin/com/ongo/application/analytics/AnalyticsUseCase.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/analytics/dto/DeepAnalyticsDtos.kt`
- Modify: `backend/onGo-api/src/main/kotlin/com/ongo/api/analytics/AnalyticsController.kt`

**Step 1: DTO 작성**

```kotlin
package com.ongo.application.analytics.dto

data class TrafficSourceResponse(
    val period: String,
    val sources: Map<String, Long>,
    val total: Long,
)

data class DemographicsResponse(
    val period: String,
    val ageDistribution: Map<String, Double>,
    val genderDistribution: Map<String, Double>,
    val topCountries: Map<String, Long>,
)

data class CTRTrendPoint(
    val date: String,
    val impressions: Long,
    val views: Long,
    val ctr: Double,
)

data class CTRResponse(
    val period: String,
    val avgCTR: Double,
    val totalImpressions: Long,
    val data: List<CTRTrendPoint>,
)

data class AvgViewDurationResponse(
    val period: String,
    val avgDurationSeconds: Long,
    val data: List<AvgViewDurationPoint>,
)

data class AvgViewDurationPoint(
    val date: String,
    val avgDurationSeconds: Long,
    val totalWatchTimeSeconds: Long,
    val totalViews: Long,
)

data class SubscriberConversionResponse(
    val period: String,
    val totalGained: Long,
    val data: List<SubscriberConversionPoint>,
)

data class SubscriberConversionPoint(
    val date: String,
    val gained: Int,
    val views: Long,
    val conversionRate: Double,
)
```

**Step 2: AnalyticsUseCase에 메서드 추가**

```kotlin
fun getTrafficSources(userId: Long, days: Int): TrafficSourceResponse {
    val endDate = LocalDate.now()
    val startDate = endDate.minusDays(days.toLong())
    val insights = analyticsRepository.findChannelInsights(userId, null, startDate, endDate)

    val merged = mutableMapOf<String, Long>()
    insights.forEach { day ->
        day.trafficSource.forEach { (source, count) ->
            merged[source] = (merged[source] ?: 0) + count
        }
    }

    return TrafficSourceResponse(
        period = "${days}d",
        sources = merged.toSortedMap(),
        total = merged.values.sum(),
    )
}

fun getDemographics(userId: Long, days: Int): DemographicsResponse {
    val endDate = LocalDate.now()
    val startDate = endDate.minusDays(days.toLong())
    val insights = analyticsRepository.findChannelInsights(userId, null, startDate, endDate)

    // Average across days
    val ageAccum = mutableMapOf<String, Double>()
    val genderAccum = mutableMapOf<String, Double>()
    val countryAccum = mutableMapOf<String, Long>()

    insights.forEach { day ->
        day.demographicsAge.forEach { (k, v) -> ageAccum[k] = (ageAccum[k] ?: 0.0) + v }
        day.demographicsGender.forEach { (k, v) -> genderAccum[k] = (genderAccum[k] ?: 0.0) + v }
        day.demographicsCountry.forEach { (k, v) -> countryAccum[k] = (countryAccum[k] ?: 0) + v }
    }

    val count = insights.size.coerceAtLeast(1)
    return DemographicsResponse(
        period = "${days}d",
        ageDistribution = ageAccum.mapValues { Math.round(it.value / count * 10) / 10.0 },
        genderDistribution = genderAccum.mapValues { Math.round(it.value / count * 10) / 10.0 },
        topCountries = countryAccum.entries.sortedByDescending { it.value }.take(10).associate { it.key to it.value },
    )
}

fun getCTRTrend(userId: Long, days: Int): CTRResponse {
    val allAnalytics = analyticsRepository.findAllByUserId(userId)
    val endDate = LocalDate.now()
    val startDate = endDate.minusDays(days.toLong())

    val filtered = allAnalytics.filter { it.date in startDate..endDate }
    val byDate = filtered.groupBy { it.date }

    val dataPoints = byDate.entries.sortedBy { it.key }.map { (date, records) ->
        val totalImpressions = records.sumOf { it.impressions.toLong() }
        val totalViews = records.sumOf { it.views.toLong() }
        val ctr = if (totalImpressions > 0) (totalViews.toDouble() / totalImpressions * 100) else 0.0
        CTRTrendPoint(
            date = date.toString(),
            impressions = totalImpressions,
            views = totalViews,
            ctr = Math.round(ctr * 100) / 100.0,
        )
    }

    val totalImpressions = dataPoints.sumOf { it.impressions }
    val totalViews = dataPoints.sumOf { it.views }
    val avgCTR = if (totalImpressions > 0) (totalViews.toDouble() / totalImpressions * 100) else 0.0

    return CTRResponse(
        period = "${days}d",
        avgCTR = Math.round(avgCTR * 100) / 100.0,
        totalImpressions = totalImpressions,
        data = dataPoints,
    )
}

fun getAvgViewDuration(userId: Long, days: Int): AvgViewDurationResponse {
    val allAnalytics = analyticsRepository.findAllByUserId(userId)
    val endDate = LocalDate.now()
    val startDate = endDate.minusDays(days.toLong())
    val filtered = allAnalytics.filter { it.date in startDate..endDate }
    val byDate = filtered.groupBy { it.date }

    val dataPoints = byDate.entries.sortedBy { it.key }.map { (date, records) ->
        val totalWatch = records.sumOf { it.watchTimeSeconds }
        val totalViews = records.sumOf { it.views.toLong() }
        val avg = if (totalViews > 0) totalWatch / totalViews else 0L
        AvgViewDurationPoint(
            date = date.toString(),
            avgDurationSeconds = avg,
            totalWatchTimeSeconds = totalWatch,
            totalViews = totalViews,
        )
    }

    val totalWatch = filtered.sumOf { it.watchTimeSeconds }
    val totalViews = filtered.sumOf { it.views.toLong() }

    return AvgViewDurationResponse(
        period = "${days}d",
        avgDurationSeconds = if (totalViews > 0) totalWatch / totalViews else 0,
        data = dataPoints,
    )
}

fun getSubscriberConversion(userId: Long, days: Int): SubscriberConversionResponse {
    val allAnalytics = analyticsRepository.findAllByUserId(userId)
    val endDate = LocalDate.now()
    val startDate = endDate.minusDays(days.toLong())
    val filtered = allAnalytics.filter { it.date in startDate..endDate }
    val byDate = filtered.groupBy { it.date }

    val dataPoints = byDate.entries.sortedBy { it.key }.map { (date, records) ->
        val gained = records.sumOf { it.subscriberGained }
        val views = records.sumOf { it.views.toLong() }
        val rate = if (views > 0) (gained.toDouble() / views * 100) else 0.0
        SubscriberConversionPoint(
            date = date.toString(),
            gained = gained,
            views = views,
            conversionRate = Math.round(rate * 1000) / 1000.0,
        )
    }

    return SubscriberConversionResponse(
        period = "${days}d",
        totalGained = filtered.sumOf { it.subscriberGained.toLong() },
        data = dataPoints,
    )
}
```

**Step 3: Controller에 엔드포인트 추가**

```kotlin
@Operation(summary = "트래픽 소스 조회")
@GetMapping("/traffic-sources")
fun getTrafficSources(
    @CurrentUser userId: Long,
    @RequestParam(defaultValue = "30") days: Int,
): ResponseEntity<ResData<TrafficSourceResponse>> =
    ResData.success(analyticsUseCase.getTrafficSources(userId, days))

@Operation(summary = "시청자 인구통계 조회")
@GetMapping("/demographics")
fun getDemographics(
    @CurrentUser userId: Long,
    @RequestParam(defaultValue = "30") days: Int,
): ResponseEntity<ResData<DemographicsResponse>> =
    ResData.success(analyticsUseCase.getDemographics(userId, days))

@Operation(summary = "CTR 트렌드 조회")
@GetMapping("/ctr")
fun getCTRTrend(
    @CurrentUser userId: Long,
    @RequestParam(defaultValue = "30") days: Int,
): ResponseEntity<ResData<CTRResponse>> =
    ResData.success(analyticsUseCase.getCTRTrend(userId, days))

@Operation(summary = "평균 시청 시간 트렌드")
@GetMapping("/avg-view-duration")
fun getAvgViewDuration(
    @CurrentUser userId: Long,
    @RequestParam(defaultValue = "30") days: Int,
): ResponseEntity<ResData<AvgViewDurationResponse>> =
    ResData.success(analyticsUseCase.getAvgViewDuration(userId, days))

@Operation(summary = "구독 전환 분석")
@GetMapping("/subscriber-conversion")
fun getSubscriberConversion(
    @CurrentUser userId: Long,
    @RequestParam(defaultValue = "30") days: Int,
): ResponseEntity<ResData<SubscriberConversionResponse>> =
    ResData.success(analyticsUseCase.getSubscriberConversion(userId, days))
```

**Step 4: 빌드 확인**

```bash
cd backend && ./gradlew compileKotlin
```

Expected: PASS

**Step 5: Commit**

```bash
git add backend/onGo-application/ backend/onGo-api/
git commit -m "feat: 심층 분석 API — 트래픽 소스, 인구통계, CTR, 평균 시청 시간, 구독 전환"
```

---

### Task 5: 프론트엔드 — 타입 + API + 스토어

**Files:**
- Modify: `frontend/src/types/analytics.ts`
- Modify: `frontend/src/api/analytics.ts`
- Modify: `frontend/src/stores/analytics.ts`

**Step 1: 타입 추가**

`analytics.ts` 파일 끝에:

```typescript
// Deep analytics types
export interface TrafficSourceResponse {
  period: string
  sources: Record<string, number>
  total: number
}

export interface DemographicsResponse {
  period: string
  ageDistribution: Record<string, number>
  genderDistribution: Record<string, number>
  topCountries: Record<string, number>
}

export interface CTRTrendPoint {
  date: string
  impressions: number
  views: number
  ctr: number
}

export interface CTRResponse {
  period: string
  avgCTR: number
  totalImpressions: number
  data: CTRTrendPoint[]
}

export interface AvgViewDurationPoint {
  date: string
  avgDurationSeconds: number
  totalWatchTimeSeconds: number
  totalViews: number
}

export interface AvgViewDurationResponse {
  period: string
  avgDurationSeconds: number
  data: AvgViewDurationPoint[]
}

export interface SubscriberConversionPoint {
  date: string
  gained: number
  views: number
  conversionRate: number
}

export interface SubscriberConversionResponse {
  period: string
  totalGained: number
  data: SubscriberConversionPoint[]
}
```

**Step 2: API 클라이언트 확장**

```typescript
  trafficSources(days = 30) {
    return apiClient
      .get<ResData<TrafficSourceResponse>>(`/analytics/traffic-sources?days=${days}`)
      .then(unwrapResponse)
  },
  demographics(days = 30) {
    return apiClient
      .get<ResData<DemographicsResponse>>(`/analytics/demographics?days=${days}`)
      .then(unwrapResponse)
  },
  ctr(days = 30) {
    return apiClient
      .get<ResData<CTRResponse>>(`/analytics/ctr?days=${days}`)
      .then(unwrapResponse)
  },
  avgViewDuration(days = 30) {
    return apiClient
      .get<ResData<AvgViewDurationResponse>>(`/analytics/avg-view-duration?days=${days}`)
      .then(unwrapResponse)
  },
  subscriberConversion(days = 30) {
    return apiClient
      .get<ResData<SubscriberConversionResponse>>(`/analytics/subscriber-conversion?days=${days}`)
      .then(unwrapResponse)
  },
```

**Step 3: 스토어에 심층 분석 데이터 추가**

```typescript
// refs
const trafficSources = ref<TrafficSourceResponse | null>(null)
const demographics = ref<DemographicsResponse | null>(null)
const ctrData = ref<CTRResponse | null>(null)
const avgViewDuration = ref<AvgViewDurationResponse | null>(null)
const subscriberConversion = ref<SubscriberConversionResponse | null>(null)
const deepAnalyticsLoading = ref(false)

// fetch function
async function fetchDeepAnalytics(days = 30) {
  deepAnalyticsLoading.value = true
  try {
    const results = await Promise.allSettled([
      analyticsApi.trafficSources(days),
      analyticsApi.demographics(days),
      analyticsApi.ctr(days),
      analyticsApi.avgViewDuration(days),
      analyticsApi.subscriberConversion(days),
    ])
    if (results[0].status === 'fulfilled') trafficSources.value = results[0].value
    if (results[1].status === 'fulfilled') demographics.value = results[1].value
    if (results[2].status === 'fulfilled') ctrData.value = results[2].value
    if (results[3].status === 'fulfilled') avgViewDuration.value = results[3].value
    if (results[4].status === 'fulfilled') subscriberConversion.value = results[4].value
  } finally {
    deepAnalyticsLoading.value = false
  }
}

// return에 추가
return {
  // ... 기존
  trafficSources, demographics, ctrData, avgViewDuration, subscriberConversion,
  deepAnalyticsLoading, fetchDeepAnalytics,
}
```

**Step 4: 빌드 확인**

```bash
cd frontend && npx vue-tsc --noEmit
```

Expected: PASS

**Step 5: Commit**

```bash
git add frontend/src/types/analytics.ts frontend/src/api/analytics.ts frontend/src/stores/analytics.ts
git commit -m "feat: 심층 분석 프론트엔드 타입/API/스토어 연동"
```

---

### Task 6: 프론트엔드 — 심층 분석 차트 컴포넌트 4개

**Files:**
- Create: `frontend/src/components/analytics/TrafficSourceChart.vue`
- Create: `frontend/src/components/analytics/DemographicsChart.vue`
- Create: `frontend/src/components/analytics/CTRTrendChart.vue`
- Create: `frontend/src/components/analytics/SubscriberConversionChart.vue`

**Step 1: TrafficSourceChart.vue** — 도넛 차트

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { Doughnut } from 'vue-chartjs'
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from 'chart.js'
import type { TrafficSourceResponse } from '@/types/analytics'

ChartJS.register(ArcElement, Tooltip, Legend)

const props = defineProps<{ data: TrafficSourceResponse | null }>()

const TRAFFIC_LABELS: Record<string, string> = {
  SEARCH: '검색',
  SUGGESTED: '추천 영상',
  EXTERNAL: '외부 링크',
  BROWSE: '탐색',
  CHANNEL: '채널 페이지',
  NOTIFICATION: '알림',
  OTHER: '기타',
}

const COLORS = ['#8b5cf6', '#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#6366f1', '#94a3b8']

const chartData = computed(() => {
  if (!props.data) return null
  const entries = Object.entries(props.data.sources)
  return {
    labels: entries.map(([k]) => TRAFFIC_LABELS[k] || k),
    datasets: [{
      data: entries.map(([, v]) => v),
      backgroundColor: COLORS.slice(0, entries.length),
    }],
  }
})

const chartOptions = {
  responsive: true,
  plugins: {
    legend: { position: 'right' as const },
  },
}
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg p-6 border border-gray-200 dark:border-gray-700">
    <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-4">트래픽 소스</h3>
    <div v-if="chartData" class="max-w-md mx-auto">
      <Doughnut :data="chartData" :options="chartOptions" />
    </div>
    <p v-else class="text-center text-gray-400 py-8">데이터가 없습니다</p>
  </div>
</template>
```

**Step 2: DemographicsChart.vue** — 연령 바 차트 + 성별 도넛

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { Bar, Doughnut } from 'vue-chartjs'
import { Chart as ChartJS, CategoryScale, LinearScale, BarElement, ArcElement, Tooltip, Legend } from 'chart.js'
import type { DemographicsResponse } from '@/types/analytics'

ChartJS.register(CategoryScale, LinearScale, BarElement, ArcElement, Tooltip, Legend)

const props = defineProps<{ data: DemographicsResponse | null }>()

const GENDER_LABELS: Record<string, string> = { male: '남성', female: '여성', other: '기타' }
const GENDER_COLORS = ['#3b82f6', '#ec4899', '#94a3b8']

const ageChartData = computed(() => {
  if (!props.data) return null
  const entries = Object.entries(props.data.ageDistribution).sort(([a], [b]) => a.localeCompare(b))
  return {
    labels: entries.map(([k]) => k),
    datasets: [{ label: '비율 (%)', data: entries.map(([, v]) => v), backgroundColor: '#8b5cf6' }],
  }
})

const genderChartData = computed(() => {
  if (!props.data) return null
  const entries = Object.entries(props.data.genderDistribution)
  return {
    labels: entries.map(([k]) => GENDER_LABELS[k] || k),
    datasets: [{ data: entries.map(([, v]) => v), backgroundColor: GENDER_COLORS.slice(0, entries.length) }],
  }
})

const topCountries = computed(() => {
  if (!props.data) return []
  return Object.entries(props.data.topCountries).sort(([, a], [, b]) => b - a).slice(0, 5)
})
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg p-6 border border-gray-200 dark:border-gray-700">
    <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-4">시청자 인구통계</h3>
    <div v-if="props.data" class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <div>
        <h4 class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">연령대</h4>
        <Bar v-if="ageChartData" :data="ageChartData" :options="{ responsive: true, plugins: { legend: { display: false } } }" />
      </div>
      <div>
        <h4 class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">성별</h4>
        <Doughnut v-if="genderChartData" :data="genderChartData" :options="{ responsive: true }" />
      </div>
      <div>
        <h4 class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">상위 국가</h4>
        <ul class="space-y-2">
          <li v-for="[country, count] in topCountries" :key="country" class="flex justify-between text-sm">
            <span class="text-gray-700 dark:text-gray-300">{{ country }}</span>
            <span class="font-medium text-gray-900 dark:text-white">{{ count.toLocaleString() }}</span>
          </li>
        </ul>
        <p v-if="topCountries.length === 0" class="text-gray-400 text-sm">데이터 없음</p>
      </div>
    </div>
    <p v-else class="text-center text-gray-400 py-8">데이터가 없습니다</p>
  </div>
</template>
```

**Step 3: CTRTrendChart.vue** — 라인 차트 (CTR + 노출수)

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { Line } from 'vue-chartjs'
import { Chart as ChartJS, CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend } from 'chart.js'
import type { CTRResponse } from '@/types/analytics'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Tooltip, Legend)

const props = defineProps<{ data: CTRResponse | null }>()

const chartData = computed(() => {
  if (!props.data) return null
  return {
    labels: props.data.data.map(d => d.date.slice(5)),
    datasets: [
      {
        label: 'CTR (%)',
        data: props.data.data.map(d => d.ctr),
        borderColor: '#8b5cf6',
        backgroundColor: 'rgba(139, 92, 246, 0.1)',
        fill: true,
        yAxisID: 'y',
      },
      {
        label: '노출수',
        data: props.data.data.map(d => d.impressions),
        borderColor: '#94a3b8',
        borderDash: [5, 5],
        yAxisID: 'y1',
      },
    ],
  }
})

const chartOptions = {
  responsive: true,
  interaction: { mode: 'index' as const, intersect: false },
  scales: {
    y: { type: 'linear' as const, position: 'left' as const, title: { display: true, text: 'CTR (%)' } },
    y1: { type: 'linear' as const, position: 'right' as const, title: { display: true, text: '노출수' }, grid: { drawOnChartArea: false } },
  },
}
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg p-6 border border-gray-200 dark:border-gray-700">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-white">CTR 트렌드</h3>
      <div v-if="props.data" class="flex items-center gap-4 text-sm">
        <span class="text-gray-500 dark:text-gray-400">평균 CTR: <strong class="text-purple-600">{{ props.data.avgCTR }}%</strong></span>
        <span class="text-gray-500 dark:text-gray-400">총 노출: <strong>{{ props.data.totalImpressions.toLocaleString() }}</strong></span>
      </div>
    </div>
    <Line v-if="chartData" :data="chartData" :options="chartOptions" />
    <p v-else class="text-center text-gray-400 py-8">데이터가 없습니다</p>
  </div>
</template>
```

**Step 4: SubscriberConversionChart.vue** — 바+라인 복합 차트

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { Bar } from 'vue-chartjs'
import { Chart as ChartJS, CategoryScale, LinearScale, BarElement, PointElement, LineElement, Tooltip, Legend, LineController, BarController } from 'chart.js'
import type { SubscriberConversionResponse } from '@/types/analytics'

ChartJS.register(CategoryScale, LinearScale, BarElement, PointElement, LineElement, Tooltip, Legend, LineController, BarController)

const props = defineProps<{ data: SubscriberConversionResponse | null }>()

const chartData = computed(() => {
  if (!props.data) return null
  return {
    labels: props.data.data.map(d => d.date.slice(5)),
    datasets: [
      {
        type: 'bar' as const,
        label: '신규 구독자',
        data: props.data.data.map(d => d.gained),
        backgroundColor: '#10b981',
        yAxisID: 'y',
      },
      {
        type: 'line' as const,
        label: '전환율 (%)',
        data: props.data.data.map(d => d.conversionRate),
        borderColor: '#f59e0b',
        yAxisID: 'y1',
      },
    ],
  }
})

const chartOptions = {
  responsive: true,
  scales: {
    y: { type: 'linear' as const, position: 'left' as const, title: { display: true, text: '구독자' } },
    y1: { type: 'linear' as const, position: 'right' as const, title: { display: true, text: '전환율 (%)' }, grid: { drawOnChartArea: false } },
  },
}
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg p-6 border border-gray-200 dark:border-gray-700">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-white">구독 전환 분석</h3>
      <span v-if="props.data" class="text-sm text-gray-500 dark:text-gray-400">
        총 신규 구독: <strong class="text-green-600">+{{ props.data.totalGained.toLocaleString() }}</strong>
      </span>
    </div>
    <Bar v-if="chartData" :data="chartData" :options="chartOptions" />
    <p v-else class="text-center text-gray-400 py-8">데이터가 없습니다</p>
  </div>
</template>
```

**Step 5: 빌드 확인**

```bash
cd frontend && npx vue-tsc --noEmit
```

Expected: PASS

**Step 6: Commit**

```bash
git add frontend/src/components/analytics/
git commit -m "feat: 심층 분석 차트 컴포넌트 — 트래픽 소스, 인구통계, CTR, 구독 전환"
```

---

### Task 7: AnalyticsView.vue에 "심층 분석" 서브탭 추가

**Files:**
- Modify: `frontend/src/views/AnalyticsView.vue`

**Step 1: AnalyticsSubTab 타입 확장**

```typescript
type AnalyticsSubTab = 'overview' | 'cohort' | 'retention' | 'deep'
```

**Step 2: import 추가**

```typescript
import TrafficSourceChart from '@/components/analytics/TrafficSourceChart.vue'
import DemographicsChart from '@/components/analytics/DemographicsChart.vue'
import CTRTrendChart from '@/components/analytics/CTRTrendChart.vue'
import SubscriberConversionChart from '@/components/analytics/SubscriberConversionChart.vue'
```

**Step 3: 탭 버튼 추가 (리텐션 버튼 뒤에)**

```html
<button
  @click="analyticsSubTab = 'deep'"
  :class="[
    analyticsSubTab === 'deep'
      ? 'border-primary-500 text-primary-600 dark:border-primary-400 dark:text-primary-400'
      : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400',
    'whitespace-nowrap border-b-2 px-1 py-3 text-sm font-medium',
  ]"
>
  심층 분석
</button>
```

**Step 4: 탭 변경 시 데이터 로드**

```typescript
import { watch } from 'vue'

watch(analyticsSubTab, (tab) => {
  if (tab === 'deep') {
    const days = period.value === '7d' ? 7 : period.value === '30d' ? 30 : 90
    analyticsStore.fetchDeepAnalytics(days)
  }
})
```

**Step 5: 심층 분석 탭 콘텐츠**

리텐션 탭 콘텐츠 아래에:

```html
    <!-- Deep Analytics Tab -->
    <div v-if="analyticsSubTab === 'deep'" class="space-y-6">
      <div v-if="analyticsStore.deepAnalyticsLoading" class="text-center py-12">
        <LoadingSpinner />
        <p class="mt-2 text-sm text-gray-500">심층 분석 데이터를 불러오는 중...</p>
      </div>
      <template v-else>
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <TrafficSourceChart :data="analyticsStore.trafficSources" />
          <DemographicsChart :data="analyticsStore.demographics" />
        </div>
        <CTRTrendChart :data="analyticsStore.ctrData" />
        <SubscriberConversionChart :data="analyticsStore.subscriberConversion" />
      </template>
    </div>
```

**Step 6: 빌드 확인**

```bash
cd frontend && npx vue-tsc --noEmit
```

Expected: PASS

**Step 7: Commit**

```bash
git add frontend/src/views/AnalyticsView.vue
git commit -m "feat: AnalyticsView 심층 분석 서브탭 추가 — 4개 차트 통합"
```

---

### Task 8: 전체 빌드 검증

**Step 1: 백엔드 빌드**

```bash
cd backend && ./gradlew compileKotlin
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
