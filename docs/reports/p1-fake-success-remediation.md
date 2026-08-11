# P1 fake-success 제거 배치

작업일 2026-08-11 · 공유 워크트리(main, HEAD `0d65a778`) · 커밋하지 않음

## 1. 경쟁자 수동 동기화가 실제로 갱신한다

**이전**: `CompetitorController.syncCompetitors` 가 `listCompetitors(userId)` 를 그대로
돌려주며 "동기화가 완료되었습니다" 를 붙였다. 제공자를 한 번도 부르지 않았다.

**변경**
- 신규 `CompetitorRefreshService` — 갱신 로직의 단일 출처. 결과를 `SYNCED / UNSUPPORTED / FAILED`
  로 구분한다. 미지원(YouTube 외 플랫폼, API 키 없음)은 재시도해도 같으므로 실패와 분리했다.
- `lookup()` 과 `persist()` 를 **일부러 분리**했다. 스케줄러는 HTTP 필터를 지나지 않아
  계정 동결 가드를 직접 보는데, 그 재확인이 외부 조회와 저장 **사이**에 들어가야 한다.
  합치면 스케줄러가 가드를 끼울 자리를 잃는다.
- `CompetitorSyncScheduler` 는 이제 같은 서비스를 쓴다. TOCTOU 가드는 그대로.
- `CompetitorUseCase.syncCompetitors(userId)` 추가. `@Transactional` 을 붙이지 않았다 —
  건별 외부 HTTP 조회를 트랜잭션 안에 두면 커넥션을 외부 I/O 시간만큼 잡는다.
- 한 건도 갱신 못 하고 실패만 있으면 `BusinessException("COMPETITOR_SYNC_FAILED")`.
  부분 성공은 오류로 보지 않는다(새 데이터가 실제로 생겼으므로).
- 응답 `CompetitorSyncResponse` 에 requested/synced/unsupported/failed + 건별 결과를 담고,
  컨트롤러 메시지도 실제 건수를 반영한다.

> 프론트에 `/competitors/sync` 소비자가 없다(`competitorApi` 에 sync 메서드 없음).
> 응답 형태 변경이 UI 를 깨지 않는다.

## 2. 애널리틱스 하드코딩 0 제거

- `getTopVideos`: `totalViews = 0, totalLikes = 0`(+ "populated from aggregate query" 주석)
  → 같은 파일 `getVideoComparison` 이 쓰는 `findByVideoUploadIdsAndDateRange` 를 재사용해
  조회 기간의 실제 합계를 채운다. **새 쿼리 없음.**
- `getPlatformComparison`: `likes = 0, comments = 0, shares = 0`
  → `findCrossPlatformDetailMetrics` 로 교체. 같은 `analytics_daily ⋈ video_uploads` 를
  같은 기간으로 훑으면서 네 지표를 모두 집계한다. **리포지토리/DTO 확장 없음.**
- 알 수 없는 플랫폼 문자열은 0 으로 채우지 않고 건너뛴다(같은 종류의 거짓 데이터 방지).
- 빈 상태 의미 보존: 집계 행이 없으면 0/빈 목록이며 영상 자체는 그대로 노출.

## 3. 감정 분석이 실패를 감추지 않는다

`AnalyzeSentimentUseCase.analyzeBatch` 가 예외·빈 응답을 `comments.map { "NEUTRAL" }` 로
폴백했다. 호출자는 실패를 알 수 없었고 그 값이 DB 에 저장돼 감정 통계까지 오염됐다.
→ `BusinessException("AI_SENTIMENT_FAILED")` 를 던진다.

- 유일한 호출자 `VideoCommentSyncService:118` 은 이미 try/catch 로 감싸 로그를 남기고
  진행한다. 동기화는 계속되며, 어떤 선택을 했는지가 코드에 드러난다.
- 모델이 일부 인덱스를 빠뜨린 경우는 호출 실패와 구분해 그 자리만 NEUTRAL 로 두되
  누락 건수를 warn 으로 남긴다.

**남은 것**: `Comment.sentiment` 가 `String = "NEUTRAL"` 논널이라, 동기화 폴백은 여전히
NEUTRAL 을 저장한다. → 공유 검토 후 `UNANALYZED` sentinel을 도입해 실제 중립과 분리했고,
댓글 카드/필터에도 미분석 상태를 표시한다. 기존 `VARCHAR(20)` 컬럼을 사용하므로 마이그레이션은
필요하지 않다. 기존에 이미 저장된 NEUTRAL 행의 의미는 소급 변경하지 않는다.

추가 교차 검토에서 영상 목록의 `totalViews = 0`, 공개 분석의 shares=0, 재활용 분석의
`duration = 0`도 실제 값으로 위장할 수 있는 경로로 확인했다. 영상 목록은 analytics_daily
집계를 batch 조회하고, 공개 분석은 저장된 shares를 반환한다. 재활용은 Video 도메인에 duration
필드가 없으므로 duration에 근거한 CLIP 추천을 제거해 알 수 없는 값을 0으로 가정하지 않는다.

