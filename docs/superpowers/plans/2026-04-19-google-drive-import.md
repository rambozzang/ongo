# Google Drive 영상 임포트 구현 플랜

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사용자가 자신의 구글 드라이브 영상을 onGo에 연결·선택해 기존 SNS 배포 파이프라인으로 업로드하도록 한다.

**Architecture:** 별도 OAuth 연결(`user_content_sources`) → Drive API로 파일 목록 조회 → 선택 즉시 드라이브→S3 비동기 복사(`drive_import_jobs`) → 완료되면 기존 `PublishVideoUseCase` 재사용. 진행률은 SSE + polling 폴백.

**Tech Stack:** Spring Boot 4.0 + Kotlin + jOOQ + Flyway V42 / JUnit 5 + mockk / Vue 3 + Pinia / Spring WebFlux WebClient (스트리밍 다운로드)

**Related spec:** `docs/superpowers/specs/2026-04-19-google-drive-import-design.md`

---

## ⚠ 프로젝트 jOOQ 컨벤션 (중요)

이 프로젝트는 **jOOQ 코드 생성 플러그인을 사용하지 않는다**. 대신:

- `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/Tables.kt` 에 `object Tables { val USERS = DSL.table("users") ... }` 와 `object Fields { val ID = DSL.field("id", Long::class.java) ... }` 형태로 **수동 선언**.
- Repository 구현 이름: `*JooqRepository` (예: `ChannelJooqRepository`) — 위치: `persistence/jooq/` 디렉터리 (도메인별 하위 폴더 없음).
- Record 클래스 없음. `org.jooq.Record.get(Field<T>)` 를 extension 함수와 함께 사용. 날짜는 `record.localDateTime(FIELD)` 헬퍼 사용.
- PostgreSQL enum: DB 컬럼은 ENUM 타입이지만 jOOQ에서는 `String` 으로 저장/조회. 비교 시 `::text` cast 필드(예: `STATUS_TEXT = DSL.field("status::text", String::class.java)`) 사용. insert/update는 `.set(STATUS, source.status.name)` 같이 String 전달.
- 예시 참고 파일: `ChannelJooqRepository.kt` (라인 1~120).

이 플랜의 Task 2, 6, 7은 이 컨벤션에 맞춰져 있다. 생성 코드 기반 표현(`UserContentSourcesRecord`, `ContentSourceStatusEnum` 등)은 등장하지 않는다.

---

## Phase 구조

- **Phase 1** — DB 마이그레이션 + 도메인 모델 + jOOQ 재생성 (기반)
- **Phase 2** — OAuth 연결 + ContentSource CRUD
- **Phase 3** — GoogleDriveClient + 파일 목록/검색 API
- **Phase 4** — Import 파이프라인 (드라이브 → S3 → Video DRAFT)
- **Phase 5** — 진행률 SSE/polling + Recovery + 프론트 + 매뉴얼

각 Phase는 독립 배포 가능한 단위다. Phase 경계에서 `./gradlew build` + 수동 스모크 통과해야 다음으로.

---

## Phase 1 — DB + Domain (기반)

### Task 1: Flyway V42 마이그레이션 작성

**Files:**
- Create: `backend/onGo-api/src/main/resources/db/migration/V42__content_sources_and_drive_import.sql`

- [ ] **Step 1: 마이그레이션 SQL 작성**

```sql
-- V42__content_sources_and_drive_import.sql

CREATE TYPE content_source_type AS ENUM ('GOOGLE_DRIVE');
CREATE TYPE content_source_status AS ENUM ('ACTIVE', 'EXPIRED', 'REVOKED');
CREATE TYPE video_source AS ENUM ('UPLOAD_PC', 'GOOGLE_DRIVE');
CREATE TYPE drive_import_status AS ENUM ('PENDING', 'DOWNLOADING', 'COMPLETED', 'FAILED', 'CANCELLED');

CREATE TABLE IF NOT EXISTS user_content_sources (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_type             content_source_type NOT NULL,
    external_account_id     VARCHAR(255) NOT NULL,
    account_email           VARCHAR(255) NOT NULL,
    account_display_name    VARCHAR(255),
    access_token            TEXT NOT NULL,
    refresh_token           TEXT,
    token_expires_at        TIMESTAMP,
    granted_scopes          TEXT,
    status                  content_source_status NOT NULL DEFAULT 'ACTIVE',
    last_error              TEXT,
    connected_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    last_used_at            TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_content_sources UNIQUE (user_id, source_type)
);
CREATE INDEX idx_user_content_sources_user ON user_content_sources(user_id);

COMMENT ON TABLE user_content_sources IS '영상 입력 소스 연결 (드라이브 등)';
COMMENT ON COLUMN user_content_sources.access_token IS 'AES-256 암호화된 OAuth 액세스 토큰';
COMMENT ON COLUMN user_content_sources.refresh_token IS 'AES-256 암호화된 OAuth 리프레시 토큰';
COMMENT ON COLUMN user_content_sources.external_account_id IS 'OAuth sub claim — 이메일 변경돼도 안정';

ALTER TABLE videos
    ADD COLUMN source              video_source NOT NULL DEFAULT 'UPLOAD_PC',
    ADD COLUMN source_reference    JSONB;

COMMENT ON COLUMN videos.source IS '영상 원본 출처 (PC 업로드 / 구글 드라이브)';
COMMENT ON COLUMN videos.source_reference IS '소스별 원본 참조 JSON';

CREATE INDEX idx_videos_user_source ON videos(user_id, source);

CREATE TABLE IF NOT EXISTS drive_import_jobs (
    id                      BIGSERIAL PRIMARY KEY,
    video_id                BIGINT NOT NULL UNIQUE REFERENCES videos(id) ON DELETE CASCADE,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content_source_id       BIGINT NOT NULL REFERENCES user_content_sources(id) ON DELETE RESTRICT,
    drive_file_id           VARCHAR(255) NOT NULL,
    drive_file_name         VARCHAR(500) NOT NULL,
    file_size_bytes         BIGINT NOT NULL,
    bytes_transferred       BIGINT NOT NULL DEFAULT 0,
    status                  drive_import_status NOT NULL DEFAULT 'PENDING',
    s3_key                  TEXT,
    error_message           TEXT,
    retry_count             INTEGER NOT NULL DEFAULT 0,
    started_at              TIMESTAMP,
    completed_at            TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_drive_import_bytes CHECK (bytes_transferred >= 0 AND bytes_transferred <= file_size_bytes)
);
CREATE INDEX idx_drive_import_jobs_user_status ON drive_import_jobs(user_id, status);
CREATE INDEX idx_drive_import_jobs_status_updated ON drive_import_jobs(status, updated_at)
    WHERE status IN ('PENDING', 'DOWNLOADING');

COMMENT ON TABLE drive_import_jobs IS '구글 드라이브 → S3 복사 작업 추적';
```

- [ ] **Step 2: 마이그레이션 적용 검증**

Run: `./gradlew flywayMigrate` (또는 `./gradlew bootRun`으로 실행 시 자동 적용)
Expected: 에러 없이 V42 적용, `flyway_schema_history`에 row 추가

수동 검증: psql로 `\dt user_content_sources`, `\dt drive_import_jobs`, `\d videos` 로 컬럼/테이블 확인.

- [ ] **Step 3: 커밋**

```bash
git add backend/onGo-api/src/main/resources/db/migration/V42__content_sources_and_drive_import.sql
git commit -m "feat: V42 마이그레이션 — user_content_sources + drive_import_jobs + videos.source 컬럼 추가"
```

---

### Task 2: Tables.kt에 신규 테이블/필드 수동 등록

⚠ 프로젝트는 jOOQ codegen을 쓰지 않고 `Tables.kt` 에 모든 테이블/필드를 수동 선언한다 (플랜 상단 "프로젝트 jOOQ 컨벤션" 참고). 따라서 "재생성" 개념이 없고, 이번 V42에서 추가된 2개 테이블과 `videos`의 새 컬럼을 수동으로 등록한다.

**Files:**
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/Tables.kt`

- [ ] **Step 1: `object Tables` 블록에 두 개 테이블 추가**

파일 상단의 `object Tables { ... }` 블록 안 (기존 테이블 목록 끝에) 추가:

```kotlin
// Content sources (Phase 1: Google Drive import)
val USER_CONTENT_SOURCES = DSL.table("user_content_sources")
val DRIVE_IMPORT_JOBS = DSL.table("drive_import_jobs")
```

- [ ] **Step 2: `object Fields` 블록에 신규 필드 추가**

파일 하단의 `object Fields { ... }` 블록 안 (적절한 위치 — 보통 파일 맨 끝 `}` 직전) 에 아래 블록 추가:

```kotlin
// user_content_sources
val SOURCE_TYPE = DSL.field("source_type", String::class.java)
val SOURCE_TYPE_TEXT = DSL.field("source_type::text", String::class.java)
val EXTERNAL_ACCOUNT_ID = DSL.field("external_account_id", String::class.java)
val ACCOUNT_EMAIL = DSL.field("account_email", String::class.java)
val ACCOUNT_DISPLAY_NAME = DSL.field("account_display_name", String::class.java)
val GRANTED_SCOPES = DSL.field("granted_scopes", String::class.java)
val LAST_ERROR = DSL.field("last_error", String::class.java)
val LAST_USED_AT = DSL.field("last_used_at", java.time.LocalDateTime::class.java)

// drive_import_jobs
val VIDEO_ID = DSL.field("video_id", Long::class.java)
val CONTENT_SOURCE_ID = DSL.field("content_source_id", Long::class.java)
val DRIVE_FILE_ID = DSL.field("drive_file_id", String::class.java)
val DRIVE_FILE_NAME = DSL.field("drive_file_name", String::class.java)
val BYTES_TRANSFERRED = DSL.field("bytes_transferred", Long::class.java)
val S3_KEY = DSL.field("s3_key", String::class.java)
val ERROR_MESSAGE = DSL.field("error_message", String::class.java)
val RETRY_COUNT = DSL.field("retry_count", Int::class.java)
val STARTED_AT = DSL.field("started_at", java.time.LocalDateTime::class.java)
val COMPLETED_AT = DSL.field("completed_at", java.time.LocalDateTime::class.java)

// videos extended (Google Drive import)
val SOURCE = DSL.field("source", String::class.java)
val SOURCE_TEXT = DSL.field("source::text", String::class.java)
val SOURCE_REFERENCE = DSL.field("source_reference", Any::class.java)
```

**주의:**
- `STATUS` (content_source_status, drive_import_status) 와 `FILE_SIZE_BYTES`, `USER_ID`, `ID`, `CREATED_AT`, `UPDATED_AT` 등은 기존에 이미 선언되어 있으므로 **재선언 금지**. 파일을 Read로 열어 중복 여부 확인 후 신규 필드만 추가.
- `SOURCE_TYPE_TEXT`, `SOURCE_TEXT` 같은 `::text` cast 필드는 enum 비교(`.where(SOURCE_TYPE_TEXT.eq("GOOGLE_DRIVE"))`)용.
- `SOURCE_REFERENCE` 는 JSONB 컬럼 — `Any::class.java` 로 선언 (기존 `THUMBNAIL_URLS` 와 동일 패턴).

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :onGo-infrastructure:compileKotlin`
Expected: BUILD SUCCESSFUL. 컴파일 실패 시 중복 필드 선언 확인.

- [ ] **Step 4: 커밋**

```bash
git add backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/Tables.kt
git commit -m "feat: Tables.kt에 user_content_sources / drive_import_jobs 테이블 및 신규 필드 등록"
```

---

### Task 3: ContentSourceType / Status enum 정의

**Files:**
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/contentsource/ContentSourceType.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/contentsource/ContentSourceStatus.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/contentsource/DriveImportStatus.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/contentsource/VideoSource.kt`

- [ ] **Step 1: enum 파일 작성**

`ContentSourceType.kt`:
```kotlin
package com.ongo.domain.contentsource

enum class ContentSourceType {
    GOOGLE_DRIVE
}
```

`ContentSourceStatus.kt`:
```kotlin
package com.ongo.domain.contentsource

enum class ContentSourceStatus {
    ACTIVE, EXPIRED, REVOKED
}
```

`DriveImportStatus.kt`:
```kotlin
package com.ongo.domain.contentsource

enum class DriveImportStatus {
    PENDING, DOWNLOADING, COMPLETED, FAILED, CANCELLED;

    fun isTerminal(): Boolean = this == COMPLETED || this == FAILED || this == CANCELLED
}
```

`VideoSource.kt`:
```kotlin
package com.ongo.domain.contentsource

enum class VideoSource {
    UPLOAD_PC, GOOGLE_DRIVE
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :onGo-domain:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/onGo-domain/src/main/kotlin/com/ongo/domain/contentsource/
git commit -m "feat: ContentSource 도메인 enum 추가 (Type/Status/DriveImportStatus/VideoSource)"
```

---

### Task 4: ContentSource 도메인 엔티티 + DriveImportJob 엔티티

**Files:**
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/contentsource/ContentSource.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/contentsource/DriveImportJob.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/contentsource/DriveFile.kt`
- Test: `backend/onGo-domain/src/test/kotlin/com/ongo/domain/contentsource/ContentSourceTest.kt`
- Test: `backend/onGo-domain/src/test/kotlin/com/ongo/domain/contentsource/DriveImportJobTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

`ContentSourceTest.kt`:
```kotlin
package com.ongo.domain.contentsource

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentSourceTest {
    @Test
    fun `needsRefresh returns true when token expires within 60 seconds`() {
        val source = sampleSource(tokenExpiresAt = Instant.now().plusSeconds(30))
        assertTrue(source.needsRefresh(Instant.now()))
    }

    @Test
    fun `needsRefresh returns false when token expires more than 60 seconds later`() {
        val source = sampleSource(tokenExpiresAt = Instant.now().plusSeconds(600))
        assertFalse(source.needsRefresh(Instant.now()))
    }

    @Test
    fun `markExpired sets status to EXPIRED`() {
        val source = sampleSource(status = ContentSourceStatus.ACTIVE)
        val expired = source.markExpired("invalid_grant")
        assertEquals(ContentSourceStatus.EXPIRED, expired.status)
        assertEquals("invalid_grant", expired.lastError)
    }

    private fun sampleSource(
        status: ContentSourceStatus = ContentSourceStatus.ACTIVE,
        tokenExpiresAt: Instant? = Instant.now().plusSeconds(3600),
    ) = ContentSource(
        id = 1L,
        userId = 100L,
        sourceType = ContentSourceType.GOOGLE_DRIVE,
        externalAccountId = "google-sub-123",
        accountEmail = "user@gmail.com",
        accountDisplayName = "User",
        accessTokenEncrypted = "enc",
        refreshTokenEncrypted = "enc",
        tokenExpiresAt = tokenExpiresAt,
        grantedScopes = "drive.readonly",
        status = status,
        lastError = null,
        connectedAt = Instant.now(),
        lastUsedAt = null,
        updatedAt = Instant.now(),
    )
}
```

- [ ] **Step 2: 테스트 실행해 실패 확인**

Run: `./gradlew :onGo-domain:test --tests "com.ongo.domain.contentsource.ContentSourceTest"`
Expected: 컴파일 에러 (ContentSource 미정의)

- [ ] **Step 3: 엔티티 구현**

`ContentSource.kt`:
```kotlin
package com.ongo.domain.contentsource

import java.time.Instant

data class ContentSource(
    val id: Long,
    val userId: Long,
    val sourceType: ContentSourceType,
    val externalAccountId: String,
    val accountEmail: String,
    val accountDisplayName: String?,
    val accessTokenEncrypted: String,
    val refreshTokenEncrypted: String?,
    val tokenExpiresAt: Instant?,
    val grantedScopes: String?,
    val status: ContentSourceStatus,
    val lastError: String?,
    val connectedAt: Instant,
    val lastUsedAt: Instant?,
    val updatedAt: Instant,
) {
    fun needsRefresh(now: Instant): Boolean {
        val expiresAt = tokenExpiresAt ?: return true
        return expiresAt.isBefore(now.plusSeconds(REFRESH_MARGIN_SECONDS))
    }

    fun markExpired(reason: String): ContentSource =
        copy(status = ContentSourceStatus.EXPIRED, lastError = reason, updatedAt = Instant.now())

    fun markRevoked(reason: String): ContentSource =
        copy(status = ContentSourceStatus.REVOKED, lastError = reason, updatedAt = Instant.now())

    fun markUsed(): ContentSource =
        copy(lastUsedAt = Instant.now(), updatedAt = Instant.now())

    companion object {
        const val REFRESH_MARGIN_SECONDS = 60L
    }
}
```

`DriveFile.kt`:
```kotlin
package com.ongo.domain.contentsource

import java.time.Instant

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val durationSeconds: Long?,
    val thumbnailUrl: String?,
    val modifiedAt: Instant,
    val kind: Kind,
) {
    enum class Kind { FILE, FOLDER }

    fun isFolder(): Boolean = kind == Kind.FOLDER
    fun isVideo(): Boolean = kind == Kind.FILE && mimeType.startsWith("video/")
}
```

`DriveImportJob.kt`:
```kotlin
package com.ongo.domain.contentsource

import java.time.Instant

