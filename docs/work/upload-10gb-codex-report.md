# C5 Codex 구현 보고서

## 변경 파일

- `backend/onGo-application/src/main/kotlin/com/ongo/application/video/PlatformUploadCapability.kt`
- `backend/onGo-application/src/main/kotlin/com/ongo/application/ugc/shorts/ShortsPipelineUseCase.kt`
- `backend/onGo-api/src/main/resources/application.yml`
- `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/platform/TempFileChunkBuffer.kt`
- `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/platform/YouTubeStreamWriter.kt`
- `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/platform/TikTokStreamWriter.kt`
- `backend/onGo-application/src/test/kotlin/com/ongo/application/video/PlatformUploadCapabilityTest.kt`
- `backend/onGo-application/src/test/kotlin/com/ongo/application/ugc/shorts/ShortsPipelineUseCaseTest.kt`
- `backend/onGo-infrastructure/src/test/kotlin/com/ongo/infrastructure/external/platform/TempDiskSpaceGuardTest.kt`
- `backend/onGo-infrastructure/src/test/kotlin/com/ongo/infrastructure/external/platform/YouTubeStreamWriterHttpContractTest.kt`
- `backend/onGo-infrastructure/src/test/kotlin/com/ongo/infrastructure/external/platform/TikTokStreamWriterTest.kt`
- `docs/work/upload-10gb-codex-report.md`

동시 작업 중인 다른 업로드 경로 파일은 수정하지 않았습니다. 마이그레이션은 추가하지 않았고 커밋도 하지 않았습니다.

## 구현 및 근거

### 1. 플랫폼별 업로드 한도

`PlatformUploadCapability`의 활성 플랫폼 한도를 각 제공자의 업로드 문서에서 기억하는 값으로 갱신하고, 공통 `FileValidationUtil.VIDEO_DIRECT_UPLOAD_MAX_BYTES`(10GiB)를 상한으로 적용했습니다. 따라서 제공자가 더 큰 파일을 허용하더라도 이 직접 업로드 경로가 10GiB를 넘게 허용하지 않습니다. YouTube는 256GB, TikTok은 4GB, Instagram Reels는 1GB, Facebook은 10GB, Pinterest는 2GB, LinkedIn은 5GB, Tumblr는 100MB를 사용했습니다. Threads는 흐름/한도 불확실성을 고려해 500MB로 보수 설정했습니다. WordPress 100MB, Vimeo 500MB, Dailymotion 2GiB와 Naver Clip 2GiB는 호스팅·요금제·공개 API 제약 때문에 보수적인 값 또는 비공개 경로 표기이며 보장 한도로 간주하면 안 됩니다. Twitter는 비활성 경로입니다.

한도 설명에는 2026-09-23 기준 기억한 문서 값이며 외부 조회를 하지 않았음을 표시했습니다. 설정 대신 새 전역 숫자 상수를 도입하거나 제공자 규칙을 여러 업로더에 복사하지 않았습니다. 스트림 라이터의 크기 게이트는 제거하고 애플리케이션 계층의 단일 플랫폼 capability 검사와 공통 10GiB 상한에 맡겼습니다.

**판매/운영 전에 확인 필요:** Threads의 정확한 게시 경로별 한도, Facebook의 실제 Graph API 업로드 경로 및 계정 조건, WordPress 호스트/PHP 설정, Vimeo 요금제별 용량, Dailymotion의 현재 공식 파일 한도, 그리고 기억 기반으로 기록한 나머지 제공자 값의 현재 문서 일치 여부. 이 작업은 요청에 따라 외부 호출 없이 진행했으므로 플랫폼 값은 계약상 보수적 초기값이지 최신 공식 문서 검증 결과가 아닙니다.

### 2. YouTube/TikTok 스트리밍 임시 파일

두 writer의 2GB `MAX_MEMORY_FILE_SIZE` 거부와 중복 크기 제한을 제거했습니다. 두 writer 모두 전체 업로드 파일을 `TempFileChunkBuffer`의 임시 파일로 보관합니다. YouTube는 `FileSystemResource`로 파일을 전달하고, TikTok은 파일 스트림에서 약 10MB 단위로만 읽습니다. 전체 파일을 메모리의 단일 byte array로 만들지 않습니다.

외부 세션/API 호출 전에 임시 파일 디렉터리의 여유 공간을 확인하는 `TempDiskSpaceGuard`를 추가했습니다. 필요한 공간은 예상 파일 크기 + 512MiB 여유분입니다. 용량 부족, 용량 조회 실패, 산술 오버플로는 명시적인 실패로 처리하며 검사 실패 시 임시 파일도 정리합니다.

### 3. Shorts 소스 10GiB 및 FFmpeg 경로

