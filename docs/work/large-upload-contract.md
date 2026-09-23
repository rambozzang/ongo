# 대용량 영상 처리 개선 — 작업 계약서

작성: Claude (2026-09-23). 두 에이전트가 **같은 작업트리**를 쓴다. 아래 소유권을 어기면 서로의 변경을 덮는다.

## 배경
- 업로드: 브라우저 → R2 presigned **PUT 한 번**. 이어받기 없음, 30분 제한, 한도 2GB.
- 게시: `PlatformUploadServiceImpl.uploadFromCloudUrl` 이 **플랫폼마다** R2 원본을 서버 임시파일로 받음(`downloadFileToTemp`).
  2GB × N개 플랫폼만큼 디스크 사용. Instagram/Threads 클라이언트에는 `videoUrl`(Pull) 코드가 있으나 실제로 쓰이는지 불명.

## 역할과 파일 소유권

### Codex — 게시 경로 조사 (**읽기 전용**)
- 소스 코드 **수정 금지**. 커밋·stash·checkout·reset 금지. 빌드/테스트 실행은 허용.
- 쓸 수 있는 파일은 단 하나: `docs/work/publish-path-audit.md`
- 조사 질문:
  1. 플랫폼 11개 각각: 게시 시 R2 원본을 서버로 **내려받는가(Push)**, URL 만 넘기는가(Pull)? 근거 `파일:줄`.
  2. `uploadFromCloudUrl`/`PlatformStreamWriter` 경로와 각 `*Client.getVideoUpload/upload` 경로 중 **실제 운영 경로**는 어느 쪽인가? 호출 체인.
  3. Instagram/Threads 의 `videoUrl` 코드는 실제로 호출되는가, 죽은 코드인가?
  4. 같은 영상을 여러 플랫폼에 게시할 때 임시파일이 몇 개 생기는가, 언제 지워지는가(실패·예외 포함)?
  5. Pull 로 바꿀 수 있는 플랫폼과, 그때 presigned GET URL 만료(현재 기본값?)가 플랫폼 처리 시간보다 짧아 깨질 위험.
  6. Push 전용 플랫폼에서 "원본 한 번만 받아 공유" 로 바꿀 때 건드려야 할 파일 목록과 위험.
- 결론에 **권장 설계 1개**와 변경 대상 파일 목록을 적을 것. 추정과 확인을 구분할 것.

### Claude — R2 멀티파트 업로드 구현
소유 파일(Codex 는 읽기만):
- `frontend/src/composables/usePresignedUpload.ts` 및 테스트
- `backend/**/storage/**`, `backend/**/video/UploadVideoUseCase.kt`, 업로드 관련 Controller/DTO
- 신규 멀티파트 관련 파일 전부

## 공통 금지
- 운영 서버·운영 DB 접근, 실제 외부 API 호출, 서버 재기동 금지
- `설정_작업.txt` 읽기·수정 금지
- 커밋은 Claude 만 한다

---

## 2단계 (2026-09-23 갱신) — Codex 구현 작업

감사 보고서(`publish-path-audit.md`)를 Claude 가 검증했다(주장 3개 코드로 확인).
Instagram/Threads 의 이중 스테이징을 Codex 가 **구현**한다.

### 문제
durable 게시(원본이 이미 R2 에 있음)에서 Instagram/Threads 는
`R2 원본 → 서버 임시파일 → TempFileChunkBuffer → R2 임시객체 재업로드 → Graph API video_url`.
재업로드 객체의 URL(`storageClient.uploadFile` = 7일 presigned GET)은 원본에 발급되는 URL
(`VideoStorageService.getFileUrl` = 7일 presigned GET)과 **같은 종류**다. 원본 URL 을 바로 넘기면 된다.

### 목표
- durable 게시 경로에서 Instagram/Threads 는 **원본 fileUrl 을 그대로** client `uploadVideo` 에 넘긴다.
  로컬 다운로드·청크 버퍼·임시 R2 객체를 만들지 않는다.
- `StreamPublishUseCase` 경로(원본이 R2 에 없음)는 기존 writer 동작을 **그대로 유지**한다.
- 결과 매핑(PublishOutcome, UNCONFIRMED 처리, 에러 메시지)은 기존과 동일해야 한다.

### Codex 소유 파일 (Claude 는 읽기만)
- `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/upload/PlatformUploadServiceImpl.kt`
- `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/platform/InstagramStreamWriter.kt`
- `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/platform/ThreadsStreamWriter.kt`
- `backend/onGo-application/src/main/kotlin/com/ongo/application/video/PlatformUploadCapability.kt` (필요할 때만)
- 위 파일들의 테스트와 신규 테스트

### 완료 조건
- 회귀 테스트: durable 게시에서 Instagram/Threads 가 `downloadFileToTemp`·`storageClient.uploadFile` 을
  **호출하지 않고** 원본 URL 을 넘기는 것을 단언. StreamPublish 경로는 기존대로 writer 를 쓰는 것을 단언.
- 테스트가 실제로 결함을 잡는지 **변이 검증**(되돌려서 실패 확인 후 원복).
- `cd backend && ./gradlew :onGo-infrastructure:test :onGo-application:test` 통과.
- 커밋 금지. 끝나면 터미널에 '구현 완료' + 변경 파일 + 테스트 결과 요약.

### 여전히 Claude 소유 (건드리지 말 것)
StorageClient/S3StorageClient/VideoStorageService/StorageService/UploadVideoUseCase/VideoController/DTO, 프런트 업로드 코드.