data class DriveImportJob(
    val id: Long,
    val videoId: Long,
    val userId: Long,
    val contentSourceId: Long,
    val driveFileId: String,
    val driveFileName: String,
    val fileSizeBytes: Long,
    val bytesTransferred: Long,
    val status: DriveImportStatus,
    val s3Key: String?,
    val errorMessage: String?,
    val retryCount: Int,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    fun percent(): Int =
        if (fileSizeBytes == 0L) 0 else ((bytesTransferred * 100) / fileSizeBytes).toInt().coerceIn(0, 100)
}
```

`DriveImportJobTest.kt`:
```kotlin
package com.ongo.domain.contentsource

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class DriveImportJobTest {
    @Test
    fun `percent returns 0 when fileSizeBytes is zero`() {
        val job = sampleJob(fileSizeBytes = 0, bytesTransferred = 0)
        assertEquals(0, job.percent())
    }

    @Test
    fun `percent returns 50 when half transferred`() {
        val job = sampleJob(fileSizeBytes = 1000, bytesTransferred = 500)
        assertEquals(50, job.percent())
    }

    @Test
    fun `percent caps at 100`() {
        val job = sampleJob(fileSizeBytes = 100, bytesTransferred = 100)
        assertEquals(100, job.percent())
    }

    private fun sampleJob(fileSizeBytes: Long, bytesTransferred: Long) = DriveImportJob(
        id = 1, videoId = 10, userId = 100, contentSourceId = 1,
        driveFileId = "f1", driveFileName = "a.mp4",
        fileSizeBytes = fileSizeBytes, bytesTransferred = bytesTransferred,
        status = DriveImportStatus.DOWNLOADING, s3Key = null, errorMessage = null,
        retryCount = 0, startedAt = Instant.now(), completedAt = null,
        createdAt = Instant.now(), updatedAt = Instant.now(),
    )
}
```

- [ ] **Step 4: 테스트 실행해 통과 확인**

Run: `./gradlew :onGo-domain:test --tests "com.ongo.domain.contentsource.*"`
Expected: 테스트 6개 모두 PASS

- [ ] **Step 5: 커밋**

```bash
git add backend/onGo-domain/src/main/kotlin/com/ongo/domain/contentsource/ContentSource.kt \
        backend/onGo-domain/src/main/kotlin/com/ongo/domain/contentsource/DriveImportJob.kt \
        backend/onGo-domain/src/main/kotlin/com/ongo/domain/contentsource/DriveFile.kt \
        backend/onGo-domain/src/test/kotlin/com/ongo/domain/contentsource/
git commit -m "feat: ContentSource / DriveImportJob / DriveFile 도메인 엔티티 + 단위 테스트"
```

---

### Task 5: Repository 인터페이스 + 예외 클래스

**Files:**
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/contentsource/ContentSourceRepository.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/contentsource/DriveImportJobRepository.kt`
- Create: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/contentsource/exception/ContentSourceException.kt`

- [ ] **Step 1: Repository 인터페이스**

`ContentSourceRepository.kt`:
```kotlin
package com.ongo.domain.contentsource

interface ContentSourceRepository {
    fun findById(id: Long): ContentSource?
    fun findByUserAndType(userId: Long, type: ContentSourceType): ContentSource?
    fun findAllByUser(userId: Long): List<ContentSource>
    fun save(source: ContentSource): ContentSource
    fun updateStatus(id: Long, status: ContentSourceStatus, lastError: String?)
    fun updateTokens(
        id: Long,
        accessTokenEncrypted: String,
        refreshTokenEncrypted: String?,
        expiresAt: java.time.Instant?,
    )
    fun markUsed(id: Long)
    fun delete(id: Long)
}
```

`DriveImportJobRepository.kt`:
```kotlin
package com.ongo.domain.contentsource

interface DriveImportJobRepository {
    fun findById(id: Long): DriveImportJob?
    fun findByVideoId(videoId: Long): DriveImportJob?
    fun findActiveByUserAndFileId(userId: Long, driveFileId: String): List<DriveImportJob>
    fun countActiveByUser(userId: Long): Int
    fun listActiveByUser(userId: Long): List<DriveImportJob>
    fun listStale(olderThanSeconds: Long): List<DriveImportJob>
    fun save(job: DriveImportJob): DriveImportJob
    fun updateStatus(id: Long, status: DriveImportStatus, errorMessage: String?)
    fun updateProgress(id: Long, bytesTransferred: Long)
    fun markCompleted(id: Long, s3Key: String)
}
```

- [ ] **Step 2: 예외 클래스**

`exception/ContentSourceException.kt`:
```kotlin
package com.ongo.domain.contentsource.exception

sealed class ContentSourceException(message: String) : RuntimeException(message)

class ContentSourceNotConnectedException : ContentSourceException("구글 드라이브가 연결되지 않았습니다")
class ContentSourceExpiredException(reason: String) : ContentSourceException("인증이 만료되었습니다: $reason")
class ContentSourceRevokedException(reason: String) : ContentSourceException("권한이 회수되었습니다: $reason")
class DriveFileNotFoundException(fileId: String) : ContentSourceException("드라이브 파일을 찾을 수 없습니다: $fileId")
class DriveFilePermissionDeniedException(fileId: String) : ContentSourceException("파일 접근 권한이 없습니다: $fileId")
class DuplicateDriveImportException(fileId: String) : ContentSourceException("이미 가져온 적이 있는 파일입니다: $fileId")
class ConcurrentImportLimitException(limit: Int) : ContentSourceException("동시에 가져올 수 있는 파일은 최대 ${limit}개입니다")
class OAuthStateMismatchException : ContentSourceException("OAuth state 검증 실패")
class DriveDownloadFailedException(cause: Throwable) : ContentSourceException("드라이브 다운로드 실패: ${cause.message}")
```

- [ ] **Step 3: 컴파일 확인 및 커밋**

Run: `./gradlew :onGo-domain:compileKotlin :onGo-domain:test`
Expected: BUILD SUCCESSFUL

```bash
git add backend/onGo-domain/src/main/kotlin/com/ongo/domain/contentsource/
git commit -m "feat: ContentSource/DriveImportJob Repository 인터페이스 + 예외 계층"
```

---

### Task 6: jOOQ Repository 구현 — ContentSource

⚠ 프로젝트 컨벤션: Repository는 `persistence/jooq/` 디렉터리에 `*JooqRepository` 이름으로 배치하며 단일 파일에 toDomain 확장 함수까지 포함한다 (별도 Mapper 파일 없음). 참고 파일: `ChannelJooqRepository.kt`.

**Files:**
- Create: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/ContentSourceJooqRepository.kt`
- Test: `backend/onGo-infrastructure/src/test/kotlin/com/ongo/infrastructure/persistence/jooq/ContentSourceJooqRepositoryIT.kt` (@SpringBootTest + Testcontainers)

- [ ] **Step 1: Repository + toDomain 확장 함수 (단일 파일)**

`ContentSourceJooqRepository.kt` 전체:
```kotlin
package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentsource.ContentSource
import com.ongo.domain.contentsource.ContentSourceRepository
import com.ongo.domain.contentsource.ContentSourceStatus
import com.ongo.domain.contentsource.ContentSourceType
import com.ongo.infrastructure.persistence.jooq.Fields.*
import com.ongo.infrastructure.persistence.jooq.Tables.USER_CONTENT_SOURCES
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class ContentSourceJooqRepository(
    private val dsl: DSLContext,
) : ContentSourceRepository {

    override fun findById(id: Long): ContentSource? =
        dsl.select().from(USER_CONTENT_SOURCES).where(ID.eq(id))
            .fetchOne()?.toContentSource()

    override fun findByUserAndType(userId: Long, type: ContentSourceType): ContentSource? =
        dsl.select().from(USER_CONTENT_SOURCES)
            .where(USER_ID.eq(userId))
            .and(SOURCE_TYPE_TEXT.eq(type.name))
            .fetchOne()?.toContentSource()

    override fun findAllByUser(userId: Long): List<ContentSource> =
        dsl.select().from(USER_CONTENT_SOURCES)
            .where(USER_ID.eq(userId))
            .orderBy(CONNECTED_AT.desc())
            .fetch().map { it.toContentSource() }

    override fun save(source: ContentSource): ContentSource {
        val now = LocalDateTime.now()
        val id = if (source.id == 0L) {
            dsl.insertInto(USER_CONTENT_SOURCES)
                .set(USER_ID, source.userId)
                .set(SOURCE_TYPE, source.sourceType.name)
                .set(EXTERNAL_ACCOUNT_ID, source.externalAccountId)
                .set(ACCOUNT_EMAIL, source.accountEmail)
                .set(ACCOUNT_DISPLAY_NAME, source.accountDisplayName)
                .set(ACCESS_TOKEN, source.accessTokenEncrypted)
                .set(REFRESH_TOKEN, source.refreshTokenEncrypted)
                .set(TOKEN_EXPIRES_AT, source.tokenExpiresAt?.atZone(ZoneOffset.UTC)?.toLocalDateTime())
                .set(GRANTED_SCOPES, source.grantedScopes)
                .set(STATUS, source.status.name)
                .set(UPDATED_AT, now)
                .returningResult(ID).fetchOne()!!.get(ID)
        } else {
            dsl.update(USER_CONTENT_SOURCES)
                .set(ACCOUNT_EMAIL, source.accountEmail)
                .set(ACCOUNT_DISPLAY_NAME, source.accountDisplayName)
                .set(EXTERNAL_ACCOUNT_ID, source.externalAccountId)
                .set(ACCESS_TOKEN, source.accessTokenEncrypted)
                .set(REFRESH_TOKEN, source.refreshTokenEncrypted)
                .set(TOKEN_EXPIRES_AT, source.tokenExpiresAt?.atZone(ZoneOffset.UTC)?.toLocalDateTime())
                .set(GRANTED_SCOPES, source.grantedScopes)
                .set(STATUS, source.status.name)
                .set(LAST_ERROR, source.lastError)
                .set(UPDATED_AT, now)
                .where(ID.eq(source.id)).execute()
            source.id
        }
        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: ContentSourceStatus, lastError: String?) {
        dsl.update(USER_CONTENT_SOURCES)
            .set(STATUS, status.name)
            .set(LAST_ERROR, lastError)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id)).execute()
    }

    override fun updateTokens(id: Long, accessTokenEncrypted: String, refreshTokenEncrypted: String?, expiresAt: Instant?) {
        dsl.update(USER_CONTENT_SOURCES)
            .set(ACCESS_TOKEN, accessTokenEncrypted)
            .set(REFRESH_TOKEN, refreshTokenEncrypted)
            .set(TOKEN_EXPIRES_AT, expiresAt?.atZone(ZoneOffset.UTC)?.toLocalDateTime())
            .set(STATUS, ContentSourceStatus.ACTIVE.name)
            .set(LAST_ERROR, null as String?)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id)).execute()
    }

    override fun markUsed(id: Long) {
        val now = LocalDateTime.now()
        dsl.update(USER_CONTENT_SOURCES)
            .set(LAST_USED_AT, now)
            .set(UPDATED_AT, now)
            .where(ID.eq(id)).execute()
    }

    override fun delete(id: Long) {
        dsl.deleteFrom(USER_CONTENT_SOURCES).where(ID.eq(id)).execute()
    }

    private fun Record.toContentSource(): ContentSource {
        val sourceTypeStr = get(SOURCE_TYPE) ?: "GOOGLE_DRIVE"
        val statusStr = get(STATUS) ?: "ACTIVE"
        return ContentSource(
            id = get(ID),
            userId = get(USER_ID),
            sourceType = try { ContentSourceType.valueOf(sourceTypeStr) } catch (_: Exception) { ContentSourceType.GOOGLE_DRIVE },
            externalAccountId = get(EXTERNAL_ACCOUNT_ID),
            accountEmail = get(ACCOUNT_EMAIL),
            accountDisplayName = get(ACCOUNT_DISPLAY_NAME),
            accessTokenEncrypted = get(ACCESS_TOKEN),
            refreshTokenEncrypted = get(REFRESH_TOKEN),
            tokenExpiresAt = localDateTime(TOKEN_EXPIRES_AT)?.atZone(ZoneOffset.UTC)?.toInstant(),
            grantedScopes = get(GRANTED_SCOPES),
            status = try { ContentSourceStatus.valueOf(statusStr) } catch (_: Exception) { ContentSourceStatus.ACTIVE },
            lastError = get(LAST_ERROR),
            connectedAt = localDateTime(CONNECTED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
            lastUsedAt = localDateTime(LAST_USED_AT)?.atZone(ZoneOffset.UTC)?.toInstant(),
            updatedAt = localDateTime(UPDATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
        )
    }
}
```

**주의:**
- `ChannelJooqRepository.kt` 패턴과 동일: enum은 `.set(STATUS, source.status.name)` 으로 String 저장, 비교는 `SOURCE_TYPE_TEXT.eq(type.name)` 으로 `::text` cast 사용.
- `Record.toContentSource()` 확장은 private 으로 파일 하단. 기존 `ChannelJooqRepository.toChannel()` 과 동일 패턴.
- `localDateTime(FIELD)` 은 `Tables.kt` 상단에 정의된 Record 확장 함수 (Import 불필요, 같은 패키지).

- [ ] **Step 2: 통합 테스트 (Testcontainers)**

`ContentSourceJooqRepositoryIT.kt`:
```kotlin
package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentsource.*
import com.ongo.infrastructure.persistence.jooq.Tables.USER_CONTENT_SOURCES
import org.jooq.DSLContext
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ContentSourceJooqRepositoryIT {
    @Autowired lateinit var repo: ContentSourceRepository
    @Autowired lateinit var dsl: DSLContext

    companion object {
        @Container @JvmStatic
        val pg = PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("ongo_test")
        }
        @JvmStatic @DynamicPropertySource
        fun props(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url") { pg.jdbcUrl }
            r.add("spring.datasource.username") { pg.username }
            r.add("spring.datasource.password") { pg.password }
        }
    }

    @BeforeEach fun cleanup() { dsl.deleteFrom(USER_CONTENT_SOURCES).execute() }

    @Test fun `save and findByUserAndType roundtrip`() {
        val saved = repo.save(newActiveSource(userId = 100L))
        val found = repo.findByUserAndType(100L, ContentSourceType.GOOGLE_DRIVE)
        assertEquals(saved.id, found?.id)
        assertEquals("user@gmail.com", found?.accountEmail)
    }

    @Test fun `updateStatus marks expired`() {
        val saved = repo.save(newActiveSource(userId = 100L))
        repo.updateStatus(saved.id, ContentSourceStatus.EXPIRED, "invalid_grant")
        val found = repo.findById(saved.id)!!
        assertEquals(ContentSourceStatus.EXPIRED, found.status)
        assertEquals("invalid_grant", found.lastError)
    }

    @Test fun `unique constraint on user and source type`() {
        repo.save(newActiveSource(userId = 100L))
        assertThrows<DuplicateKeyException> {
            repo.save(newActiveSource(userId = 100L))
        }
    }

    private fun newActiveSource(userId: Long) = ContentSource(
        id = 0L, userId = userId, sourceType = ContentSourceType.GOOGLE_DRIVE,
        externalAccountId = "google-sub-${userId}", accountEmail = "user@gmail.com",
        accountDisplayName = "User", accessTokenEncrypted = "enc:at",
        refreshTokenEncrypted = "enc:rt", tokenExpiresAt = Instant.now().plusSeconds(3600),
        grantedScopes = "drive.readonly", status = ContentSourceStatus.ACTIVE,
        lastError = null, connectedAt = Instant.now(), lastUsedAt = null, updatedAt = Instant.now(),
    )
}
```

- [ ] **Step 3: 테스트 실행**

Run: `./gradlew :onGo-infrastructure:test --tests "*.ContentSourceJooqRepositoryIT"`
Expected: 모든 테스트 PASS

- [ ] **Step 4: 커밋**

```bash
git add backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/ContentSourceJooqRepository.kt \
        backend/onGo-infrastructure/src/test/kotlin/com/ongo/infrastructure/persistence/jooq/ContentSourceJooqRepositoryIT.kt
git commit -m "feat: ContentSourceJooqRepository + 통합 테스트"
```

---

### Task 7: jOOQ Repository 구현 — DriveImportJob

Task 6과 동일 구조. `persistence/jooq/` 디렉터리, 단일 파일, `::text` cast + String 저장 패턴.

**Files:**
- Create: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/DriveImportJobJooqRepository.kt`
- Test: `backend/onGo-infrastructure/src/test/kotlin/com/ongo/infrastructure/persistence/jooq/DriveImportJobJooqRepositoryIT.kt`

- [ ] **Step 1: Repository + toDomain 확장 (단일 파일)**

`DriveImportJobJooqRepository.kt` 핵심 구조:
```kotlin
package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentsource.DriveImportJob
import com.ongo.domain.contentsource.DriveImportJobRepository
import com.ongo.domain.contentsource.DriveImportStatus
import com.ongo.infrastructure.persistence.jooq.Fields.*
import com.ongo.infrastructure.persistence.jooq.Tables.DRIVE_IMPORT_JOBS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZoneOffset

@Repository
class DriveImportJobJooqRepository(private val dsl: DSLContext) : DriveImportJobRepository {

    private val STATUS_TEXT_DRIVE = DSL.field("status::text", String::class.java)

    override fun findById(id: Long): DriveImportJob? =
        dsl.select().from(DRIVE_IMPORT_JOBS).where(ID.eq(id))
            .fetchOne()?.toDriveImportJob()

    override fun findByVideoId(videoId: Long): DriveImportJob? =
        dsl.select().from(DRIVE_IMPORT_JOBS).where(VIDEO_ID.eq(videoId))
            .fetchOne()?.toDriveImportJob()

    override fun findActiveByUserAndFileId(userId: Long, driveFileId: String): List<DriveImportJob> =
        dsl.select().from(DRIVE_IMPORT_JOBS)
            .where(USER_ID.eq(userId))
            .and(DRIVE_FILE_ID.eq(driveFileId))
            .and(STATUS_TEXT_DRIVE.`in`("PENDING", "DOWNLOADING", "COMPLETED"))
            .forUpdate()
            .fetch().map { it.toDriveImportJob() }

