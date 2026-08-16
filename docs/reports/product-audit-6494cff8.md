# onGo 심층 제품 감사 — `6494cff8`

> **2026-08-11 반론 검토 후 우선순위 확정판은 문서 맨 아래 「반론 검토 결과」를 보라.**
> 아래 본문의 초기 등급 중 4건이 강등되고 1건이 신규 추가됐다.

> **역사적 스냅샷:** 이 문서는 2026-08-11 당시의 분석 기록이다. 이후 OAuth client credential은
> 서버 소유 authorization URL로 전환됐고, Naver Clip은 명시적 미지원으로 정리됐다. 현재 운영 설정은
> `docs/operations/EXTERNAL_SERVICE_SETUP_CHECKLIST.md`와 `backend/ENV_VARIABLES.md`를 기준으로 확인한다.


2026-08-11 · 깨끗한 트리(미커밋 0) · 분석 전용, 코드 미수정
기준: **크리에이터/소규모 에이전시가 돈을 낼 핵심 업무** (제작 → 멀티채널 발행 → 발행 확인 → 성과/소통)

---

## (1) 이미 충분히 구현된 기능

**F1. 플랫폼 클라이언트 13종이 전부 실제 API를 호출한다.**
`external/{youtube,tiktok,instagram,threads,facebook,twitter,naverclip,pinterest,linkedin,wordpress,dailymotion,vimeo,tumblr}/*Client.kt`
업로드·상태조회·분석·댓글이 모두 실호출이다. `PlatformClient.kt:61-147` 계약을 각 클라이언트가 override 한다.
스텁이나 가짜 응답은 발견되지 않았다.

**F2. 플랫폼별 제약을 서버가 강제한다.**
`PlatformUploadCapability.kt:31-86` 이 플랫폼마다 최대 파일크기·제목·설명·태그수·확장자·미디어타입·캡션길이를 갖고,
`PublishVideoUseCase.kt:52-62` 가 발행 직전 이를 검증한다. 미지원은 `unavailableReason` 으로 **이유를 말하고 거절**한다
(예: X 동영상 `PlatformUploadCapability.kt:41`).

**F3. 예약 발행이 플랫폼 native scheduler에 의존하지 않는다.**
`PlatformUploadCapability.kt:10-12` — onGo durable queue 가 처리하므로 `scheduling=false` 인 채널도 같은 시각 게시가 된다.
멀티채널 동시 발행이라는 핵심 가치가 플랫폼 기능 편차에 좌우되지 않는다.

**F4. 운영 설정이 기동·배포 두 단계에서 검증된다.**
`ProductionConfigurationValidator.kt:44-86` (JWT 길이, 암호화키 32바이트, CORS localhost 금지, R2 엔드포인트 정규식,
PortOne 3키, Google/Kakao OAuth, OAUTH_STATE_SECRET 길이) + `deploy/required-env.sh:22` 16개 변수 선검증.
선검증이 `stop.sh` **앞**이라 값 누락 시 기존 서비스가 살아 있는 채로 배포만 실패한다.

**F5. dev-login 이 이중 게이트로 막혀 있다.**
`DevAuthController.kt:19` `@Profile("dev","local")` + `SecurityConfig.kt:70` 조건부 공개 경로.

**F6. 실패를 성공으로 위장하지 않는 코드가 자리잡았다.**
`AuthUseCase.kt:287-296` — 계정 삭제 job 이 없으므로 **가짜 202를 내지 않고** 예외를 던진다
("처리할 job 이 없는데 접수됐다고 하면 거짓말이다"). 감정분석 `AI_SENTIMENT_FAILED`, 경쟁자 sync 실건수 반환도 같은 계열.

---

## (2) 기능적으로 반드시 필요한데 비어 있거나 불완전

### ❶ 채널 연동이 실질적으로 1개다 — **P0**

```
frontend/src/utils/oauth.ts:54-174   13개 플랫폼 연결 URL 생성
frontend/.env.production             VITE_GOOGLE_CLIENT_ID 단 1개
```

12개 플랫폼이 `import.meta.env.VITE_* || ''` 로 **빈 client_id** 를 제공자에 보낸다.
연결 버튼을 누르면 제공자 오류 페이지로 나간다. **게이팅 로직이 없다** — `isConfigured` 류의 검사가
프론트 어디에도 없음(grep 0건).

멀티채널이 이 제품의 핵심 가치 제안인데, 채널 화면은 13개를 제안하고 1개만 동작한다.
이건 미구현이 아니라 **작동하지 않는 약속**이라 신뢰를 직접 깎는다.

