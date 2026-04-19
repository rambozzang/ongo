# 구글 드라이브 영상 임포트 기능 설계서

- 작성일: 2026-04-19
- 작성자: onGo 팀
- 상태: Draft (유저 리뷰 대기)
- Flyway 버전: V42

## 1. 배경과 목표

### 1.1 배경

현재 onGo의 영상 업로드 경로는 **사용자 PC에서 드래그앤드롭 / 파일 피커**가 유일하다. 크리에이터 실사용 환경에서는 원본 영상이 구글 드라이브에 이미 보관된 경우가 많아, 매번 PC로 내려받아 다시 업로드하는 단계가 불편한 마찰로 작용한다.

### 1.2 목표

사용자가 자신의 구글 드라이브를 onGo에 연결하고, 드라이브에 저장된 영상을 브라우저에서 탐색/선택해 **기존 SNS 배포 파이프라인(YouTube/TikTok/Instagram/Naver Clip)으로 업로드**할 수 있도록 한다.

### 1.3 비목표 (Out of Scope)

- 구글 드라이브에 **쓰기/수정/삭제** (최소 권한 원칙)
- Shared Drives(조직 공유 드라이브) 지원 — Phase 2
- 다중 구글 계정 연결 — Phase 2
- Dropbox / OneDrive / iCloud — Phase 2 (다만 데이터 모델은 확장 가능하게 설계)

## 2. 핵심 설계 결정

설계 브레인스토밍에서 내린 5개의 주요 결정과 이유:

| # | 결정 | 이유 |
|---|------|------|
| 1 | **별도 OAuth 연결** (기존 Google 로그인 scope 확장 X) | 로그인 방식(Kakao/Google)과 무관해야 함. 기존 `Channel` 연동 패턴을 재활용. 드라이브 사용자만 연결하므로 권한 요청이 최소화됨 |
| 2 | **드라이브 → S3 복사 → 기존 파이프라인 재사용** | 4개 플랫폼 동시 배포가 핵심 가치. 직접 스트리밍은 드라이브에서 4번 다운로드되어 비효율. S3 복사본은 예약 배포·재시도 로직과 호환성 100% |
| 3 | **자체 파일 브라우저 UI** (Google Picker API X) | 서버 토큰 보관 구조와 자연스럽게 연결. 디자인 시스템(다크모드, i18n) 일관성. 영상 파일만 노출 가능 |
| 4 | **선택 즉시 S3 복사** | 복사 완료 후 Video 레코드는 PC 업로드 영상과 구분 불가능 → 이후 모든 코드(예약, 재시도, 메타 편집, 배포)는 드라이브 출처를 알 필요 없음 |
| 5 | **새 테이블 `user_content_sources`** | "배포 채널(channels)"과 "입력 소스"의 의미 분리. Phase 2에 Dropbox/OneDrive 추가 시 자연스러운 확장점 |

### 2.1 합의된 세부 정책

| 항목 | 정책 |
|------|------|
| 다중 계정 | 유저당 드라이브 계정 1개 (UNIQUE 제약). Phase 2에 제약 제거로 확장 |
| 파일 범위 | My Drive + "나와 공유된 파일". 공유 드라이브는 Phase 2 |
| MIME 필터 | `video/*` 만 노출 |
| 파일 크기 한도 | 기존 플랜별 영상 업로드 한도 적용 (드라이브 경유라고 완화 없음) |
| 스토리지 집계 | S3 복사본 기준으로 기존 스토리지 한도에 동일 카운트 |
| OAuth scope | `https://www.googleapis.com/auth/drive.readonly` (최소 권한) |
| 토큰 만료 | 401/403 감지 시 `status = EXPIRED` 마킹 → FE에서 "다시 연결" 배너 |
| 복사 취소 | 복사 중 취소 가능. S3 부분 업로드 정리 + Video 레코드 삭제 |
| 중복 선택 | 같은 drive_file_id로 복사 중/DRAFT 상태 Video 존재 시 확인 모달 후 새 복사 허용 |
| 폴더 네비게이션 | 루트 → 하위 폴더 진입 + 뒤로가기(breadcrumb) + 검색 + 페이지네이션 |