    override fun countActiveByUser(userId: Long): Int =
        dsl.selectCount().from(DRIVE_IMPORT_JOBS)
            .where(USER_ID.eq(userId))
            .and(STATUS_TEXT_DRIVE.`in`("PENDING", "DOWNLOADING"))
            .fetchOne(0, Int::class.java) ?: 0

    override fun listActiveByUser(userId: Long): List<DriveImportJob> =
        dsl.select().from(DRIVE_IMPORT_JOBS)
            .where(USER_ID.eq(userId))
            .and(STATUS_TEXT_DRIVE.`in`("PENDING", "DOWNLOADING"))
            .orderBy(CREATED_AT.desc())
            .fetch().map { it.toDriveImportJob() }

    override fun listStale(olderThanSeconds: Long): List<DriveImportJob> =
        dsl.select().from(DRIVE_IMPORT_JOBS)
            .where(STATUS_TEXT_DRIVE.`in`("PENDING", "DOWNLOADING"))
            .and(UPDATED_AT.lt(LocalDateTime.now().minusSeconds(olderThanSeconds)))
            .fetch().map { it.toDriveImportJob() }

    override fun save(job: DriveImportJob): DriveImportJob {
        val now = LocalDateTime.now()
        val id = if (job.id == 0L) {
            dsl.insertInto(DRIVE_IMPORT_JOBS)
                .set(VIDEO_ID, job.videoId)
                .set(USER_ID, job.userId)
                .set(CONTENT_SOURCE_ID, job.contentSourceId)
                .set(DRIVE_FILE_ID, job.driveFileId)
                .set(DRIVE_FILE_NAME, job.driveFileName)
                .set(FILE_SIZE_BYTES, job.fileSizeBytes)
                .set(BYTES_TRANSFERRED, job.bytesTransferred)
                .set(STATUS, job.status.name)
                .set(S3_KEY, job.s3Key)
                .set(ERROR_MESSAGE, job.errorMessage)
                .set(RETRY_COUNT, job.retryCount)
                .set(UPDATED_AT, now)
                .returningResult(ID).fetchOne()!!.get(ID)
        } else { job.id }
        return findById(id)!!
    }

    override fun updateStatus(id: Long, status: DriveImportStatus, errorMessage: String?) {
        val now = LocalDateTime.now()
        val q = dsl.update(DRIVE_IMPORT_JOBS)
            .set(STATUS, status.name)
            .set(ERROR_MESSAGE, errorMessage)
            .set(UPDATED_AT, now)
        // DOWNLOADING 첫 진입 시 started_at 설정
        if (status == DriveImportStatus.DOWNLOADING) q.set(STARTED_AT, now)
        if (status.isTerminal()) q.set(COMPLETED_AT, now)
        q.where(ID.eq(id)).execute()
    }

    override fun updateProgress(id: Long, bytesTransferred: Long) {
        dsl.update(DRIVE_IMPORT_JOBS)
            .set(BYTES_TRANSFERRED, bytesTransferred)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id)).execute()
    }

    override fun markCompleted(id: Long, s3Key: String) {
        val now = LocalDateTime.now()
        dsl.update(DRIVE_IMPORT_JOBS)
            .set(STATUS, DriveImportStatus.COMPLETED.name)
            .set(S3_KEY, s3Key)
            .set(COMPLETED_AT, now)
            .set(UPDATED_AT, now)
            .where(ID.eq(id)).execute()
    }

    private fun Record.toDriveImportJob(): DriveImportJob {
        val statusStr = get(STATUS) ?: "PENDING"
        return DriveImportJob(
            id = get(ID),
            videoId = get(VIDEO_ID),
            userId = get(USER_ID),
            contentSourceId = get(CONTENT_SOURCE_ID),
            driveFileId = get(DRIVE_FILE_ID),
            driveFileName = get(DRIVE_FILE_NAME),
            fileSizeBytes = get(FILE_SIZE_BYTES),
            bytesTransferred = get(BYTES_TRANSFERRED),
            status = try { DriveImportStatus.valueOf(statusStr) } catch (_: Exception) { DriveImportStatus.PENDING },
            s3Key = get(S3_KEY),
            errorMessage = get(ERROR_MESSAGE),
            retryCount = get(RETRY_COUNT),
            startedAt = localDateTime(STARTED_AT)?.atZone(ZoneOffset.UTC)?.toInstant(),
            completedAt = localDateTime(COMPLETED_AT)?.atZone(ZoneOffset.UTC)?.toInstant(),
            createdAt = localDateTime(CREATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
            updatedAt = localDateTime(UPDATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
        )
    }
}
```

**주의:**
- `STATUS_TEXT_DRIVE` 는 이 Repository 로컬로 선언. Fields.kt의 `STATUS_TEXT` 는 `channels.status::text` 용도로 이미 쓰이지만 구문상 동일(`"status::text"`) 이라 공용 가능. 명확성을 위해 로컬 변수명 사용.
- `forUpdate()` 로 동시 클릭 직렬화 (중복 검사 단계 보호).
- DB의 `drive_import_status` enum 과 Kotlin `DriveImportStatus` enum 이름·값 완전히 일치.

- [ ] **Step 2: 통합 테스트**

`DriveImportJobJooqRepositoryIT.kt` — Task 6의 Testcontainers 테스트와 동일한 `@SpringBootTest + @Testcontainers + @DynamicPropertySource` 셋업 사용. 테스트 케이스:
- `save/findById roundtrip` — 전체 필드 값 왕복 확인
- `findActiveByUserAndFileId returns PENDING/DOWNLOADING/COMPLETED jobs` — COMPLETED 도 포함되는지 확인
- `findActiveByUserAndFileId excludes FAILED/CANCELLED`
- `countActiveByUser returns only PENDING+DOWNLOADING`
- `listStale returns jobs older than cutoff` — 테스트에서 `updated_at` 을 과거로 돌려 검증
- `updateProgress increments bytes_transferred`
- `CHECK constraint rejects bytesTransferred > fileSizeBytes` — insert 시 `DataIntegrityViolationException` 기대

- [ ] **Step 3: 실행 + 커밋**

```bash
./gradlew :onGo-infrastructure:test --tests "*.DriveImportJobJooqRepositoryIT"
git add backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/DriveImportJobJooqRepository.kt \
        backend/onGo-infrastructure/src/test/kotlin/com/ongo/infrastructure/persistence/jooq/DriveImportJobJooqRepositoryIT.kt
git commit -m "feat: DriveImportJobJooqRepository + 동시성/CHECK 테스트"
```

---

### Task 8: videos.source 컬럼 — 기존 Video 도메인 확장

**Files:**
- Modify: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/video/Video.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/video/VideoRepositoryImpl.kt` (매퍼/insert/update 에 source 반영)
- Modify: `backend/onGo-domain/src/main/kotlin/com/ongo/common/enums/UploadStatus.kt` — `IMPORTING`, `IMPORT_FAILED` 상태 추가

- [ ] **Step 1: UploadStatus enum에 IMPORTING / IMPORT_FAILED 추가**

기존 enum 위치 확인 후 값 추가. DB 쪽 `video_status` enum도 ALTER 필요 — Task 1 V42에는 없으므로 **V42에 추가**:

```sql
-- V42 끝부분에 추가
ALTER TYPE video_status ADD VALUE IF NOT EXISTS 'IMPORTING';
ALTER TYPE video_status ADD VALUE IF NOT EXISTS 'IMPORT_FAILED';
```

⚠ **V42 마이그레이션이 이미 적용되어 있다면 V43으로 별도 파일 생성**. 아직 개발 환경이고 V42 미적용이면 V42에 덧붙여 재적용.

- [ ] **Step 2: Video 엔티티에 source / sourceReference 필드**

```kotlin
// Video.kt 추가 필드
val source: VideoSource = VideoSource.UPLOAD_PC,
val sourceReference: JsonNode? = null,    // com.fasterxml.jackson.databind.JsonNode
```

VideoRepositoryImpl에서 새 컬럼 읽기/쓰기 반영. 기본값 `UPLOAD_PC`이므로 기존 호출부는 변경 불필요.

- [ ] **Step 3: 기존 Video 테스트 재실행 (회귀 없는지)**

Run: `./gradlew :onGo-domain:test :onGo-infrastructure:test --tests "*Video*"`
Expected: 기존 테스트 전부 PASS

- [ ] **Step 4: 커밋**

```bash
git add backend/onGo-api/src/main/resources/db/migration/V42__content_sources_and_drive_import.sql \
        backend/onGo-domain/src/main/kotlin/com/ongo/domain/video/Video.kt \
        backend/onGo-domain/src/main/kotlin/com/ongo/common/enums/UploadStatus.kt \
        backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/video/VideoRepositoryImpl.kt
git commit -m "feat: Video 엔티티에 source/sourceReference 추가 + IMPORTING 상태 도입"
```

---

### Phase 1 완료 체크

- [ ] `./gradlew build` BUILD SUCCESSFUL
- [ ] `./gradlew bootRun` 정상 부팅 (V42 마이그레이션 적용됨)
- [ ] psql에서 `\dt user_content_sources`, `\d videos` 컬럼 확인
- [ ] `git log --oneline` 8개 Phase 1 커밋 확인

---

## Phase 2 — OAuth 연결 + ContentSource CRUD

### Task 9: GoogleDriveProperties + application.yml 설정

**Files:**
- Create: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/googledrive/GoogleDriveProperties.kt`
- Modify: `backend/onGo-api/src/main/resources/application.yml`

- [ ] **Step 1: 설정 프로퍼티 클래스**

```kotlin
@ConfigurationProperties(prefix = "ongo.content-source.google-drive")
data class GoogleDriveProperties(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val scopes: List<String> = listOf("https://www.googleapis.com/auth/drive.readonly"),
    val import: Import = Import(),
) {
    data class Import(
        val downloadBufferSize: Int = 8 * 1024 * 1024,
        val progressUpdateIntervalMs: Long = 2000,
        val maxConcurrentPerUser: Int = 2,
        val timeoutMinutes: Long = 60,
    )
}
```

- [ ] **Step 2: application.yml 추가**

```yaml
ongo:
  content-source:
    google-drive:
      client-id: ${GOOGLE_DRIVE_CLIENT_ID:}
      client-secret: ${GOOGLE_DRIVE_CLIENT_SECRET:}
      redirect-uri: ${APP_BASE_URL:http://localhost:8777}/api/v1/content-sources/google-drive/callback
      scopes:
        - https://www.googleapis.com/auth/drive.readonly
      import:
        download-buffer-size: 8388608
        progress-update-interval-ms: 2000
        max-concurrent-per-user: 2
        timeout-minutes: 60
```

`@EnableConfigurationProperties(GoogleDriveProperties::class)` 를 적절한 Configuration 클래스에 추가.

- [ ] **Step 3: 커밋**

```bash
git add backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/googledrive/GoogleDriveProperties.kt \
        backend/onGo-api/src/main/resources/application.yml
git commit -m "feat: GoogleDriveProperties 설정 추가"
```

---

### Task 10: GoogleDriveOAuthClient

**Files:**
- Create: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/googledrive/GoogleDriveOAuthClient.kt`
- Create: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/googledrive/dto/TokenResponse.kt`
- Test: `backend/onGo-infrastructure/src/test/kotlin/com/ongo/infrastructure/external/googledrive/GoogleDriveOAuthClientTest.kt`

- [ ] **Step 1: 실패 테스트**

```kotlin
class GoogleDriveOAuthClientTest {
    private val mockServer = MockWebServer().apply { start() }
    private val props = GoogleDriveProperties(
        clientId = "cid", clientSecret = "csec",
        redirectUri = "http://localhost/cb",
    )
    private val client = GoogleDriveOAuthClient(
        WebClient.create(), props, tokenEndpoint = mockServer.url("/token").toString(),
    )

    @Test fun `exchangeCode parses successful response`() {
        mockServer.enqueue(MockResponse()
            .setBody("""{"access_token":"at","refresh_token":"rt","expires_in":3600,"id_token":"idtok","scope":"drive.readonly","token_type":"Bearer"}""")
            .setHeader("Content-Type", "application/json"))

        val result = client.exchangeCode("authcode")

        assertEquals("at", result.accessToken)
        assertEquals("rt", result.refreshToken)
        assertEquals("idtok", result.idToken)
    }

    @Test fun `exchangeCode throws on 400 response`() {
        mockServer.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))
        assertThrows<OAuthTokenExchangeException> { client.exchangeCode("bad") }
    }

    @AfterEach fun teardown() { mockServer.shutdown() }
}
```

- [ ] **Step 2: 구현**

```kotlin
@Component
class GoogleDriveOAuthClient(
    private val webClient: WebClient,
    private val props: GoogleDriveProperties,
    private val tokenEndpoint: String = "https://oauth2.googleapis.com/token",
    private val revokeEndpoint: String = "https://oauth2.googleapis.com/revoke",
) {
    fun authUrl(state: String): String {
        val scopes = props.scopes.joinToString(" ")
        return "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=${props.clientId}" +
            "&redirect_uri=${URLEncoder.encode(props.redirectUri, UTF_8)}" +
            "&response_type=code" +
            "&scope=${URLEncoder.encode(scopes, UTF_8)}" +
            "&access_type=offline&prompt=consent" +
            "&state=${URLEncoder.encode(state, UTF_8)}"
    }

    fun exchangeCode(code: String): TokenResponse =
        webClient.post().uri(tokenEndpoint)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .bodyValue(
                LinkedMultiValueMap<String, String>().apply {
                    add("code", code); add("client_id", props.clientId)
                    add("client_secret", props.clientSecret)
                    add("redirect_uri", props.redirectUri); add("grant_type", "authorization_code")
                })
            .retrieve()
            .onStatus({ it.is4xxClientError }) { Mono.error(OAuthTokenExchangeException("exchange failed")) }
            .bodyToMono(TokenResponse::class.java)
            .block() ?: throw OAuthTokenExchangeException("empty response")

    fun refresh(refreshToken: String): TokenResponse = /* 유사, grant_type=refresh_token */ ...

    fun revoke(token: String): Boolean = /* POST revokeEndpoint?token=... */ ...
}

class OAuthTokenExchangeException(msg: String) : RuntimeException(msg)
```

- [ ] **Step 3: 테스트 실행 + 커밋**

```bash
./gradlew :onGo-infrastructure:test --tests "*.GoogleDriveOAuthClientTest"
git add backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/googledrive/GoogleDriveOAuthClient.kt \
        backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/googledrive/dto/TokenResponse.kt \
        backend/onGo-infrastructure/src/test/kotlin/com/ongo/infrastructure/external/googledrive/GoogleDriveOAuthClientTest.kt
git commit -m "feat: GoogleDriveOAuthClient — code 교환/refresh/revoke + MockWebServer 테스트"
```

---

### Task 11: OAuthStateManager (CSRF)

**Files:**
- Create: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/googledrive/OAuthStateManager.kt`
- Test: `backend/onGo-infrastructure/src/test/kotlin/com/ongo/infrastructure/external/googledrive/OAuthStateManagerTest.kt`

- [ ] **Step 1: 테스트 작성**

```kotlin
class OAuthStateManagerTest {
    private val secret = "test-secret-32-chars-minimum-abc"
    private val manager = OAuthStateManager(secret, ttlSeconds = 300)

    @Test fun `issue and verify roundtrip returns userId`() {
        val state = manager.issue(userId = 42L)
        assertEquals(42L, manager.verify(state))
    }

    @Test fun `verify throws on tampered state`() {
        val state = manager.issue(userId = 42L)
        val tampered = state.replaceAfterLast('.', "tamperedSig")
        assertThrows<OAuthStateMismatchException> { manager.verify(tampered) }
    }

    @Test fun `verify throws when expired`() {
        val manager2 = OAuthStateManager(secret, ttlSeconds = 0)
        val state = manager2.issue(42L)
        Thread.sleep(100)
        assertThrows<OAuthStateMismatchException> { manager2.verify(state) }
    }
}
```

- [ ] **Step 2: 구현**

```kotlin
@Component
class OAuthStateManager(
    @Value("\${ongo.content-source.oauth-state-secret}") private val secret: String,
    private val ttlSeconds: Long = 300,
) {
    fun issue(userId: Long): String {
        val nonce = UUID.randomUUID().toString()
        val issuedAt = Instant.now().epochSecond
        val payload = "$userId:$nonce:$issuedAt"
        val sig = hmac(payload)
        return "${Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())}.$sig"
    }

    fun verify(state: String): Long {
        val parts = state.split(".")
        if (parts.size != 2) throw OAuthStateMismatchException()
        val payload = String(Base64.getUrlDecoder().decode(parts[0]))
        val expected = hmac(payload)
        if (!MessageDigest.isEqual(expected.toByteArray(), parts[1].toByteArray()))
            throw OAuthStateMismatchException()
        val (userId, _, issuedAt) = payload.split(":")
        if (Instant.now().epochSecond - issuedAt.toLong() > ttlSeconds)
            throw OAuthStateMismatchException()
        return userId.toLong()
    }

    private fun hmac(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.toByteArray()))
    }
}
```

`application.yml`에 `ongo.content-source.oauth-state-secret: ${OAUTH_STATE_SECRET:change-me-32chars-minimum-local-dev}` 추가.

- [ ] **Step 3: 테스트 + 커밋**

