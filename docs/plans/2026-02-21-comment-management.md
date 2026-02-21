# Phase 2 댓글 관리 시스템 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Phase 2 댓글 관리 시스템의 미구현 기능(플랜 게이팅, 자동 동기화, AI 감정분석, AI 답글 통합, 댓글 고정, 알림)을 완성한다.

**Architecture:** 기존 Clean Architecture(api→application→domain→infrastructure) 레이어를 따르며, CommentSyncUseCase에 스케줄러를 추가하고, AI 감정분석을 동기화 파이프라인에 통합한다. 프론트엔드에서는 AI 답글 생성 UI를 CommentCard에 통합한다.

**Tech Stack:** Spring Boot 4 + Kotlin, jOOQ, Spring AI (Claude), Vue 3 + TypeScript + Tailwind CSS, Pinia

---

## 현재 상태 분석

| 기능 | 상태 | 비고 |
|------|------|------|
| 댓글 목록 조회/필터 | ✅ 완료 | `CommentUseCase.listComments()` |
| 수동 동기화 | ✅ 완료 | `CommentSyncUseCase.syncAllComments()` |
| 답글 작성 (수동) | ✅ 완료 | `CommentEngagementUseCase.replyToComment()` |
| 댓글 삭제/숨김 | ✅ 완료 | `CommentEngagementUseCase` |
| 플랫폼 capabilities | ✅ 완료 | `PlatformCommentPort` |
| AI 답글 생성 | ✅ 완료 | `GenerateReplyUseCase` (2크레딧) |
| **플랜 게이팅** | ❌ 미구현 | Pro/Business만 접근 가능해야 함 |
| **자동 동기화 스케줄러** | ❌ 미구현 | 매시간 자동 동기화 필요 |
| **AI 감정분석** | ❌ 미구현 | sentiment 필드가 항상 "NEUTRAL" |
| **AI 답글 UI 통합** | ❌ 미구현 | AI API 존재하나 댓글 UI에 미연결 |
| **댓글 고정 API** | ❌ 미구현 | isPinned 필드만 존재, API 없음 |
| **댓글 알림** | ❌ 미구현 | NotificationType.COMMENT 존재하나 발행 로직 없음 |

---

### Task 1: 플랜 게이팅 — 댓글 기능 Pro/Business 제한

**Files:**
- Modify: `backend/onGo-application/src/main/kotlin/com/ongo/application/comment/CommentUseCase.kt:18`
- Modify: `backend/onGo-application/src/main/kotlin/com/ongo/application/comment/CommentSyncUseCase.kt:34`
- Modify: `backend/onGo-application/src/main/kotlin/com/ongo/application/comment/CommentEngagementUseCase.kt:28`

**Step 1: CommentUseCase에 플랜 검증 추가**

`CommentUseCase.kt`에 UserRepository 주입 후 `listComments()` 시작에 플랜 검증 추가:

```kotlin
// 기존 생성자에 추가
private val userRepository: UserRepository,

// listComments() 함수 시작에 추가
fun listComments(...): CommentListResponse {
    val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
    if (user.planType != PlanType.PRO && user.planType != PlanType.BUSINESS) {
        throw PlanLimitExceededException("댓글 관리", 0)
    }
    // ... 기존 로직
}
```

**Step 2: CommentSyncUseCase에 플랜 검증 추가**

`CommentSyncUseCase.kt` 생성자에 UserRepository 추가, `syncAllComments()` 시작에 동일 검증:

```kotlin
private val userRepository: UserRepository,

fun syncAllComments(userId: Long): CommentSyncResult {
    val user = userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)
    if (user.planType != PlanType.PRO && user.planType != PlanType.BUSINESS) {
        throw PlanLimitExceededException("댓글 관리", 0)
    }
    // ... 기존 로직
}
```

**Step 3: CommentEngagementUseCase에 플랜 검증 추가**

`CommentEngagementUseCase.kt` 생성자에 UserRepository 추가, `replyToComment()`, `deleteComment()`, `hideComment()` 시작에 동일 검증.

**Step 4: 빌드 확인**

