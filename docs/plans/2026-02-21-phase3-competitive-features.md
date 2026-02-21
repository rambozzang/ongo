# Phase 3: 경쟁력 강화 5대 기능 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 세계적 크리에이터 플랫폼 대비 약점 5개 영역(AI 리사이징, 트렌드, CRM, 다국어, 브랜드딜)을 보강하여 경쟁력 극대화

**Architecture:** 각 기능을 독립 모듈로 구현. 기존 Clean Architecture 계층(domain→application→api→infrastructure)과 이벤트 기반 비동기 처리 패턴을 따른다. 프론트엔드는 Vue 3 + Pinia 스토어 + Tailwind CSS 패턴.

**Tech Stack:** Spring Boot 4 + Kotlin + jOOQ, Vue 3 + Pinia + Tailwind, FFmpeg, Spring AI (Claude), YouTube Data API v3

---

## Feature A: AI 영상 리사이징

### Task 1: DB 마이그레이션 — video_resizes 테이블

**Files:**
- Create: `backend/sql/V15__video_resizes.sql`

**Step 1: 마이그레이션 SQL 작성**

```sql
-- V15__video_resizes.sql
-- 영상 리사이징 결과 저장

CREATE TABLE video_resizes (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id),
    original_video_id BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    aspect_ratio      VARCHAR(10) NOT NULL,  -- '9:16', '1:1', '4:5', '16:9'
    width             INT NOT NULL,
    height            INT NOT NULL,
    file_url          TEXT,
    file_size_bytes   BIGINT,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, PROCESSING, COMPLETED, FAILED
    error_message     TEXT,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMP
);

CREATE INDEX idx_video_resizes_user ON video_resizes(user_id);
CREATE INDEX idx_video_resizes_original ON video_resizes(original_video_id);
```

**Step 2: Commit**

```bash
git add backend/sql/V15__video_resizes.sql
git commit -m "chore: 영상 리사이징 DB 마이그레이션"
```

---

### Task 2: 도메인 엔티티 + 리포지토리

**Files:**
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/video/entity/VideoResize.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/video/VideoResizeRepository.kt`

**Step 1: VideoResize 엔티티**

```kotlin
package com.ongo.domain.video.entity

import java.time.LocalDateTime

data class VideoResize(
    val id: Long? = null,
    val userId: Long,
    val originalVideoId: Long,
    val aspectRatio: String,
    val width: Int,
    val height: Int,
    val fileUrl: String? = null,
    val fileSizeBytes: Long? = null,
    val status: String = "PENDING",
    val errorMessage: String? = null,
    val createdAt: LocalDateTime? = null,
    val completedAt: LocalDateTime? = null,
)
```

**Step 2: Repository 인터페이스**

```kotlin
package com.ongo.domain.video

import com.ongo.domain.video.entity.VideoResize