```bash
./gradlew :onGo-infrastructure:test --tests "*.OAuthStateManagerTest"
git add backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/googledrive/OAuthStateManager.kt \
        backend/onGo-infrastructure/src/test/kotlin/com/ongo/infrastructure/external/googledrive/OAuthStateManagerTest.kt \
        backend/onGo-api/src/main/resources/application.yml
git commit -m "feat: OAuthStateManager — HMAC 서명 state 발급/검증"
```

---

### Task 12: ContentSourceTokenManager (refresh 경합 제어)

**Files:**
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/ContentSourceTokenManager.kt`
- Test: `backend/onGo-application/src/test/kotlin/com/ongo/application/contentsource/ContentSourceTokenManagerTest.kt`

- [ ] **Step 1: 테스트 작성**

```kotlin
class ContentSourceTokenManagerTest {
    private val repo = mockk<ContentSourceRepository>(relaxed = true)
    private val oauth = mockk<GoogleDriveOAuthClient>()
    private val encryptor = mockk<TokenEncryptor>()

    private val manager = ContentSourceTokenManager(repo, oauth, encryptor)

    @BeforeEach fun setup() {
        every { encryptor.decrypt(any()) } answers { firstArg<String>().removePrefix("enc:") }
        every { encryptor.encrypt(any()) } answers { "enc:${firstArg<String>()}" }
    }

    @Test fun `ensureValid returns token unchanged when not expired`() {
        val source = activeSourceExpiring(Instant.now().plusSeconds(600))
        every { repo.findById(1) } returns source
        val token = manager.ensureValidToken(sourceId = 1)
        assertEquals("access", token)
        verify(exactly = 0) { oauth.refresh(any()) }
    }

    @Test fun `ensureValid refreshes when expired`() {
        every { repo.findById(1) } returns activeSourceExpiring(Instant.now().minusSeconds(10))
        every { oauth.refresh("refresh") } returns TokenResponse(
            accessToken = "new-at", refreshToken = null, expiresIn = 3600,
            idToken = null, scope = "drive.readonly", tokenType = "Bearer"
        )
        val token = manager.ensureValidToken(1)
        assertEquals("new-at", token)
        verify { repo.updateTokens(1, "enc:new-at", null, any()) }
    }

    @Test fun `concurrent refresh is serialized`() {
        every { repo.findById(1) } returnsMany listOf(
            activeSourceExpiring(Instant.now().minusSeconds(10)),
            activeSourceExpiring(Instant.now().plusSeconds(3600)),   // 두번째 호출은 이미 갱신됨 가정
        )
        every { oauth.refresh("refresh") } returns TokenResponse("new-at", null, 3600, null, null, "Bearer")
        val pool = Executors.newFixedThreadPool(5)
        val results = (1..5).map { pool.submit { manager.ensureValidToken(1) } }.map { it.get() }
        pool.shutdown()
        // refresh는 최대 1회만 호출되어야 함
        verify(atMost = 1) { oauth.refresh(any()) }
    }
}
```

- [ ] **Step 2: 구현**

```kotlin
@Component
class ContentSourceTokenManager(
    private val repo: ContentSourceRepository,
    private val oauth: GoogleDriveOAuthClient,
    private val encryptor: TokenEncryptor,
) {
    private val locks: LoadingCache<Long, ReentrantLock> =
        Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(10)).build { ReentrantLock() }

    fun ensureValidToken(sourceId: Long): String {
        val lock = locks[sourceId]
        lock.lock()
        try {
            val source = repo.findById(sourceId) ?: throw ContentSourceNotConnectedException()
            if (source.status != ContentSourceStatus.ACTIVE) throw ContentSourceExpiredException(source.lastError ?: "")
            if (!source.needsRefresh(Instant.now())) return encryptor.decrypt(source.accessTokenEncrypted)
            return refreshLocked(source)
        } finally { lock.unlock() }
    }

    private fun refreshLocked(source: ContentSource): String {
        val refreshToken = source.refreshTokenEncrypted?.let(encryptor::decrypt)
            ?: throw ContentSourceExpiredException("no refresh token")
        try {
            val resp = oauth.refresh(refreshToken)
            val newAccessEnc = encryptor.encrypt(resp.accessToken)
            val newRefreshEnc = resp.refreshToken?.let(encryptor::encrypt)
            val newExpiresAt = Instant.now().plusSeconds(resp.expiresIn.toLong())
            repo.updateTokens(source.id, newAccessEnc, newRefreshEnc, newExpiresAt)
            return resp.accessToken
        } catch (e: OAuthTokenExchangeException) {
            repo.updateStatus(source.id, ContentSourceStatus.EXPIRED, e.message)
            throw ContentSourceExpiredException(e.message ?: "")
        }
    }
}
```

- [ ] **Step 3: 테스트 + 커밋**

```bash
./gradlew :onGo-application:test --tests "*.ContentSourceTokenManagerTest"
git add backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/ContentSourceTokenManager.kt \
        backend/onGo-application/src/test/kotlin/com/ongo/application/contentsource/ContentSourceTokenManagerTest.kt
git commit -m "feat: ContentSourceTokenManager — per-source 락으로 refresh 경합 제어"
```

---

### Task 13: ConnectGoogleDriveUseCase

**Files:**
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/ConnectGoogleDriveUseCase.kt`
- Test: `backend/onGo-application/src/test/kotlin/com/ongo/application/contentsource/ConnectGoogleDriveUseCaseTest.kt`

- [ ] **Step 1: 테스트**

```kotlin
class ConnectGoogleDriveUseCaseTest {
    private val repo = mockk<ContentSourceRepository>(relaxed = true)
    private val oauth = mockk<GoogleDriveOAuthClient>()
    private val stateManager = mockk<OAuthStateManager>()
    private val encryptor = mockk<TokenEncryptor>()
    private val idTokenVerifier = mockk<GoogleIdTokenVerifier>()

    private val useCase = ConnectGoogleDriveUseCase(repo, oauth, stateManager, encryptor, idTokenVerifier)

    @Test fun `handleCallback inserts new source when none exists`() {
        every { stateManager.verify("state1") } returns 100L
        every { oauth.exchangeCode("code") } returns TokenResponse(
            accessToken = "at", refreshToken = "rt", expiresIn = 3600,
            idToken = "idtok", scope = "drive.readonly", tokenType = "Bearer"
        )
        every { idTokenVerifier.verify("idtok") } returns GoogleIdTokenPayload(
            sub = "google-sub-xyz", email = "u@gmail.com", name = "User"
        )
        every { encryptor.encrypt(any()) } answers { "enc:${firstArg<String>()}" }
        every { repo.findByUserAndType(100L, ContentSourceType.GOOGLE_DRIVE) } returns null
        every { repo.save(any()) } answers { firstArg<ContentSource>().copy(id = 7L) }

        val result = useCase.handleCallback(code = "code", state = "state1")

        assertEquals(7L, result.id)
        verify { repo.save(match {
            it.userId == 100L && it.externalAccountId == "google-sub-xyz"
                && it.accountEmail == "u@gmail.com" && it.status == ContentSourceStatus.ACTIVE
        }) }
    }

    @Test fun `handleCallback updates existing source`() {
        every { stateManager.verify("state1") } returns 100L
        every { oauth.exchangeCode("code") } returns TokenResponse("at","rt",3600,"idtok","drive.readonly","Bearer")
        every { idTokenVerifier.verify("idtok") } returns GoogleIdTokenPayload("google-sub-new", "new@gmail.com", "New")
        every { encryptor.encrypt(any()) } answers { "enc:${firstArg<String>()}" }
        every { repo.findByUserAndType(100L, ContentSourceType.GOOGLE_DRIVE) } returns
            activeSource(id = 5L, externalAccountId = "google-sub-old", email = "old@gmail.com")
        every { repo.save(any()) } answers { firstArg() }

        val result = useCase.handleCallback("code", "state1")

        assertEquals(5L, result.id)
        verify { repo.save(match {
            it.id == 5L && it.externalAccountId == "google-sub-new" && it.accountEmail == "new@gmail.com"
                && it.status == ContentSourceStatus.ACTIVE
        }) }
    }

    @Test fun `handleCallback throws on state mismatch`() {
        every { stateManager.verify("bad") } throws OAuthStateMismatchException()
        assertThrows<OAuthStateMismatchException> { useCase.handleCallback("code", "bad") }
    }
}
```

- [ ] **Step 2: 구현**

```kotlin
@Service
class ConnectGoogleDriveUseCase(
    private val repo: ContentSourceRepository,
    private val oauth: GoogleDriveOAuthClient,
    private val stateManager: OAuthStateManager,
    private val encryptor: TokenEncryptor,
    private val idTokenVerifier: GoogleIdTokenVerifier,
) {
    fun authUrl(userId: Long): String = oauth.authUrl(state = stateManager.issue(userId))

    @Transactional
    fun handleCallback(code: String, state: String): ContentSource {
        val userId = stateManager.verify(state)
        val token = oauth.exchangeCode(code)
        val idToken = token.idToken ?: throw OAuthTokenExchangeException("missing id_token")
        val payload = idTokenVerifier.verify(idToken)

        val existing = repo.findByUserAndType(userId, ContentSourceType.GOOGLE_DRIVE)
        val source = ContentSource(
            id = existing?.id ?: 0L,
            userId = userId,
            sourceType = ContentSourceType.GOOGLE_DRIVE,
            externalAccountId = payload.sub,
            accountEmail = payload.email,
            accountDisplayName = payload.name,
            accessTokenEncrypted = encryptor.encrypt(token.accessToken),
            refreshTokenEncrypted = token.refreshToken?.let(encryptor::encrypt)
                ?: existing?.refreshTokenEncrypted,
            tokenExpiresAt = Instant.now().plusSeconds(token.expiresIn.toLong()),
            grantedScopes = token.scope,
            status = ContentSourceStatus.ACTIVE,
            lastError = null,
            connectedAt = existing?.connectedAt ?: Instant.now(),
            lastUsedAt = existing?.lastUsedAt,
            updatedAt = Instant.now(),
        )
        return repo.save(source)
    }
}
```

- [ ] **Step 3: GoogleIdTokenVerifier 구현**

`GoogleIdTokenVerifier.kt`:
```kotlin
@Component
class GoogleIdTokenVerifier(private val props: GoogleDriveProperties) {
    private val verifier = com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier.Builder(
        NetHttpTransport(), GsonFactory()
    ).setAudience(listOf(props.clientId)).build()

    fun verify(idToken: String): GoogleIdTokenPayload {
        val parsed = verifier.verify(idToken) ?: throw OAuthTokenExchangeException("invalid id_token")
        return GoogleIdTokenPayload(
            sub = parsed.payload.subject,
            email = parsed.payload.email ?: "",
            name = parsed.payload["name"]?.toString(),
        )
    }
}

data class GoogleIdTokenPayload(val sub: String, val email: String, val name: String?)
```

의존성 확인: `com.google.api-client:google-api-client`가 이미 YouTubeClient 쪽에 있으니 재사용.

- [ ] **Step 4: 테스트 + 커밋**

```bash
./gradlew :onGo-application:test --tests "*.ConnectGoogleDriveUseCaseTest"
git add backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/ConnectGoogleDriveUseCase.kt \
        backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/googledrive/GoogleIdTokenVerifier.kt \
        backend/onGo-application/src/test/kotlin/com/ongo/application/contentsource/ConnectGoogleDriveUseCaseTest.kt
git commit -m "feat: ConnectGoogleDriveUseCase — OAuth 콜백 → ContentSource upsert"
```

---

### Task 14: DisconnectContentSourceUseCase

**Files:**
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/DisconnectContentSourceUseCase.kt`
- Test: `backend/onGo-application/src/test/kotlin/com/ongo/application/contentsource/DisconnectContentSourceUseCaseTest.kt`

- [ ] **Step 1: 테스트 + 구현**

```kotlin
@Service
class DisconnectContentSourceUseCase(
    private val repo: ContentSourceRepository,
    private val oauth: GoogleDriveOAuthClient,
    private val encryptor: TokenEncryptor,
) {
    @Transactional
    fun execute(userId: Long, sourceId: Long) {
        val source = repo.findById(sourceId) ?: throw ContentSourceNotConnectedException()
        require(source.userId == userId) { throw AccessDeniedException("not owner") }

        // 구글 측 토큰 revoke (실패해도 무시 — DB에서는 삭제)
        source.refreshTokenEncrypted?.let { runCatching { oauth.revoke(encryptor.decrypt(it)) } }
        repo.delete(sourceId)
    }
}
```

테스트: 정상 삭제, 타인 리소스 접근 시 AccessDeniedException, revoke 실패해도 삭제 성공.

- [ ] **Step 2: 테스트 + 커밋**

```bash
./gradlew :onGo-application:test --tests "*.DisconnectContentSourceUseCaseTest"
git add backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/DisconnectContentSourceUseCase.kt \
        backend/onGo-application/src/test/kotlin/com/ongo/application/contentsource/DisconnectContentSourceUseCaseTest.kt
git commit -m "feat: DisconnectContentSourceUseCase — 구글 revoke + DB 삭제"
```

---

### Task 15: ContentSourceController

**Files:**
- Create: `backend/onGo-api/src/main/kotlin/com/ongo/api/contentsource/ContentSourceController.kt`
- Create: `backend/onGo-api/src/main/kotlin/com/ongo/api/contentsource/dto/ContentSourceDto.kt`
- Test: `backend/onGo-api/src/test/kotlin/com/ongo/api/contentsource/ContentSourceControllerTest.kt` (@WebMvcTest)

- [ ] **Step 1: DTO**

```kotlin
data class ContentSourceDto(
    val id: Long,
    val sourceType: ContentSourceType,
    val accountEmail: String,
    val accountDisplayName: String?,
    val status: ContentSourceStatus,
    val connectedAt: Instant,
    val lastUsedAt: Instant?,
) {
    companion object {
        fun from(src: ContentSource) = ContentSourceDto(
            id = src.id, sourceType = src.sourceType,
            accountEmail = src.accountEmail, accountDisplayName = src.accountDisplayName,
            status = src.status, connectedAt = src.connectedAt, lastUsedAt = src.lastUsedAt,
        )
    }
}

data class AuthUrlResponse(val authUrl: String)
```

- [ ] **Step 2: Controller 구현**

```kotlin
@RestController
@RequestMapping("/api/v1/content-sources")
class ContentSourceController(
    private val repo: ContentSourceRepository,
    private val connectUseCase: ConnectGoogleDriveUseCase,
    private val disconnectUseCase: DisconnectContentSourceUseCase,
) {
    @GetMapping
    fun list(@AuthenticationPrincipal principal: UserPrincipal): ResData<List<ContentSourceDto>> =
        ResData.ok(repo.findAllByUser(principal.id).map(ContentSourceDto::from))

    @GetMapping("/google-drive/auth-url")
    fun authUrl(@AuthenticationPrincipal principal: UserPrincipal): ResData<AuthUrlResponse> =
        ResData.ok(AuthUrlResponse(connectUseCase.authUrl(principal.id)))

    @GetMapping("/google-drive/callback")
    fun callback(
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Void> {
        if (error != null) return redirectTo("/settings/content-sources?error=$error")
        if (code == null || state == null) return redirectTo("/settings/content-sources?error=invalid_callback")
        runCatching { connectUseCase.handleCallback(code, state) }
            .onFailure { return redirectTo("/settings/content-sources?error=${it.javaClass.simpleName}") }
        return redirectTo("/settings/content-sources?connected=google-drive")
    }

    @DeleteMapping("/{id}")
    fun disconnect(@AuthenticationPrincipal principal: UserPrincipal, @PathVariable id: Long): ResData<Unit> {
        disconnectUseCase.execute(principal.id, id)
        return ResData.ok(Unit)
    }

    @PostMapping("/{id}/reconnect")
    fun reconnect(@AuthenticationPrincipal principal: UserPrincipal, @PathVariable id: Long): ResData<AuthUrlResponse> {
        // 기존 source 확인(소유자 검증) 후 authUrl 재발급
        val src = repo.findById(id) ?: throw ContentSourceNotConnectedException()
        require(src.userId == principal.id) { throw AccessDeniedException("not owner") }
        return ResData.ok(AuthUrlResponse(connectUseCase.authUrl(principal.id)))
    }

    private fun redirectTo(path: String): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.FOUND).location(URI.create(path)).build()
}
```

- [ ] **Step 3: GlobalExceptionHandler에 매핑 추가**

기존 handler 파일에 추가:
```kotlin
@ExceptionHandler(ContentSourceNotConnectedException::class)
fun handleNotConnected(e: ContentSourceNotConnectedException) = ResponseEntity
    .status(404).body(ResData.error("CONTENT_SOURCE_NOT_CONNECTED", e.message))

@ExceptionHandler(ContentSourceExpiredException::class)
fun handleExpired(e: ContentSourceExpiredException) = ResponseEntity
    .status(401).body(ResData.error("CONTENT_SOURCE_EXPIRED", e.message))

// ... 나머지 예외 동일 패턴
```

- [ ] **Step 4: @WebMvcTest**

```kotlin
@WebMvcTest(ContentSourceController::class)
class ContentSourceControllerTest {
    @Autowired lateinit var mvc: MockMvc
    @MockkBean lateinit var repo: ContentSourceRepository
    @MockkBean lateinit var connect: ConnectGoogleDriveUseCase
    @MockkBean lateinit var disconnect: DisconnectContentSourceUseCase

    @Test @WithMockUser(id = "100")
    fun `GET content-sources returns list`() {
        every { repo.findAllByUser(100) } returns listOf(sampleSource())
        mvc.get("/api/v1/content-sources").andExpect {
            status { isOk() }
            jsonPath("$.data[0].accountEmail") { value("user@gmail.com") }
        }
    }

