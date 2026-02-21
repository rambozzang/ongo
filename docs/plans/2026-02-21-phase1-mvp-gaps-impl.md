# Phase 1 MVP Gap 수정 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Phase 1 MVP 점검에서 발견된 3개 Gap(온보딩 플랜 연동, 저크레딧 알림, 구독자 트렌드 차트)을 수정한다.

**Architecture:** 기존 인프라(이벤트 리스너, WebSocket, subscription API)를 재활용하여 최소한의 코드 변경으로 Gap을 메운다.

**Tech Stack:** Kotlin (Spring Boot), Vue.js 3, jOOQ, Spring Events

---

### Task 1: 저크레딧 알림 이벤트 리스너 생성

**Files:**
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/credit/LowCreditAlertEventListener.kt`

**Step 1: 리스너 파일 생성**

```kotlin
package com.ongo.application.credit

import com.ongo.application.notification.WebSocketNotificationService
import com.ongo.common.enums.NotificationType
import com.ongo.domain.notification.Notification
import com.ongo.domain.notification.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LowCreditAlertEventListener(
    private val notificationRepository: NotificationRepository,
    private val webSocketNotificationService: WebSocketNotificationService,
) {

    private val log = LoggerFactory.getLogger(LowCreditAlertEventListener::class.java)

    @EventListener
    @Transactional
    fun handleLowCreditAlert(event: LowCreditAlertEvent) {
        val notification = Notification(
            userId = event.userId,
            type = NotificationType.CREDIT_LOW,
            title = "AI 크레딧 부족",
            message = "잔여 크레딧이 ${event.balance}/${event.freeMonthly}로 20% 이하입니다. 충전을 권장합니다.",
        )
        notificationRepository.save(notification)

        webSocketNotificationService.sendToUser(
            userId = event.userId,
            type = "CREDIT_LOW",
            payload = mapOf(
                "balance" to event.balance,
                "freeMonthly" to event.freeMonthly,
            ),
        )

        log.info("크레딧 부족 알림 전송 완료. userId: {}", event.userId)
    }
}
```

**Step 2: 빌드 확인**

Run: `./gradlew :backend:onGo-application:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: 커밋**

```bash
git add backend/onGo-application/src/main/kotlin/com/ongo/application/credit/LowCreditAlertEventListener.kt
git commit -m "feat: 저크레딧 알림 이벤트 리스너 추가"
```

---

### Task 2: 온보딩 플랜 선택 백엔드 연동

**Files:**
- Modify: `frontend/src/views/OnboardingView.vue`

**Step 1: import 추가**

`OnboardingView.vue` 의 기존 import 영역에 `subscriptionApi` 추가:

```typescript
import { subscriptionApi } from '@/api/subscription'
```

**Step 2: completeOnboarding() 함수 수정**

기존 코드 (라인 549-563):
```typescript
async function completeOnboarding() {
  isSubmitting.value = true
  try {
    await authApi.completeOnboarding()
    if (authStore.user) {
      authStore.user.onboardingCompleted = true
    }
    currentStep.value = 5
  } catch {
    // Still proceed to completion screen
    currentStep.value = 5
  } finally {
    isSubmitting.value = false
  }
}
```

수정 후:
```typescript
async function completeOnboarding() {
  isSubmitting.value = true
  try {
    await authApi.completeOnboarding()
    if (authStore.user) {
      authStore.user.onboardingCompleted = true
    }
    // 무료가 아닌 플랜 선택 시 구독 변경 (실패해도 온보딩 완료 진행)
    if (selectedPlan.value !== 'FREE') {
      try {
        await subscriptionApi.changePlan({ planType: selectedPlan.value })
      } catch {
        // 플랜 변경 실패 시 무시 - 나중에 구독 페이지에서 변경 가능
      }
    }
    currentStep.value = 5
  } catch {
    // Still proceed to completion screen
    currentStep.value = 5
  } finally {
    isSubmitting.value = false
  }
}
```

**Step 3: 커밋**

```bash
git add frontend/src/views/OnboardingView.vue
git commit -m "feat: 온보딩 플랜 선택 시 구독 API 연동"
```

---

### Task 3: 구독자 트렌드 — 백엔드 데이터 모델 수정

