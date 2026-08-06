# onGo 판매 준비도 소스 감사 보고서

- 감사일: 2026-07-15
- 판정: **NO-GO — 현재 상태로 유료 고객에게 판매하면 안 됨**
- 범위: 백엔드 Kotlin 1,081개, 프론트엔드 Vue 240개/TypeScript 175개, 운영 Flyway 47개, CI/CD·Docker·Nginx·백업/설정
- 방식: 전체 저장소 정적 패턴 스캔 + 핵심 신뢰 경계(인증, 권한, 결제, 업로드, 데이터 격리, 배포) 줄 단위 추적 + 빌드/테스트/린트/의존성 감사
- 비고: 코드는 수정하지 않았으며, 감사 기준 기록을 위해 `.impeccable.md`만 추가함

## 1. 최종 판정

제품 외형과 기능 수는 많지만, 판매 가능한 시스템이 갖춰야 할 최소 조건인 관리자 인증 경계, 결제 원장 정확성, 핵심 기능의 실제 동작, 고객 데이터 보호, 재현 가능한 배포, 회귀 테스트가 충족되지 않는다.

특히 아래 네 항목은 단독으로도 판매를 중단해야 하는 P0 차단 사유다.

1. 인증 없이 운영 ADMIN 계정을 발급하는 공개 API가 존재한다.
2. 크레딧 구매가 금액을 크레딧으로 잘못 적립하고, 실제 소비 원장에는 기록되지 않는다.
3. 운영 UI가 노출하는 기능 상당수가 `wip` 프로필로 백엔드에서 비활성화된다.
4. 저장소에 실제 DB 덤프와 평문 JWT refresh token, 사용자 식별 데이터가 커밋돼 있다.

## 2. 판매 준비도 점수

| 영역 | 점수 | 핵심 근거 |
|---|---:|---|
| 보안·권한 | 1/5 | 무인증 ADMIN 발급, XSS와 localStorage 토큰, 업로드 검증 누락 |
| 핵심 기능 완결성 | 2/5 | 에셋 업로드가 파일을 저장하지 않음, 55개 컨트롤러가 WIP |
| 결제·정산 정확성 | 1/5 | 크레딧 금액/수량 혼동, 구매 원장 누락, Toss replay 방지 없음 |
| 데이터 보호·컴플라이언스 | 1/5 | DB 덤프/토큰 커밋, 개인정보·약관·환불 고지 화면 부재 |
| 테스트·품질 보증 | 1/5 | 프론트 테스트 0, API 테스트 0, CI가 백엔드 테스트 제외 |
| 배포·운영 | 1/5 | Docker 포트/profile/env 불일치, 관측성·롤백 검증 부족 |
| UX 기술 품질 | 11/20 | 접근성 2, 성능 3, 반응형 2, 테마 2, 안티패턴 2 |

종합 판매 준비도: **약 28/100**. 기능 시연용 알파에는 가깝지만, 유료 운영 서비스 기준에는 미달한다.

## 3. 자동 검증 결과

| 검사 | 결과 |
|---|---|
| 프론트 프로덕션 빌드 | 성공 (`vue-tsc` + Vite, 1,672 modules) |
| ESLint | 오류 0, 경고 71 (`any`, 미사용 변수, `v-html` XSS 경고 포함) |
| 백엔드 테스트 | 실패: 28개 실행, 2개 Testcontainers 초기화 실패; API/common 모듈은 테스트 없음 |
| 테스트 자산 | 백엔드 테스트 파일 19개/`@Test` 119개, 프론트 테스트 0개 |
| npm production audit | High 1 (`form-data`), Moderate 1 (`dompurify`) |
| 프론트 번들 | 빌드 성공, 큰 청크: jsPDF 390KB, 앱 청크 335KB, UserManual 209KB, charts 186KB |

백엔드 테스트 실패 원인은 이 환경에서 Docker provider를 찾지 못한 것이지만, CI도 테스트를 제외하므로 정상 환경에서의 통합 통과 증거가 없다.