    @Test @WithMockUser(id = "100")
    fun `GET auth-url returns URL`() {
        every { connect.authUrl(100) } returns "https://accounts.google.com/o/oauth2/auth?..."
        mvc.get("/api/v1/content-sources/google-drive/auth-url").andExpect {
            status { isOk() }
            jsonPath("$.data.authUrl") { exists() }
        }
    }
}
```

- [ ] **Step 5: 커밋**

```bash
./gradlew :onGo-api:test --tests "*.ContentSourceControllerTest"
git add backend/onGo-api/src/main/kotlin/com/ongo/api/contentsource/ \
        backend/onGo-api/src/test/kotlin/com/ongo/api/contentsource/ \
        backend/onGo-api/src/main/kotlin/com/ongo/api/common/GlobalExceptionHandler.kt
git commit -m "feat: ContentSourceController — 연결/해제/authUrl/callback + 예외 매핑"
```

---

### Phase 2 완료 체크

- [ ] 수동 스모크: `GET /api/v1/content-sources/google-drive/auth-url` 호출 → 구글 OAuth URL 반환
- [ ] 실제 구글 OAuth 동의 완료 후 `user_content_sources` row 생성 확인
- [ ] `DELETE /api/v1/content-sources/{id}` 호출 후 row 삭제 확인

---

## Phase 3 — GoogleDriveClient + 파일 목록 API

### Task 16: GoogleDriveApi (@HttpExchange) + DTO

**Files:**
- Create: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/googledrive/GoogleDriveApi.kt`
- Create: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/googledrive/dto/DriveFilesListResponse.kt`

- [ ] **Step 1: API 인터페이스**

```kotlin
@HttpExchange(url = "https://www.googleapis.com/drive/v3")
interface GoogleDriveApi {
    @GetExchange("/files")
    fun listFiles(
        @RequestHeader("Authorization") auth: String,
        @RequestParam q: String,
        @RequestParam pageSize: Int,
        @RequestParam(required = false) pageToken: String?,
        @RequestParam fields: String,
        @RequestParam includeItemsFromAllDrives: Boolean = false,
        @RequestParam supportsAllDrives: Boolean = false,
        @RequestParam(required = false) orderBy: String?,
    ): DriveFilesListResponse

    @GetExchange("/files/{fileId}")
    fun getFile(
        @PathVariable fileId: String,
        @RequestHeader("Authorization") auth: String,
        @RequestParam fields: String,
    ): DriveFileResource
}
```

DTO:
```kotlin
data class DriveFilesListResponse(
    val files: List<DriveFileResource>,
    val nextPageToken: String?,
)
data class DriveFileResource(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: String?,                   // Long 문자열
    val thumbnailLink: String?,
    val iconLink: String?,
    val modifiedTime: String,            // ISO-8601
    val parents: List<String>?,
    val videoMediaMetadata: VideoMediaMetadata?,
)
data class VideoMediaMetadata(val durationMillis: String?, val width: Int?, val height: Int?)
```

- [ ] **Step 2: Configuration에서 클라이언트 빈 등록**

기존 YouTube 쪽 `YouTubeConfig.kt` 참고해 동일 패턴으로 `GoogleDriveConfig.kt`에 `WebClient + HttpServiceProxyFactory` 로 `GoogleDriveApi` 빈 생성.

- [ ] **Step 3: 커밋**

```bash
git add backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/googledrive/GoogleDriveApi.kt \
        backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/googledrive/dto/ \
        backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/googledrive/GoogleDriveConfig.kt
git commit -m "feat: GoogleDriveApi @HttpExchange 인터페이스 + DTO + Config"
```

---

### Task 17: GoogleDriveClient — 파일 목록/조회 + 다운로드 스트림

**Files:**
- Create: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/googledrive/GoogleDriveClient.kt`
- Test: `backend/onGo-infrastructure/src/test/kotlin/com/ongo/infrastructure/external/googledrive/GoogleDriveClientTest.kt`

- [ ] **Step 1: 테스트 (MockWebServer)**

```kotlin
class GoogleDriveClientTest {
    private val server = MockWebServer().apply { start() }
    private val api: GoogleDriveApi = ... // server.url("/") 사용
    private val webClient: WebClient = WebClient.create(server.url("/").toString())
    private val client = GoogleDriveClient(api, webClient)

    @Test fun `listVideoFiles builds correct query for folder`() {
        server.enqueue(MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("""{"files":[{"id":"f1","name":"a.mp4","mimeType":"video/mp4","size":"1000","modifiedTime":"2026-04-19T00:00:00Z"}],"nextPageToken":null}"""))

        val result = client.listVideoFiles(
            token = "at",
            folderId = "rootFolder",
            searchQuery = null,
            pageSize = 50,
            pageToken = null,
        )

        val request = server.takeRequest()
        assertTrue(request.path!!.contains("q=mimeType+contains+'video%2F'"))
        assertTrue(request.path!!.contains("'rootFolder'+in+parents"))
        assertEquals(1, result.items.size)
        assertEquals("f1", result.items[0].id)
    }

    @Test fun `listVideoFiles builds search query when q given`() {
        server.enqueue(MockResponse().setBody("""{"files":[],"nextPageToken":null}"""))
        client.listVideoFiles(token = "at", folderId = null, searchQuery = "홍길동", pageSize = 50, pageToken = null)
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("name+contains+'%ED%99%8D%EA%B8%B8%EB%8F%99'"))
    }

    @Test fun `getFileMeta returns parsed resource`() {
        server.enqueue(MockResponse().setBody("""{"id":"f1","name":"a.mp4","mimeType":"video/mp4","size":"999","modifiedTime":"2026-04-19T00:00:00Z"}"""))
        val file = client.getFileMeta("at", "f1")
        assertEquals("f1", file.id); assertEquals(999L, file.sizeBytes)
    }
}
```

- [ ] **Step 2: 구현**

```kotlin
@Component
class GoogleDriveClient(
    private val api: GoogleDriveApi,
    @Qualifier("driveWebClient") private val webClient: WebClient,
) {
    data class ListResult(val items: List<DriveFile>, val nextPageToken: String?)

    fun listVideoFiles(token: String, folderId: String?, searchQuery: String?, pageSize: Int, pageToken: String?, includeSharedWithMe: Boolean = false): ListResult {
        val q = buildQuery(folderId, searchQuery, includeSharedWithMe)
        val resp = api.listFiles(
            auth = "Bearer $token", q = q, pageSize = pageSize, pageToken = pageToken,
            fields = LIST_FIELDS, orderBy = "folder,name",
        )
        return ListResult(
            items = resp.files.map(::toDomain),
            nextPageToken = resp.nextPageToken,
        )
    }

    fun getFileMeta(token: String, fileId: String): DriveFile {
        val r = try {
            api.getFile(fileId = fileId, auth = "Bearer $token", fields = FILE_FIELDS)
        } catch (e: WebClientResponseException.NotFound) { throw DriveFileNotFoundException(fileId) }
          catch (e: WebClientResponseException.Forbidden) { throw DriveFilePermissionDeniedException(fileId) }
        return toDomain(r)
    }

    fun downloadStream(token: String, fileId: String): Flux<DataBuffer> =
        webClient.get()
            .uri("https://www.googleapis.com/drive/v3/files/{id}?alt=media", fileId)
            .header("Authorization", "Bearer $token")
            .retrieve()
            .onStatus({ it == HttpStatus.NOT_FOUND }) { Mono.error(DriveFileNotFoundException(fileId)) }
            .onStatus({ it == HttpStatus.FORBIDDEN }) { Mono.error(DriveFilePermissionDeniedException(fileId)) }
            .bodyToFlux(DataBuffer::class.java)

    private fun buildQuery(folderId: String?, search: String?, includeShared: Boolean): String = buildList {
        add("mimeType contains 'video/' or mimeType = 'application/vnd.google-apps.folder'")
        add("trashed = false")
        when {
            !search.isNullOrBlank() -> add("name contains '${search.replace("'", "\\'")}'")
            folderId != null && folderId != "sharedWithMe" -> add("'$folderId' in parents")
            folderId == "sharedWithMe" && includeShared -> add("sharedWithMe")
        }
    }.joinToString(" and ")

    private fun toDomain(r: DriveFileResource): DriveFile = DriveFile(
        id = r.id, name = r.name, mimeType = r.mimeType,
        sizeBytes = r.size?.toLongOrNull(),
        durationSeconds = r.videoMediaMetadata?.durationMillis?.toLongOrNull()?.let { it / 1000 },
        thumbnailUrl = r.thumbnailLink ?: r.iconLink,
        modifiedAt = Instant.parse(r.modifiedTime),
        kind = if (r.mimeType == "application/vnd.google-apps.folder") DriveFile.Kind.FOLDER else DriveFile.Kind.FILE,
    )

    companion object {
        const val LIST_FIELDS = "files(id,name,mimeType,size,thumbnailLink,iconLink,modifiedTime,parents,videoMediaMetadata),nextPageToken"
        const val FILE_FIELDS = "id,name,mimeType,size,thumbnailLink,iconLink,modifiedTime,parents,videoMediaMetadata"
    }
}
```

- [ ] **Step 3: 테스트 + 커밋**

```bash
./gradlew :onGo-infrastructure:test --tests "*.GoogleDriveClientTest"
git add backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/googledrive/GoogleDriveClient.kt \
        backend/onGo-infrastructure/src/test/kotlin/com/ongo/infrastructure/external/googledrive/GoogleDriveClientTest.kt
git commit -m "feat: GoogleDriveClient — listVideoFiles/getFileMeta/downloadStream + 쿼리 조립 테스트"
```

---

### Task 18: ListDriveFilesUseCase + Breadcrumb

**Files:**
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/ListDriveFilesUseCase.kt`
- Test: `backend/onGo-application/src/test/kotlin/com/ongo/application/contentsource/ListDriveFilesUseCaseTest.kt`

- [ ] **Step 1: 테스트**

```kotlin
class ListDriveFilesUseCaseTest {
    private val repo = mockk<ContentSourceRepository>(relaxed = true)
    private val tokenManager = mockk<ContentSourceTokenManager>()
    private val driveClient = mockk<GoogleDriveClient>()

    private val useCase = ListDriveFilesUseCase(repo, tokenManager, driveClient)

    @Test fun `execute throws when no active source`() {
        every { repo.findByUserAndType(100L, ContentSourceType.GOOGLE_DRIVE) } returns null
        assertThrows<ContentSourceNotConnectedException> {
            useCase.execute(userId = 100L, folderId = "root", q = null, pageToken = null, pageSize = 50)
        }
    }

    @Test fun `execute fetches and marks used`() {
        val source = activeSource(id = 7L, userId = 100L)
        every { repo.findByUserAndType(100L, ContentSourceType.GOOGLE_DRIVE) } returns source
        every { tokenManager.ensureValidToken(7L) } returns "at"
        every { driveClient.listVideoFiles("at", "root", null, 50, null, false) } returns
            GoogleDriveClient.ListResult(items = emptyList(), nextPageToken = null)

        val result = useCase.execute(100L, "root", null, null, 50)

        assertEquals(0, result.items.size)
        verify { repo.markUsed(7L) }
    }
}
```

- [ ] **Step 2: 구현**

```kotlin
@Service
class ListDriveFilesUseCase(
    private val repo: ContentSourceRepository,
    private val tokenManager: ContentSourceTokenManager,
    private val driveClient: GoogleDriveClient,
) {
    data class Result(
        val items: List<DriveFile>,
        val nextPageToken: String?,
        val breadcrumbs: List<Breadcrumb>,
    )
    data class Breadcrumb(val id: String, val name: String)

    fun execute(userId: Long, folderId: String?, q: String?, pageToken: String?, pageSize: Int, includeSharedWithMe: Boolean = false): Result {
        val source = repo.findByUserAndType(userId, ContentSourceType.GOOGLE_DRIVE)
            ?: throw ContentSourceNotConnectedException()
        val token = tokenManager.ensureValidToken(source.id)

        val list = driveClient.listVideoFiles(token, folderId, q, pageSize.coerceAtMost(100), pageToken, includeSharedWithMe)
        val breadcrumbs = if (folderId.isNullOrBlank() || folderId == "root") {
            listOf(Breadcrumb("root", "내 드라이브"))
        } else {
            buildBreadcrumbs(token, folderId)
        }
        repo.markUsed(source.id)
        return Result(list.items, list.nextPageToken, breadcrumbs)
    }

    private fun buildBreadcrumbs(token: String, folderId: String): List<Breadcrumb> {
        val chain = mutableListOf<Breadcrumb>()
        var current: String? = folderId
        var safety = 20
        while (current != null && safety-- > 0) {
            val f = runCatching { driveClient.getFileMeta(token, current) }.getOrNull() ?: break
            chain.add(0, Breadcrumb(f.id, f.name))
            current = /* f.parents?.firstOrNull() - GoogleDriveClient 에 parents 노출 필요 */ null
        }
        chain.add(0, Breadcrumb("root", "내 드라이브"))
        return chain
    }
}
```

⚠ `buildBreadcrumbs`가 제대로 동작하려면 `DriveFile` 도메인에 `parents: List<String>` 필드를 추가하거나, GoogleDriveClient에 별도 `getFileParents` 메서드를 노출해야 한다. 여기서는 간단화를 위해 **Phase 3 초기엔 breadcrumb 없이 반환**하고, 이후 Task에서 확장.

- [ ] **Step 3: 테스트 + 커밋**

```bash
./gradlew :onGo-application:test --tests "*.ListDriveFilesUseCaseTest"
git add backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/ListDriveFilesUseCase.kt \
        backend/onGo-application/src/test/kotlin/com/ongo/application/contentsource/ListDriveFilesUseCaseTest.kt
git commit -m "feat: ListDriveFilesUseCase — 토큰 갱신 + 파일 목록 + markUsed"
```

---

### Task 19: DriveFileController

**Files:**
- Create: `backend/onGo-api/src/main/kotlin/com/ongo/api/contentsource/DriveFileController.kt`
- Create: `backend/onGo-api/src/main/kotlin/com/ongo/api/contentsource/dto/DriveFileDto.kt`
- Test: `backend/onGo-api/src/test/kotlin/com/ongo/api/contentsource/DriveFileControllerTest.kt`

- [ ] **Step 1: DTO + Controller**

```kotlin
data class DriveFileDto(
    val id: String, val name: String, val mimeType: String,
    val sizeBytes: Long?, val durationSeconds: Long?,
    val thumbnailUrl: String?, val modifiedAt: Instant, val kind: String,
) {
    companion object {
        fun from(f: DriveFile) = DriveFileDto(
            id = f.id, name = f.name, mimeType = f.mimeType, sizeBytes = f.sizeBytes,
            durationSeconds = f.durationSeconds, thumbnailUrl = f.thumbnailUrl,
            modifiedAt = f.modifiedAt, kind = f.kind.name,
        )
    }
}

data class DriveFileListDto(
    val items: List<DriveFileDto>,
    val nextPageToken: String?,
    val breadcrumbs: List<BreadcrumbDto>,
)
data class BreadcrumbDto(val id: String, val name: String)

@RestController
@RequestMapping("/api/v1/drive/files")
class DriveFileController(private val listUseCase: ListDriveFilesUseCase) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "root") folderId: String,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) pageToken: String?,
        @RequestParam(defaultValue = "50") pageSize: Int,
        @RequestParam(defaultValue = "false") includeSharedWithMe: Boolean,
    ): ResData<DriveFileListDto> {
        val result = listUseCase.execute(principal.id, folderId, q, pageToken, pageSize, includeSharedWithMe)
        return ResData.ok(DriveFileListDto(
            items = result.items.map(DriveFileDto::from),
            nextPageToken = result.nextPageToken,
            breadcrumbs = result.breadcrumbs.map { BreadcrumbDto(it.id, it.name) },
        ))
    }
}
```

- [ ] **Step 2: @WebMvcTest**

4xx/401/404 예외 응답 케이스 포함. 401은 `ContentSourceExpiredException` 던져졌을 때 error code `CONTENT_SOURCE_EXPIRED` 확인.

- [ ] **Step 3: 수동 스모크**

백엔드 부팅 후:
```
GET /api/v1/drive/files?folderId=root
→ ResData<DriveFileListDto> with items[]
```

- [ ] **Step 4: 커밋**

```bash
./gradlew :onGo-api:test --tests "*.DriveFileControllerTest"
git add backend/onGo-api/src/main/kotlin/com/ongo/api/contentsource/DriveFileController.kt \
        backend/onGo-api/src/main/kotlin/com/ongo/api/contentsource/dto/DriveFileDto.kt \
        backend/onGo-api/src/test/kotlin/com/ongo/api/contentsource/DriveFileControllerTest.kt
git commit -m "feat: DriveFileController — 파일 목록/검색 API"
```

---

### Phase 3 완료 체크

- [ ] 수동 스모크: Phase 2에서 연결한 드라이브 계정으로 `GET /api/v1/drive/files` 호출 → 실제 드라이브 영상 목록 반환
- [ ] 폴더 진입 (`?folderId=xxx`) 동작 확인
- [ ] 검색 (`?q=test`) 동작 확인

---

## Phase 4 — Import 파이프라인 (드라이브 → S3)

### Task 20: ImportDriveFileUseCase — 동기 파트 (Video/Job 생성)

**Files:**
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/ImportDriveFileUseCase.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/DriveImportRequestedEvent.kt`
- Test: `backend/onGo-application/src/test/kotlin/com/ongo/application/contentsource/ImportDriveFileUseCaseTest.kt`

- [ ] **Step 1: 테스트 — 성공 케이스 / 플랜 한도 / 중복 / 동시 제한**