**Files:**
- Modify: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/analytics/TrendData.kt`
- Modify: `backend/onGo-application/src/main/kotlin/com/ongo/application/analytics/dto/AnalyticsDtos.kt`

**Step 1: TrendData 도메인에 subscribers 추가**

```kotlin
data class TrendData(
    val date: LocalDate,
    val platform: Platform?,
    val views: Long,
    val subscribers: Long = 0,
)
```

**Step 2: TrendPoint DTO에 subscribers 추가**

```kotlin
data class TrendPoint(
    val date: LocalDate,
    val totalViews: Long,
    val platformViews: Map<String, Long>,
    val totalSubscribers: Long = 0,
    val platformSubscribers: Map<String, Long> = emptyMap(),
)
```

**Step 3: 빌드 확인**

Run: `./gradlew :backend:onGo-domain:compileKotlin :backend:onGo-application:compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: 커밋**

```bash
git add backend/onGo-domain/src/main/kotlin/com/ongo/domain/analytics/TrendData.kt
git add backend/onGo-application/src/main/kotlin/com/ongo/application/analytics/dto/AnalyticsDtos.kt
git commit -m "feat: TrendData/TrendPoint에 구독자 필드 추가"
```

---

### Task 4: 구독자 트렌드 — jOOQ 리포지토리 쿼리 수정

**Files:**
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/AnalyticsJooqRepository.kt` (getTrendData 메서드, ~라인 143-170)

**Step 1: getTrendData()에 subscribers 조인 추가**

기존 쿼리는 `analytics_daily` JOIN `video_uploads`에서 views만 집계한다.
`analytics_daily` 테이블에 subscribers 컬럼이 있는지에 따라 접근 방식이 다르다.
`analytics_daily`에 subscribers 컬럼이 있다면 SUM으로 집계, 없다면 0으로 기본값 반환.

수정 후 getTrendData() 메서드:
```kotlin
override fun getTrendData(userId: Long, days: Int): List<TrendData> {
    val from = LocalDate.now().minusDays(days.toLong())
    val uploadIds = getUserUploadIds(userId)

    if (uploadIds.isEmpty()) return emptyList()

    val platformField = DSL.field("vu.platform", String::class.java)
    val dateField = DSL.field("ad.date", LocalDate::class.java)
    val viewsSum = DSL.sum(DSL.field("ad.views", Int::class.java)).`as`("total_views")
    val subscribersSum = DSL.sum(DSL.field("ad.subscribers", Int::class.java)).`as`("total_subscribers")

    return dsl.select(dateField, platformField, viewsSum, subscribersSum)
        .from(DSL.table("analytics_daily").`as`("ad"))
        .join(DSL.table("video_uploads").`as`("vu"))
        .on(DSL.field("ad.video_upload_id", Long::class.java).eq(DSL.field("vu.id", Long::class.java)))
        .where(DSL.field("ad.video_upload_id", Long::class.java).`in`(uploadIds))
        .and(dateField.greaterOrEqual(from))
        .groupBy(dateField, platformField)
        .orderBy(dateField.asc())
        .fetch()
        .map { record ->
            val platformStr = record.get(platformField)
            TrendData(
                date = record.get("date", LocalDate::class.java),
                platform = platformStr?.let { Platform.valueOf(it) },
                views = record.get("total_views", Long::class.java) ?: 0L,
                subscribers = record.get("total_subscribers", Long::class.java) ?: 0L,
            )
        }
}
```

> **NOTE:** `analytics_daily` 테이블에 `subscribers` 컬럼이 없으면 DB 마이그레이션이 필요하다.
> 그 경우 `sql/` 디렉토리에서 스키마를 확인하고, `ALTER TABLE analytics_daily ADD COLUMN subscribers INT DEFAULT 0;`을 추가한다.

**Step 2: AnalyticsUseCase.getTrends() 수정**

```kotlin
fun getTrends(userId: Long, days: Int): TrendDataResponse {
    val trendData = analyticsRepository.getTrendData(userId, days)
    val grouped = trendData.groupBy { it.date }

    val points = grouped.map { (date, items) ->
        val platformViews = items
            .filter { it.platform != null }
            .associate { it.platform!!.name to it.views }
        val platformSubscribers = items
            .filter { it.platform != null }
            .associate { it.platform!!.name to it.subscribers }
        TrendPoint(
            date = date,
            totalViews = items.sumOf { it.views },
            platformViews = platformViews,
            totalSubscribers = items.sumOf { it.subscribers },
            platformSubscribers = platformSubscribers,
        )
    }.sortedBy { it.date }

    return TrendDataResponse(data = points)
}
```

**Step 3: 빌드 확인**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: 커밋**

```bash
git add backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/AnalyticsJooqRepository.kt
git add backend/onGo-application/src/main/kotlin/com/ongo/application/analytics/AnalyticsUseCase.kt
git commit -m "feat: 트렌드 데이터 쿼리에 구독자 수 포함"
```

---

### Task 5: 구독자 트렌드 — 프론트엔드 타입 및 차트 추가

**Files:**
- Modify: `frontend/src/types/analytics.ts`
- Modify: `frontend/src/views/AnalyticsView.vue`

**Step 1: TrendDataPoint 타입에 subscribers 추가**

`frontend/src/types/analytics.ts` 의 TrendDataPoint:
```typescript
export interface TrendDataPoint {
  date: string
  totalViews: number
  platformViews: Record<string, number>
  totalSubscribers?: number
  platformSubscribers?: Record<string, number>
}
```

**Step 2: AnalyticsView.vue에 구독자 트렌드 Area Chart 추가**

조회수 트렌드 차트(`</div>` 닫는 태그, ~라인 237) 바로 아래, 플랫폼 비교 섹션 위에 삽입:

템플릿 섹션:
```vue
<!-- Subscriber Trend Area Chart -->
<div class="card mb-6">
  <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">구독자 변화 추이</h2>
  <div class="relative h-48 w-full overflow-hidden rounded-lg bg-gray-50 dark:bg-gray-900">
    <template v-if="trendData.length > 0 && subscriberMaxValue > 0">
      <svg
        class="h-full w-full"
        :viewBox="`0 0 ${trendChartWidth} 200`"
        preserveAspectRatio="none"
      >
        <defs>
          <linearGradient id="subscriberGradient" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="#6366f1" stop-opacity="0.3" />
            <stop offset="100%" stop-color="#6366f1" stop-opacity="0.05" />
          </linearGradient>
        </defs>
        <path :d="subscriberAreaPath" fill="url(#subscriberGradient)" />
        <polyline
          :points="subscriberLinePath"
          fill="none"
          stroke="#6366f1"
          stroke-width="2"
        />
      </svg>
    </template>
    <div v-else class="flex h-full items-center justify-center text-sm text-gray-400 dark:text-gray-500">
      구독자 데이터가 없습니다
    </div>
  </div>