### ❷ 계정 삭제 기능이 없다 — **P0 (법적·심사 blocker)**

`AuthUseCase.kt:288-296` — 정책상 삭제 가능한 경우에도 `CODE_NOT_READY` 예외를 던진다.
공개 URL `/data-deletion` 은 존재하지만 실제 삭제 절차가 없다.
개인정보보호법 요구이자 **Meta/TikTok 앱 심사의 필수 항목**이다. 심사를 넣는 순간 막힌다.

### ❸ NAVER_CLIP 은 연결되지만 발행할 수 없다 — **P1**

`oauth.ts` 는 NAVER_CLIP 연결을 제안하고 `NaverClipClient.kt` 도 있으나
`PlatformUploadCapability.kt:31-86` 맵에 **NAVER_CLIP 항목이 없다.**
→ `PublishVideoUseCase.kt:52-53` 에서 `IllegalArgumentException`. 연결 성공 후 발행 단계에서만 막힌다.

### ❹ capability 가 게이팅 장치가 아니다 — **P1**

`CapabilityUseCase.kt:15-51` — 35개 키가 **전부 `enabled=true` 하드코딩**이다.
사용자·플랜·배포 설정 어느 것과도 연결돼 있지 않다.

즉 "서버가 노출을 통제한다"는 설계 의도(`CapabilityUseCase.kt:6-8` 주석)가 실제로는 구현돼 있지 않다.
프론트의 fail-closed 는 **네트워크 장애 시에만** 의미가 있고, 평상시엔 35개가 항상 켜진다.

---

## (3) 중복 / 통합이 필요한 부분

| 항목 | 현황 | 근거 |
|---|---|---|
| 라우트 총계 vs 핵심 흐름 | **69개** vs 4~5개 | `router/index.ts` |
| v1/v2 중복 | `calendar` `channels` `inbox` `settings` **4쌍 모두 생존** | `router/index.ts` |
| 성과 | 5개로 분산 (`performance` `analytics/compare` `revenue` `ab-tests` `goals`) | `useNavigation.ts:178-187` |
| 소통 | 3개 (`inbox-v2` `audience` `notifications`) | `useNavigation.ts:189-196` |
| 콘텐츠 자산 | 6개 (`videos` `templates` `brandkit` `assets` `subtitle-editor` `recycling`) | `useNavigation.ts:149-165` |

**P1** — 성과 5개와 소통 3개는 라우트를 유지한 채 진입점만 탭으로 합칠 수 있다.
**P0** — v1/v2 4쌍은 한쪽을 `redirect` 로 흡수해야 한다. 두 URL 공존은 북마크·지원·분석을 갈라 놓는다. 라우터 단독 변경이라 위험이 낮다.

---

## (4) 가짜 성공 · 하드코딩 · 미지원 · 운영 리스크

| # | 내용 | 근거 | 등급 |
|---|---|---|---|
| R1 | 13개 연동 제안 / 1개 동작 (❶) | `oauth.ts:54-174`, `.env.production` | **P0** |
| R2 | capability 전량 하드코딩 `enabled=true` | `CapabilityUseCase.kt:15-51` | **P1** |
| R3 | `PORTONE_WEBHOOK_SECRET` 이 **기동 검증엔 없다**. 배포 게이트에만 있음 | `ProductionConfigurationValidator.kt` 0건 / `required-env.sh:22` 1건 | **P1** |
| R4 | 호스트 바이너리 전제 — `yt-dlp`/`ffmpeg` 가 PATH 기본값 | `application.yml:144,160` | **P2** |
| R5 | NAVER_CLIP 연결-발행 불일치 (❸) | `PublishVideoUseCase.kt:52-53` | **P1** |

**R3 보충**: 웹훅 시크릿이 비면 `PortOneWebhookVerifier.kt:49` 가 error 로그만 남기고 웹훅을 전량 거부한다.
결제는 성립하는데 **취소·환불이 반영되지 않는** 조용한 반쪽 동작이다. 배포 경로로 기동하면 막히지만,
수동 기동·환경변수 유실 시에는 통과한다.

**가짜 성공은 이번 스캔에서 새로 발견되지 않았다.** `TODO/FIXME/미구현` 전수 grep 결과 실질 항목은
`AuthUseCase.kt:290`(계정삭제, 정직하게 실패) 과 `PublicApiUseCase.kt:1037,1043`(X 스레드 이미지 미지원, 명시적 거절) 뿐이다.
직전 라운드의 fake-success 제거 작업이 실제로 반영돼 있다.