Run: `backend/gradlew -p backend compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 5: 커밋**

```bash
git add backend/onGo-application/src/main/kotlin/com/ongo/application/comment/
git commit -m "feat: 댓글 관리 기능 Pro/Business 플랜 게이팅 적용"
```

---

### Task 2: 댓글 고정/해제 API 엔드포인트

**Files:**
- Modify: `backend/onGo-application/src/main/kotlin/com/ongo/application/comment/CommentEngagementUseCase.kt`
- Modify: `backend/onGo-api/src/main/kotlin/com/ongo/api/comment/CommentController.kt`
- Modify: `frontend/src/stores/comments.ts` (pinComment 구현)
- Modify: `frontend/src/api/comments.ts` (pin API 호출 추가)

**Step 1: CommentEngagementUseCase에 pinComment 메서드 추가**

```kotlin
@Transactional
fun pinComment(userId: Long, commentId: Long): CommentResponse {
    val comment = commentRepository.findById(commentId)
        ?: throw NotFoundException("댓글", commentId)
    if (comment.userId != userId) throw ForbiddenException("해당 댓글에 대한 권한이 없습니다")

    val updated = comment.copy(isPinned = !comment.isPinned)
    return commentRepository.update(updated).toResponse()
}
```

**Step 2: CommentController에 PUT /api/v1/comments/{id}/pin 엔드포인트 추가**

```kotlin
@Operation(summary = "댓글 고정/해제 토글", description = "지정된 댓글의 고정 상태를 토글합니다.")
@PutMapping("/{id}/pin")
fun pinComment(
    @Parameter(hidden = true) @CurrentUser userId: Long,
    @PathVariable id: Long,
): ResponseEntity<ResData<CommentResponse>> {
    val result = commentEngagementUseCase.pinComment(userId, id)
    return ResData.success(result)
}
```

**Step 3: 프론트엔드 API에 pin 호출 추가**

`frontend/src/api/comments.ts`에 추가:

```typescript
pin(id: number) {
  return apiClient.put<ResData<Comment>>(`/comments/${id}/pin`).then(unwrapResponse)
},
```

**Step 4: Pinia 스토어 pinComment을 API 호출로 변경**

`frontend/src/stores/comments.ts`의 `pinComment` 액션에서 로컬 토글 대신 API 호출:

```typescript
async pinComment(id: number) {
  const updated = await commentsApi.pin(id)
  const idx = this.comments.findIndex(c => c.id === id)
  if (idx !== -1) this.comments[idx] = updated
},
```

**Step 5: 빌드 확인**

Run: `backend/gradlew -p backend compileKotlin`
Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 둘 다 성공

**Step 6: 커밋**

```bash
git add backend/onGo-api/src/main/kotlin/com/ongo/api/comment/CommentController.kt \
       backend/onGo-application/src/main/kotlin/com/ongo/application/comment/CommentEngagementUseCase.kt \
       frontend/src/api/comments.ts frontend/src/stores/comments.ts
git commit -m "feat: 댓글 고정/해제 API 엔드포인트 및 프론트엔드 연동"
```

---

### Task 3: 댓글 자동 동기화 스케줄러

**Files:**
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/comment/CommentSyncScheduler.kt`

**Step 1: CommentSyncScheduler 생성**

기존 스케줄러 패턴(예: `AnalyticsSyncScheduler`, `CreditScheduler`) 참고:

```kotlin
package com.ongo.application.comment

import com.ongo.common.enums.PlanType
import com.ongo.domain.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class CommentSyncScheduler(
    private val commentSyncUseCase: CommentSyncUseCase,
    private val userRepository: UserRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 * * * *") // 매시간 정각
    fun syncCommentsForActiveUsers() {
        log.info("댓글 자동 동기화 스케줄러 시작")

        val proBusinessUsers = userRepository.findAll()
            .filter { it.planType == PlanType.PRO || it.planType == PlanType.BUSINESS }

        var successCount = 0
        var failCount = 0

        for (user in proBusinessUsers) {
            try {
                val result = commentSyncUseCase.syncAllComments(user.id!!)
                log.debug("댓글 동기화 완료: userId={}, synced={}, new={}",
                    user.id, result.totalSynced, result.totalNew)
                successCount++
            } catch (e: Exception) {
                log.warn("댓글 동기화 실패: userId={}, error={}", user.id, e.message)
                failCount++
            }
        }

        log.info("댓글 자동 동기화 스케줄러 완료: success={}, fail={}", successCount, failCount)
    }
}
```