## 3. 전체 플로우

```
[1] 연결
    FE: "구글 드라이브 연결" 버튼
    → BE: GET /api/v1/content-sources/google-drive/auth-url
    → FE: 구글 OAuth 동의 화면 리다이렉트 (scope: drive.readonly)
    → BE: GET /api/v1/content-sources/google-drive/callback?code=...
    → ConnectGoogleDriveUseCase: 토큰 교환 + user_content_sources INSERT (ACTIVE)
    → FE: 연결 완료 화면

[2] 파일 탐색
    FE: 업로드 화면에서 "구글 드라이브에서 가져오기" 탭 선택
    → BE: GET /api/v1/drive/files?folderId=root&q=&pageToken=
    → ListDriveFilesUseCase: 토큰 갱신 체크 → GoogleDriveClient.listFiles()
    → FE: 폴더 트리 + 영상 카드 그리드 렌더

[3] 선택 → 복사 (핵심)
    FE: 영상 카드 클릭 → 중복 검사 → 확인 모달
    → BE: POST /api/v1/drive/imports (body: {fileId})
    → ImportDriveFileUseCase:
         1) 플랜 한도 체크 (영상 크기 + 잔여 스토리지)
         2) Video 레코드 생성 (status=IMPORTING, source=GOOGLE_DRIVE)
         3) 백그라운드 virtual thread 시작:
              GoogleDriveClient.downloadStream(fileId)
                → S3StorageClient.uploadStream(key, ...)
                → 매 2초마다 drive_import_jobs.bytes_transferred 갱신
              완료: Video.fileUrl 세팅, status=DRAFT, job.status=COMPLETED
              실패: job.status=FAILED, video.status=IMPORT_FAILED
    → FE: SSE 구독 (폴백 polling) 으로 진행률 표시

[4] 배포 (기존 파이프라인 100% 재사용)
    FE: 메타데이터 편집 → "배포" 클릭
    → 기존 PublishVideoUseCase → VideoPublishEvent → 4개 플랫폼
```

**핵심 통찰:** `ImportDriveFileUseCase` 완료 시점에 Video는 PC 업로드와 구분 불가능한 상태가 된다. 이후 모든 코드(예약, 재시도, 메타 편집, 배포)는 드라이브 출처를 알 필요 없음. 이것이 변경 영향을 최소화하는 핵심 아이디어다.

## 4. 모듈 배치

```
onGo-domain/
  contentsource/
    ContentSource.kt                  # Aggregate root
    ContentSourceType.kt              # enum: GOOGLE_DRIVE
    ContentSourceStatus.kt            # enum: ACTIVE, EXPIRED, REVOKED
    ContentSourceRepository.kt        # interface
    DriveFile.kt                      # 도메인 모델
    DriveImportJob.kt
    exception/
      ContentSourceNotConnectedException.kt
      ContentSourceExpiredException.kt
      ContentSourceRevokedException.kt
      DriveFileNotFoundException.kt
      DuplicateDriveImportException.kt
      ConcurrentImportLimitException.kt
      OAuthStateMismatchException.kt

onGo-application/
  contentsource/
    ConnectGoogleDriveUseCase.kt      # OAuth 콜백 → 토큰 저장
    DisconnectContentSourceUseCase.kt
    ListDriveFilesUseCase.kt          # 폴더 탐색 + 검색
    ImportDriveFileUseCase.kt         # 드라이브 → S3 → Video DRAFT
    CancelDriveImportUseCase.kt
    DriveImportEventListener.kt       # @TransactionalEventListener + @Async
    DriveImportRecoveryService.kt     # 부팅 시 stale job 정리
    DriveImportProgressTracker.kt     # SSE emitter registry

onGo-infrastructure/
  external/googledrive/
    GoogleDriveClient.kt              # files.list / files.get / download stream
    GoogleDriveApi.kt                 # @HttpExchange 인터페이스
    GoogleDriveOAuthClient.kt         # 토큰 발급 / refresh / revoke
    GoogleDriveProperties.kt          # client_id/secret/redirect_uri
    ContentSourceTokenManager.kt      # per-source 락으로 refresh 경합 제어
  persistence/contentsource/
    ContentSourceRepositoryImpl.kt    # jOOQ
    DriveImportJobRepositoryImpl.kt   # jOOQ

onGo-api/
  contentsource/
    ContentSourceController.kt        # 연결/해제/상태
    DriveFileController.kt            # 목록/검색
    DriveImportController.kt          # 임포트 시작/진행률/취소
    dto/
      ContentSourceDto.kt
      DriveFileDto.kt
      DriveImportProgressDto.kt
```