## 4. P0 — 판매 차단

### P0-1. 무인증 운영 ADMIN 로그인

- 위치: `backend/onGo-infrastructure/.../SecurityConfig.kt:32-45`, `backend/onGo-api/.../AuthController.kt:180-193`, `backend/onGo-application/.../AuthUseCase.kt:153-191`
- 사실: `/api/v1/auth/dev-login`이 모든 프로필에서 `permitAll`이고, 일반 `AuthController`에 있어 `@Profile` 제한이 없다. 호출 시 `admin@ongo.kr` ADMIN/BUSINESS 사용자를 자동 생성하고 토큰을 반환한다.
- 영향: 인터넷의 누구나 전체 관리자 API와 고객 데이터에 접근할 수 있다.
- 조치: 일반 컨트롤러에서 엔드포인트와 `devLogin()` 제거. 개발용 컨트롤러만 `@Profile("dev", "local")`로 유지하고 운영 부팅 시 dev endpoint bean 부재를 테스트한다.

### P0-2. 크레딧 결제 원장과 지급 수량이 잘못됨

- 위치: `PaddleWebhookService.kt:247-296`, `PaymentService.kt:82-87`, `CreditService.kt:197-215`, `CreditPackage.kt:3-12`
- 사실:
  - Paddle/Toss 결제의 `totalAmount`(원화 금액)를 크레딧 수량으로 그대로 전달한다. 예: 4,900원 상품은 정의상 500크레딧인데 4,900크레딧 지급.
  - `addPurchasedCredits()`는 `ai_credits.balance`만 올리고 `ai_purchased_credits` 행을 만들지 않는다.
  - 조회와 차감은 `ai_purchased_credits` 합계를 기준으로 하므로 구매 크레딧이 표시·소비되지 않는다.
- 영향: 과다 지급, 결제 후 잔액 불일치, 고객 분쟁, 회계/환불 불가능.
- 조치: webhook의 검증된 `price_id`를 서버 상품 카탈로그에 매핑하고, 결제/구매 패키지/크레딧 원장을 하나의 트랜잭션으로 기록한다. `reference_id`와 PG transaction ID에 DB unique를 강제하고 금액·통화·상품을 모두 대조한다.

### P0-3. 운영 UI와 백엔드 기능 계약이 광범위하게 불일치

- 위치: 105개 컨트롤러 중 55개가 `@Profile("wip")`; 프론트 라우터는 39개 업무 화면을 상시 노출 (`frontend/src/router/index.ts:24-249`).
- 대표: A/B 테스트, 자막 번역, 수익 예측, 소셜 리스닝, 콘텐츠 라이브러리, 미디어킷, 자동화/분석 확장 기능.
- 사실: 운영 `prod` 프로필에서는 WIP 컨트롤러가 등록되지 않지만 관련 메뉴/화면/API 클라이언트가 사용자에게 노출된다.
- 영향: 광고한 기능이 404/실패하며 상품 신뢰와 환불 위험이 크다.
- 조치: 판매 SKU를 실제 동작하는 기능으로 축소하고 feature manifest를 서버-클라이언트가 공유한다. WIP는 빌드 시 제거하거나 서버 capability 응답으로 숨긴다. 기능별 계약/E2E 통과 없이는 메뉴를 공개하지 않는다.

### P0-4. 실제 DB 덤프와 인증 토큰이 저장소에 포함됨

- 위치: `ongo_backup_20260223.sql:4121-4123,4176-4178`, `deploy/ongo_backup_20260218.sql` 동일 데이터
- 사실: 사용자 이메일/이름/팀 초대/콘텐츠 데이터와 평문 JWT refresh token이 Git 추적 파일에 있다. `.gitignore`는 DB dump를 제외하지 않는다.
- 영향: 저장소 접근자에게 고객/운영 데이터와 인증 자료가 노출된다. 현재 토큰이 만료됐더라도 Git 이력과 키 재사용 위험은 남는다.
- 조치: 즉시 전 토큰·JWT secret·플랫폼 키 회전, 덤프를 Git 이력에서 제거, 유출 대응 기록 작성, `*.sql` 백업 차단(마이그레이션 경로만 예외), 비식별 fixture로 교체한다.

