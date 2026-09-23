# 게시 경로 감사 — 대용량 업로드 계약

조사 기준: 2026-09-23 로컬 작업트리 정적 코드. 계약서의 Codex 범위에 따라 소스는 수정하지 않았다. Claude가 같은 작업트리에서 업로드 코드를 수정 중이므로 아래 내용은 조사 시점의 스냅샷이며, 동시 변경분과 배포본 차이를 포함한 운영 검증은 아니다. 외부 API 실제 호출 및 운영 서버 확인은 하지 않았다.

## 요약

- 일반 게시(브라우저가 R2에 업로드한 뒤 `PublishVideoUseCase`가 게시 이벤트를 내는 경로)에서 지원되는 11개 중 8개는 서버가 원본을 임시 파일로 받는 Push/혼합 경로이고, Facebook·WordPress·Vimeo 3개는 원본 URL을 넘기는 Pull 경로다.
- YouTube·TikTok·Instagram·Threads는 `PlatformUploadServiceImpl`이 StreamWriter 경로를 선택한다. Instagram/Threads도 원본은 먼저 로컬에 내려받는다. 이 둘은 로컬 청크 버퍼를 다시 임시 R2 객체로 올린 뒤 Graph API에 URL을 준다.
- 다중 플랫폼 게시 시 Push/혼합 목적지마다 독립적인 전체 원본 임시 파일이 생긴다. 따라서 한 영상의 동시 최대 로컬 복사본 수는 Push 목적지 수(최대 8개)이며, 2GB를 모두 허용하는 플랫폼 조합이라면 최악의 디스크 사용량은 이론상 약 16GB + 처리 버퍼다. IG/Threads는 추가 임시 R2 객체를 목적지마다 하나씩 쓴다.
- 직접 확인된 Pull 전환 후보는 이미 URL을 받는 Facebook·WordPress·Vimeo 외에는 없다. 나머지는 현재 client/writer에서 실제 바이트 Push가 구현되어 있어 서버 다운로드 제거에 별도 플랫폼/API 작업이 필요하다.
- 우선 권장: 원본 R2 객체를 유지하고, 멀티플랫폼 게시마다 플랫폼별 GET을 반복하지 않도록 **공유 로컬 스테이징 파일 1개 + 참조 카운트/작업 종료 후 삭제**로 바꾼다. 플랫폼별 동시 작업·재시도·프로세스 종료를 고려한 안전한 수명 관리가 필수다. 아래에 잠재 위험과 대안 조건을 적었다.

## 게시 경로와 11개 플랫폼

게시 이벤트 처리기는 각 플랫폼 업로드를 개별 작업으로 실행하고, 업로드 서비스에 파일 URL을 전달한다 (`VideoPublishEventListener.kt:82-82,131-149`; 병렬 실행부 `VideoPublishEventListener.kt:58-75`). 예약 게시 디스패처도 게시 시각에 URL을 다시 얻어 이벤트로 전달한다 (`ScheduledVideoUploadDispatcher.kt:34-50`). 업로드 서비스는 video에 대해 해당 플랫폼 StreamWriter가 있고 capability의 `directVideoUpload`가 true일 때만 StreamWriter로 보낸다. 그 외에는 `PlatformClientFactory`가 반환한 client의 `uploadVideo`를 호출하며 URL을 request에 넣는다 (`PlatformUploadServiceImpl.kt:96-123`).