> **참고:** `syncAllComments()`에 Task 1에서 추가한 플랜 검증이 있으므로, 스케줄러에서 호출할 때는 이미 Pro/Business 사용자만 필터링해서 호출한다. 만약 스케줄러가 내부 호출이라 플랜 검증이 중복되면, 별도 내부용 메서드(`syncAllCommentsInternal`)를 만들거나, 스케줄러의 필터가 충분하므로 그대로 진행해도 무방하다.

**Step 2: UserRepository에 findAll() 존재 여부 확인**

`UserRepository`에 `findAll()`이 없으면 추가가 필요하다. 기존 인터페이스를 확인하고 필요시 추가:

```kotlin
// UserRepository 인터페이스에 추가 (없는 경우)
fun findAll(): List<User>
```

**Step 3: 빌드 확인**

Run: `backend/gradlew -p backend compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: 커밋**

```bash
git add backend/onGo-application/src/main/kotlin/com/ongo/application/comment/CommentSyncScheduler.kt
git commit -m "feat: 댓글 자동 동기화 스케줄러 추가 (매시간)"
```

---

### Task 4: AI 감정분석 통합 — 동기화 시 자동 분류

**Files:**
- Modify: `backend/onGo-application/src/main/kotlin/com/ongo/application/comment/CommentSyncUseCase.kt`
- Modify: `backend/onGo-application/src/main/kotlin/com/ongo/application/ai/PromptTemplates.kt`
- Modify: `backend/onGo-application/src/main/kotlin/com/ongo/application/ai/result/AiStructuredResults.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/ai/AnalyzeSentimentUseCase.kt`
- Modify: `backend/onGo-common/src/main/kotlin/com/ongo/common/enums/AiFeature.kt`

**Step 1: AiFeature enum에 SENTIMENT_ANALYSIS 추가**

`AiFeature.kt`에 추가 (비용 0 — 동기화 시 자동 실행이므로 크레딧 미차감):

```kotlin
SENTIMENT_ANALYSIS("감정 분석", 0),
```

**Step 2: SentimentAnalysisResult 추가**

`AiStructuredResults.kt`에 추가:

```kotlin
data class SentimentAnalysisResult(
    val results: List<SentimentItem>,
) {
    data class SentimentItem(
        val index: Int,
        val sentiment: String, // POSITIVE, NEUTRAL, NEGATIVE
    )
}
```

**Step 3: PromptTemplates에 감정분석 프롬프트 추가**

`PromptTemplates.kt`에 추가:

```kotlin
val SENTIMENT_ANALYSIS_SYSTEM = """
    당신은 댓글 감정 분석 전문가입니다.
    주어진 댓글 목록의 감정을 POSITIVE, NEUTRAL, NEGATIVE 중 하나로 분류하세요.
    각 댓글의 인덱스와 감정을 JSON으로 반환하세요.
""".trimIndent()

val SENTIMENT_ANALYSIS_USER = """
    다음 댓글들의 감정을 분석해주세요:
    {comments}
""".trimIndent()
```

**Step 4: AnalyzeSentimentUseCase 생성**

```kotlin
package com.ongo.application.ai

import com.ongo.application.ai.result.SentimentAnalysisResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AnalyzeSentimentUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val rateLimiter: AiRateLimiter,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun analyzeBatch(userId: Long, comments: List<String>): List<String> {
        if (comments.isEmpty()) return emptyList()

        rateLimiter.checkRateLimit(userId)

        val numberedComments = comments.mapIndexed { i, c -> "${i}: ${c.take(200)}" }.joinToString("\n")
        val userPrompt = PromptTemplates.SENTIMENT_ANALYSIS_USER
            .replace("{comments}", InputSanitizer.sanitize(numberedComments))

        return try {
            val result = chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.SENTIMENT_ANALYSIS_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(SentimentAnalysisResult::class.java)

            if (result != null) {
                val sentimentMap = result.results.associate { it.index to it.sentiment }
                comments.indices.map { sentimentMap[it] ?: "NEUTRAL" }
            } else {
                comments.map { "NEUTRAL" }
            }
        } catch (e: Exception) {
            log.warn("감정 분석 실패, NEUTRAL로 폴백: {}", e.message)
            comments.map { "NEUTRAL" }
        }
    }
}
```

**Step 5: CommentSyncUseCase에 감정분석 통합**

`CommentSyncUseCase.kt` 생성자에 `AnalyzeSentimentUseCase` 추가 후, `syncVideoComments()`의 `upsertBatch` 호출 전에 감정분석 수행:

```kotlin
private val analyzeSentimentUseCase: AnalyzeSentimentUseCase,