## 5. P1 — 출시 전 필수 수정

### P1-1. 저장형/AI 매개 XSS와 브라우저 토큰 탈취

- 위치: `AiView.vue:483,669,1058-1069`, `GuidelinesEditor.vue:56-67,145-148`, `TemplatePreview.vue:29-36,70-76`, `api/client.ts:15-20,39-44,78-92`
- 사실: 사용자/AI/템플릿 문자열을 escape·검증 없이 정규식으로 HTML화해 `v-html`에 넣는다. access/refresh token은 localStorage에 있다.
- 영향: 악성 HTML 이벤트 속성이 실행되면 두 토큰이 모두 탈취돼 계정 장악으로 이어진다.
- 조치: 신뢰된 Markdown parser + 최신 DOMPurify의 엄격 allowlist를 단일 공통 컴포넌트로 사용하고, refresh token은 Secure/HttpOnly/SameSite cookie로 이동한다. CSP에서 nonce/hash를 적용하고 inline 허용을 줄인다.

### P1-2. 핵심 스트리밍 업로드에서 서버 파일 검증 미호출

- 위치: `StreamPublishController.kt:42-59`, `StreamPublishUseCase.kt:52-79,144-166`, `FileValidationUtil.kt:65-164`
- 사실: 검증 유틸은 존재하지만 핵심 `/videos/stream-publish` 경로에서 확장자/MIME/크기/실제 magic bytes 검사를 호출하지 않는다. multipart 전역 한도는 5GB다.
- 영향: 위장 파일, 비정상 대용량, 디스크 고갈, 플랫폼 API 오염. semaphore는 임시 파일 저장 후 적용돼 요청 폭주를 막지 못한다.
- 조치: 인증 직후 사용자별 rate limit과 동시성 gate를 먼저 적용하고, 허용 크기·확장자·MIME·magic bytes를 검사한 뒤 제한된 전용 볼륨에 저장한다. temp quota와 부팅 시 orphan cleanup을 추가한다.

### P1-3. 에셋 업로드가 파일을 저장하지 않음

- 위치: `AssetController.kt:47-72`, `AssetUseCase.kt:38-60`
- 사실: 파일 바이트를 storage port로 전송하지 않고 `/storage/assets/{uuid}_{name}` 문자열과 DB 행만 만든 뒤 성공을 반환한다. 삭제는 전혀 다른 key 형식 `assets/{assetId}/{filename}`을 사용한다.
- 영향: 업로드 성공 메시지 뒤 실제 파일이 없고 다운로드/삭제가 깨진다.
- 조치: 검증 → 실제 object storage upload → DB commit/보상 삭제의 일관된 흐름과 다운로드 E2E를 구현한다.

### P1-4. Toss webhook이 외부 호출 불가하며 replay/금액 검증이 없음

- 위치: `PaymentController.kt:42-57`, `SecurityConfig.kt:32-45`, `PaymentService.kt:49-88`
- 사실: public matcher는 Paddle webhook만 포함해 Toss endpoint는 JWT를 요구한다. 서명된 동일 payload 재전송을 막는 event ID/상태 guard가 없고 `payload.totalAmount`와 DB 주문 금액도 비교하지 않는다.
- 영향: 정상 결제 갱신 실패 또는 동일 이벤트에 의한 크레딧 중복 지급.
- 조치: PG 공식 서명 규격과 raw body 검증, 공개 경로 최소 허용, event/payment unique 기반 멱등성, 주문 금액/통화/상태 대조를 추가한다.

### P1-5. CI가 테스트를 의도적으로 제외