```kotlin
class ImportDriveFileUseCaseTest {
    private val contentSourceRepo = mockk<ContentSourceRepository>(relaxed = true)
    private val jobRepo = mockk<DriveImportJobRepository>(relaxed = true)
    private val videoRepo = mockk<VideoRepository>(relaxed = true)
    private val tokenManager = mockk<ContentSourceTokenManager>()
    private val driveClient = mockk<GoogleDriveClient>()
    private val planLimitChecker = mockk<PlanLimitChecker>()
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val props = GoogleDriveProperties(clientId = "c", clientSecret = "s", redirectUri = "r")

    private val useCase = ImportDriveFileUseCase(
        contentSourceRepo, jobRepo, videoRepo, tokenManager, driveClient, planLimitChecker, publisher, props
    )

    @Test fun `start creates video and job in IMPORTING state and publishes event`() {
        val source = activeSource(id = 1, userId = 100)
        every { contentSourceRepo.findByUserAndType(100, ContentSourceType.GOOGLE_DRIVE) } returns source
        every { tokenManager.ensureValidToken(1) } returns "at"
        every { driveClient.getFileMeta("at", "f1") } returns
            DriveFile("f1", "a.mp4", "video/mp4", 1000L, 60L, null, Instant.now(), DriveFile.Kind.FILE)
        every { planLimitChecker.canImport(100, 1000L) } returns true
        every { jobRepo.findActiveByUserAndFileId(100, "f1") } returns emptyList()
        every { jobRepo.countActiveByUser(100) } returns 0
        every { videoRepo.save(any()) } answers { firstArg<Video>().copy(id = 10L) }
        every { jobRepo.save(any()) } answers { firstArg<DriveImportJob>().copy(id = 20L) }

        val result = useCase.start(userId = 100, fileId = "f1", confirmDuplicate = false)

        assertEquals(10L, result.videoId)
        assertEquals(20L, result.jobId)
        verify { videoRepo.save(match { it.source == VideoSource.GOOGLE_DRIVE && it.status == UploadStatus.IMPORTING }) }
        verify { jobRepo.save(match { it.status == DriveImportStatus.PENDING && it.driveFileId == "f1" }) }
        verify { publisher.publishEvent(DriveImportRequestedEvent(20L)) }
    }

    @Test fun `start throws PlanLimitExceededException when file too large`() {
        every { contentSourceRepo.findByUserAndType(100, ContentSourceType.GOOGLE_DRIVE) } returns activeSource()
        every { tokenManager.ensureValidToken(any()) } returns "at"
        every { driveClient.getFileMeta("at", "f1") } returns
            DriveFile("f1", "huge.mp4", "video/mp4", 10_000_000_000L, null, null, Instant.now(), DriveFile.Kind.FILE)
        every { planLimitChecker.canImport(100, 10_000_000_000L) } returns false

        assertThrows<PlanLimitExceededException> { useCase.start(100, "f1", false) }
    }

    @Test fun `start throws DuplicateImport when active job exists`() {
        every { contentSourceRepo.findByUserAndType(100, ContentSourceType.GOOGLE_DRIVE) } returns activeSource()
        every { tokenManager.ensureValidToken(any()) } returns "at"
        every { driveClient.getFileMeta(any(), "f1") } returns
            DriveFile("f1", "a.mp4", "video/mp4", 1000L, null, null, Instant.now(), DriveFile.Kind.FILE)
        every { planLimitChecker.canImport(any(), any()) } returns true
        every { jobRepo.findActiveByUserAndFileId(100, "f1") } returns listOf(
            sampleJob(status = DriveImportStatus.DOWNLOADING))
        every { jobRepo.countActiveByUser(any()) } returns 0

        assertThrows<DuplicateDriveImportException> { useCase.start(100, "f1", confirmDuplicate = false) }
    }

    @Test fun `start bypasses duplicate check when confirmDuplicate=true`() {
        // ... 중복 존재해도 성공해야 함
    }

    @Test fun `start throws ConcurrentImportLimit when 2 active`() {
        every { contentSourceRepo.findByUserAndType(100, ContentSourceType.GOOGLE_DRIVE) } returns activeSource()
        every { tokenManager.ensureValidToken(any()) } returns "at"
        every { driveClient.getFileMeta(any(), "f1") } returns
            DriveFile("f1", "a.mp4", "video/mp4", 1000L, null, null, Instant.now(), DriveFile.Kind.FILE)
        every { planLimitChecker.canImport(any(), any()) } returns true
        every { jobRepo.findActiveByUserAndFileId(any(), any()) } returns emptyList()
        every { jobRepo.countActiveByUser(100) } returns 2    // 이미 2개 진행 중

        assertThrows<ConcurrentImportLimitException> { useCase.start(100, "f1", false) }
    }
}
```

- [ ] **Step 2: 구현**

```kotlin
data class DriveImportRequestedEvent(val jobId: Long)

data class DriveImportStartResult(val jobId: Long, val videoId: Long, val status: DriveImportStatus)

@Service
class ImportDriveFileUseCase(
    private val contentSourceRepo: ContentSourceRepository,
    private val jobRepo: DriveImportJobRepository,
    private val videoRepo: VideoRepository,
    private val tokenManager: ContentSourceTokenManager,
    private val driveClient: GoogleDriveClient,
    private val planLimitChecker: PlanLimitChecker,
    private val publisher: ApplicationEventPublisher,
    private val props: GoogleDriveProperties,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) {
    @Transactional
    fun start(userId: Long, fileId: String, confirmDuplicate: Boolean): DriveImportStartResult {
        val source = contentSourceRepo.findByUserAndType(userId, ContentSourceType.GOOGLE_DRIVE)
            ?: throw ContentSourceNotConnectedException()
        val token = tokenManager.ensureValidToken(source.id)
        val meta = driveClient.getFileMeta(token, fileId)

        require(meta.isVideo()) { throw IllegalArgumentException("video 파일만 가져올 수 있습니다") }
        val size = meta.sizeBytes ?: throw IllegalStateException("파일 크기를 알 수 없습니다")

        if (!planLimitChecker.canImport(userId, size))
            throw PlanLimitExceededException("영상 크기/스토리지 한도 초과")

        val duplicates = jobRepo.findActiveByUserAndFileId(userId, fileId)  // FOR UPDATE
        if (duplicates.isNotEmpty() && !confirmDuplicate) throw DuplicateDriveImportException(fileId)

        val activeCount = jobRepo.countActiveByUser(userId)
        if (activeCount >= props.import.maxConcurrentPerUser)
            throw ConcurrentImportLimitException(props.import.maxConcurrentPerUser)

        val sourceRef = objectMapper.valueToTree<JsonNode>(mapOf(
            "fileId" to meta.id, "fileName" to meta.name,
            "mimeType" to meta.mimeType, "sizeBytes" to meta.sizeBytes,
            "modifiedAt" to meta.modifiedAt.toString(),
        ))

        val video = videoRepo.save(Video(
            id = 0L, userId = userId,
            title = meta.name.substringBeforeLast('.'),
            status = UploadStatus.IMPORTING,
            mediaType = MediaType.VIDEO,
            source = VideoSource.GOOGLE_DRIVE,
            sourceReference = sourceRef,
            fileSizeBytes = size,
            originalFilename = meta.name,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        ))

        val job = jobRepo.save(DriveImportJob(
            id = 0L, videoId = video.id, userId = userId,
            contentSourceId = source.id, driveFileId = meta.id, driveFileName = meta.name,
            fileSizeBytes = size, bytesTransferred = 0,
            status = DriveImportStatus.PENDING, s3Key = null, errorMessage = null,
            retryCount = 0, startedAt = null, completedAt = null,
            createdAt = Instant.now(), updatedAt = Instant.now(),
        ))

        publisher.publishEvent(DriveImportRequestedEvent(job.id))
        return DriveImportStartResult(jobId = job.id, videoId = video.id, status = DriveImportStatus.PENDING)
    }
}
```

`PlanLimitChecker`는 application 모듈의 기존 SubscriptionService/CreditService 패턴을 따라 신규 작성. 한도는 `Subscription.plan.maxVideoFileSizeBytes` 와 스토리지 잔여량 체크.

- [ ] **Step 3: 테스트 실행 + 커밋**

```bash
./gradlew :onGo-application:test --tests "*.ImportDriveFileUseCaseTest"
git add backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/ImportDriveFileUseCase.kt \
        backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/DriveImportRequestedEvent.kt \
        backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/PlanLimitChecker.kt \
        backend/onGo-application/src/test/kotlin/com/ongo/application/contentsource/ImportDriveFileUseCaseTest.kt
git commit -m "feat: ImportDriveFileUseCase — 한도/중복/동시제한 검증 + Video/Job 생성 + 이벤트 발행"
```

---

### Task 21: DriveImportEventListener — 비동기 복사 워커

**Files:**
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/DriveImportEventListener.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/DriveImportCancellationRegistry.kt`
- Test: `backend/onGo-application/src/test/kotlin/com/ongo/application/contentsource/DriveImportEventListenerTest.kt`

- [ ] **Step 1: CancellationRegistry**

```kotlin
@Component
class DriveImportCancellationRegistry {
    private val flags = ConcurrentHashMap<Long, AtomicBoolean>()
    fun register(jobId: Long) = flags.computeIfAbsent(jobId) { AtomicBoolean(false) }
    fun requestCancel(jobId: Long) { flags[jobId]?.set(true) }
    fun isCancelled(jobId: Long): Boolean = flags[jobId]?.get() ?: false
    fun clear(jobId: Long) { flags.remove(jobId) }
}
```

- [ ] **Step 2: Listener 구현**

```kotlin
@Component
class DriveImportEventListener(
    private val jobRepo: DriveImportJobRepository,
    private val videoRepo: VideoRepository,
    private val tokenManager: ContentSourceTokenManager,
    private val driveClient: GoogleDriveClient,
    private val storage: StorageClient,
    private val progressTracker: DriveImportProgressTracker,
    private val cancellationRegistry: DriveImportCancellationRegistry,
    private val props: GoogleDriveProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async("driveImportExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: DriveImportRequestedEvent) {
        val jobId = event.jobId
        val cancelFlag = cancellationRegistry.register(jobId)
        val job = jobRepo.findById(jobId) ?: return
        try {
            jobRepo.updateStatus(jobId, DriveImportStatus.DOWNLOADING, null)
            val token = tokenManager.ensureValidToken(job.contentSourceId)
            val s3Key = buildS3Key(job)

            val downloadFlux = driveClient.downloadStream(token, job.driveFileId)

            var transferred = 0L
            var lastUpdate = System.currentTimeMillis()
            val wrappedInput = asInputStream(downloadFlux, cancelFlag, onBytes = { n ->
                transferred += n
                val now = System.currentTimeMillis()
                if (now - lastUpdate >= props.import.progressUpdateIntervalMs) {
                    jobRepo.updateProgress(jobId, transferred)
                    progressTracker.publish(jobId, transferred, job.fileSizeBytes, DriveImportStatus.DOWNLOADING)
                    lastUpdate = now
                }
            })

            storage.uploadStream(
                key = s3Key, stream = wrappedInput,
                contentType = videoContentType(job.driveFileName),
                size = job.fileSizeBytes,
            )

            if (cancelFlag.get()) throw CancellationException()

            val fileUrl = storage.publicUrlFor(s3Key)
            videoRepo.updateAfterImport(job.videoId, fileUrl, UploadStatus.DRAFT)
            jobRepo.markCompleted(jobId, s3Key)
            progressTracker.publish(jobId, job.fileSizeBytes, job.fileSizeBytes, DriveImportStatus.COMPLETED)
            log.info("drive import completed: jobId={}, userId={}, bytes={}", jobId, job.userId, job.fileSizeBytes)
        } catch (c: CancellationException) {
            jobRepo.updateStatus(jobId, DriveImportStatus.CANCELLED, "사용자 취소")
            videoRepo.delete(job.videoId)
            runCatching { storage.abortMultipart(buildS3Key(job)) }
            progressTracker.publish(jobId, 0, job.fileSizeBytes, DriveImportStatus.CANCELLED)
        } catch (e: Exception) {
            log.error("drive import failed: jobId={}", jobId, e)
            jobRepo.updateStatus(jobId, DriveImportStatus.FAILED, e.message)
            videoRepo.updateStatus(job.videoId, UploadStatus.IMPORT_FAILED)
            runCatching { storage.abortMultipart(buildS3Key(job)) }
            progressTracker.publish(jobId, 0, job.fileSizeBytes, DriveImportStatus.FAILED, e.message)
        } finally {
            cancellationRegistry.clear(jobId)
        }
    }

    private fun buildS3Key(job: DriveImportJob): String {
        val ext = job.driveFileName.substringAfterLast('.', "mp4")
        return "users/${job.userId}/videos/${job.videoId}/${System.currentTimeMillis()}.$ext"
    }

    private fun videoContentType(name: String): String =
        when (name.substringAfterLast('.').lowercase()) {
            "mp4" -> "video/mp4"; "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"; "webm" -> "video/webm"; else -> "application/octet-stream"
        }

    /** Flux<DataBuffer> → InputStream 변환 (DataBufferUtils.write + PipedOutputStream 또는 DataBuffer.asInputStream 사용) */
    private fun asInputStream(flux: Flux<DataBuffer>, cancelFlag: AtomicBoolean, onBytes: (Int) -> Unit): InputStream = ...
}
```

**핵심 난점:** `Flux<DataBuffer>` → blocking `InputStream` 변환. `DataBufferUtils.write(flux, PipedOutputStream)` 패턴 사용. 또는 `reactor.core.publisher.Flux.toIterable` 후 DataBuffer bytes → InputStream.

- [ ] **Step 3: Async executor 설정**

```kotlin
@Configuration
@EnableAsync
class DriveImportAsyncConfig {
    @Bean("driveImportExecutor")
    fun driveImportExecutor(): Executor = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("drive-import-", 0).factory()
    )
}
```

- [ ] **Step 4: Listener 테스트 (mockk)**

정상 복사 / 취소 / 다운로드 실패 3 케이스.

- [ ] **Step 5: 커밋**

```bash
./gradlew :onGo-application:test --tests "*.DriveImportEventListenerTest"
git add backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/DriveImportEventListener.kt \
        backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/DriveImportCancellationRegistry.kt \
        backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/DriveImportAsyncConfig.kt \
        backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/DriveImportProgressTracker.kt \
        backend/onGo-application/src/test/kotlin/com/ongo/application/contentsource/DriveImportEventListenerTest.kt
git commit -m "feat: DriveImportEventListener — 비동기 복사 워커 + 취소/실패 처리 + virtual thread pool"
```

---

### Task 22: StorageClient 확장 — uploadStream / abortMultipart

**Files:**
- Modify: `backend/onGo-domain/src/main/kotlin/com/ongo/domain/storage/StorageClient.kt` (또는 Port)
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/storage/S3StorageClient.kt`
- Modify: `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/storage/MinioStorageClient.kt`

- [ ] **Step 1: 인터페이스 확장**

기존 `StorageClient`에 추가:
```kotlin
fun uploadStream(key: String, stream: InputStream, contentType: String, size: Long): String
fun abortMultipart(key: String)
fun publicUrlFor(key: String): String
```

- [ ] **Step 2: S3 구현 (AWS SDK v2 MultipartUpload)**

```kotlin
override fun uploadStream(key: String, stream: InputStream, contentType: String, size: Long): String {
    val put = PutObjectRequest.builder()
        .bucket(bucket).key(key).contentType(contentType).contentLength(size).build()
    s3.putObject(put, RequestBody.fromInputStream(stream, size))
    return "s3://$bucket/$key"
}
```

대용량은 `TransferManager` 또는 수동 multipart — 일단 `putObject`로 시작 후 5GB 초과 시 multipart.

MinIO는 S3 호환이라 동일 코드 재사용.

- [ ] **Step 3: 테스트 (Testcontainers minio)**

간단한 upload → download round-trip 테스트.

- [ ] **Step 4: 커밋**

```bash
./gradlew :onGo-infrastructure:test --tests "*.StorageClient*"
git add backend/onGo-domain/src/main/kotlin/com/ongo/domain/storage/ \
        backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/storage/
git commit -m "feat: StorageClient.uploadStream / abortMultipart / publicUrlFor 추가"
```

---

### Task 23: DriveImportController — 시작/취소/조회