// syncVideoComments() 내부, upsertBatch 호출 전에 추가:
// AI 감정분석 (신규 댓글만)
val newComments = allComments.filter { comment ->
    val p = comment.platform
    val pcId = comment.platformCommentId
    p == null || pcId == null ||
        commentRepository.findByPlatformAndPlatformCommentId(p, pcId) == null
}

val sentimentResults = if (newComments.isNotEmpty()) {
    try {
        analyzeSentimentUseCase.analyzeBatch(userId, newComments.map { it.content })
    } catch (e: Exception) {
        log.warn("감정분석 스킵: {}", e.message)
        newComments.map { "NEUTRAL" }
    }
} else emptyList()

// 신규 댓글에 감정 적용
val newCommentSet = newComments.mapIndexed { i, c -> c.platformCommentId to sentimentResults.getOrElse(i) { "NEUTRAL" } }.toMap()
val enrichedComments = allComments.map { comment ->
    val sentiment = newCommentSet[comment.platformCommentId]
    if (sentiment != null) comment.copy(sentiment = sentiment) else comment
}

val upserted = commentRepository.upsertBatch(enrichedComments)
```

**Step 6: 빌드 확인**

Run: `backend/gradlew -p backend compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 7: 커밋**

```bash
git add backend/onGo-application/src/main/kotlin/com/ongo/application/ai/AnalyzeSentimentUseCase.kt \
       backend/onGo-application/src/main/kotlin/com/ongo/application/comment/CommentSyncUseCase.kt \
       backend/onGo-application/src/main/kotlin/com/ongo/application/ai/PromptTemplates.kt \
       backend/onGo-application/src/main/kotlin/com/ongo/application/ai/result/AiStructuredResults.kt \
       backend/onGo-common/src/main/kotlin/com/ongo/common/enums/AiFeature.kt
git commit -m "feat: 댓글 동기화 시 AI 감정분석 자동 수행"
```

---

### Task 5: AI 답글 생성 UI 통합 — CommentCard에 AI 답글 버튼 추가

**Files:**
- Modify: `frontend/src/components/comments/CommentReplyForm.vue`
- Modify: `frontend/src/components/comments/CommentCard.vue`
- Modify: `frontend/src/stores/comments.ts`

**Step 1: CommentReplyForm에 AI 답글 생성 기능 추가**

`CommentReplyForm.vue`에 "AI 답글 생성" 버튼 추가. 클릭 시 `aiApi.generateReply()`를 호출하고, 3가지 톤 결과를 드롭다운으로 표시. 선택하면 textarea에 자동 입력:

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { aiApi } from '@/api/ai'

const props = defineProps<{
  commentContent: string   // 원본 댓글 내용 (AI 답글용)
  channelTone?: string     // 채널 톤
}>()

const emit = defineEmits<{
  submit: [text: string]
  cancel: []
}>()

const replyText = ref('')
const isSubmitting = ref(false)
const isGeneratingAi = ref(false)
const aiSuggestions = ref<{ tone: string; reply: string }[]>([])
const showAiSuggestions = ref(false)
const maxLength = 500

async function generateAiReply() {
  isGeneratingAi.value = true
  try {
    const result = await aiApi.generateReply({
      comment: props.commentContent,
      channelTone: props.channelTone || 'friendly',
    })
    aiSuggestions.value = result.replies
    showAiSuggestions.value = true
  } catch {
    // 에러 시 무시 — 크레딧 부족 등
  } finally {
    isGeneratingAi.value = false
  }
}