- 위치: `Jenkinsfile-backend:26-30`, `deploy/deploy.sh:93-98`
- 사실: 두 경로 모두 `-x test`로 운영 artifact를 만든다. 프론트에는 테스트 script와 테스트 파일이 없다.
- 영향: 인증·결제·권한·마이그레이션 회귀가 그대로 운영 배포된다.
- 조치: 단위 → 통합(Testcontainers) → API 계약 → 핵심 E2E를 필수 stage로 만들고 실패 시 artifact/deploy를 금지한다.

### P1-6. Docker/air-gap 배포 정의가 현재 애플리케이션과 맞지 않음

- 위치: `backend/Dockerfile:24-26`, `backend/onGo-api/src/main/resources/application.yml:1-3`, `deploy/airgap/docker-compose.prod.yml:7-16`
- 사실:
  - 앱은 8070인데 Dockerfile은 8777을 EXPOSE/health check한다.
  - JSON ENTRYPOINT 안의 `${SPRING_PROFILES_ACTIVE:-prod}`는 shell expansion되지 않는다.
  - air-gap compose의 `DB_URL`은 Spring datasource 설정에서 사용하지 않으며, JWT/암호화/결제 필수 환경값도 빠져 있다.
  - 고정 DB/MinIO 기본 비밀번호를 사용하고 MinIO 관리 포트를 외부 공개한다.
- 영향: 이미지가 unhealthy 또는 기동 실패하며, 설치형 고객 환경은 기본 자격증명에 노출된다.
- 조치: exec-form은 profile env를 Spring 표준 env로 전달하고, 포트/health check를 8070으로 통일한다. compose secrets, 내부 network, 필수 env validation, 실제 air-gap smoke test를 추가한다.

### P1-7. 의존성 취약점과 SCA gate 부재

- 위치: `frontend/package-lock.json`; 경로 `axios@1.16.1 → form-data@4.0.5`, `jspdf@4.2.1 → dompurify@3.4.7`
- 사실: npm audit 기준 High 1(CRLF injection), Moderate 1(XSS/config pollution 계열), 수정 버전 존재. 백엔드 자동 SCA 작업도 없다.
- 영향: 입력 경계와 HTML sanitization 체인에 알려진 취약점이 남는다.
- 조치: lockfile 갱신 후 audit 0 또는 승인된 예외만 허용하고, Dependabot/Renovate와 backend dependency scan을 CI gate로 둔다.

### P1-8. 개인정보·이용약관·환불/사업자 고지 경로 부재

- 위치: `frontend/src/router/index.ts:4-255` 전체 공개/인증 라우트
- 사실: 로그인·결제 화면은 있으나 개인정보처리방침, 이용약관, 환불정책, 사업자/고객지원 고지 라우트가 없다.
- 영향: 한국 소비자 대상 유료 SaaS 판매·개인정보/OAuth 처리의 법적·신뢰 요건을 충족했다고 입증하기 어렵다.
- 조치: 법률 검토를 거친 문서와 동의 버전/시각 기록, 탈퇴·보존·삭제 정책, subprocessors/국외이전 고지를 구현한다.

### P1-9. 기능별 workspace/자원 소유권 검증이 일관되지 않음

- 위치 예: `CollaborationBoardController.kt:24-83`와 `CollaborationBoardUseCase.kt:25-75`; `SubtitleTranslationController.kt:32-71`와 use case `23-49`
- 사실: 인증 userId를 받지만 무시하고 요청 workspaceId 또는 자원 id만으로 조회/수정/삭제하는 구현이 다수 존재한다. 일부는 WIP지만 WIP 활성화 순간 IDOR가 된다.
- 영향: 다른 고객의 순차 ID/workspace ID를 알면 데이터 조회·변조 가능.
- 조치: 모든 repository 쿼리를 `(resourceId, tenantId)` 형태로 만들고 membership/role을 application boundary에서 강제한다. 멀티테넌시 negative 테스트를 공통 계약으로 작성한다.

### P1-10. 계정 삭제가 연관 데이터/외부 토큰 삭제를 보장하지 않음