| 플랫폼 | 일반 게시의 실제 경로 | Push/Pull 판정 및 근거 |
|---|---|---|
| YouTube | StreamWriter → 로컬 파일 청크 전송 | **Push.** capability가 direct true (`PlatformUploadCapability.kt:36-38`); 공통 경로가 R2 URL을 먼저 로컬 임시 파일로 다운로드 후 256KiB 단위로 writer에 전달 (`PlatformUploadServiceImpl.kt:96-102,349-388`). |
| TikTok | StreamWriter → 로컬 파일 청크 전송 | **Push.** capability direct true (`PlatformUploadCapability.kt:39-42`); 같은 공통 다운로드/청크 경로 (`PlatformUploadServiceImpl.kt:96-102,349-388`). |
| Instagram | StreamWriter → 임시 R2 객체 → Graph API `video_url` | **혼합(서버 staging 후 provider Pull).** direct true (`PlatformUploadCapability.kt:62-67`); 공통 로컬 다운로드/청크 (`PlatformUploadServiceImpl.kt:96-102,349-388`); writer가 임시 객체 URL을 client에 제공 (`InstagramStreamWriter.kt:63-105`); client가 그 URL을 `videoUrl`로 API에 전달 (`InstagramClient.kt:97-110`, API 파라미터 `InstagramApi.kt:21`). |
| Threads | StreamWriter → 임시 R2 객체 → Graph API `video_url` | **혼합(서버 staging 후 provider Pull).** direct true (`PlatformUploadCapability.kt:68-73`); 공통 로컬 다운로드/청크 (`PlatformUploadServiceImpl.kt:96-102,349-388`); writer/client/API 연결 (`ThreadsStreamWriter.kt:60-103`, `ThreadsClient.kt:28-43`, `ThreadsApi.kt:16`). |
| Facebook | Client → API 요청에 원본 URL | **Pull 후보/URL 전달.** direct false, cloud true (`PlatformUploadCapability.kt:74-78`); client가 `request.fileUrl`을 API 호출로 넘김 (`FacebookClient.kt:22-35`, `FacebookApi.kt:14-24`). 코드로 URL 전달은 확인되지만 Facebook 측의 실제 fetch 시점·처리 보장은 미검증. |
| Pinterest | Client → 로컬 다운로드 → 업로드 | **Push.** capability direct false/cloud true (`PlatformUploadCapability.kt:79-82`); `downloadFileToTemp` 후 파일 업로드, finally 삭제 (`PinterestClient.kt:32-55`). |
| LinkedIn | Client → 로컬 다운로드 → 4MB 파트 업로드 | **Push.** capability direct false/cloud true (`PlatformUploadCapability.kt:83-87`); source 다운로드와 파트 전송 및 finally 삭제 (`LinkedInClient.kt:28-31,97-162`). |
| WordPress | Client → 미디어 URL 전달 | **Pull 후보/URL 전달.** capability direct false/cloud true (`PlatformUploadCapability.kt:88-91`); `media_urls[]`에 원본 URL을 넣음 (`WordPressClient.kt:27-41`). WordPress 측 처리·원격 fetch SLA는 미검증. |
| Dailymotion | Client → 로컬 다운로드 → multipart 업로드 | **Push.** capability direct false/cloud true (`PlatformUploadCapability.kt:92-95`); helper 다운로드 및 finally 삭제 (`DailymotionClient.kt:28-74`). |
| Vimeo | Client → pull API | **Pull.** capability direct false/cloud true (`PlatformUploadCapability.kt:96-99`); 요청 DTO가 `approach="pull"`, `link=request.fileUrl`을 설정 (`VimeoClient.kt:26-40`, `VimeoDtos.kt:6-14`). |
| Tumblr | Client → 로컬 다운로드 → multipart 업로드 | **Push.** capability direct false/cloud true (`PlatformUploadCapability.kt:100-103`); helper 다운로드 및 finally 삭제 (`TumblrClient.kt:27-85`). |

지원 여부는 capability 표에서 판정했다. 플랫폼 enum에는 13개가 있지만 X/Twitter와 Naver Clip은 현재 동영상 게시가 비활성/미지원이어서 위 11개 집계에서 제외된다 (`PlatformUploadCapability.kt:43-60`).

### Instagram/Threads `videoUrl` 생존 여부

일반 게시에서는 죽은 코드가 아니다. `directVideoUpload=true`이고 writer가 등록되어 있어 `uploadFromCloudUrl` → `InstagramStreamWriter.complete`/`ThreadsStreamWriter.complete` → 각 client `uploadVideo` → API의 `video_url` 파라미터로 도달한다. 단, 서비스의 legacy client fallback도 남아 있고 그 경로는 원본 `request.fileUrl`을 직접 전달한다. 따라서 “운영 게시 이벤트에서 실제 호출됨”은 코드 호출 체인 기준 확인, 각 배포 환경에서 factory 등록까지 되는지는 DI 구성과 배포본을 별도로 확인해야 한다.

## 임시 파일/객체 수와 정리

### 일반 게시 이벤트 경로