---

## (5) 우선순위 종합

| 우선 | 항목 | 근거 | 성격 |
|---|---|---|---|
| **P0** | 연결 가능한 플랫폼만 노출 (미설정은 비활성 + 사유) | `oauth.ts:54-174` | 신뢰·전환 |
| **P0** | 계정 삭제 실제 구현 | `AuthUseCase.kt:288-296` | 법적·심사 |
| **P0** | v1/v2 4쌍 redirect 정리 | `router/index.ts` | IA |
| **P1** | capability 를 플랜/설정 기반으로 실동작화 | `CapabilityUseCase.kt:15-51` | 제품 통제 |
| **P1** | NAVER_CLIP capability 추가 또는 연결 목록에서 제거 | `PlatformUploadCapability.kt:31-86` | 일관성 |
| **P1** | `PORTONE_WEBHOOK_SECRET` 을 기동 검증에 추가 | `ProductionConfigurationValidator.kt` | 정산 안전 |
| **P1** | 성과 5개 / 소통 3개 진입점 통합 | `useNavigation.ts:178-196` | 사용성 |
| **P2** | 호스트 바이너리 가용성 헬스체크 노출 | `application.yml:144,160` | 운영 |

### '있으면 좋은' — 별도 낮은 우선순위 (P3)

- 콘텐츠 자산 6개 진입점 정리
- `RedesignLayout.vue` 의 `!important` CSS 어댑터 축소 (마이그레이션 잔량 지표)
- 착지 화면 i18n 잔여 하드코딩

> 이 셋은 **매출에 직접 연결되는 근거가 없다.** 위 P0/P1 이 끝나기 전에는 손대지 않는 것을 권한다.

---

## (6) 실제 사용자 검증 또는 운영 OAuth/DB 가 있어야만 확인 가능한 항목

정적 분석으로는 **판정 불가**다. 코드가 맞아도 아래가 틀리면 제품이 성립하지 않는다.

1. **YouTube Data API 쿼터 실측** — 업로드 1건이 큰 비용이고 쿼터는 **프로젝트 단위**다.
   유료 사용자 수를 정하는 상한이므로 GCP 콘솔 Quotas 실측이 선행돼야 한다.
2. **각 플랫폼 심사 통과 여부** — TikTok Direct Post audit, Meta App Review.
   심사 전에는 게시가 비공개로 강제되거나 아예 막힌다. 코드로는 확인 불가.
3. **PortOne 라이브 결제 → 취소 → 부분취소 E2E** — 특히 웹훅 수신·서명 검증(R3).
4. **실제 채널 연결 후 분석·댓글 동기화 정확도** — 13개 클라이언트가 실호출이라는 것과
   응답 매핑이 맞다는 것은 다른 문제다. 운영 토큰 없이는 검증 불가.
5. **R2 대용량 업로드 / 재개** — 2GB급 파일의 presigned 업로드 실동작.
6. **`onboarding_completed` 이후 재로그인 흐름** — 실제 계정으로만 확인 가능.

---

## Claude 자신의 이전 판단 재검토

**정정 1 — Facebook/Threads 분석을 "0/빈값 반환"으로 분류한 것은 오판이었다.**
휴리스틱 grep 이 `views = 0` 초기화 라인을 잡았을 뿐, `FacebookClient.getVideoAnalytics` 와
`ThreadsClient.getVideoAnalytics` 는 모두 실제 insights API 를 호출하고 파싱한다.
자동 분류 결과를 본문 확인 없이 보고했다면 잘못된 P0 를 만들 뻔했다.

**정정 2 — 직전 라운드에 구현한 `MobileBottomNav` capability 게이팅의 실효는 제한적이다.**
당시 "모바일 유일 상시 내비가 fail-open" 이라 P0 로 보고했는데, `CapabilityUseCase` 가 전부
`enabled=true` 상수인 이상 **실제로 걸러질 항목이 없다.** 값은 capability 동기화 실패 구간으로 한정된다.
수정 자체는 옳지만(표면 간 규칙 일치) 등급은 P0 가 아니라 P2 였다.
근본 문제는 R2(capability 가 게이팅이 아님) 쪽이다.

## Codex 의 UI/UX 개선 재검토

**타당한 부분** — 레일을 "핵심 작업 고정 + 나머지 접기"로 큐레이션한 방향은 옳다.
`SideNav`/`MobileMenuSheet` 에만 있던 접기가 레일에서 빠져 35개가 상시 노출되던 문제를 해소한다.