## 5. 데이터 모델 (Flyway V42)

### 5.1 새 ENUM

```sql
CREATE TYPE content_source_type AS ENUM ('GOOGLE_DRIVE');
CREATE TYPE content_source_status AS ENUM ('ACTIVE', 'EXPIRED', 'REVOKED');
CREATE TYPE video_source AS ENUM ('UPLOAD_PC', 'GOOGLE_DRIVE');
CREATE TYPE drive_import_status AS ENUM ('PENDING', 'DOWNLOADING', 'COMPLETED', 'FAILED', 'CANCELLED');
```

### 5.2 `user_content_sources`

```sql
CREATE TABLE IF NOT EXISTS user_content_sources (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_type             content_source_type NOT NULL,
    external_account_id     VARCHAR(255) NOT NULL,   -- 구글 sub claim
    account_email           VARCHAR(255) NOT NULL,
    account_display_name    VARCHAR(255),
    access_token            TEXT NOT NULL,            -- AES-256 암호화
    refresh_token           TEXT,                     -- AES-256 암호화
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
```

### 5.3 `videos` 컬럼 추가

```sql
ALTER TABLE videos
    ADD COLUMN source              video_source NOT NULL DEFAULT 'UPLOAD_PC',
    ADD COLUMN source_reference    JSONB;

COMMENT ON COLUMN videos.source IS '영상 원본 출처 (PC 업로드 / 구글 드라이브)';
COMMENT ON COLUMN videos.source_reference IS '소스별 원본 참조 JSON: {fileId, fileName, mimeType, sizeBytes, md5Checksum, modifiedAt} 등';

CREATE INDEX idx_videos_user_source ON videos(user_id, source);
```

`source_reference`를 JSONB로 둔 이유: 드라이브는 `{fileId, ...}`, Dropbox는 `{path, rev, ...}` 처럼 소스별 필드가 달라 별도 컬럼화는 과하다.

### 5.4 `drive_import_jobs`

```sql
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

**왜 별도 테이블?** PC 업로드 영상에 의미 없는 NULL 컬럼이 생기지 않도록. Import는 일회성 작업이라 수명도 다름(완료 후 30일 보관 후 정리).

### 5.5 롤백 SQL (문서 보관용, Flyway undo 사용 안 함)

```sql
DROP TABLE drive_import_jobs;
ALTER TABLE videos DROP COLUMN source_reference, DROP COLUMN source;
DROP TABLE user_content_sources;
DROP TYPE drive_import_status;
DROP TYPE video_source;
DROP TYPE content_source_status;
DROP TYPE content_source_type;
```

## 6. 백엔드 API

모든 응답은 `ResData<T>` 래퍼. 인증은 기존 JWT 필터 적용.

### 6.1 엔드포인트 명세

#### ContentSourceController

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/v1/content-sources` | 내가 연결한 모든 소스 목록 |
| `GET` | `/api/v1/content-sources/google-drive/auth-url` | OAuth 동의 URL 생성 (state 포함) |
| `GET` | `/api/v1/content-sources/google-drive/callback` | OAuth 콜백 (code → 토큰 교환) |
| `DELETE` | `/api/v1/content-sources/{id}` | 연결 해제 (구글 revoke + row 삭제) |
| `POST` | `/api/v1/content-sources/{id}/reconnect` | 만료/회수된 연결 재승인 |

#### DriveFileController

| Method | Path | 설명 |
|--------|------|------|
| `GET` | `/api/v1/drive/files` | 파일/폴더 목록 조회 |
| `GET` | `/api/v1/drive/files/{fileId}` | 단일 파일 메타 (확인 모달용) |