- 위치: `AuthUseCase.kt:217-223`
- 사실: refresh token 삭제 후 `userRepository.delete(userId)`만 호출한다. 플랫폼 OAuth revoke, object storage 삭제, 결제/법정보존 분리, 비동기 삭제 추적이 없다.
- 영향: 탈퇴 후 개인정보와 외부 접근권한이 잔존하거나 FK로 삭제가 실패할 수 있다.
- 조치: 데이터 분류별 삭제/익명화 saga, OAuth revoke, storage cleanup, 법정보존 ledger, 사용자 완료 통지를 구현한다.

## 6. P2 — 안정화 단계

### P2-1. 프로덕션 소스맵이 배포 산출물에 포함됨

- 위치: `frontend/vite.config.ts:102-105`; 빌드 결과에 다수 `.map` 생성, 배포는 `dist/*` 전체 복사 (`deploy/deploy.sh:184-192`).
- 영향: 원본 구조와 구현이 공개 서버에서 추측 가능한 URL로 다운로드될 수 있다.
- 조치: 오류 추적 서비스에만 업로드 후 공개 artifact에서는 map을 제거하거나 Nginx에서 차단한다.

### P2-2. 테스트 범위가 코드 규모에 비해 지나치게 작음

- 사실: 1,081개 main Kotlin/105 controllers/133 use cases에 테스트 파일 19개. API/common 테스트 없음. 프론트 415개 소스에 테스트 0.
- 영향: 현재 빌드 성공은 동작·권한·접근성·결제 정확성을 의미하지 않는다.
- 조치: 위험 기반으로 auth/payment/upload/tenant first, 이후 주요 사용자 journey E2E를 추가한다.

### P2-3. rate limiter가 단일 프로세스 메모리와 proxy IP에 의존

- 위치: `AuthController.kt:71,95`, `AuthRateLimiter.kt`, `SubscriptionController.kt:27-46`
- 영향: 다중 인스턴스에서 우회되며, reverse proxy 설정에 따라 모든 사용자가 같은 `remoteAddr`로 묶여 제3자가 로그인 전체를 막을 수 있다.
- 조치: 신뢰 proxy 설정 후 정규화한 client IP/user key, Redis 또는 gateway 기반 분산 제한을 사용한다.

### P2-4. 운영 관측성이 health와 파일 로그 중심

- 위치: `application.yml` management exposure `health` only, `logback-spring.xml:12-38`
- 영향: 결제 webhook backlog, 플랫폼별 실패율, 업로드 queue, AI 비용, DB pool 포화의 조기 탐지가 어렵다.
- 조치: 보호된 metrics/Prometheus, structured logs, alert rules, SLO/error budget, audit log를 추가한다.

### P2-5. 문서와 실제 스택 불일치

- 위치: `CLAUDE.md`는 Spring Boot 4.0.2/JDK25/Gradle9라고 설명하지만 `backend/build.gradle.kts`는 Spring Boot 3.4.2/JDK21이며 wrapper는 Gradle 8.14.2.
- 영향: 설치·지원·보안 패치 기준이 혼란스럽고 고객 문서 신뢰가 떨어진다.
- 조치: 실행 가능한 manifest에서 문서를 생성하고 CI에서 버전 일치 검사를 한다.

### P2-6. 운영/설계 마이그레이션 트리가 이중화됨

- 위치: 운영 `onGo-api/src/main/resources/db/migration` 47개와 `backend/sql` 90개 이상.
- 영향: 어떤 스키마가 실제 제품인지 오인하기 쉽고 WIP 테이블/코드가 제품 완성도로 잘못 집계된다.
- 조치: 실행되는 Flyway tree만 source of truth로 두고 WIP 설계 SQL은 docs/archive로 이동한다.

### P2-7. 프론트 오류를 무시하거나 로컬 mock으로 대체하는 저장소가 다수

- 위치: ESLint 미사용 catch 경고가 여러 store에 반복되고, `templates.ts`는 API 실패 시 localStorage/mock fallback, 일부 기능 데이터도 localStorage에 잔존.
- 영향: 서버 실패가 사용자에게 실제 저장 성공처럼 보이고 기기 간 데이터 불일치가 생긴다.
- 조치: 명시적 offline 모드가 아니라면 실패를 UI에 노출하고 retry/rollback하며, mock fallback은 dev build로 제한한다.