각 플랫폼 작업은 별도 `PlatformUploadServiceImpl.upload` 호출이다. 따라서 공유 원본 로컬 파일은 없고, Push/혼합 목적지마다 `downloadFileToTemp`가 만든 전체 파일(`ongo-cloud-source-*.upload`) 한 개씩 생긴다 (`PlatformClient.kt:36-57`). 이 helper는 다운로드 중 예외가 나면 자체 파일을 지운다. 다운로드 성공 후 삭제 책임은 호출자다.

- StreamWriter 4개(YouTube, TikTok, Instagram, Threads): 플랫폼별 로컬 원본 파일 1개. writer 초기화/청크/완료 중 성공·실패 어느 쪽이든 `uploadFromCloudUrl`의 `finally`에서 abort를 호출하고 로컬 파일을 삭제한다 (`PlatformUploadServiceImpl.kt:349-388`). `abort()`가 `complete()` 성공 후에도 실행되므로 구현별 abort의 멱등성과 완료 상태 처리는 검토 대상이다.
- Client Push 4개(Pinterest, LinkedIn, Dailymotion, Tumblr): 플랫폼별 로컬 원본 파일 1개. 각 client의 finally에서 삭제한다 (위 표의 파일·줄 참조). `downloadFileToTemp` 자체가 실패한 경우는 helper가 삭제한다.
- Pull 3개(Facebook, WordPress, Vimeo): 이 비디오 uploadVideo 경로에서는 원본 로컬 전체 파일을 만들지 않고 URL을 전달한다.
- Instagram/Threads: 위 로컬 원본 파일에 더해 writer가 플랫폼별 임시 R2 객체를 만든다. `complete()` finally에서 삭제하며(`InstagramStreamWriter.kt:63-113`, `ThreadsStreamWriter.kt:60-110`), 실패 abort 경로도 storage key를 best-effort 삭제한다. 삭제 실패는 `runCatching`으로 억제되므로 orphan 객체 가능성이 있다.

11개를 동시에 게시할 때 Push/혼합 목적지 8개가 모두 동시에 다운로드/업로드 중이면 로컬 전체 파일 최대 8개가 겹칠 수 있다. 크기 상한은 플랫폼별로 다르며(`PlatformUploadCapability.kt:36-103`), 2GB 파일 8개가 모두 가능한 조합은 아니지만 보수적인 절대 상한 계산은 16GB다. retry가 순차 재실행되면 각 시도의 임시 파일은 개별 정리되지만, 여러 플랫폼 작업은 병렬이므로 플랫폼 수만큼 동시 점유 가능하다. OS 강제 종료/전원 장애는 finally를 보장하지 않으므로 잔여 파일 정리 정책도 확인이 필요하다.

### 별도 스트리밍 게시 경로 주의

`StreamPublishUseCase`/`StreamPublishController`는 일반 R2 게시 이벤트와 다른 API 경로다. 즉시 multipart 스트리밍 게시에서는 요청 전체에 대해 로컬 임시 파일 하나를 만들고 여러 writer에 재사용한 뒤 작업 종료 finally에서 삭제한다 (`StreamPublishUseCase.kt:260-283,671-679`). 계약서의 `uploadFromCloudUrl`가 호출되는 일반 durable publish 경로와 혼동하면 안 된다. 예약 처리 경로도 별도 dispatcher를 통한다. 공유 staging 설계는 두 경로의 소유권·정리 정책이 다름을 반영해야 한다.

## Pull 전환 후보와 URL 만료

확인된 URL 전달 방식은 Facebook, WordPress, Vimeo다. 이들은 현재 일반 게시 경로에서도 서버 전체파일 다운로드를 하지 않는다. 직접 Push인 YouTube/TikTok/Pinterest/LinkedIn/Dailymotion/Tumblr은 각 provider 프로토콜에 파일 바이트나 파트 데이터를 보내므로, 저장소 URL만 넘기는 방식은 client/API 계약을 새로 구현하지 않는 한 현재 대체할 수 없다. Instagram/Threads는 URL 기반 provider ingestion이 있지만 이미 로컬 staging을 수행한다. 직접 원본 R2 URL을 넣어 로컬 staging을 제거할 수 있는지는 API 권한/URL 접근성/미디어 제약/처리 완료까지의 수명 요구를 외부 문서와 실제 통합 테스트로 검증해야 하며 여기서는 가능하다고 단정하지 않는다.