**쿼리 파라미터:** `folderId`(기본 `root`), `q`(검색어), `includeSharedWithMe`(기본 false), `pageToken`, `pageSize`(기본 50, 최대 100).

**내부 쿼리 조립:**
```
mimeType contains 'video/' AND trashed = false
  AND (q ? name contains '{q}' : '{folderId}' in parents)
supportsAllDrives = false (Phase 1)
fields = files(id,name,mimeType,size,thumbnailLink,modifiedTime,iconLink,videoMediaMetadata)
```

#### DriveImportController

| Method | Path | 설명 |
|--------|------|------|
| `POST` | `/api/v1/drive/imports` | 임포트 시작 (body: `{fileId, confirmDuplicate?}`) |
| `GET` | `/api/v1/drive/imports/{jobId}` | 진행률 조회 (polling) |
| `GET` | `/api/v1/drive/imports/{jobId}/stream` | SSE 진행률 스트림 |
| `POST` | `/api/v1/drive/imports/{jobId}/cancel` | 진행 중 취소 |
| `GET` | `/api/v1/drive/imports?status=DOWNLOADING` | 내 진행 중 임포트 목록 (복원용) |

### 6.2 DTO

```kotlin
data class ContentSourceDto(
    val id: Long,
    val sourceType: ContentSourceType,
    val accountEmail: String,
    val accountDisplayName: String?,
    val status: ContentSourceStatus,
    val connectedAt: Instant,
    val lastUsedAt: Instant?,
)

data class DriveFileDto(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val durationSeconds: Long?,
    val thumbnailUrl: String?,
    val modifiedAt: Instant,
    val kind: DriveItemKind,  // FILE | FOLDER
)

data class DriveFileListDto(
    val items: List<DriveFileDto>,
    val nextPageToken: String?,
    val breadcrumbs: List<DriveFolderDto>,
)

data class DriveImportStartDto(
    val jobId: Long,
    val videoId: Long,
    val status: DriveImportStatus,
)

data class DriveImportProgressDto(
    val jobId: Long,
    val videoId: Long,
    val status: DriveImportStatus,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val percent: Int,
    val errorMessage: String?,
)
```

### 6.3 UseCase 흐름

#### ConnectGoogleDriveUseCase

1. state 검증 (HMAC 서명, CSRF + userId 바인딩, 5분 TTL)
2. `GoogleDriveOAuthClient.exchangeCode(code)` → AccessToken, RefreshToken, expiresAt, idToken
3. idToken.sub = external_account_id 추출
4. userinfo 엔드포인트로 email, name 조회
5. 기존 row 있으면 UPDATE (토큰 갱신 + status=ACTIVE), 없으면 INSERT
6. `TokenEncryptor.encrypt()` 로 토큰 암호화 후 저장

#### ListDriveFilesUseCase

1. 활성 ContentSource 조회 (없으면 `ContentSourceNotConnectedException`)
2. `ensureValidToken()`: token_expires_at < now + 60s 이면 refresh
3. `GoogleDriveClient.listFiles(query, pageToken)`
4. breadcrumbs 계산: folderId != root 이면 files.get(folderId, fields=parents) 반복
5. 마지막에 last_used_at 갱신

**토큰 갱신 실패 처리:** 401/403 → `ContentSourceStatus = EXPIRED` 마킹 + `ContentSourceExpiredException` → 전역 핸들러가 `ResData.error("CONTENT_SOURCE_EXPIRED")` 반환.

#### ImportDriveFileUseCase (핵심)

**동기 파트 (HTTP 요청 컨텍스트):**
1. 활성 ContentSource 검증 + 토큰 갱신
2. `GoogleDriveClient.getFileMeta(fileId)` — name, mimeType, sizeBytes 확인
3. 플랜 한도 체크: 파일 크기 ≤ `plan.maxVideoFileSize`, 유저 스토리지 잔여량 체크. 초과 시 `PlanLimitExceededException`
4. 중복 체크: `SELECT ... FOR UPDATE` 로 같은 drive_file_id 이면서 status IN (PENDING, DOWNLOADING, COMPLETED) 인 job 조회. 있고 `confirmDuplicate != true`면 `DuplicateDriveImportException`
5. 동시 임포트 제한: 유저당 max 2 → 초과 시 `ConcurrentImportLimitException`
6. INSERT videos(status=IMPORTING, source=GOOGLE_DRIVE, source_reference={fileId,...})
7. INSERT drive_import_jobs(status=PENDING)
8. `DriveImportStartDto` 즉시 반환
9. `ApplicationEventPublisher.publishEvent(DriveImportRequestedEvent(jobId))`