interface VideoResizeRepository {
    fun findById(id: Long): VideoResize?
    fun findByOriginalVideoId(videoId: Long): List<VideoResize>
    fun findByUserId(userId: Long): List<VideoResize>
    fun save(resize: VideoResize): VideoResize
    fun updateStatus(id: Long, status: String, fileUrl: String? = null, fileSizeBytes: Long? = null, errorMessage: String? = null)
}
```

**Step 3: 빌드 확인**

```bash
cd backend && ./gradlew compileKotlin
```

**Step 4: Commit**

```bash
git add backend/onGo-domain/
git commit -m "feat: VideoResize 도메인 엔티티 + 리포지토리"
```

---

### Task 3: jOOQ 인프라 — Tables/Fields + Repository 구현

**Files:**
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/Tables.kt`
- Create: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/VideoResizeJooqRepository.kt`

**Step 1: Tables.kt에 등록**

Tables 객체에:
```kotlin
val VIDEO_RESIZES = DSL.table("video_resizes")
```

Fields 객체에:
```kotlin
// video_resizes
val ORIGINAL_VIDEO_ID = DSL.field("original_video_id", Long::class.java)
val ASPECT_RATIO = DSL.field("aspect_ratio", String::class.java)
val COMPLETED_AT_RESIZE = DSL.field("completed_at", java.time.LocalDateTime::class.java)
```

**Step 2: VideoResizeJooqRepository 구현**

```kotlin
package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.video.VideoResizeRepository
import com.ongo.domain.video.entity.VideoResize
import com.ongo.infrastructure.persistence.jooq.Fields.*
import com.ongo.infrastructure.persistence.jooq.Tables.VIDEO_RESIZES
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class VideoResizeJooqRepository(
    private val dsl: DSLContext,
) : VideoResizeRepository {

    override fun findById(id: Long): VideoResize? =
        dsl.select().from(VIDEO_RESIZES).where(ID.eq(id)).fetchOne()?.toVideoResize()

    override fun findByOriginalVideoId(videoId: Long): List<VideoResize> =
        dsl.select().from(VIDEO_RESIZES).where(ORIGINAL_VIDEO_ID.eq(videoId))
            .orderBy(CREATED_AT.desc()).fetch().map { it.toVideoResize() }

    override fun findByUserId(userId: Long): List<VideoResize> =
        dsl.select().from(VIDEO_RESIZES).where(USER_ID.eq(userId))
            .orderBy(CREATED_AT.desc()).fetch().map { it.toVideoResize() }

    override fun save(resize: VideoResize): VideoResize {
        val id = dsl.insertInto(VIDEO_RESIZES)
            .set(USER_ID, resize.userId)
            .set(ORIGINAL_VIDEO_ID, resize.originalVideoId)
            .set(ASPECT_RATIO, resize.aspectRatio)
            .set(WIDTH, resize.width)
            .set(HEIGHT, resize.height)
            .set(STATUS, resize.status)
            .returningResult(ID)
            .fetchOne()!!.get(ID)
        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: String, fileUrl: String?, fileSizeBytes: Long?, errorMessage: String?) {
        val update = dsl.update(VIDEO_RESIZES)
            .set(STATUS, status)
        if (fileUrl != null) update.set(FILE_URL, fileUrl)
        if (fileSizeBytes != null) update.set(FILE_SIZE_BYTES, fileSizeBytes)
        if (errorMessage != null) update.set(ERROR_MESSAGE, errorMessage)
        if (status == "COMPLETED" || status == "FAILED") {
            update.set(COMPLETED_AT_RESIZE, LocalDateTime.now())
        }
        update.where(ID.eq(id)).execute()
    }

    private fun Record.toVideoResize(): VideoResize = VideoResize(
        id = get(ID),
        userId = get(USER_ID),
        originalVideoId = get(ORIGINAL_VIDEO_ID),
        aspectRatio = get(ASPECT_RATIO),
        width = get(WIDTH),
        height = get(HEIGHT),
        fileUrl = get(FILE_URL),
        fileSizeBytes = get(FILE_SIZE_BYTES),
        status = get(STATUS),
        errorMessage = get(ERROR_MESSAGE),
        createdAt = localDateTime(CREATED_AT),
        completedAt = localDateTime(COMPLETED_AT_RESIZE),
    )
}
```

**Step 3: 빌드 확인**

```bash
cd backend && ./gradlew compileKotlin
```

**Step 4: Commit**

```bash
git add backend/onGo-infrastructure/
git commit -m "feat: VideoResize jOOQ Repository 구현"
```

---

### Task 4: FFmpeg 리사이즈 서비스 (인프라)

**Files:**
- Create: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/transcode/FfmpegResizeService.kt`

**Step 1: FFmpeg 리사이즈 구현**