**남는 문제 — UI 정리만으로는 닫히지 않는다.**
1. **v1/v2 중복(4쌍)은 그대로다.** 레일이 v2만 걸어도 레거시 URL 이 살아 있어 북마크·외부 링크로 유입된다.
   라우터 변경 없이는 해결되지 않는다.
2. **"서버 주도 노출"이라는 전제가 아직 참이 아니다.** 레일이 capability 를 신뢰하는데 그 값이 상수다.
   메뉴 축소가 서버 정책이 아니라 프론트 상수에 머물러 있다.
**정정 3 — "핵심 흐름이 첫 클릭에서 셸을 벗어난다"는 이전 라운드의 지적은 이 커밋에서 성립하지 않는다.**
`TodayView.vue:271-272` 는 여전히 `/videos/{id}` 로 보내지만, `router/index.ts:172-174` 에서
`videos/:id` 가 `RedesignLayout`(`:130`) 하위 **자식 라우트**다. 즉 셸 안에서 렌더된다.
남는 것은 내비게이션 단절이 아니라 **레거시 화면(`VideoDetailView.vue`)의 시각적 이질감**이며,
그건 `RedesignLayout.vue` 의 `!important` CSS 어댑터가 덮고 있는 범위(P3)다.
셸 통합은 Codex 작업으로 실제 해소됐다고 본다.

---

# 반론 검토 결과 — P0/P1 확정 (2026-08-11)

각 항목에 **가장 강한 반론**을 세우고, 반론이 이기면 강등했다. 결과적으로 P0 는 3건에서 **2건**으로 줄었다.

## 확정 P0 — 2건

### P0-1. 연결 가능한 플랫폼만 노출 — **유지**
`oauth.ts:54-174` / `frontend/.env.production`

- **반론**: "이건 코드 문제가 아니라 설정 문제다. 키만 넣으면 한 번의 배포로 끝난다."
- **재반론**: 키를 넣을 수가 없다. TikTok audit, Meta App Review 를 통과해야 발급되고 리드타임이 몇 달이다.
  그동안 화면은 계속 13개를 약속한다. **코드로 해야 할 일은 키 확보가 아니라 미설정 플랫폼을 비활성으로
  표시하는 것**이고, 그건 작다. 반론은 문제를 재정의할 뿐 등급을 낮추지 못한다.
- 판정: **P0 유지.** 단 작업 정의를 "13개 연동"이 아니라 "정직한 노출"로 못 박는다.

### P0-2. 계정 삭제 구현 — **유지 (그리고 P0-1 의 선행조건)**
`AuthUseCase.kt:288-296`

- **반론**: "유료 사용자가 0명이다. 지울 사람이 없으니 미뤄도 된다."
- **재반론**: 두 가지로 진다. 첫째, 법적 노출은 **첫 실사용자**에서 시작한다. 둘째가 결정적인데,
  **Meta/TikTok 심사가 데이터 삭제 경로를 요구한다.** 즉 P0-2 를 끝내야 심사가 통과되고, 심사가 통과돼야
  P0-1 의 키가 나온다.
- 판정: **P0 유지.** 병렬이 아니라 **사슬**이다: `계정삭제 → 플랫폼 심사 → 키 발급 → 다중 연동`.
  이 순서를 뒤집으면 어느 것도 끝나지 않는다.

## 강등 4건

### P0-3 → **P1**. v1/v2 라우트 4쌍
- **반론**: "레일은 v2 만 건다. 레거시 URL 은 옛 북마크로만 들어오는데, 사용자가 없으니 북마크도 없다.
  오늘의 사용자 피해는 0 이다."
- 이 반론이 이긴다. 비용은 사용자 이탈이 아니라 **유지보수 혼선과 향후 분석 분할**이다.
- 판정: **P1.** 라우터 단독 변경이라 언제든 싸게 처리할 수 있다는 점도 등급을 낮춘다.

### P1-1 → **P2**. capability 하드코딩
- **반론**: "유료 기능이 새는가? 아니다. 플랜 한도는 행위 시점에 서버가 막는다."
- 확인 결과 반론이 맞다. `StreamPublishUseCase.kt:77`(월간 업로드), `ScheduleUseCase.kt:232,236`(예약),
  `CommentUseCase.kt:121` · `CommentSyncUseCase.kt:35` · `CommentEngagementUseCase.kt:166`(댓글),
  `ChannelUseCase.kt` 가 각각 `PlanLimitExceededException` 을 던진다. **메뉴가 다 보여도 유료 기능은 안 새어 나간다.**