**비동기 파트 (`@TransactionalEventListener` + `@Async` virtual thread pool):**
```
DriveImportEventListener.handle(event):
  jobs UPDATE status=DOWNLOADING, started_at=NOW
  try:
    inputStream = GoogleDriveClient.downloadStream(fileId)  // alt=media
    S3StorageClient.uploadStream(
      key = "users/{userId}/videos/{videoId}/{timestamp}.{ext}",
      stream = progressWrappedStream(inputStream, onBytes = { n ->
        bytesTransferred += n
        매 2초마다 jobs UPDATE bytes_transferred (throttle)
        SSE emitter.send(progress)
      }),
      contentType = mimeType,
      size = sizeBytes
    )
    videos UPDATE file_url=s3Url, file_size_bytes, duration_seconds, status=DRAFT
    jobs UPDATE status=COMPLETED, completed_at=NOW
  catch CancellationException:
    jobs UPDATE status=CANCELLED
    S3 abortMultipartUpload
    videos DELETE
  catch Exception:
    jobs UPDATE status=FAILED, error_message, retry_count+=1
    videos UPDATE status=IMPORT_FAILED
```

#### 진행률 전달

- **SSE 기본, polling 폴백:** FE는 먼저 `/stream` 시도 → 프록시 차단 시 3초 polling
- **인메모리 SseEmitterRegistry:** `jobId → SseEmitter`. 워커가 업데이트 시 emit
- **재시작 시 복원:** DB 기반 진행률 덕에 SSE 연결 끊겨도 polling으로 이어붙임

### 6.4 GoogleDriveClient / GoogleDriveApi 설계

`@HttpExchange` 인터페이스 (기존 `YouTubeApi` 패턴):

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
    ): DriveFilesListResponse

    @GetExchange("/files/{fileId}")
    fun getFile(
        @PathVariable fileId: String,
        @RequestHeader("Authorization") auth: String,
        @RequestParam fields: String,
    ): DriveFileResponse
}
```

스트리밍 다운로드는 `WebClient` 직접 사용 (청크 Flux 반환):

```kotlin
class GoogleDriveClient(private val webClient: WebClient, ...) {
    fun downloadStream(fileId: String, token: String): Flux<DataBuffer> =
        webClient.get()
            .uri("https://www.googleapis.com/drive/v3/files/{id}?alt=media", fileId)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .retrieve()
            .bodyToFlux(DataBuffer::class.java)
}
```

### 6.5 설정 (`application.yml`)

```yaml
ongo:
  content-source:
    google-drive:
      client-id: ${GOOGLE_DRIVE_CLIENT_ID}
      client-secret: ${GOOGLE_DRIVE_CLIENT_SECRET}
      redirect-uri: ${APP_BASE_URL}/api/v1/content-sources/google-drive/callback
      scopes:
        - https://www.googleapis.com/auth/drive.readonly
      import:
        download-buffer-size: 8388608          # 8MB
        progress-update-interval-ms: 2000
        max-concurrent-per-user: 2
        timeout-minutes: 60
```

## 7. 프론트엔드

### 7.1 신규/수정 파일 목록

```
frontend/src/
  views/
    settings/ContentSourcesView.vue              # 신규
    UploadView.vue                               # 수정 (탭 추가)
    DriveCallbackView.vue                        # 신규

  components/upload/
    UploadSourceTabs.vue                         # 신규
    drive/
      GoogleDriveBrowser.vue                     # 신규
      DriveBreadcrumb.vue                        # 신규
      DriveFileCard.vue                          # 신규
      DriveSearchBox.vue                         # 신규
      DriveImportModal.vue                       # 신규
      DriveImportProgressList.vue                # 신규
      ConnectDrivePrompt.vue                     # 신규

  stores/
    contentSource.ts                             # 신규
    driveBrowser.ts                              # 신규
    driveImport.ts                               # 신규

  composables/
    useDriveImportStream.ts                      # 신규 (SSE/polling 폴백)

  api/
    contentSource.ts                             # 신규
    drive.ts                                     # 신규

  router/index.ts                                # 수정
  i18n/locales/{ko,en}.json                      # 수정
  views/UserManualView.vue                       # 수정 (매뉴얼 추가)