```kotlin
package com.ongo.infrastructure.transcode

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path

@Service
class FfmpegResizeService {

    private val log = LoggerFactory.getLogger(javaClass)

    data class ResizeSpec(
        val aspectRatio: String,
        val width: Int,
        val height: Int,
    )

    companion object {
        val SPECS = mapOf(
            "9:16" to ResizeSpec("9:16", 1080, 1920),
            "1:1" to ResizeSpec("1:1", 1080, 1080),
            "4:5" to ResizeSpec("4:5", 1080, 1350),
            "16:9" to ResizeSpec("16:9", 1920, 1080),
        )
    }

    /**
     * 입력 영상을 지정된 비율로 리사이즈.
     * 중앙 기준 크롭 후 스케일링.
     * @return 출력 파일 경로
     */
    fun resize(inputPath: String, outputPath: String, spec: ResizeSpec): Path {
        log.info("FFmpeg 리사이즈 시작: input={}, ratio={}, target={}x{}", inputPath, spec.aspectRatio, spec.width, spec.height)

        // crop=중앙 기준, scale=목표 해상도
        val vf = "crop=min(iw\\,ih*${spec.width}/${spec.height}):min(ih\\,iw*${spec.height}/${spec.width}),scale=${spec.width}:${spec.height}"

        val command = listOf(
            "ffmpeg", "-y",
            "-i", inputPath,
            "-vf", vf,
            "-c:v", "libx264",
            "-preset", "medium",
            "-crf", "23",
            "-c:a", "aac",
            "-b:a", "128k",
            "-movflags", "+faststart",
            outputPath,
        )

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            log.error("FFmpeg 리사이즈 실패 (exit={}): {}", exitCode, output.takeLast(500))
            throw RuntimeException("FFmpeg 리사이즈 실패 (exit code: $exitCode)")
        }

        val outputFile = Path.of(outputPath)
        log.info("FFmpeg 리사이즈 완료: output={}, size={}bytes", outputPath, Files.size(outputFile))
        return outputFile
    }
}
```

**Step 2: 빌드 확인**

```bash
cd backend && ./gradlew compileKotlin
```

**Step 3: Commit**

```bash
git add backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/transcode/FfmpegResizeService.kt
git commit -m "feat: FFmpeg 리사이즈 서비스 구현"
```

---

### Task 5: 백엔드 API — VideoResizeUseCase + Controller

**Files:**
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/video/VideoResizeUseCase.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/video/dto/VideoResizeDtos.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/video/VideoResizeEventListener.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/event/VideoResizeEvent.kt`
- Modify: `backend/onGo-api/src/main/kotlin/com/ongo/api/video/VideoController.kt` — 리사이즈 엔드포인트 추가

**Step 1: 이벤트 + DTO**

VideoResizeEvent:
```kotlin
package com.ongo.domain.event

data class VideoResizeRequestedEvent(
    val resizeId: Long,
    val videoId: Long,
    val userId: Long,
)
```

VideoResizeDtos:
```kotlin
package com.ongo.application.video.dto

data class ResizeRequest(
    val aspectRatios: List<String>,  // ["9:16", "1:1"]
)

data class VideoResizeResponse(
    val id: Long,
    val originalVideoId: Long,
    val aspectRatio: String,
    val width: Int,
    val height: Int,
    val fileUrl: String?,
    val status: String,
    val createdAt: String?,
)
```

**Step 2: UseCase**

```kotlin
package com.ongo.application.video

import com.ongo.application.credit.CreditService
import com.ongo.application.video.dto.ResizeRequest
import com.ongo.application.video.dto.VideoResizeResponse
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.event.VideoResizeRequestedEvent
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoResizeRepository
import com.ongo.domain.video.entity.VideoResize
import com.ongo.infrastructure.transcode.FfmpegResizeService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class VideoResizeUseCase(
    private val videoRepository: VideoRepository,
    private val resizeRepository: VideoResizeRepository,
    private val creditService: CreditService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    companion object {
        const val CREDIT_COST = 3
    }

    @Transactional
    fun requestResize(userId: Long, videoId: Long, request: ResizeRequest): List<VideoResizeResponse> {
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw BusinessException("FORBIDDEN", "해당 영상에 대한 권한이 없습니다")

        val validRatios = FfmpegResizeService.SPECS.keys
        val requestedRatios = request.aspectRatios.filter { it in validRatios }
        if (requestedRatios.isEmpty()) throw BusinessException("INVALID_RATIO", "유효한 비율을 선택해주세요: $validRatios")

        // 크레딧 차감 (비율 수 × 3 credits)
        val totalCost = requestedRatios.size * CREDIT_COST
        creditService.validateAndDeduct(userId, totalCost, "VIDEO_RESIZE")

        val results = requestedRatios.map { ratio ->
            val spec = FfmpegResizeService.SPECS[ratio]!!
            val resize = resizeRepository.save(
                VideoResize(
                    userId = userId,
                    originalVideoId = videoId,
                    aspectRatio = ratio,
                    width = spec.width,
                    height = spec.height,
                )
            )
            eventPublisher.publishEvent(VideoResizeRequestedEvent(resize.id!!, videoId, userId))
            resize.toResponse()
        }
        return results
    }

    fun getResizes(userId: Long, videoId: Long): List<VideoResizeResponse> =
        resizeRepository.findByOriginalVideoId(videoId).map { it.toResponse() }

    private fun VideoResize.toResponse() = VideoResizeResponse(
        id = id!!,
        originalVideoId = originalVideoId,
        aspectRatio = aspectRatio,
        width = width,
        height = height,
        fileUrl = fileUrl,
        status = status,
        createdAt = createdAt?.toString(),
    )
}
```

**Step 3: EventListener (비동기 처리)**

```kotlin
package com.ongo.application.video