현재 URL 정책: `VideoStorageService.getFileUrl`은 원본 객체 키를 찾아 GET presigned URL을 **7일**로 생성한다 (`VideoStorageService.kt:51-58`). 게시 이벤트 시점과 예약 발송 시점에 새 URL을 발급한다 (`VideoPublishEventListener.kt:131-139`, `ScheduledVideoUploadDispatcher.kt:34-50`). 따라서 예약 대기 자체로 저장 URL이 만료되는 위험은 줄인다. 그러나 외부 provider가 비동기 fetch를 7일 넘게 지연하거나 재시도/처리를 그 뒤까지 이어가는 경우 URL이 만료될 위험은 남는다. provider 측 처리 최대시간과 URL fetch 시점을 이 코드만으로 알 수 없어 위험을 정량 확정할 수 없다. 공유 로컬 Push 설계는 원본 presigned URL 만료와 무관하게 다운로드를 시작한 뒤 처리할 수 있지만, 초기 GET의 7일 만료 전 시작은 여전히 필요하다.

## 권장 설계 및 변경 대상

권장 1안: 게시 작업 단위의 **공유 로컬 staging cache**. 최초 Push 필요 작업이 원본 R2를 임시 파일 하나로 내려받고, 같은 영상/게시 이벤트의 Push 대상들이 읽기 전용으로 공유한다. 모든 관련 작업의 완료·실패·취소가 끝났을 때 소유자가 파일을 삭제한다. pull-only Facebook/WordPress/Vimeo는 staging을 요구하지 않는다. 대용량 동시 요청을 제한하는 별도 디스크 admission control/용량 메트릭과, 프로세스 비정상 종료 후 잔여 파일 청소를 함께 둔다. client가 파일 경로를 직접 쓰는 Push 구현과 StreamWriter의 chunk reader가 같은 staged file source를 쓸 수 있도록 추상화한다.

**중요한 한계:** 공유 staging은 다운로드 횟수를 8→1로 줄이나, 2GB 원본을 최소 한 번 로컬 디스크에 저장하고 작업 완료까지 보유한다. 이 설계는 디스크 피크를 최악 16GB에서 약 2GB/영상으로 낮추는 선택이다. 디스크 완전 제거가 요구되면 플랫폼별 Push 프로토콜에 맞는 R2 range streaming/remote multipart가 대안이지만, retry·range 일관성·네트워크 연결 수·provider backpressure를 새로 설계해야 하므로 더 큰 변경이다.

변경 후보 파일 (구현 전 실제 소유권/현재 Claude 변경과 재대조할 것):

1. `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/upload/PlatformUploadServiceImpl.kt` — 다운로드 1회 staging 생성과 생명주기/lease.
2. `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/platform/PlatformClient.kt` — 현재 URL-only request/helper 계약을 staged source 계약으로 확장하거나 별도 abstraction 추가.
3. `backend/onGo-application/src/main/kotlin/com/ongo/application/video/VideoPublishEventListener.kt` — 플랫폼 병렬작업들이 staging을 공유하고 마지막 작업 완료 후 정리하도록 작업 단위 조율. 실패/취소/중복 이벤트 고려.
4. `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/external/platform/YouTubeStreamWriter.kt`, `TikTokStreamWriter.kt`, `InstagramStreamWriter.kt`, `ThreadsStreamWriter.kt` 및 `PlatformStreamWriter` — source reader/staged 파일 입력과 abort/finally 소유권 검토.
5. Push client: `PinterestClient.kt`, `LinkedInClient.kt`, `DailymotionClient.kt`, `TumblrClient.kt` — `downloadFileToTemp` 호출 제거, 공유 파일/stream 사용, client별 finally가 공유 파일을 삭제하지 않게 변경.
6. 공통 플랫폼 capability/설정 및 운영 메트릭 — Push 집합, 용량 상한/admission control과 orphan 정리 관측.
7. `StreamPublishUseCase.kt`는 별도 업로드 경로이므로 공유 API를 사용할지 독립 유지할지 결정하고 해당 파일도 별도 영향 분석. 예약 dispatch 자체는 새 staging 작업의 수명 범위에 넣을지 확인.

위 목록은 설계 영향 후보이지 구현 변경이 아니다. 이 조사에서는 이 문서 외에는 수정하지 않았다.