## 4. 온보딩이 실패 시 완료로 넘어가지 않는다

`completeOnboarding` 의 `catch { currentStep.value = 5 }`(주석: "Still proceed to
completion screen") 제거. 서버가 `onboarding_completed` 를 기록하지 못했는데 화면만 완료로
보이면, 사용자는 끝난 줄 알고 나가지만 다음 로그인에 온보딩으로 되돌아온다.

- 현재 단계에 머무르고 `role="alert"` 배너로 사유를 보여준다(서버 메시지 우선, 없으면 안내 문구).
- 완료 버튼이 남아 재시도 가능.
- 채널 연동은 선택 그대로("나중에 연결" 경로 유지). 테스트가 이 경로로 진행한다.
- i18n `onboarding.completeFailed` ko/en 추가.

## 변경 파일

### 백엔드 (운영 코드)
- `backend/onGo-application/src/main/kotlin/com/ongo/application/competitor/CompetitorRefreshService.kt` (신규)
- `backend/onGo-application/src/main/kotlin/com/ongo/application/competitor/CompetitorSyncScheduler.kt`
- `backend/onGo-application/src/main/kotlin/com/ongo/application/competitor/CompetitorUseCase.kt`
- `backend/onGo-application/src/main/kotlin/com/ongo/application/competitor/dto/CompetitorDtos.kt`
- `backend/onGo-api/src/main/kotlin/com/ongo/api/competitor/CompetitorController.kt`
- `backend/onGo-application/src/main/kotlin/com/ongo/application/analytics/AnalyticsUseCase.kt`
- `backend/onGo-application/src/main/kotlin/com/ongo/application/ai/AnalyzeSentimentUseCase.kt`

### 백엔드 (테스트)
- `backend/onGo-application/src/test/kotlin/com/ongo/application/competitor/CompetitorRefreshServiceTest.kt` (신규, 4)
- `backend/onGo-application/src/test/kotlin/com/ongo/application/competitor/CompetitorUseCaseSyncTest.kt` (신규, 5)
- `backend/onGo-application/src/test/kotlin/com/ongo/application/analytics/AnalyticsUseCaseAggregateTest.kt` (신규, 5)
- `backend/onGo-application/src/test/kotlin/com/ongo/application/ai/AnalyzeSentimentUseCaseTest.kt` (신규, 1)

### 프론트엔드
- `frontend/src/views/OnboardingView.vue`
- `frontend/src/views/OnboardingView.test.ts` (신규, 3)
- `frontend/src/locales/ko/common.json`, `frontend/src/locales/en/common.json`

## 실행한 명령과 결과

```bash
# backend/ 에서
./gradlew :onGo-application:test \
  --tests "com.ongo.application.competitor.*" \
  --tests "com.ongo.application.ai.AnalyzeSentimentUseCaseTest" \
  --tests "com.ongo.application.analytics.AnalyticsUseCaseAggregateTest"
# → 21 tests, failures=0, errors=0
#   CompetitorRefreshServiceTest 4 / CompetitorUseCaseSyncTest 5
#   CompetitorSyncSchedulerFreezeTest 6 (기존, 리팩터 후에도 통과)
#   AnalyzeSentimentUseCaseTest 1 / AnalyticsUseCaseAggregateTest 5

./gradlew :onGo-api:compileKotlin        # BUILD SUCCESSFUL

# frontend/ 에서
npm run build                            # vue-tsc + vite, exit 0, 1512 modules
npx vitest run src/views/OnboardingView.test.ts   # 3 passed
```

빌드 성공만 믿지 않고 `build/test-results/test/TEST-*.xml` 의 `tests`/`failures` 수치로
실제 실행을 확인했다(필터가 아무 테스트도 잡지 못해도 BUILD SUCCESSFUL 이 나온다).

## 주의 — 공유 워크트리

다른 에이전트가 같은 트리에서 UGC/Shorts·video 계열을 동시에 수정 중이다. 지시대로
그 파일들은 건드리지 않았다. 작업 중 두 가지 겹침이 있었다.

1. `CompetitorSyncSchedulerFreezeTest.kt` 가 내가 만들기 전에 이미
   `CompetitorRefreshService(repository, lookup)` 를 기대하는 형태로 수정돼 있었다.
   내 구현이 그 시그니처와 일치해 그대로 통과한다.
2. 내가 쓴 `AnalyzeSentimentUseCaseTest.kt` 를 다른 에이전트가 자기 버전으로 덮었다.
   핵심 단언(`BusinessException` + `AI_SENTIMENT_FAILED`)은 그대로라 되돌리지 않았다.
   다만 내가 넣었던 "빈 입력이면 AI 를 호출하지 않는다" 케이스는 사라졌다.

커밋/푸시는 하지 않았다.

## Codex 작업(platformPostId·다중 채널)과의 충돌 여부 — 없음

`platformPostId` 는 UGC publishing 과 video publish 경로에만 있다.

```
domain/ugc/publishing/{CampaignPost,CampaignPostRepository,CampaignPublishPort}.kt
application/video/{VideoPublishEvent,VideoPublishEventListener,UploadCompletedEventListener,VideoUploadPoller,StreamPublishUseCase}.kt
application/ugc/*, infrastructure/.../JooqCampaignPostRepository.kt, Tables.kt, V50 마이그레이션
```

내가 바꾼 파일(competitor·analytics·ai·onboarding)과 **교집합이 0** 이다.

내 애널리틱스 변경이 의존하는 타입은 모두 이번 라운드에서 변경되지 않았다:
`VideoUpload` / `AnalyticsRepository` / `AnalyticsDaily` / `VideoUploadRepository` — 변경 없음.

유일한 접점은 다른 에이전트가 `AnalyticsJooqRepository.findCrossPlatformDetailMetrics` 에
상한(`ad.date <= to`)을 추가한 것이다. 내 `getPlatformComparison` 이 새로 쓰기 시작한 바로 그
쿼리인데, **호환되는 강화**다(미래 일자 행 제외). 시그니처·반환 타입이 그대로라 충돌하지 않으며,
재실행한 테스트도 전부 통과한다.

## 판단 1 — 플랫폼 다중 채널 / UGC event 연결

**가능하지만 이번 범위에서 하면 안 된다. 제품 결정이 선행돼야 한다.**

- 데이터는 이미 있다. `video_uploads.channel_id` 가 존재하고 `analytics_daily` 는
  `video_upload_id` 로 묶이므로 채널별 집계가 **새 테이블 없이 파생 가능**하다.
- 그런데 `AnalyticsJooqRepository` 전체에 `channel_id` 가 **0회** 등장한다. 즉 현재 집계는
  플랫폼 단위이고 채널 정체성이 아예 표현돼 있지 않다. 유튜브 채널이 둘이면 한 줄로 합쳐진다.
  (이건 내 변경이 만든 게 아니다 — 이전 `getTrendData` 기반 구현도 동일했다.)
- **막는 지점**: `channelId` 가 레거시 호환 때문에 nullable 이다. 채널별로 그룹핑하면 옛 행이
  조용히 사라진다. 이번 배치가 고치고 있는 것과 정확히 같은 종류의 버그다. 도입한다면
  "미지정" 버킷이 반드시 필요하다.
- 필요한 변경: `CrossPlatformDetailRaw`/`PlatformSummary` 에 채널 식별자 추가 → SQL groupBy →
  API DTO → 프론트. 그리고 "플랫폼 비교"가 플랫폼 단위인지 채널 단위인지 정하는 결정.
- UGC event 연결은 `platformPostId` 체인(VideoPublishEvent → CampaignPost)에서 이미 다뤄지고
  있고 내 파일과 무관하다. 채널 식별자를 그 이벤트에 실을 거라면 애널리틱스 쪽도 같은 식별자를
  써야 하므로 **양쪽을 한 번에 정하는 편이 안전하다.**

## 판단 2 — UNANALYZED 도입

**기술적으로 가능하고 스키마 변경도 필요 없다. 다만 집계 한 곳을 같이 고쳐야 한다.**

- 컬럼은 `sentiment VARCHAR(20) DEFAULT 'NEUTRAL'` (V24/V29). **enum 타입도 CHECK 제약도 없다.**
  `'UNANALYZED'`(10자)는 그대로 들어간다. 마이그레이션 불필요.
- `Comment.sentiment: String = "NEUTRAL"` 기본값은 유지하고, AI 분석이 돌지 않았거나 실패한
  경우에만 명시적으로 `UNANALYZED` 를 쓰면 된다.
- **반드시 같이 고쳐야 하는 곳**: `CommentUseCase.kt:89-94` 의 감정 추이 집계가
  `sentiments["POSITIVE"|"NEUTRAL"|"NEGATIVE"]` 로 정확히 세 키만 읽는다. `UNANALYZED` 는
  어느 버킷에도 안 잡혀 **총계에서 조용히 사라진다.** 버킷을 추가하든 분모에서 제외하든
  명시적으로 정해야 한다. 안 그러면 거짓 데이터를 없애려다 데이터 누락을 만든다.
- 영향 범위: `VideoCommentSyncService`(폴백 지점), `Comment` 기본값 정책,
  `CommentUseCase` 집계, 노출한다면 프론트 라벨. 마이그레이션 0건.
- 필터(`CommentUseCase:26,38,50`)는 `sentiment: String?` 를 uppercase 로 넘기므로 그대로 동작한다.

권고: 이번 배치에는 넣지 않는다. 별도 작업으로 "UNANALYZED 를 집계에서 어떻게 다룰지"를
정한 뒤 한 번에 반영하는 것이 안전하다.