import com.ongo.domain.event.VideoResizeRequestedEvent
import com.ongo.domain.video.VideoResizeRepository
import com.ongo.infrastructure.storage.StorageService
import com.ongo.infrastructure.transcode.FfmpegResizeService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.nio.file.Files
import java.nio.file.Path

@Component
class VideoResizeEventListener(
    private val resizeRepository: VideoResizeRepository,
    private val ffmpegResizeService: FfmpegResizeService,
    private val storageService: StorageService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleResize(event: VideoResizeRequestedEvent) {
        log.info("영상 리사이즈 시작: resizeId={}, videoId={}", event.resizeId, event.videoId)

        val resize = resizeRepository.findById(event.resizeId) ?: return
        resizeRepository.updateStatus(event.resizeId, "PROCESSING")

        val tempDir = Path.of("/tmp/ongo-resize/${event.resizeId}")
        Files.createDirectories(tempDir)
        val inputFile = tempDir.resolve("original.mp4")
        val outputFile = tempDir.resolve("resized_${resize.aspectRatio.replace(":", "x")}.mp4")

        try {
            // 1. 원본 다운로드
            storageService.downloadFile(event.videoId).use { input ->
                Files.copy(input, inputFile)
            }

            // 2. FFmpeg 리사이즈
            val spec = FfmpegResizeService.SPECS[resize.aspectRatio]
                ?: throw RuntimeException("Unknown ratio: ${resize.aspectRatio}")
            ffmpegResizeService.resize(inputFile.toString(), outputFile.toString(), spec)

            // 3. 결과 업로드
            val fileSize = Files.size(outputFile)
            val uploadKey = "videos/${event.videoId}/resized/${resize.aspectRatio.replace(":", "x")}.mp4"
            val url = storageService.uploadFile(uploadKey, outputFile)

            resizeRepository.updateStatus(event.resizeId, "COMPLETED", url, fileSize)
            log.info("영상 리사이즈 완료: resizeId={}, size={}bytes", event.resizeId, fileSize)
        } catch (e: Exception) {
            log.error("영상 리사이즈 실패: resizeId={}", event.resizeId, e)
            resizeRepository.updateStatus(event.resizeId, "FAILED", errorMessage = e.message?.take(500))
        } finally {
            try {
                Files.deleteIfExists(outputFile)
                Files.deleteIfExists(inputFile)
                Files.deleteIfExists(tempDir)
            } catch (e: Exception) {
                log.debug("임시 파일 정리: {}", e.message)
            }
        }
    }
}
```

**Step 4: Controller 엔드포인트 추가**

VideoController.kt에 추가:
```kotlin
@Operation(summary = "영상 리사이즈 요청")
@PostMapping("/{id}/resize")
fun requestResize(
    @Parameter(hidden = true) @CurrentUser userId: Long,
    @PathVariable id: Long,
    @RequestBody request: ResizeRequest,
): ResponseEntity<ResData<List<VideoResizeResponse>>> =
    ResData.success(videoResizeUseCase.requestResize(userId, id, request))