Shorts 입력 기본 한도를 `FileValidationUtil.VIDEO_DIRECT_UPLOAD_MAX_BYTES`에 연결했습니다. `shorts.transcribe.max-source-bytes`는 양수 설정으로 더 낮출 수 있지만 공통 한도를 높일 수 없으며, 기본 sentinel `0`은 공유 한도를 뜻합니다. `application.yml`은 별도의 10GiB 숫자를 복제하지 않고 설정 환경변수(`SHORTS_TRANSCRIBE_MAX_SOURCE_BYTES`)로 낮출 수 있음을 문서화합니다.

10GiB 경계에서 허용하고 10GiB + 1 byte에서 저장 전에 거부하는 테스트를 추가했습니다. FFmpeg transcription은 원본 URL을 직접 입력으로 사용하고 압축된 오디오 청크만 임시 디렉터리에 출력합니다. 렌더러도 URL을 FFmpeg 입력으로 전달하고 렌더 결과만 임시 디렉터리에 기록합니다. 두 경로에서 원본 전체 파일을 애플리케이션 메모리/로컬 디스크로 다운로드하는 경로를 확인하지 못해 추가 디스크 사전 검사는 적용하지 않았습니다.

### 4. 테스트

플랫폼 capability 상한, Shorts 경계/설정, 디스크 공간 여유분, 저장 공간 부족 시 외부 호출 전 실패를 검증했습니다. 기존 테스트는 약화하지 않았습니다.

## 검증 명령과 결과

작업 디렉터리 `backend`에서 실행:

```text
./gradlew :onGo-api:compileKotlin \
  :onGo-application:test --tests 'com.ongo.application.video.PlatformUploadCapabilityTest' \
  --tests 'com.ongo.application.ugc.shorts.ShortsPipelineUseCaseTest' \
  :onGo-infrastructure:test --tests 'com.ongo.infrastructure.external.platform.TempDiskSpaceGuardTest' \
  --tests 'com.ongo.infrastructure.external.platform.TikTokStreamWriterTest' \
  --tests 'com.ongo.infrastructure.external.platform.YouTubeStreamWriterHttpContractTest' \
  --tests 'com.ongo.infrastructure.external.platform.TikTokStreamWriterHttpContractTest' \
  --tests 'com.ongo.infrastructure.render.FfmpegTranscriptionAudioAdapterTest' \
  --tests 'com.ongo.infrastructure.render.FfmpegVideoRendererTest'
```

결과: `BUILD SUCCESSFUL`; 8개 테스트 클래스, **116/116 통과** (플랫폼 6, Shorts 77, 디스크 가드 2, TikTok writer 3, YouTube HTTP 계약 3, TikTok HTTP 계약 2, FFmpeg transcription 15, FFmpeg renderer 8). `:onGo-api:compileKotlin`도 통과했습니다.

```text
./gradlew :onGo-api:test --tests 'com.ongo.api.ApplicationContextSmokeIT'
```

결과: `BUILD SUCCESSFUL`; **2/2 통과**. 기본 설정에서 애플리케이션 컨텍스트 기동을 확인했습니다.

```text
git diff --check
```

결과: 통과, 공백 오류 없음.

## 변이 검증

각 변이를 잠시 적용해 해당 회귀 테스트가 실패하는지 확인한 뒤 원복했습니다. 최종 코드에 대해 위 테스트들을 다시 실행해 통과를 확인했습니다.

- 플랫폼 한도 clamp를 우회하도록 변이: `PlatformUploadCapabilityTest` **1/6 실패**, 10GiB 공통 상한 단언이 잡았습니다.
- 디스크 조건을 사실상 무효화하도록 변이: `TempDiskSpaceGuardTest` **1/2 실패**, 여유 공간 부족 단언이 잡았습니다.
- Shorts 기본 상한을 공통 한도의 절반으로 변이: `ShortsPipelineUseCaseTest` **1/77 실패**, 10GiB 경계 테스트가 잡았습니다.

최종 상태에서는 모든 변이를 원복했고 최종 집중 검증은 **116/116 통과**했습니다. 외부 플랫폼 호출은 실행하지 않았습니다.

## Claude 리뷰 수정 (2026-09-23)

1. **출처 주석을 선언 괄호 안에 넣어 프런트 계약 테스트가 깨졌다** — `platformCountContract.test.ts` 는
   `PlatformUploadCapability(Platform.X, true, ...` 를 정규식으로 읽는다. 주석을 각 항목 위로 옮겼다(동작 변화 없음).
2. 새 주석이 영어였다 — 주변 코드와 같게 한국어로. 쇼츠 원본 한도에서 지워진 설계 근거("우리가 감당하기로 정한 선")를 되살렸다.
3. 확인만: YouTube·TikTok 전송기는 원본 전체를 임시 파일에 쌓는다(메모리 아님). 디스크 가드는 동시 전송끼리
   공간을 예약하지 않는다 — 동시 수는 업로드 세마포어가 제한한다. 주석에 한계를 적었다.
4. 플랫폼 한도는 기억 기반 값이다. 판매 전 공식 문서 대조가 필요하다(위 "확인 필요").