```

### 7.2 화면 흐름

**A. `/settings/content-sources`**
- PageHeader: "외부 저장소 연결"
- ContentSource 카드 리스트: 미연결/연결됨/만료됨 3가지 상태
- Phase 2 자리: Dropbox, OneDrive

**B. UploadView 내부**
- DriveImportProgressList: 진행 중인 import를 상단 고정 (페이지 이동/새로고침 후에도 복원)
- UploadSourceTabs (OTabs 재사용): PC / 구글 드라이브
- 드라이브 탭: 미연결 → ConnectDrivePrompt, 연결됨 → GoogleDriveBrowser

**C. GoogleDriveBrowser**
- DriveSearchBox, DriveBreadcrumb, 파일/폴더 카드 그리드 (4열, 모바일 2열)
- 폴더 클릭 → `driveBrowser.enterFolder()`, 영상 클릭 → DriveImportModal

**D. DriveImportModal** (BaseModal 재사용)
- 파일명, 크기, 예상 시간 안내
- 중복 감지 시 경고 + "새로 가져오기" 확인
- 한도 초과 시 플랜 업그레이드 링크

### 7.3 Pinia 스토어 설계

`contentSource.ts`: sources 목록, googleDrive getter, startConnect (풀리다이렉트), disconnect

`driveBrowser.ts`: currentFolderId, breadcrumbs, items, nextPageToken, searchQuery, loadFolder/enterFolder/goBack/search/loadMore

`driveImport.ts`:
- `activeJobs: Map<number, DriveImportProgress>`
- `start(fileId, confirmDuplicate)`: API 호출 → subscribe → 성공 시 `/videos/{videoId}/edit`로 이동
- `subscribe(jobId)`: `useDriveImportStream(jobId)` 구독
- `cancel(jobId)`: POST /cancel
- `restoreOnMount()`: 페이지 진입 시 진행 중 job 재구독

### 7.4 SSE/polling 폴백

```typescript
export function useDriveImportStream(jobId: number) {
  // SSE 시도 → onerror 발생 시 3초 polling 폴백
  // status in COMPLETED/FAILED/CANCELLED 도달 시 cleanup
}
```

### 7.5 라우팅 추가

```typescript
{ path: '/settings/content-sources', component: ContentSourcesView, meta: { auth: true } },
{ path: '/oauth/google-drive/callback', component: DriveCallbackView, meta: { auth: true } },
```

### 7.6 i18n 키 구조

```json
{
  "drive": {
    "connect": { "title": "...", "cta": "..." },
    "browser": { "search": "...", "myDrive": "...", "sharedWithMe": "...", "loadMore": "..." },
    "import": { "confirmTitle": "...", "duplicate": "...", "limitExceeded": "..." },
    "progress": { "downloading": "...", "completed": "...", "failed": "...", "cancelled": "..." }
  }
}
```

ko/en 동일 키. UserManualView.vue 한·영 양쪽에 "구글 드라이브에서 가져오기" 섹션 추가.

### 7.7 디자인 시스템 체크리스트

- [x] primary-* 토큰만 사용 (indigo 금지)
- [x] `.card`, `btn-primary/btn-secondary/btn-danger`, `input-field`
- [x] BaseModal (인라인 모달 금지)
- [x] OTabs, EmptyState, PageHeader 재사용
- [x] `mx-auto` 사용 금지 (왼쪽 정렬)
- [x] mobile:/tablet:/desktop: 브레이크포인트

## 8. 에러 처리 / 엣지 케이스

### 8.1 예외 → HTTP 매핑

| 예외 | HTTP | error code |
|------|------|-----------|
| `ContentSourceNotConnectedException` | 404 | `CONTENT_SOURCE_NOT_CONNECTED` |
| `ContentSourceExpiredException` | 401 | `CONTENT_SOURCE_EXPIRED` |
| `ContentSourceRevokedException` | 401 | `CONTENT_SOURCE_REVOKED` |
| `DriveFileNotFoundException` | 404 | `DRIVE_FILE_NOT_FOUND` |
| `DriveFilePermissionDeniedException` | 403 | `DRIVE_FILE_FORBIDDEN` |
| `DuplicateDriveImportException` | 409 | `DUPLICATE_DRIVE_IMPORT` |
| `PlanLimitExceededException` (재사용) | 402 | `PLAN_LIMIT_EXCEEDED` |
| `ConcurrentImportLimitException` | 429 | `CONCURRENT_IMPORT_LIMIT` |
| `OAuthStateMismatchException` | 400 | `OAUTH_STATE_MISMATCH` |
| `DriveDownloadFailedException` | 500 | `DRIVE_DOWNLOAD_FAILED` |

### 8.2 엣지 케이스별 처리

| # | 상황 | 처리 |
|---|------|------|
| A | 토큰 갱신 경합 (동시 요청) | `ContentSourceTokenManager`가 Caffeine 기반 per-source-id ReentrantLock 보유. 락 안에서 재검사 후 refresh |
| B | 임포트 중 사용자가 토큰 회수 | 다음 청크 다운로드 401 → 1회 refresh 재시도 → 또 실패 시 `REVOKED` 마킹, job FAILED |
| C | 임포트 중 드라이브 파일 삭제/이동 | alt=media 다운로드 404 → 즉시 FAILED, retry 안 함 |
| D | S3 업로드 중 서버 재시작 | 부팅 시 `DriveImportRecoveryService`: `PENDING/DOWNLOADING && updated_at < NOW()-5min` → S3 abort + FAILED 마킹 (자동 재시작 X) |
| E | 같은 파일 동시 두 번 클릭 | DB 트랜잭션 + `SELECT ... FOR UPDATE` 로 직렬화. 둘째는 `DuplicateDriveImportException` |
| F | 거대 파일 (수십 GB) | 플랜 한도로 컷. 한도 내라도 `Flux<DataBuffer>` 청크 + S3 MultipartUpload로 메모리 안전 |
| G | 확장자 ↔ MIME 불일치 | 드라이브 `mimeType` 우선. 알 수 없는 video MIME은 mimeType 역추출 |
| H | SSE 연결 누수 | emitter timeout 30분, onCompletion/onTimeout/onError에서 registry 제거 |
| I | Free 플랜 유저 드라이브 연결 | 연결 허용. 임포트 시 플랜 한도 체크 동일 적용. UI에 "이 플랜 최대 파일 크기" 안내 |
| J | OAuth 동의 거부 (`?error=access_denied`) | `DriveCallbackView`가 error 파라미터 감지 → 토스트 → 설정 페이지로 복귀. 백엔드 호출 안 함 |
| K | 다른 구글 계정으로 재연결 | UPDATE로 덮어씀. FE에서 사전에 "기존 연결을 X 계정으로 교체?" 확인 모달 |

## 9. 보안

- OAuth state HMAC 서명 검증 (CSRF 방지)
- redirect_uri 화이트리스트 (프로퍼티로만 관리)
- 모든 엔드포인트에서 userId 일치 검증 (IDOR 방지)
- 토큰은 AES-256 암호화 후 DB 저장 (기존 `TokenEncryptor` 재사용)
- 응답 DTO에 토큰/refresh token 노출 금지
- OAuth scope 최소 권한 (`drive.readonly`)
- 로그에 토큰·파일 내용 출력 금지 (fileId, userId, bytesTransferred만)
- Disconnect 시 구글 revoke 엔드포인트 호출
- Rate limiting: 유저당 분당 60회 (`drive.*`)
- 동시 임포트 제한: 유저당 2개

## 10. 관측성

### 로그
- 임포트 시작/완료/실패 시 `userId`, `jobId`, `fileSizeBytes`, `durationMs` 구조화 로그

### 메트릭 (Micrometer)
- `drive.import.started.count`
- `drive.import.completed.count` (status 태그)
- `drive.import.duration` (히스토그램)
- `drive.import.bytes.transferred.total`
- `drive.api.request.count` (endpoint 태그)

### 알림
- 임포트 실패율 5분 평균 > 20% 시 슬랙 알림

## 11. 테스트 전략

### 단위 테스트 (Kotest, ≥80% 커버리지 목표)

| 클래스 | 핵심 테스트 |
|--------|------------|
| `ContentSourceTokenManager` | 만료 토큰 자동 refresh / 동시 refresh 직렬화 / refresh 실패 시 EXPIRED |
| `ImportDriveFileUseCase` | 플랜 한도 초과 거부 / 중복 검출 / 비활성 소스 거부 / 동시 클릭 직렬화 |
| `GoogleDriveOAuthClient` | code → token 교환 / refresh / 401 매핑 |
| `DriveImportEventListener` | 정상 status 전이 / 다운로드 실패 시 FAILED / 취소 시 S3 abort |
| `DriveImportRecoveryService` | 5분 이상 stale job → FAILED 전환 |

### 통합 테스트 (`@SpringBootTest` + Testcontainers PostgreSQL)
- OAuth 콜백 전체 플로우 (구글 API는 WireMock stub)
- ContentSource jOOQ repository CRUD + 암호화 라운드트립
- Flyway V42 마이그레이션 적용 검증
- DriveImportJob 동시성: 같은 fileId 두 요청 → 한쪽만 성공
- SSE 엔드포인트 연결 → 진행률 emit → 완료 수신

### 컨트랙트 테스트 (구글 API)
- WireMock으로 `files.list`, `files.get`, `files.get?alt=media`, `oauth2/token`, `oauth2/revoke` 고정 응답
- 401/403/429/5xx 에러 케이스 별도 매핑

### E2E (Playwright, 선택)
- "드라이브 연결 → 파일 선택 → 임포트 진행률 → DRAFT 도착 → 메타 편집" 플로우 1개
- OAuth는 mock 서버로 우회

## 12. 성능 / 비용 추정

- 평균 영상 500MB, 대역폭 100Mbps 가정: 다운로드 ≈ 40초, S3 동시 업로드 → 총 50초 내외
- S3 비용: 기존 PC 업로드와 동일 (드라이브 경유로 추가 비용 없음)
- 메모리: 청크 8MB × 동시 임포트 2 = 유저당 16MB 작업 메모리

## 13. 구현 PR 분할 (권장)

1. **PR1**: V42 마이그레이션 + 도메인 + jOOQ 재생성
2. **PR2**: `GoogleDriveOAuthClient` + `ContentSourceController` + 설정 화면
3. **PR3**: `GoogleDriveClient` + 파일 목록 API + 브라우저 UI
4. **PR4**: `ImportDriveFileUseCase` + 워커 + 진행률 SSE/polling + UploadView 통합
5. **PR5**: Recovery + 메트릭 + 매뉴얼 업데이트

## 14. 일정 추정 (참고용)

| 단계 | 예상 |
|------|------|
| Flyway V42 + 도메인/jOOQ 생성 | 0.5일 |
| OAuth + ContentSource CRUD | 1일 |
| GoogleDriveClient + 파일 목록 API | 1일 |
| ImportUseCase + 워커 + Recovery | 1.5일 |
| SSE/polling + 진행률 | 0.5일 |
| 프론트 컴포넌트 + 스토어 | 2일 |
| 통합/E2E 테스트 | 1일 |
| 매뉴얼 업데이트 + i18n | 0.5일 |
| **합계** | **약 8일** |

## 15. 열린 이슈 / Phase 2 후보

- 공유 드라이브(Shared Drives) 지원
- 다중 구글 계정 연결
- Dropbox / OneDrive / iCloud 연동
- 드라이브 파일 변경 감지 → 자동 재임포트
- 임포트 완료된 드라이브 원본에 onGo 처리 완료 라벨 붙이기 (쓰기 권한 필요, 동의 필수)