function selectAiSuggestion(reply: string) {
  replyText.value = reply
  showAiSuggestions.value = false
}

// ... 기존 submit, cancel 로직 유지
</script>
```

템플릿에 AI 관련 UI 추가:
- 🤖 "AI 답글 생성" 버튼 (textarea 아래, 제출 버튼 왼쪽)
- 생성 중 로딩 스피너
- 3가지 톤 결과 카드 (정중한/친근한/유머러스한) — 클릭하면 textarea에 삽입

**Step 2: CommentCard에서 commentContent prop 전달**

`CommentCard.vue`의 `<CommentReplyForm>` 태그에 `:comment-content="comment.content"` prop 추가:

```vue
<CommentReplyForm
  v-if="isReplying"
  :comment-content="comment.content"
  :channel-tone="'friendly'"
  @submit="handleReply"
  @cancel="isReplying = false"
/>
```

**Step 3: 빌드 확인**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 성공

**Step 4: 커밋**

```bash
git add frontend/src/components/comments/CommentReplyForm.vue \
       frontend/src/components/comments/CommentCard.vue
git commit -m "feat: 댓글 답글 폼에 AI 답글 생성 기능 통합"
```

---

### Task 6: 새 댓글 알림 전송

**Files:**
- Modify: `backend/onGo-application/src/main/kotlin/com/ongo/application/comment/CommentSyncUseCase.kt`
- Modify (참고): `backend/onGo-domain/src/main/kotlin/com/ongo/domain/settings/UserSettings.kt` — `notificationComment` 필드 활용

**Step 1: CommentSyncUseCase에 알림 로직 추가**

생성자에 추가:
```kotlin
private val notificationRepository: NotificationRepository,
private val webSocketNotificationService: WebSocketNotificationService,
private val settingsRepository: UserSettingsRepository,
```

`syncVideoComments()` 끝에서 신규 댓글이 있으면 알림 발행:

```kotlin
// 신규 댓글이 있고, 사용자 설정이 realtime이면 즉시 알림
if (newCount > 0) {
    val settings = settingsRepository.findByUserId(userId)
    if (settings?.notificationComment == "realtime") {
        val notification = Notification(
            userId = userId,
            type = NotificationType.COMMENT,
            title = "새 댓글 ${newCount}개",
            message = "${platform.name}에서 새 댓글 ${newCount}개가 도착했습니다.",
        )
        notificationRepository.save(notification)
        webSocketNotificationService.sendToUser(
            userId = userId,
            type = "COMMENT",
            payload = mapOf("newCount" to newCount, "platform" to platform.name, "videoId" to videoId),
        )
    }
}
```

> **daily/weekly 다이제스트:** daily/weekly 알림은 별도 스케줄러(WeeklyDigestScheduler 패턴 참고)로 미발송 알림을 집계하여 전송하는 방식. Phase 2에서는 realtime만 우선 구현하고, daily/weekly는 후속 태스크로 남겨둔다.

**Step 2: 빌드 확인**

Run: `backend/gradlew -p backend compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: 커밋**

```bash
git add backend/onGo-application/src/main/kotlin/com/ongo/application/comment/CommentSyncUseCase.kt
git commit -m "feat: 새 댓글 실시간 알림 전송 (realtime 설정 시)"
```

---

### Task 7: 전체 빌드 및 통합 검증

**Files:** 없음 (검증만)

**Step 1: 백엔드 전체 빌드**

Run: `backend/gradlew -p backend compileKotlin`
Expected: BUILD SUCCESSFUL

**Step 2: 프론트엔드 타입 체크**

Run: `cd frontend && npx vue-tsc --noEmit`
Expected: 에러 없음

**Step 3: 최종 커밋 (필요시)**

전체 검증 후 누락된 변경이 있으면 추가 커밋.

---

## 후속 과제 (이 계획 범위 밖)

- [ ] 댓글 스레딩: 동기화 시 `parentCommentId` 매핑 및 프론트엔드 중첩 UI
- [ ] daily/weekly 댓글 다이제스트 알림 스케줄러
- [ ] TikTok 답글 전송 에러 핸들링 개선 (현재 read-only)
- [ ] 댓글 감정분석 결과 대시보드 위젯