### P2-8. 서버 응답형 보안 헤더와 TLS 구성이 불완전

- 위치: `frontend/nginx.conf`, `deploy/oracle/nginx-ongo.conf`, `SecurityConfig.kt:25-29`
- 사실: 앱 CSP는 있으나 Nginx에 HSTS, nosniff, Referrer-Policy, Permissions-Policy가 없고 제공된 Oracle 설정은 HTTP 80만 정의한다.
- 영향: 실제 상위 TLS termination에 의존하며 설치형/오구성 환경의 방어층이 약하다.
- 조치: TLS topology를 문서화하고 reverse proxy 표준 보안 헤더/HTTPS redirect/config test를 제공한다.

## 7. P3 — 정리/폴리시

1. 대형 단일 파일: `AiView.vue` 1,346줄, `ScheduleView.vue` 1,288줄, `AnalyticsView.vue` 1,199줄, `VideosView.vue` 1,017줄. 변경 영향과 테스트 난이도를 높인다.
2. ESLint 경고 71개와 `any`가 store/API 오류 경계를 흐린다. lint warning budget을 0으로 낮춘다.
3. `.vue.backup`, 대시보드 캡처/체크 파일, 중복 백업·계획 산출물을 제품 소스와 분리한다.

## 8. UI 기술 감사 (audit health)

| # | 차원 | 점수 | 핵심 근거 |
|---|---:|---:|---|
| 1 | 접근성 | 2/4 | 공통 focus/aria/reduced-motion 노력은 있으나 모달/직접 구현과 85개 outline-none 지점, 자동 검증 부재 |
| 2 | 성능 | 3/4 | route lazy loading/manual chunks/PWA 양호; PDF·차트 큰 청크와 과대 화면 컴포넌트 존재 |
| 3 | 반응형 | 2/4 | breakpoints와 모바일 dashboard는 있으나 600~900px 고정 최소폭 표/캘린더가 반복됨 |
| 4 | 테마 | 2/4 | dark mode/token 파일은 있으나 실제 코드는 Tailwind gray/blue와 hex 309건으로 분산 |
| 5 | 안티패턴 | 2/4 | 동일 카드 반복, purple/blue AI palette, glass utilities, hero metrics/과도한 기능 표면 |
| 합계 |  | **11/20** | **Acceptable — 상당한 수정 필요** |

### 안티패턴 판정

**Fail: AI 생성형 제품처럼 보일 가능성이 높다.** `card` 기반 동일 구조, purple/blue 강조, glassmorphism utility, 대시보드 metric 카드, 매우 많은 기능 화면이 동일한 시각 문법으로 반복된다. 특히 실제 동작하지 않는 WIP 기능을 폭넓게 노출한 점이 “깊이보다 기능 수가 많은 생성형 UI” 인상을 강화한다.

### 접근성/반응형 대표 근거

- 긍정: `useAccessibility.ts`에 focus trap, arrow navigation, live announcement, reduced-motion 지원이 있고 `main.css:205-214`에서 전역 reduced motion을 처리한다.
- 개선: 37개가량의 modal 성격 파일이 있고 구현이 분산돼 focus return/inert/ESC/aria contract를 일괄 보장하기 어렵다.
- 개선: `AssetsView.vue:421`, `AnalyticsView.vue:530`, `ScheduleView.vue:262-281`, `CsvPreviewTable.vue:194` 등에 640~900px 최소폭이 반복된다. 의도적 가로 스크롤이라도 keyboard/스크린리더 문맥과 모바일 대체 뷰가 필요하다.
- 개선: token 정의는 있으나 공통 `main.css` 자체도 token 대신 gray/primary utility를 사용해 두 체계가 공존한다.

## 9. 긍정적 발견