@Operation(summary = "리사이즈 결과 조회")
@GetMapping("/{id}/resizes")
fun getResizes(
    @Parameter(hidden = true) @CurrentUser userId: Long,
    @PathVariable id: Long,
): ResponseEntity<ResData<List<VideoResizeResponse>>> =
    ResData.success(videoResizeUseCase.getResizes(userId, id))
```

**Step 5: 빌드 확인 + Commit**

```bash
cd backend && ./gradlew compileKotlin
git add backend/
git commit -m "feat: AI 영상 리사이즈 — UseCase, EventListener, Controller"
```

---

### Task 6: 프론트엔드 — 리사이즈 API + UI

**Files:**
- Modify: `frontend/src/api/video.ts` — 리사이즈 API 추가
- Modify: `frontend/src/types/video.ts` — VideoResize 타입 추가
- Create: `frontend/src/components/video/VideoResizePanel.vue`

**Step 1: 타입 + API**

video.ts 타입에 추가:
```typescript
export interface VideoResize {
  id: number
  originalVideoId: number
  aspectRatio: string
  width: number
  height: number
  fileUrl: string | null
  status: string
  createdAt: string | null
}
```

video.ts API에 추가:
```typescript
requestResize(videoId: number, aspectRatios: string[]) {
  return apiClient.post<ResData<VideoResize[]>>(`/videos/${videoId}/resize`, { aspectRatios }).then(unwrapResponse)
},
getResizes(videoId: number) {
  return apiClient.get<ResData<VideoResize[]>>(`/videos/${videoId}/resizes`).then(unwrapResponse)
},
```

**Step 2: VideoResizePanel 컴포넌트**

선택 가능한 비율 버튼(9:16, 1:1, 4:5, 16:9) + 리사이즈 요청 + 결과 목록 표시. VideoDetailView에 삽입.

**Step 3: 빌드 확인 + Commit**

```bash
cd frontend && npx vue-tsc --noEmit
git add frontend/
git commit -m "feat: 영상 리사이즈 프론트엔드 UI"
```

---

## Feature B: 트렌드 모니터링

### Task 7: DB 마이그레이션 — trends + trend_alerts

**Files:**
- Create: `backend/sql/V16__trends.sql`

```sql
-- V16__trends.sql
-- 트렌드 모니터링

