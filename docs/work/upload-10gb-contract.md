# 영상 업로드 10GB — 작업 계약 (Claude · Codex)

작성 2026-09-23. 대표 결정: **영상 업로드 한도를 10GB 로 올린다.** 같은 작업트리에서 동시에 일한다.
자기 소유가 아닌 파일은 고치지 않는다. 커밋은 Claude 만 한다.
Codex 는 끝나면 `docs/work/upload-10gb-codex-report.md` 에 보고한다.

## 공통 금지 (revenue-path-contract.md 와 같다)
`설정_작업.txt` 금지 · 운영 DB/설정/비밀값 금지 · 실제 외부 API 호출 금지 · 가짜 성공 금지 ·
기존 테스트 완화·삭제 금지 · git checkout/reset/revert/stash 금지 · Gradle 동시 실행 오류는 재실행.

## 용어 — 두 한도를 섞지 않는다
- **직접 업로드 한도 10GiB** — 브라우저가 R2 로 직접 올리는 경로(presigned PUT·멀티파트), URL 가져오기,
  쇼츠 원본. 상수: `FileValidationUtil.VIDEO_DIRECT_UPLOAD_MAX_BYTES = 10L * 1024 * 1024 * 1024` (Claude 가 만든다)
- **서버 경유 한도 2GiB (유지)** — 요청 본문이 우리 서버를 지나는 경로(즉시 스트림 게시 MultipartFile,
  에셋 업로드, 공개 API 미디어). nginx·서블릿 한도와 묶여 있어 올리지 않는다.
  상수: `FileValidationUtil.SERVER_PROXIED_MAX_BYTES = 2GiB` (Claude)
- **플랫폼 한도** — 각 플랫폼이 받는 최대 크기. 우리 업로드 한도와 별개다. 10GB 원본이라도 플랫폼이
  4GB 까지만 받으면 게시 전에 그 이유로 막아야 한다.

## Claude 소유
- `onGo-common/.../FileValidationUtil.kt` (상수 두 개, 기본값)
- `UploadVideoUseCase.kt`, `StreamPublishUseCase.kt` 의 **파일 크기 검증 부분만**
- 방치 업로드 정리: `StaleUploadCleanupUseCase.kt`, `VideoRepository.findStaleUploading`, `VideoJooqRepository`
  — 생성 시각이 아니라 **마지막 활동 시각** 기준으로 (10GB 는 느린 회선에서 3시간을 넘는다)
- URL 가져오기: `VideoDownloadUseCase.kt`, `YtDlpVideoDownloader.kt`
- 프런트: `stores/uploadQueue.ts`, 업로드 화면 문구, 매뉴얼(`manualSections.ts`)
- `deploy/*` (필요 시)

## Codex 소유 (C5 — 저장소 이후 경로)
1. `PlatformUploadCapability.kt` — `maxFileSizeBytes` 를 **각 플랫폼 공식 문서의 실제 한도**로. 외부 호출
   금지이므로 알고 있는 문서 값을 쓰고, 출처(문서명)와 확인 시점을 주석으로 남긴다. 확신이 없으면
   보수적으로 두고 보고서에 "확인 필요" 로 적는다. 우리 직접 업로드 한도(10GiB)를 넘는 값은 10GiB 로 자른다.
2. `YouTubeStreamWriter.kt`, `TikTokStreamWriter.kt` — `MAX_MEMORY_FILE_SIZE = 2GB` 가 남아 있다. 버퍼는
   이미 임시 파일(`TempFileChunkBuffer`)인지 확인하고, 맞다면 메모리 근거가 사라진 2GB 상한을 걷어낸다.
   대신 **임시 디스크 여유 공간 검사**(파일 크기 + 여유분이 없으면 전송 전에 명확한 오류)를 둔다.
   메모리에 파일 전체를 올리는 경로가 남아 있으면 그것부터 보고한다.
3. 쇼츠 원본 한도 `shorts.transcribe.max-source-bytes`(application.yml·`ShortsPipelineUseCase`) 를 10GiB 로.
   음성 추출(`FfmpegTranscriptionAudioAdapter`)·렌더(`FfmpegVideoRenderer`)가 10GB 원본을 **통째로 내려받거나
   메모리에 올리지 않는지** 확인하고, 내려받는다면 디스크 여유 검사를 둔다.
4. 각 변경에 테스트. 메모리·디스크 검사는 단위 테스트로, 기존 테스트 완화 금지.

## 인터페이스
- Codex 는 `FileValidationUtil.VIDEO_DIRECT_UPLOAD_MAX_BYTES` 를 import 해서 쓴다(Claude 가 먼저 만든다).
- 마이그레이션은 없다고 본다. 필요하면 **V118** 한 개만, 보고서에 이유를 쓴다.