**Files:**
- Create: `backend/onGo-api/src/main/kotlin/com/ongo/api/contentsource/DriveImportController.kt`
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/CancelDriveImportUseCase.kt`
- Test: `backend/onGo-api/src/test/kotlin/com/ongo/api/contentsource/DriveImportControllerTest.kt`

- [ ] **Step 1: CancelUseCase**

```kotlin
@Service
class CancelDriveImportUseCase(
    private val jobRepo: DriveImportJobRepository,
    private val cancellationRegistry: DriveImportCancellationRegistry,
) {
    @Transactional
    fun execute(userId: Long, jobId: Long) {
        val job = jobRepo.findById(jobId) ?: throw IllegalArgumentException("job not found")
        require(job.userId == userId) { throw AccessDeniedException("not owner") }
        if (job.status.isTerminal()) return     // 이미 끝난 job은 no-op
        cancellationRegistry.requestCancel(jobId)
        // 실제 CANCELLED 전환은 워커가 인지 후 처리
    }
}
```

- [ ] **Step 2: Controller**

```kotlin
@RestController
@RequestMapping("/api/v1/drive/imports")
class DriveImportController(
    private val importUseCase: ImportDriveFileUseCase,
    private val cancelUseCase: CancelDriveImportUseCase,
    private val jobRepo: DriveImportJobRepository,
    private val progressTracker: DriveImportProgressTracker,
) {
    data class ImportRequest(val fileId: String, val confirmDuplicate: Boolean = false)

    @PostMapping
    fun start(@AuthenticationPrincipal principal: UserPrincipal, @RequestBody req: ImportRequest):
        ResData<DriveImportStartDto> {
        val r = importUseCase.start(principal.id, req.fileId, req.confirmDuplicate)
        return ResData.ok(DriveImportStartDto(r.jobId, r.videoId, r.status))
    }

    @GetMapping("/{jobId}")
    fun get(@AuthenticationPrincipal principal: UserPrincipal, @PathVariable jobId: Long):
        ResData<DriveImportProgressDto> {
        val job = jobRepo.findById(jobId) ?: throw IllegalArgumentException("not found")
        require(job.userId == principal.id)
        return ResData.ok(DriveImportProgressDto.from(job))
    }

    @GetMapping(path = ["/{jobId}/stream"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(@AuthenticationPrincipal principal: UserPrincipal, @PathVariable jobId: Long): SseEmitter {
        val job = jobRepo.findById(jobId) ?: throw IllegalArgumentException("not found")
        require(job.userId == principal.id)
        return progressTracker.subscribe(jobId)
    }

    @PostMapping("/{jobId}/cancel")
    fun cancel(@AuthenticationPrincipal principal: UserPrincipal, @PathVariable jobId: Long): ResData<Unit> {
        cancelUseCase.execute(principal.id, jobId)
        return ResData.ok(Unit)
    }

    @GetMapping
    fun listActive(@AuthenticationPrincipal principal: UserPrincipal): ResData<List<DriveImportProgressDto>> =
        ResData.ok(jobRepo.listActiveByUser(principal.id).map(DriveImportProgressDto::from))
}
```

- [ ] **Step 3: 테스트 + 커밋**

```bash
./gradlew :onGo-api:test --tests "*.DriveImportControllerTest"
git add backend/onGo-api/src/main/kotlin/com/ongo/api/contentsource/DriveImportController.kt \
        backend/onGo-api/src/main/kotlin/com/ongo/api/contentsource/dto/DriveImportProgressDto.kt \
        backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/CancelDriveImportUseCase.kt \
        backend/onGo-api/src/test/kotlin/com/ongo/api/contentsource/DriveImportControllerTest.kt
git commit -m "feat: DriveImportController — 시작/조회/SSE/취소 + CancelDriveImportUseCase"
```

---

### Phase 4 완료 체크

- [ ] 수동 스모크: Phase 3까지 연결된 드라이브에서 `POST /api/v1/drive/imports` 호출 → Video/Job 레코드 생성 + S3 복사 진행 → 완료 시 `videos.status = DRAFT`, `videos.file_url` 세팅
- [ ] 프론트 없이 `GET /api/v1/drive/imports/{jobId}` polling으로 진행률 증가 확인
- [ ] 취소 API 호출 시 복사 중단 + Video 삭제 + S3 abort 확인

---

## Phase 5 — 진행률 SSE + Recovery + 프론트 + 매뉴얼

### Task 24: DriveImportProgressTracker (SSE Emitter Registry)

**Files:**
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/DriveImportProgressTracker.kt`
- Test: `backend/onGo-application/src/test/kotlin/com/ongo/application/contentsource/DriveImportProgressTrackerTest.kt`

- [ ] **Step 1: 구현**

```kotlin
@Component
class DriveImportProgressTracker {
    private val emitters = ConcurrentHashMap<Long, MutableList<SseEmitter>>()

    fun subscribe(jobId: Long): SseEmitter {
        val emitter = SseEmitter(Duration.ofMinutes(30).toMillis())
        val list = emitters.computeIfAbsent(jobId) { CopyOnWriteArrayList() }
        list.add(emitter)
        emitter.onCompletion { list.remove(emitter) }
        emitter.onTimeout { list.remove(emitter); emitter.complete() }
        emitter.onError { list.remove(emitter) }
        return emitter
    }

    fun publish(jobId: Long, bytesTransferred: Long, total: Long, status: DriveImportStatus, errorMessage: String? = null) {
        val payload = DriveImportProgressDto(
            jobId = jobId, videoId = 0L, status = status,
            bytesTransferred = bytesTransferred, totalBytes = total,
            percent = if (total == 0L) 0 else ((bytesTransferred * 100) / total).toInt().coerceIn(0, 100),
            errorMessage = errorMessage,
        )
        val list = emitters[jobId] ?: return
        list.forEach { e ->
            runCatching { e.send(SseEmitter.event().data(payload)) }
                .onFailure { list.remove(e) }
        }
        if (status.isTerminal()) {
            list.forEach { runCatching { it.complete() } }
            emitters.remove(jobId)
        }
    }
}
```

- [ ] **Step 2: 테스트**

subscribe 시 emitter 추가 / publish 시 이벤트 전달 / terminal status에서 emitter complete + 정리.

- [ ] **Step 3: 커밋**

```bash
./gradlew :onGo-application:test --tests "*.DriveImportProgressTrackerTest"
git add backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/DriveImportProgressTracker.kt \
        backend/onGo-application/src/test/kotlin/com/ongo/application/contentsource/DriveImportProgressTrackerTest.kt
git commit -m "feat: DriveImportProgressTracker — SSE emitter registry"
```

---

### Task 25: DriveImportRecoveryService (부팅 시 stale job 정리)

**Files:**
- Create: `backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/DriveImportRecoveryService.kt`
- Test: `backend/onGo-application/src/test/kotlin/com/ongo/application/contentsource/DriveImportRecoveryServiceTest.kt`

- [ ] **Step 1: 구현**

```kotlin
@Component
class DriveImportRecoveryService(
    private val jobRepo: DriveImportJobRepository,
    private val videoRepo: VideoRepository,
    private val storage: StorageClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun recoverStaleJobs() {
        val stales = jobRepo.listStale(olderThanSeconds = 5 * 60)
        log.info("recovering {} stale drive import jobs", stales.size)
        stales.forEach { job ->
            jobRepo.updateStatus(job.id, DriveImportStatus.FAILED, "서버 재시작으로 중단됨")
            videoRepo.updateStatus(job.videoId, UploadStatus.IMPORT_FAILED)
            job.s3Key?.let { runCatching { storage.abortMultipart(it) } }
        }
    }
}
```

- [ ] **Step 2: 테스트 + 커밋**

```bash
./gradlew :onGo-application:test --tests "*.DriveImportRecoveryServiceTest"
git add backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/DriveImportRecoveryService.kt \
        backend/onGo-application/src/test/kotlin/com/ongo/application/contentsource/DriveImportRecoveryServiceTest.kt
git commit -m "feat: DriveImportRecoveryService — 부팅 시 5분 이상 stale job FAILED 마킹"
```

---

### Task 26: 프론트 — api/contentSource.ts, api/drive.ts

**Files:**
- Create: `frontend/src/api/contentSource.ts`
- Create: `frontend/src/api/drive.ts`
- Modify: `frontend/src/api/index.ts` (export)

- [ ] **Step 1: API 함수**

```typescript
// contentSource.ts
import axios from './axios'
import type { ResData } from '@/types/resData'

export interface ContentSource {
  id: number
  sourceType: 'GOOGLE_DRIVE'
  accountEmail: string
  accountDisplayName: string | null
  status: 'ACTIVE' | 'EXPIRED' | 'REVOKED'
  connectedAt: string
  lastUsedAt: string | null
}

export const contentSourceApi = {
  list: () => axios.get<ResData<ContentSource[]>>('/content-sources').then(r => r.data.data!),
  getDriveAuthUrl: () => axios.get<ResData<{ authUrl: string }>>('/content-sources/google-drive/auth-url').then(r => r.data.data!),
  disconnect: (id: number) => axios.delete<ResData<void>>(`/content-sources/${id}`),
  reconnect: (id: number) => axios.post<ResData<{ authUrl: string }>>(`/content-sources/${id}/reconnect`).then(r => r.data.data!),
}
```

```typescript
// drive.ts
export interface DriveFile {
  id: string; name: string; mimeType: string
  sizeBytes: number | null; durationSeconds: number | null
  thumbnailUrl: string | null; modifiedAt: string
  kind: 'FILE' | 'FOLDER'
}

export interface DriveFileList {
  items: DriveFile[]
  nextPageToken: string | null
  breadcrumbs: { id: string; name: string }[]
}

export interface DriveImportProgress {
  jobId: number; videoId: number
  status: 'PENDING' | 'DOWNLOADING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  bytesTransferred: number; totalBytes: number; percent: number
  errorMessage: string | null
  fileName?: string
}

export const driveApi = {
  listFiles: (params: { folderId?: string; q?: string; pageToken?: string; includeSharedWithMe?: boolean }) =>
    axios.get<ResData<DriveFileList>>('/drive/files', { params }).then(r => r.data.data!),
  startImport: (fileId: string, confirmDuplicate = false) =>
    axios.post<ResData<{ jobId: number; videoId: number; status: string }>>(
      '/drive/imports', { fileId, confirmDuplicate }).then(r => r.data.data!),
  getImportProgress: (jobId: number) =>
    axios.get<ResData<DriveImportProgress>>(`/drive/imports/${jobId}`).then(r => r.data.data!),
  cancelImport: (jobId: number) =>
    axios.post<ResData<void>>(`/drive/imports/${jobId}/cancel`),
  listActiveImports: () =>
    axios.get<ResData<DriveImportProgress[]>>('/drive/imports').then(r => r.data.data!),
}
```

- [ ] **Step 2: 타입 체크 + 커밋**

```bash
cd frontend && npm run build   # vue-tsc 체크
git add frontend/src/api/contentSource.ts frontend/src/api/drive.ts frontend/src/api/index.ts
git commit -m "feat(fe): contentSource/drive API 클라이언트"
```

---

### Task 27: 프론트 — stores/contentSource.ts, stores/driveBrowser.ts, stores/driveImport.ts

**Files:**
- Create: `frontend/src/stores/contentSource.ts`
- Create: `frontend/src/stores/driveBrowser.ts`
- Create: `frontend/src/stores/driveImport.ts`
- Create: `frontend/src/composables/useDriveImportStream.ts`

- [ ] **Step 1: contentSource 스토어**

```typescript
import { defineStore } from 'pinia'
import { contentSourceApi, type ContentSource } from '@/api/contentSource'

export const useContentSourceStore = defineStore('contentSource', {
  state: () => ({
    sources: [] as ContentSource[],
    loading: false,
    error: null as string | null,
  }),
  getters: {
    googleDrive: (s): ContentSource | null => s.sources.find(x => x.sourceType === 'GOOGLE_DRIVE') ?? null,
    isDriveConnected(): boolean { return this.googleDrive?.status === 'ACTIVE' },
    isDriveExpired(): boolean { return this.googleDrive?.status === 'EXPIRED' || this.googleDrive?.status === 'REVOKED' },
  },
  actions: {
    async fetch() {
      this.loading = true
      try { this.sources = await contentSourceApi.list() }
      finally { this.loading = false }
    },
    async startConnect() {
      const { authUrl } = await contentSourceApi.getDriveAuthUrl()
      window.location.href = authUrl
    },
    async disconnect(id: number) {
      await contentSourceApi.disconnect(id)
      await this.fetch()
    },
  },
})
```

- [ ] **Step 2: driveBrowser 스토어**

```typescript
import { defineStore } from 'pinia'
import { driveApi, type DriveFile } from '@/api/drive'

export const useDriveBrowserStore = defineStore('driveBrowser', {
  state: () => ({
    currentFolderId: 'root',
    breadcrumbs: [{ id: 'root', name: '내 드라이브' }] as { id: string; name: string }[],
    items: [] as DriveFile[],
    nextPageToken: null as string | null,
    searchQuery: '',
    loading: false,
    error: null as string | null,
  }),
  actions: {
    async loadFolder(folderId = this.currentFolderId, append = false) {
      this.loading = true; this.error = null
      try {
        const res = await driveApi.listFiles({ folderId, q: this.searchQuery || undefined })
        this.items = append ? [...this.items, ...res.items] : res.items
        this.nextPageToken = res.nextPageToken
        this.breadcrumbs = res.breadcrumbs
        this.currentFolderId = folderId
      } catch (e: any) { this.error = e.response?.data?.error ?? e.message }
      finally { this.loading = false }
    },
    enterFolder(folder: { id: string; name: string }) { this.loadFolder(folder.id) },
    goBack(toIndex: number) { this.loadFolder(this.breadcrumbs[toIndex]?.id ?? 'root') },
    async search(q: string) { this.searchQuery = q; await this.loadFolder(this.currentFolderId) },
    async loadMore() {
      if (!this.nextPageToken) return
      const res = await driveApi.listFiles({
        folderId: this.currentFolderId, q: this.searchQuery || undefined, pageToken: this.nextPageToken,
      })
      this.items = [...this.items, ...res.items]
      this.nextPageToken = res.nextPageToken
    },
  },
})
```

- [ ] **Step 3: driveImport 스토어 + useDriveImportStream**

```typescript
// useDriveImportStream.ts
import type { DriveImportProgress } from '@/api/drive'
import { driveApi } from '@/api/drive'

export function useDriveImportStream(jobId: number) {
  const listeners: { progress: ((p: DriveImportProgress) => void)[]; done: ((p: DriveImportProgress) => void)[] } = {
    progress: [], done: [],
  }
  let es: EventSource | null = null
  let pollTimer: number | null = null

  function emit(p: DriveImportProgress) {
    listeners.progress.forEach(cb => cb(p))
    if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(p.status)) {
      listeners.done.forEach(cb => cb(p))
      cleanup()
    }
  }

  function startSSE() {
    es = new EventSource(`/api/v1/drive/imports/${jobId}/stream`, { withCredentials: true })
    es.onmessage = e => emit(JSON.parse(e.data))
    es.onerror = () => { es?.close(); es = null; startPolling() }
  }

  function startPolling() {
    pollTimer = window.setInterval(async () => {
      try { emit(await driveApi.getImportProgress(jobId)) } catch { /* ignore */ }
    }, 3000)
  }

  function cleanup() {
    es?.close(); es = null
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  }

  startSSE()
  return {
    onProgress: (cb: (p: DriveImportProgress) => void) => listeners.progress.push(cb),
    onDone: (cb: (p: DriveImportProgress) => void) => listeners.done.push(cb),
    cleanup,
  }
}
```

```typescript
// stores/driveImport.ts
import { defineStore } from 'pinia'
import { driveApi, type DriveImportProgress } from '@/api/drive'
import { useDriveImportStream } from '@/composables/useDriveImportStream'
import router from '@/router'

export const useDriveImportStore = defineStore('driveImport', {
  state: () => ({ activeJobs: new Map<number, DriveImportProgress>() }),
  getters: {
    activeList: (s) => Array.from(s.activeJobs.values()),
    hasActive: (s) => s.activeJobs.size > 0,
  },
  actions: {
    async start(fileId: string, fileName: string, confirmDuplicate = false) {
      const r = await driveApi.startImport(fileId, confirmDuplicate)
      this.activeJobs.set(r.jobId, {
        jobId: r.jobId, videoId: r.videoId, status: 'PENDING',
        bytesTransferred: 0, totalBytes: 1, percent: 0, errorMessage: null, fileName,
      })
      this.subscribe(r.jobId, fileName)
      return r
    },
    subscribe(jobId: number, fileName: string) {
      const stream = useDriveImportStream(jobId)
      stream.onProgress(p => this.activeJobs.set(jobId, { ...p, fileName }))
      stream.onDone(p => {
        this.activeJobs.set(jobId, { ...p, fileName })
        if (p.status === 'COMPLETED') router.push(`/videos/${p.videoId}/edit`)
        setTimeout(() => this.activeJobs.delete(jobId), 5000)
      })
    },
    async cancel(jobId: number) { await driveApi.cancelImport(jobId) },
    async restoreOnMount() {
      const jobs = await driveApi.listActiveImports()
      jobs.forEach(j => {
        this.activeJobs.set(j.jobId, j)
        this.subscribe(j.jobId, j.fileName ?? 'file')
      })
    },
  },
})
```

- [ ] **Step 4: 타입 체크 + 커밋**

```bash
cd frontend && npm run build
git add frontend/src/stores/contentSource.ts frontend/src/stores/driveBrowser.ts \
        frontend/src/stores/driveImport.ts frontend/src/composables/useDriveImportStream.ts
git commit -m "feat(fe): contentSource/driveBrowser/driveImport 스토어 + SSE 스트림 composable"
```

---

### Task 28: 프론트 — ContentSourcesView + ConnectDrivePrompt

**Files:**
- Create: `frontend/src/views/settings/ContentSourcesView.vue`
- Create: `frontend/src/components/upload/drive/ConnectDrivePrompt.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/i18n/locales/ko.json`, `en.json` (drive.* 키)

- [ ] **Step 1: ContentSourcesView.vue**

```vue
<script setup lang="ts">
import { onMounted } from 'vue'
import { useContentSourceStore } from '@/stores/contentSource'
import PageHeader from '@/components/common/PageHeader.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import { ref } from 'vue'

const store = useContentSourceStore()
const showDisconnect = ref(false)
const pendingId = ref<number | null>(null)

onMounted(() => store.fetch())

function connect() { store.startConnect() }
function askDisconnect(id: number) { pendingId.value = id; showDisconnect.value = true }
async function confirmDisconnect() {
  if (pendingId.value) await store.disconnect(pendingId.value)
  showDisconnect.value = false
}
</script>

<template>
  <div>
    <PageHeader :title="$t('drive.connect.title')" :description="$t('drive.connect.description')" />
    <div class="space-y-4">
      <div class="card">
        <div class="flex items-center justify-between">
          <div>
            <h3 class="font-semibold">{{ $t('drive.connect.googleDrive') }}</h3>
            <p v-if="store.googleDrive" class="text-sm text-gray-500">
              {{ store.googleDrive.accountEmail }}
              <span v-if="store.googleDrive.status === 'ACTIVE'" class="text-green-600">● 연결됨</span>
              <span v-else class="text-red-600">● {{ store.googleDrive.status }}</span>
            </p>
          </div>
          <div>
            <button v-if="!store.googleDrive" class="btn-primary" @click="connect">
              {{ $t('drive.connect.cta') }}
            </button>
            <button v-else-if="store.isDriveExpired" class="btn-primary" @click="connect">
              {{ $t('drive.connect.reconnect') }}
            </button>
            <button v-else class="btn-danger" @click="askDisconnect(store.googleDrive.id)">
              {{ $t('drive.connect.disconnect') }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <ConfirmModal v-model="showDisconnect"
      :title="$t('drive.connect.disconnectConfirmTitle')"
      :message="$t('drive.connect.disconnectConfirmMessage')"
      @confirm="confirmDisconnect" />
  </div>
</template>
```

- [ ] **Step 2: ConnectDrivePrompt.vue** (EmptyState 패턴)

```vue
<script setup lang="ts">
import { useContentSourceStore } from '@/stores/contentSource'
import EmptyState from '@/components/common/EmptyState.vue'
const store = useContentSourceStore()
</script>

<template>
  <EmptyState
    :title="$t('drive.connect.emptyTitle')"
    :description="$t('drive.connect.emptyDescription')">
    <template #action>
      <button class="btn-primary" @click="store.startConnect()">
        {{ $t('drive.connect.cta') }}
      </button>
    </template>
  </EmptyState>
</template>
```

- [ ] **Step 3: 라우터 등록 + i18n 키 추가**

```typescript
// router/index.ts
{ path: '/settings/content-sources', component: () => import('@/views/settings/ContentSourcesView.vue'),
  meta: { auth: true } },
{ path: '/oauth/google-drive/callback', component: () => import('@/views/DriveCallbackView.vue'),
  meta: { auth: true } },
```

DriveCallbackView.vue: 쿼리스트링의 `connected`/`error` 파라미터를 확인해 토스트 표시 후 `/settings/content-sources` 로 이동.

- [ ] **Step 4: 수동 스모크**

`cd frontend && npm run dev` 실행 → `/settings/content-sources` 진입 → "연결하기" 클릭 → 구글 동의 → 콜백 후 "연결됨" 표시.

- [ ] **Step 5: 커밋**

```bash
cd frontend && npm run build
git add frontend/src/views/settings/ContentSourcesView.vue \
        frontend/src/views/DriveCallbackView.vue \
        frontend/src/components/upload/drive/ConnectDrivePrompt.vue \
        frontend/src/router/index.ts \
        frontend/src/i18n/locales/
git commit -m "feat(fe): ContentSourcesView — 드라이브 연결/해제/재연결 UI + 라우팅 + i18n"
```

---

### Task 29: 프론트 — GoogleDriveBrowser + DriveFileCard + DriveBreadcrumb + DriveSearchBox

**Files:**
- Create: `frontend/src/components/upload/drive/GoogleDriveBrowser.vue`
- Create: `frontend/src/components/upload/drive/DriveFileCard.vue`
- Create: `frontend/src/components/upload/drive/DriveBreadcrumb.vue`
- Create: `frontend/src/components/upload/drive/DriveSearchBox.vue`

- [ ] **Step 1: DriveFileCard.vue**

```vue
<script setup lang="ts">
import type { DriveFile } from '@/api/drive'
import { FolderIcon, VideoCameraIcon } from '@heroicons/vue/24/outline'
defineProps<{ file: DriveFile }>()
defineEmits<{ (e: 'click', file: DriveFile): void }>()

function formatSize(b: number | null): string {
  if (!b) return '-'
  const mb = b / 1024 / 1024; return mb > 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${mb.toFixed(0)} MB`
}
function formatDuration(s: number | null): string {
  if (!s) return '-'
  const m = Math.floor(s / 60); const sec = s % 60
  return `${m}:${sec.toString().padStart(2, '0')}`
}
</script>

<template>
  <button class="card hover:shadow-md transition text-left w-full" @click="$emit('click', file)">
    <div class="aspect-video bg-gray-100 dark:bg-gray-800 rounded flex items-center justify-center mb-2 overflow-hidden">
      <img v-if="file.thumbnailUrl && file.kind === 'FILE'" :src="file.thumbnailUrl" class="w-full h-full object-cover" />
      <FolderIcon v-else-if="file.kind === 'FOLDER'" class="w-12 h-12 text-primary-500" />
      <VideoCameraIcon v-else class="w-12 h-12 text-gray-400" />
    </div>
    <div class="truncate font-medium">{{ file.name }}</div>
    <div v-if="file.kind === 'FILE'" class="text-xs text-gray-500 flex justify-between">
      <span>{{ formatSize(file.sizeBytes) }}</span>
      <span>{{ formatDuration(file.durationSeconds) }}</span>
    </div>
  </button>
</template>
```

- [ ] **Step 2: DriveBreadcrumb.vue**

```vue
<script setup lang="ts">
defineProps<{ items: { id: string; name: string }[] }>()
defineEmits<{ (e: 'navigate', index: number): void }>()
</script>

<template>
  <nav class="flex items-center gap-1 text-sm mb-4 flex-wrap">
    <template v-for="(b, i) in items" :key="b.id">
      <button class="hover:text-primary-600" @click="$emit('navigate', i)">{{ b.name }}</button>
      <span v-if="i < items.length - 1" class="text-gray-400">/</span>
    </template>
  </nav>
</template>
```

- [ ] **Step 3: DriveSearchBox.vue**

```vue
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useDriveBrowserStore } from '@/stores/driveBrowser'
const store = useDriveBrowserStore()
const input = ref('')
let timer: number | null = null
watch(input, v => {
  if (timer) clearTimeout(timer)
  timer = window.setTimeout(() => store.search(v), 400)
})
</script>