- 플랫폼 OAuth token 암호화는 AES-256-GCM과 random 12-byte IV를 사용한다 (`AESEncryptionUtil.kt`).
- refresh token 신규 repository는 SHA-256 hash만 DB에 저장하고 rotation을 적용한다.
- 핵심 video 조회/수정/삭제/publish 경로는 userId 소유권 검사를 수행한다.
- Paddle webhook은 raw body 서명, 5분 timestamp, event ID 멱등성 구조와 retry/dead-letter 기반을 갖췄다.
- production profile에서 Swagger를 비활성화하고 Actuator 노출을 health로 제한한다.
- 프론트 route lazy loading, vendor chunk 분리, reduced-motion, dark mode, 공통 focus utility가 있다.
- request ID, rolling logs, platform upload semaphore, Resilience4j retry/circuit breaker 등 운영 기반을 고려했다.
- 프론트 타입 검사와 프로덕션 빌드는 현재 성공한다.

## 10. 수정 순서와 출고 게이트

### 1단계 — 즉시 격리 (24시간)

1. `/auth/dev-login` 운영 차단 및 모든 관리자/JWT/플랫폼/결제 secret 회전.
2. Git DB dump 제거와 유출 범위 확인.
3. 결제/크레딧 구매 일시 중단 또는 feature flag 차단.
4. WIP 메뉴를 운영 UI에서 전부 숨겨 실제 판매 범위를 고정.

### 2단계 — 신뢰 경계 복구

1. 결제 원장·상품 매핑·멱등성 재설계 및 PG sandbox 통합 테스트.
2. 업로드 검증/저장/삭제 흐름과 resource quotas 보강.
3. XSS 제거와 refresh token HttpOnly cookie 전환.
4. tenant-aware repository 및 IDOR negative test 도입.
5. 계정 삭제/개인정보 보존 정책 구현.

### 3단계 — 판매 가능한 최소 SKU

다음 흐름만 우선 E2E로 증명한다: 소셜 로그인 → 채널 연결 → 영상 업로드 → 플랫폼 게시 → 상태/실패 복구 → 분석 조회 → 구독/결제/취소 → 탈퇴. 이 흐름에 포함되지 않는 WIP 기능은 판매 문구와 UI에서 제외한다.

### 4단계 — CI/CD 및 법적 출고

- CI 필수 gate: backend unit/integration, frontend unit, API contract, Playwright 핵심 journey, lint 0 warning, dependency audit, migration clean install/upgrade.
- 운영 gate: staging smoke, backup restore drill, rollback drill, metrics/alert, runbook/on-call.
- 상품 gate: 개인정보처리방침, 이용약관, 환불정책, 사업자/지원 정보와 동의 증적.

### 판매 재판정 조건

- P0 0건, P1 0건.
- 핵심 journey E2E 100% 통과 및 CI에서 강제.
- 결제 sandbox의 구매/중복 webhook/환불/취소/연체 시나리오 통과.
- 신규 고객 환경에서 자동 배포와 restore drill 성공.
- 보안 재감사 및 외부 침투 테스트의 high/critical 0건.

## 11. 권장 후속 명령 순서

1. **[P0] `/harden`** — dev-login, 결제 원장, tenant/IDOR, 업로드 및 XSS 경계 복구
2. **[P1] `/distill`** — WIP 기능을 판매 SKU에서 제거하고 실제 지원 범위를 축소
3. **[P1] `/optimize`** — bundle/source-map/대형 컴포넌트와 업로드 resource pressure 개선
4. **[P1] `/adapt`** — 고정 최소폭 표·캘린더의 모바일 대체 표현과 touch target 검증
5. **[P2] `/normalize`** — token과 Tailwind 색상/간격 체계를 하나로 통합
6. **[P2] `/clarify`** — 실패·재시도·결제·환불·데이터 삭제 UX 문구 정리
7. **[P3] `/polish`** — 최종 접근성, 시각 일관성, micro-detail 출고 점검

수정 후 `/audit`을 다시 실행해 점수와 P0/P1 잔존 여부를 확인해야 한다.