CREATE TABLE trends (
    id          BIGSERIAL PRIMARY KEY,
    category    VARCHAR(100),
    keyword     VARCHAR(200) NOT NULL,
    score       DOUBLE PRECISION NOT NULL DEFAULT 0,
    source      VARCHAR(50) NOT NULL,  -- YOUTUBE, GOOGLE_TRENDS, INTERNAL
    platform    VARCHAR(50),
    region      VARCHAR(10) DEFAULT 'KR',
    date        DATE NOT NULL DEFAULT CURRENT_DATE,
    metadata    JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trends_date ON trends(date);
CREATE INDEX idx_trends_keyword ON trends(keyword);
CREATE INDEX idx_trends_category ON trends(category);

CREATE TABLE trend_alerts (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    keyword     VARCHAR(200) NOT NULL,
    threshold   DOUBLE PRECISION NOT NULL DEFAULT 50,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trend_alerts_user ON trend_alerts(user_id);
```

**Step 1: Commit**

```bash
git add backend/sql/V16__trends.sql
git commit -m "chore: 트렌드 모니터링 DB 마이그레이션"
```

---

### Task 8: 트렌드 도메인 + 인프라 + API

**Files:**
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/trend/Trend.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/trend/TrendAlert.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/trend/TrendRepository.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/Tables.kt`
- Create: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/TrendJooqRepository.kt`
- Create: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/trends/GoogleTrendsService.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/trend/TrendUseCase.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/trend/dto/TrendDtos.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/trend/TrendSyncScheduler.kt`
- Create: `backend/onGo-api/src/main/kotlin/com/ongo/api/trend/TrendController.kt`

핵심 구현:
- `Trend` 엔티티: keyword, score, source(YOUTUBE/GOOGLE_TRENDS/INTERNAL), category, date
- `TrendAlert` 엔티티: user_id, keyword, threshold, enabled
- `TrendRepository`: findByDate, findByCategory, searchByKeyword, saveBatch, 알림 CRUD
- `GoogleTrendsService`: Google Trends RSS/API 호출하여 키워드 트렌드 수집
- `TrendUseCase`:
  - `getTrends(date, category, platform)` — 트렌드 목록 조회
  - `analyzeTrends(userId, category)` — AI 분석 (5 credits)
  - `manageAlerts(userId)` — 알림 CRUD
- `TrendSyncScheduler`: 매일 02:00 YouTube Data API + Google Trends 수집
- `TrendController`: 5 endpoints — GET /trends, GET /trends/analysis, POST/PUT/DELETE /trends/alerts
- AI 분석: Claude가 트렌드 데이터를 종합하여 콘텐츠 제안 생성

**빌드 확인 + Commit**

```bash
cd backend && ./gradlew compileKotlin
git add backend/
git commit -m "feat: 트렌드 모니터링 백엔드 전체 구현"
```

---

### Task 9: 트렌드 프론트엔드

**Files:**
- Create: `frontend/src/types/trend.ts`
- Create: `frontend/src/api/trend.ts`
- Create: `frontend/src/stores/trend.ts`
- Create: `frontend/src/views/TrendView.vue`
- Create: `frontend/src/components/trend/TrendChart.vue`
- Create: `frontend/src/components/trend/TrendAlertManager.vue`
- Modify: `frontend/src/router/index.ts` — /trend 라우트 추가
- Modify: `frontend/src/components/layout/SideNav.vue` — 트렌드 네비게이션 추가

TrendView 탭 구성: 키워드 트렌드 차트 | AI 분석 리포트 | 알림 관리

**빌드 확인 + Commit**

```bash
cd frontend && npx vue-tsc --noEmit
git add frontend/
git commit -m "feat: 트렌드 모니터링 프론트엔드"
```

---

## Feature C: 팬/오디언스 CRM

### Task 10: DB 마이그레이션 — audience 테이블

**Files:**
- Create: `backend/sql/V17__audience_crm.sql`

```sql
-- V17__audience_crm.sql
-- 팬/오디언스 CRM

CREATE TABLE audience_profiles (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    author_name         VARCHAR(200) NOT NULL,
    platform            VARCHAR(50),
    avatar_url          TEXT,
    engagement_score    DOUBLE PRECISION NOT NULL DEFAULT 0,
    tags                JSONB DEFAULT '[]',
    total_interactions  INT NOT NULL DEFAULT 0,
    positive_ratio      DOUBLE PRECISION DEFAULT 0,
    first_seen_at       TIMESTAMP,
    last_seen_at        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audience_profiles_user ON audience_profiles(user_id);
CREATE INDEX idx_audience_profiles_score ON audience_profiles(engagement_score DESC);
CREATE UNIQUE INDEX idx_audience_profiles_unique ON audience_profiles(user_id, author_name, platform);

CREATE TABLE audience_segments (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    conditions  JSONB NOT NULL DEFAULT '{}',
    auto_update BOOLEAN NOT NULL DEFAULT TRUE,
    member_count INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audience_segments_user ON audience_segments(user_id);

CREATE TABLE audience_profile_segments (
    profile_id  BIGINT NOT NULL REFERENCES audience_profiles(id) ON DELETE CASCADE,
    segment_id  BIGINT NOT NULL REFERENCES audience_segments(id) ON DELETE CASCADE,
    PRIMARY KEY (profile_id, segment_id)
);
```

**Commit**

```bash
git add backend/sql/V17__audience_crm.sql
git commit -m "chore: 오디언스 CRM DB 마이그레이션"
```

---

### Task 11: CRM 도메인 + 인프라 + API

**Files:**
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/audience/AudienceProfile.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/audience/AudienceSegment.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/audience/AudienceRepository.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/Tables.kt`
- Create: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/AudienceJooqRepository.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/audience/AudienceUseCase.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/audience/dto/AudienceDtos.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/audience/AudienceProfileSyncListener.kt`
- Create: `backend/onGo-api/src/main/kotlin/com/ongo/api/audience/AudienceController.kt`

핵심 구현:
- 댓글 동기화 시 자동으로 프로필 생성/업데이트 (이벤트 리스너)
- 참여도 점수 = 빈도(0.4) + 최근성(0.3) + 긍정률(0.3)
- 세그먼트: JSONB 조건으로 동적 필터링
- API: 프로필 목록/상세, 세그먼트 CRUD, 프로필 태그 수정

**빌드 확인 + Commit**

```bash
cd backend && ./gradlew compileKotlin
git add backend/
git commit -m "feat: 오디언스 CRM 백엔드 전체 구현"
```

---

### Task 12: CRM 프론트엔드

**Files:**
- Create: `frontend/src/types/audience.ts`
- Create: `frontend/src/api/audience.ts`
- Create: `frontend/src/stores/audience.ts`
- Create: `frontend/src/views/AudienceView.vue`
- Create: `frontend/src/components/audience/ProfileCard.vue`
- Create: `frontend/src/components/audience/SegmentManager.vue`
- Modify: `frontend/src/router/index.ts` — /audience 라우트 추가
- Modify: `frontend/src/components/layout/SideNav.vue` — CRM 네비게이션 추가

AudienceView 탭 구성: 팬 프로필 목록 (정렬/필터) | 세그먼트 관리 | 인사이트

**빌드 확인 + Commit**

```bash
cd frontend && npx vue-tsc --noEmit
git add frontend/
git commit -m "feat: 오디언스 CRM 프론트엔드"
```

---

## Feature D: 다국어 콘텐츠 자동화

### Task 13: DB 마이그레이션 — video_translations

**Files:**
- Create: `backend/sql/V18__translations.sql`

```sql
-- V18__translations.sql
-- 다국어 콘텐츠 자동화

CREATE TABLE video_translations (
    id               BIGSERIAL PRIMARY KEY,
    video_id         BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    language         VARCHAR(10) NOT NULL,  -- ko, en, ja, zh, es, fr, de, pt
    title            TEXT,
    description      TEXT,
    tags             JSONB DEFAULT '[]',
    subtitle_content TEXT,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, TRANSLATING, COMPLETED, FAILED
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_video_translations_video ON video_translations(video_id);
CREATE UNIQUE INDEX idx_video_translations_unique ON video_translations(video_id, language);
```

**Commit**

```bash
git add backend/sql/V18__translations.sql
git commit -m "chore: 다국어 번역 DB 마이그레이션"
```

---

### Task 14: 다국어 도메인 + 인프라 + API

**Files:**
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/translation/VideoTranslation.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/translation/TranslationRepository.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/Tables.kt`
- Create: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/TranslationJooqRepository.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/translation/TranslationUseCase.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/translation/dto/TranslationDtos.kt`
- Create: `backend/onGo-api/src/main/kotlin/com/ongo/api/translation/TranslationController.kt`

핵심 구현:
- AI 번역: Claude ChatClient로 메타데이터 + 자막 번역
- 플랫폼 특성 반영 (YouTube 태그 스타일 vs TikTok 해시태그 스타일)
- 기존 자막(SRT) 파싱 → 번역 → 새 SRT 생성
- 크레딧: 3 credits/언어
- API: POST /translations (번역 요청), GET /translations (목록), PUT (수정), DELETE

**빌드 확인 + Commit**

```bash
cd backend && ./gradlew compileKotlin
git add backend/
git commit -m "feat: 다국어 번역 백엔드 전체 구현"
```

---

### Task 15: 다국어 프론트엔드

**Files:**
- Create: `frontend/src/types/translation.ts`
- Create: `frontend/src/api/translation.ts`
- Create: `frontend/src/components/video/TranslationPanel.vue`

TranslationPanel: 언어 선택 체크박스 + 번역 요청 + 결과 편집 UI. VideoDetailView에 삽입.

**빌드 확인 + Commit**

```bash
cd frontend && npx vue-tsc --noEmit
git add frontend/
git commit -m "feat: 다국어 번역 프론트엔드"
```

---

## Feature E: 브랜드 딜/스폰서십

### Task 16: DB 마이그레이션 — brand_deals + media_kits

**Files:**
- Create: `backend/sql/V19__brand_deals.sql`

```sql
-- V19__brand_deals.sql
-- 브랜드 딜/스폰서십 관리

CREATE TABLE brand_deals (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    brand_name      VARCHAR(200) NOT NULL,
    brand_logo_url  TEXT,
    deal_type       VARCHAR(50) NOT NULL,   -- SPONSORSHIP, COLLABORATION, AFFILIATE, PRODUCT_REVIEW
    amount          BIGINT,                 -- 원 단위
    currency        VARCHAR(10) DEFAULT 'KRW',
    status          VARCHAR(20) NOT NULL DEFAULT 'PROPOSAL',  -- PROPOSAL, NEGOTIATION, IN_PROGRESS, COMPLETED, CANCELLED
    start_date      DATE,
    end_date        DATE,
    deliverables    JSONB DEFAULT '[]',
    notes           TEXT,
    contact_name    VARCHAR(100),
    contact_email   VARCHAR(200),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_brand_deals_user ON brand_deals(user_id);
CREATE INDEX idx_brand_deals_status ON brand_deals(status);

CREATE TABLE media_kits (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    pdf_url         TEXT,
    stats_snapshot  JSONB NOT NULL DEFAULT '{}',
    generated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_media_kits_user ON media_kits(user_id);
```

**Commit**

```bash
git add backend/sql/V19__brand_deals.sql
git commit -m "chore: 브랜드 딜 DB 마이그레이션"
```

---

### Task 17: 브랜드딜 도메인 + 인프라 + API

**Files:**
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/brand/BrandDeal.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/brand/MediaKit.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/brand/BrandDealRepository.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/Tables.kt`
- Create: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/BrandDealJooqRepository.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/brand/BrandDealUseCase.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/brand/dto/BrandDealDtos.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/brand/MediaKitUseCase.kt`
- Create: `backend/onGo-api/src/main/kotlin/com/ongo/api/brand/BrandDealController.kt`

핵심 구현:
- BrandDeal CRUD + 상태 관리 (PROPOSAL→NEGOTIATION→IN_PROGRESS→COMPLETED/CANCELLED)
- 대시보드 요약: 총 매출, 진행중 딜, 완료 딜, 월별 추이
- MediaKitUseCase: 채널 통계 + 상위 영상 + 인구통계 스냅샷 생성
- 미디어키트 PDF 생성은 프론트엔드 jsPDF로 처리

**빌드 확인 + Commit**

```bash
cd backend && ./gradlew compileKotlin
git add backend/
git commit -m "feat: 브랜드 딜/스폰서십 백엔드 전체 구현"
```

---

### Task 18: 브랜드딜 프론트엔드

**Files:**
- Create: `frontend/src/types/brand.ts`
- Create: `frontend/src/api/brand.ts`
- Create: `frontend/src/stores/brand.ts`
- Create: `frontend/src/views/BrandDealView.vue`
- Create: `frontend/src/components/brand/DealCard.vue`
- Create: `frontend/src/components/brand/DealForm.vue`
- Create: `frontend/src/components/brand/MediaKitPreview.vue`
- Modify: `frontend/src/router/index.ts` — /brand-deals 라우트 추가
- Modify: `frontend/src/components/layout/SideNav.vue` — 브랜드딜 네비게이션 추가

BrandDealView 탭 구성: 딜 목록 (칸반 보드) | 대시보드 (매출 차트) | 미디어키트

**빌드 확인 + Commit**

```bash
cd frontend && npx vue-tsc --noEmit
git add frontend/
git commit -m "feat: 브랜드 딜 프론트엔드"
```

---

## 마무리

### Task 19: 전체 빌드 검증 + SideNav/라우터 통합 확인

**Step 1: 백엔드 빌드**

```bash
cd backend && ./gradlew compileKotlin
```

**Step 2: 프론트엔드 타입 체크**

```bash
cd frontend && npx vue-tsc --noEmit
```

**Step 3: Vite 프로덕션 빌드**

```bash
cd frontend && npx vite build 2>&1 | tail -10
```

Expected: 모두 PASS
