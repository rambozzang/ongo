# 수익화 구현 보고서 — Codex C1·C2·C3

작성일: 2026-09-23  
기준: `docs/work/monetization-contract.md`

## 결과 요약

- **C1 반복 예약:** 예약 생성·수정에 Free 차단 및 첫 실행/다음 실행일의 플랜 한도를 적용했다. `ScheduleUseCase.validateScheduleLimit`와 반복 예약이 공용 `SchedulePlanLimit` 규칙을 사용한다. 다운그레이드 후 현재 플랜 한도를 넘는 기존 반복 예약은 실행기가 해당 정의를 비활성화하고 게시물을 생성하지 않는다.
- **C2 분석 기간:** 서버에서 사용자 `planType.analyticsDays`로 요청 기간을 clamp하고, `periodLimit` 메타데이터에 요청일·적용일·최대일·잘림 여부를 담는다. 대시보드와 최적 게시 시간 화면에 제한 안내를 표시한다.
- **C3 경쟁 채널 유료 가치:** 플랜 목록 DTO와 프런트 플랜 타입/비교표에 경쟁 채널 한도를 노출한다. 서버 한도 초과는 표준 `PLAN_LIMIT_EXCEEDED`를 사용하고 경쟁 채널 화면에서 업그레이드 CTA로 연결한다. 한·영 사용자 매뉴얼도 갱신했다.

## C2 적용 API 인벤토리

기간 제한을 서버에서 적용하고 응답에 `periodLimit`을 싣는 API:

- `/api/v1/analytics/dashboard`
- `/api/v1/analytics/trends`
- `/api/v1/analytics/videos/{id}`
- `/api/v1/analytics/heatmap` — 고정 30일 조회를 `days` 입력 및 플랜 clamp로 변경
- `/api/v1/analytics/top-videos`
- `/api/v1/analytics/platform-comparison`
- `/api/v1/analytics/optimal-times` — 고정 30일 조회를 `days` 입력 및 플랜 clamp로 변경
- `/api/v1/analytics/tags`
- `/api/v1/analytics/compare`
- `/api/v1/analytics/cohort` — `from`/`to` 범위를 허용 기간으로 축소
- `/api/v1/analytics/traffic-sources`
- `/api/v1/analytics/demographics`
- `/api/v1/analytics/ctr`
- `/api/v1/analytics/avg-view-duration`
- `/api/v1/analytics/cross-platform`
- `/api/v1/analytics/subscriber-conversion`

다음은 별도 계약 검토 대상이다. 기간 조회 파라미터가 없는 성능 점수(`/videos/{id}/performance-score`), 이상 탐지(`/anomalies`), 리텐션(`/videos/{id}/retention`), 라이브 대시보드·알림(`/live*`), 히트맵 추천(`/heatmap/recommendations`)에는 이번 요청 기간 clamp를 적용하지 않았다. 이들 API가 과거 데이터 접근이나 기간별 집계를 노출하는지 제품 정책으로 확인한 뒤 별도 범위를 정해야 한다.

## 회귀 및 변이 검증

- 백엔드: `:onGo-api:compileKotlin`, `:onGo-infrastructure:test --tests '*HeatmapQueryContractTest'`, 그리고 `RecurringScheduleUseCaseTest`, `RecurringScheduleExecutorTest`, `SchedulePlanLimitTest`, `AnalyticsPeriodLimitTest`, `CompetitorUseCaseSyncTest` 관련 테스트 성공.
- 프런트: `npx vitest run` — 150개 파일, 1,275개 테스트 통과.
- 타입 검사: `npx vue-tsc --noEmit -p tsconfig.app.json` 통과.
- 변이 검증: 반복 예약 Free 제한을 우회하도록 공용 게이트를 바꾸면 스케줄 정책 테스트 실패, 분석 clamp를 우회하면 분석 기간 테스트 실패, Free 경쟁 채널 한도를 2에서 3으로 바꾸면 가격 계약 테스트 실패함을 확인하고 각 변경을 즉시 원복했다.

## 변경 경계

요금·크레딧 값과 자동 갱신은 변경하지 않았다. Codex는 마이그레이션을 추가하지 않았고 커밋도 만들지 않았다. 동일 작업트리에서 진행 중인 Claude M1의 업로드 소스/할당량 관련 변경 및 `V116__video_source_derived.sql`은 Codex 소유가 아니므로 수정하지 않았다. 특히 `RecurringScheduleExecutor`의 저장 행에 대한 Claude 변경은 보존했다.

## Claude 리뷰 수정 (2026-09-23)

1. **`ResData.periodLimit` 가 모든 응답에 `null` 로 실렸다** — `@field:JsonInclude(NON_NULL)` 추가.
   `ResDataSerializationTest` 로 고정, 어노테이션을 지우면 실패함을 변이로 확인.
2. **반복 예약이 요금제 하향으로 조용히 멈췄다** — 사용자는 예약이 왜 안 올라갔는지 알 길이 없었다.
   `RecurringScheduleExecutor` 가 멈출 때 SYSTEM 알림("반복 예약이 멈췄습니다")을 남긴다. 알림 저장이
   실패해도 비활성화는 유지된다(테스트 고정).
3. **히트맵 기본값 `days = Int.MAX_VALUE` 가 실DB 에서 모든 호출을 깨뜨렸다** — `now - Int.MAX_VALUE`
   가 PostgreSQL 날짜 범위를 넘어 `date out of range`. 기본값을 쓰는 곳이 유료 AI 일정 추천
   (`SuggestScheduleUseCase`)과 라이브 대시보드라 운영에서 그대로 터졌을 결함이다. 목 테스트는 통과했고
   `HeatmapPublishedAtIT`(실 PG)만 10건 실패로 드러냈다. `days: Int? = null` → 기간 조건 없음으로 바꾸고,
   기간 창이 실제로 거르는지 IT 1건을 추가했다(조건을 지우면 실패함을 변이로 확인).
