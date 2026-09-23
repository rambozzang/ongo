# 수익화 완성 작업 — 계약서 (2026-09-23)

근거: `docs/work/monetization-audit.md` (Codex 감사, Claude 가 Top 3 코드 검증 완료).
사용자 지시: "물어보지 말고 최고의 완성본". **단, 실제 고객 카드·가격에 닿는 변경은 하지 않는다**
(자동 갱신 활성화, 요금·크레딧 단가 변경 금지 — 근거와 준비만 만든다).

## 원칙
- 한도는 **서버가 강제**한다. 화면만 막는 것은 한도가 아니다.
- 막을 때는 `PlanLimitExceededException`(안정 코드 `PLAN_LIMIT_EXCEEDED`)으로 막아, 프런트의
  기존 업그레이드 CTA(`usePlanLimit`)가 그대로 동작하게 한다. 새 오류 코드를 만들지 않는다.
- 요금제 판정은 `user.planType`(users.plan_type)을 쓴다 — 채널·예약 게이트와 같은 기준.
- 커밋은 Claude 만. 마이그레이션(Vxxx)은 Claude 만 추가한다.
- Gradle 을 돌리다 XML 쓰기·파일 잠금 오류가 나면 동시 빌드 충돌이다. 재실행할 것.

## Codex 담당 (Claude 는 읽기만)
### C1. 반복 예약 요금제 게이트
- `RecurringScheduleUseCase` 생성·수정에서 Free 차단, 첫 실행/다음 실행이 `planType.scheduleDays`
  를 넘으면 거부. `ScheduleUseCase.validateScheduleLimit` 와 **같은 규칙**을 공유(중복 구현 금지 —
  공용 함수로 뽑을 것).
- 이미 만들어진 반복 예약이 다운그레이드 후에도 돌지 않게 `RecurringScheduleExecutor` 실행 직전
  검사가 필요한지 판단해 구현. (주의: Claude 가 같은 Executor 의 `videoRepository.save` 한 줄에
  `source = VideoSource.DERIVED` 를 넣는다 — 그 한 줄은 건드리지 말 것. 충돌 시 Claude 가 병합.)
### C2. 분석 기간 강제
- `PlanType` 의 분석 기간(Free 7 / Starter 30 / Pro 365 / Business 무제한)을 분석 조회 API 가 강제.
  요청 `days` 가 넘으면 **거부가 아니라 한도로 잘라서** 돌려주고, 응답에 잘렸다는 사실과 한도를
  싣는다(화면이 "Pro 에서 1년 보기" 안내를 띄울 수 있게). 프런트 표시까지.
- 적용 대상 엔드포인트 목록을 보고서에 적을 것. 빠진 엔드포인트가 있으면 그것이 새는 곳이다.
### C3. 숨은 유료 가치 노출
- 경쟁 채널 한도를 플랜 응답 DTO(`SubscriptionUseCase` 플랜 목록)와 `PlanComparisonTable.vue`,
  `frontend/src/types/subscription.ts` 에 추가. `planPricingContract.test.ts` 계약 갱신.
- `COMPETITOR_LIMIT` 등 경쟁 채널 한도 초과 시 업그레이드 CTA 로 이어지는지 확인·보강.
- 사용자 매뉴얼(`frontend/src/components/manual/manualSections.ts`) 한/영에 요금제별 차이 반영.

### Codex 완료 조건
- 각 항목 회귀 테스트 + 변이 검증(되돌려 실패 확인 후 원복).
- 관련 모듈 테스트, 프런트 `npx vitest run`·`npx vue-tsc --noEmit -p tsconfig.app.json` 통과.
- 결과 보고: `docs/work/monetization-codex-report.md` + 터미널에 '구현 완료' 요약.

## Claude 담당 (Codex 는 읽기만)
### M1. 월 업로드 한도를 모든 진입점에 + "무엇을 업로드로 셀지" 정책
- 현재 검사는 `StreamPublishUseCase` 한 곳뿐이고, 카운트(`countByUserIdAndMonth`)는 쇼츠 클립·
  재활용·반복 예약 사본까지 모든 영상 행을 센다(쇼츠 한 번 돌리면 무료 사용자가 업로드를 못 함).
- 정책: 사용자가 새로 들여온 원본(UPLOAD_PC·GOOGLE_DRIVE·URL_IMPORT)만 센다. 쇼츠 결과물은
  GENERATED, 재활용·반복 예약 사본은 신규 DERIVED(V116).
- 소유 파일: `UploadVideoUseCase`, `StreamPublishUseCase`, `AssetToVideoUseCase`, 가져오기 작업 생성부,
  `RenderedClipPersister`, `RecycleVideoUseCase`, `RecurringScheduleExecutor`의 save 한 줄,
  `VideoRepository`·`VideoJooqRepository`, `VideoSource`, 신규 `MonthlyUploadQuota*`, V116, 스키마 가드들.
### M2. 쇼츠 단위경제 근거
- 원가 측정·가격 판단 자료. 가격은 바꾸지 않는다.