<template>
  <input v-model="input" :placeholder="$t('drive.browser.search')" class="input-field w-full mb-4" />
</template>
```

- [ ] **Step 4: GoogleDriveBrowser.vue**

```vue
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useDriveBrowserStore } from '@/stores/driveBrowser'
import { useDriveImportStore } from '@/stores/driveImport'
import DriveFileCard from './DriveFileCard.vue'
import DriveBreadcrumb from './DriveBreadcrumb.vue'
import DriveSearchBox from './DriveSearchBox.vue'
import DriveImportModal from './DriveImportModal.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import type { DriveFile } from '@/api/drive'

const browser = useDriveBrowserStore()
const importStore = useDriveImportStore()
const selected = ref<DriveFile | null>(null)
const showModal = ref(false)

onMounted(() => browser.loadFolder('root'))

function handleCardClick(file: DriveFile) {
  if (file.kind === 'FOLDER') browser.enterFolder({ id: file.id, name: file.name })
  else { selected.value = file; showModal.value = true }
}
async function handleImport(confirmDuplicate: boolean) {
  if (!selected.value) return
  try {
    await importStore.start(selected.value.id, selected.value.name, confirmDuplicate)
    showModal.value = false
  } catch (e: any) {
    // 409 DUPLICATE_DRIVE_IMPORT → 모달에서 재확인 노출
  }
}
</script>

<template>
  <div>
    <DriveSearchBox />
    <DriveBreadcrumb :items="browser.breadcrumbs" @navigate="browser.goBack" />
    <LoadingSpinner v-if="browser.loading && browser.items.length === 0" />
    <div v-else class="grid grid-cols-2 tablet:grid-cols-3 desktop:grid-cols-4 gap-4">
      <DriveFileCard v-for="f in browser.items" :key="f.id" :file="f" @click="handleCardClick" />
    </div>
    <div v-if="browser.nextPageToken" class="text-center mt-4">
      <button class="btn-secondary" @click="browser.loadMore()">{{ $t('drive.browser.loadMore') }}</button>
    </div>

    <DriveImportModal v-model="showModal" :file="selected" @confirm="handleImport" />
  </div>
</template>
```

- [ ] **Step 5: 수동 스모크 + 커밋**

연결된 상태에서 GoogleDriveBrowser가 포함된 페이지 접근 → 파일 목록 / 폴더 진입 / 검색 / 더보기 동작 확인.

```bash
cd frontend && npm run build
git add frontend/src/components/upload/drive/
git commit -m "feat(fe): GoogleDriveBrowser + 하위 컴포넌트 (FileCard/Breadcrumb/SearchBox)"
```

---

### Task 30: 프론트 — DriveImportModal + DriveImportProgressList + UploadView 통합

**Files:**
- Create: `frontend/src/components/upload/drive/DriveImportModal.vue`
- Create: `frontend/src/components/upload/drive/DriveImportProgressList.vue`
- Create: `frontend/src/components/upload/UploadSourceTabs.vue`
- Modify: `frontend/src/views/UploadView.vue`

- [ ] **Step 1: DriveImportModal.vue** (BaseModal 재사용)

```vue
<script setup lang="ts">
import type { DriveFile } from '@/api/drive'
import BaseModal from '@/components/common/BaseModal.vue'
defineProps<{ modelValue: boolean; file: DriveFile | null }>()
defineEmits<{ (e: 'update:modelValue', v: boolean): void; (e: 'confirm', confirmDuplicate: boolean): void }>()
</script>

<template>
  <BaseModal :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)"
    :title="$t('drive.import.confirmTitle', { name: file?.name ?? '' })">
    <p class="text-sm mb-4">{{ $t('drive.import.confirmDescription') }}</p>
    <div class="flex justify-end gap-2">
      <button class="btn-secondary" @click="$emit('update:modelValue', false)">{{ $t('common.cancel') }}</button>
      <button class="btn-primary" @click="$emit('confirm', false)">{{ $t('drive.import.cta') }}</button>
    </div>
  </BaseModal>
</template>
```

- [ ] **Step 2: DriveImportProgressList.vue**

```vue
<script setup lang="ts">
import { onMounted } from 'vue'
import { useDriveImportStore } from '@/stores/driveImport'
const store = useDriveImportStore()
onMounted(() => store.restoreOnMount())
</script>

<template>
  <div v-if="store.hasActive" class="card mb-4">
    <h4 class="font-semibold mb-2">{{ $t('drive.progress.title') }}</h4>
    <div v-for="job in store.activeList" :key="job.jobId" class="mb-2">
      <div class="flex justify-between text-sm mb-1">
        <span class="truncate">📥 {{ job.fileName ?? '' }}</span>
        <span>{{ job.percent }}%</span>
      </div>
      <div class="h-2 bg-gray-200 rounded overflow-hidden">
        <div class="h-full bg-primary-500 transition-all" :style="{ width: `${job.percent}%` }"></div>
      </div>
      <div class="flex justify-between text-xs text-gray-500 mt-1">
        <span>{{ $t(`drive.progress.${job.status.toLowerCase()}`) }}</span>
        <button v-if="['PENDING','DOWNLOADING'].includes(job.status)"
          class="text-red-600" @click="store.cancel(job.jobId)">
          {{ $t('common.cancel') }}
        </button>
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 3: UploadSourceTabs.vue + UploadView.vue 수정**

UploadView.vue 상단에 DriveImportProgressList 추가. 기존 업로드 UI를 OTabs로 감싸 "PC" / "구글 드라이브" 탭 분리. 드라이브 탭은 연결 여부에 따라 `ConnectDrivePrompt` 또는 `GoogleDriveBrowser` 렌더링.

- [ ] **Step 4: 수동 스모크 — 풀 플로우**

1. 로그인 → `/settings/content-sources` → 드라이브 연결
2. `/upload` → 구글 드라이브 탭 → 폴더 진입 → 영상 선택 → 확인 모달 → 임포트 시작
3. 진행률 바 0% → 100% → 완료 후 `/videos/:id/edit` 로 자동 이동
4. 다시 `/upload` 진입 시 진행 중 job 복원 확인 (중간 페이지 이동 시뮬)
5. 취소 버튼 동작 확인

- [ ] **Step 5: 커밋**

```bash
cd frontend && npm run build
git add frontend/src/components/upload/drive/DriveImportModal.vue \
        frontend/src/components/upload/drive/DriveImportProgressList.vue \
        frontend/src/components/upload/UploadSourceTabs.vue \
        frontend/src/views/UploadView.vue
git commit -m "feat(fe): DriveImportModal + ProgressList + UploadSourceTabs + UploadView 통합"
```

---

### Task 31: 매뉴얼 업데이트

**Files:**
- Modify: `frontend/src/views/UserManualView.vue`

- [ ] **Step 1: sectionsKo / sectionsEn 양쪽에 "구글 드라이브에서 가져오기" 섹션 추가**

한국어:
- 제목: "구글 드라이브에서 영상 가져오기"
- 본문: 연결 → 선택 → 복사 → 배포 4단계 설명 + 스크린샷 자리 (나중에)

영어 동일 내용.

- [ ] **Step 2: 수동 확인 (매뉴얼 페이지에서 새 섹션 렌더링 되는지)**

- [ ] **Step 3: 커밋**

```bash
cd frontend && npm run build
git add frontend/src/views/UserManualView.vue
git commit -m "docs(fe): 매뉴얼에 구글 드라이브 임포트 섹션 추가 (한/영)"
```

---

### Task 32: 메트릭 + 로깅 추가

**Files:**
- Modify: `backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/DriveImportEventListener.kt` (Micrometer Counter/Timer 주입)
- Modify: `backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/ImportDriveFileUseCase.kt`

- [ ] **Step 1: Counter/Timer 주입 및 기록**

```kotlin
private val startedCounter = meterRegistry.counter("drive.import.started.count")
private val completedCounter = meterRegistry.counter("drive.import.completed.count", "status", "COMPLETED")
private val failedCounter = meterRegistry.counter("drive.import.completed.count", "status", "FAILED")
private val cancelledCounter = meterRegistry.counter("drive.import.completed.count", "status", "CANCELLED")
private val durationTimer = meterRegistry.timer("drive.import.duration")
private val bytesCounter = meterRegistry.counter("drive.import.bytes.transferred.total")
```

완료/실패/취소 시점에 각각 increment. 소요 시간 타이머. transferred bytes 누적.

- [ ] **Step 2: 커밋**

```bash
git add backend/onGo-application/src/main/kotlin/com/ongo/application/contentsource/
git commit -m "feat: drive import Micrometer 메트릭 + 구조화 로깅"
```

---

### Phase 5 완료 체크

- [ ] End-to-end: 드라이브 연결 → 파일 선택 → 진행률 SSE → 복사 완료 → `/videos/:id/edit` 이동 → 기존 플로우로 4개 플랫폼 배포까지 성공
- [ ] `/actuator/metrics/drive.import.started.count` 값 증가 확인
- [ ] 서버 재시작 시뮬: 복사 중 서버 다운 → 재시작 후 해당 job이 `FAILED`로 전환되고 FE가 표시
- [ ] `./gradlew build` 전체 BUILD SUCCESSFUL
- [ ] `cd frontend && npm run build` 성공

---

## 전체 완료 후 체크리스트

- [ ] 모든 Phase의 완료 체크 항목 통과
- [ ] `./gradlew test` 전체 통과 (신규 테스트 ≥ 40개, 기존 회귀 없음)
- [ ] CLAUDE.md 기준 프론트 컨벤션 준수: PageHeader, OTabs, BaseModal, primary-* 토큰, mx-auto 금지, mobile:/tablet:/desktop: 사용
- [ ] UserManual 한/영 양쪽 동기화
- [ ] Phase별 PR을 순차 머지 (Phase 1 → 5) 또는 `feature/google-drive-import` 단일 브랜치 머지

## 주의사항 (구현자가 꼭 봐야 하는 항목)

1. **jOOQ 재생성**: V42 적용 후 `./gradlew generateJooq` 반드시 실행. 프로젝트 정책이 generated 소스를 git에 커밋하지 않는다면 CI 파이프라인에도 반영되어야 함.
2. **Flux → InputStream 변환**: Task 21의 `asInputStream` 구현은 실수하기 쉬운 부분. `DataBufferUtils.write(flux, PipedOutputStream)` + 별도 스레드에서 소비하는 패턴을 따르고 누수 검증 필수.
3. **S3 MultipartUpload**: 5GB 초과 파일은 반드시 multipart. `TransferManager` 또는 수동 multipart. 취소 시 `abortMultipartUpload` 누락 주의 (비용 유출).
4. **OAuth redirect_uri**: 구글 콘솔에 등록된 URL과 `application.yml`의 `redirect-uri`가 정확히 일치해야 함. 개발/스테이징/프로덕션마다 별도 등록.
5. **id_token 검증**: Google SDK `GoogleIdTokenVerifier`는 내부적으로 구글 JWK를 가져옴. 오프라인/테스트 환경에서는 stub 필요.
6. **프론트엔드 테스트 부재**: 프로젝트가 vitest를 포함하지 않으므로 프론트 태스크의 검증은 `vue-tsc` 타입 체크 + 수동 브라우저 스모크에 의존. 필요 시 별도 Task로 vitest 도입 가능.
7. **플랜 한도 연동**: `PlanLimitChecker`는 기존 `SubscriptionService` 패턴을 따르되, 이 플랜에 맞는 필드가 없다면 `SubscriptionPlan`에 `maxVideoFileSizeBytes` 추가가 선행 필요.