- 판정: **P2.** 남는 것은 "프론트가 서버 주도 게이팅을 믿는데 실제로는 상수"라는 **구조적 불일치**이고,
  이는 매출이 아니라 설계 정합성 문제다.

### P1-3 → **P2**. `PORTONE_WEBHOOK_SECRET` 기동 검증
- **반론**: "`required-env.sh:22` 가 이미 배포를 막는다. 기동 검증은 중복이고, 수동 기동은 애초에 금지다."
- 대체로 이긴다. 덧붙여 두 검증 모두 **비어 있는지만** 보므로 값이 틀리면 어차피 못 잡는다. 실익이 작다.
- 판정: **P2.**

### P1-4 → **P2**. 성과 5개 / 소통 3개 통합
- **반론**: "매출과 연결된 근거가 있는가? 없다."
- 내가 P3 항목을 자를 때 쓴 잣대를 여기에도 적용하면 강등이 맞다. 근거 없이 P1 에 둔 것은 일관성 위반이었다.
- 판정: **P2.**

## 신규 P1 — 반론 검토 중 발견

### P1-NEW. 플랜 업그레이드 유도 경로가 끊겨 있다
`frontend/src/stores/comments.ts:63-66` ↔ `GlobalExceptionHandler.kt:58-63` ↔ `ResData.kt:17`

FREE 사용자가 댓글함을 열면 `PlanLimitExceededException` 이 뜬다. 프론트는 이걸 잡아
`featureUnavailable` 업그레이드 화면을 띄우려 한다. 그런데 **판정 조건이 둘 다 거짓이다.**

| 프론트 기대 | 실제 계약 |
|---|---|
| `status === 403` | `handleBusiness` 가 **400** 을 준다 (`GlobalExceptionHandler.kt:61`) |
| `data.error.code === 'PLAN_LIMIT_EXCEEDED'` | `ResData.error` 는 **String** 이다 (`ResData.kt:17`). `code` 는 직렬화되지 않는다 |

→ 업그레이드 화면 대신 `"댓글 관리 한도를 초과했습니다. 현재 플랜 한도: 0"` 토스트가 뜬다.
**유료 전환이 일어나야 할 바로 그 순간에 에러 알림이 뜬다.**

- 범위는 좁다. 프론트 전체에서 error code 를 기대하는 곳은 이 한 곳뿐이다(grep 1건). 계약 전반의 붕괴는 아니다.
- 사용자는 어쨌든 사유를 보긴 한다. 그래서 P0 는 아니다.
- 판정: **P1.** 근거가 있고 매출 경로에 직접 닿는다는 점에서, 내가 원래 P1 에 뒀던 메뉴 통합보다 정당하다.

## 확정 우선순위

| 등급 | 항목 | 근거 |
|---|---|---|
| **P0** | 계정 삭제 구현 (심사·법적, P0-1 의 선행) | `AuthUseCase.kt:288-296` |
| **P0** | 미설정 플랫폼 비활성 표시 | `oauth.ts:54-174`, `.env.production` |
| **P1** | 플랜 업그레이드 유도 경로 복구 | `comments.ts:63-66`, `GlobalExceptionHandler.kt:58-63`, `ResData.kt:17` |
| **P1** | NAVER_CLIP — 연결 목록에서 제거(권장) 또는 capability 추가 | `PlatformUploadCapability.kt:31-86` |
| **P1** | v1/v2 4쌍 redirect 정리 | `router/index.ts` |
| **P2** | capability 실동작화 / 웹훅 시크릿 기동 검증 / 메뉴 통합 / 호스트 바이너리 헬스체크 | 위 각 절 |
| **P3** | 자산 6개 정리, CSS 어댑터 축소, 잔여 i18n | 매출 근거 없음 |

**NAVER_CLIP 재정의**: capability 를 추가하는 것보다 **연결 목록에서 빼는 쪽**을 권한다.
Naver Clip 은 제3자 SaaS 발행 API 가 공개돼 있는지 자체가 미확인이다. 호출할 수 없는 API 를 위해
capability 를 채우는 것은 또 하나의 빈 약속이 된다.

## 이 검토에서 바뀌지 않은 것

운영 검증 필요 항목(§6)은 그대로다. 특히 **YouTube 쿼터 실측**은 반론의 여지가 없다 —
쿼터가 프로젝트 단위인 이상 수용 가능한 유료 사용자 수의 상한을 정하며, 코드로는 절대 확인할 수 없다.