</div>
```

스크립트 섹션 (trendXLabels computed 뒤에 추가):
```typescript
// ----- Subscriber Trend Chart -----
const subscriberMaxValue = computed(() => {
  if (trendData.value.length === 0) return 0
  return Math.max(...trendData.value.map((d) => d.totalSubscribers ?? 0), 1)
})

const subscriberLinePath = computed(() => {
  const data = trendData.value
  if (data.length === 0) return ''
  const maxVal = subscriberMaxValue.value
  const padding = 10
  const height = 200
  return data
    .map((d, i) => {
      const x = padding + (i / Math.max(data.length - 1, 1)) * (trendChartWidth - padding * 2)
      const y = height - padding - ((d.totalSubscribers ?? 0) / maxVal) * (height - padding * 2)
      return `${x},${y}`
    })
    .join(' ')
})

const subscriberAreaPath = computed(() => {
  const data = trendData.value
  if (data.length === 0) return ''
  const maxVal = subscriberMaxValue.value
  const padding = 10
  const height = 200
  const points = data.map((d, i) => {
    const x = padding + (i / Math.max(data.length - 1, 1)) * (trendChartWidth - padding * 2)
    const y = height - padding - ((d.totalSubscribers ?? 0) / maxVal) * (height - padding * 2)
    return { x, y }
  })
  const first = points[0]
  const last = points[points.length - 1]
  let path = `M ${first.x},${first.y}`
  for (let i = 1; i < points.length; i++) {
    path += ` L ${points[i].x},${points[i].y}`
  }
  path += ` L ${last.x},${height - padding} L ${first.x},${height - padding} Z`
  return path
})
```

**Step 3: 커밋**

```bash
git add frontend/src/types/analytics.ts frontend/src/views/AnalyticsView.vue
git commit -m "feat: 분석 페이지에 구독자 변화 추이 차트 추가"
```

---

### Task 6: 전체 빌드 검증 및 DB 스키마 확인

**Step 1: analytics_daily 테이블 스키마 확인**

Run: `grep -r "subscribers" sql/` 또는 `grep -r "analytics_daily" sql/`
스키마에 subscribers 컬럼이 없으면 마이그레이션 추가 필요.

**Step 2: 백엔드 전체 빌드**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: 프론트엔드 타입체크**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 에러 없음

**Step 4: 최종 커밋 (필요 시)**

누락된 파일이 있다면 추가 커밋.
