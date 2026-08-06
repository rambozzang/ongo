# WIP 프로필 55개 — 최종 판정

작성: 2026-08-07 · 근거 문서 3종을 합친 결론
- `wip-inventory.md` (백엔드 근거)
- `wip-frontend-refs.md` (프론트 근거)
- `wip-abtest-activation-gate.md` (활성화 시나리오)

---

## 1. 판정 요약

| 판정 | 개수 | 내용 |
|---|---:|---|
| **keep-enable 후보** | 1 | ABTest — 게이트 통과 조건부 |
| **keep-disabled** | 51 | 어댑터·실행 계약 미완성 |
| **remove 후보 (승인 대기)** | 3 | MusicRecommender, CreatorAcademy, FanPoll |
| **remove 확정** | **0** | 아래 §4 참조 |

## 2. 이번 감사의 핵심 사실

**a. 55개 패키지 전부 테스트 소스 0건이다.**
현재 457개 통과는 이 55개의 기본 프로필 노출을 **전혀 증명하지 않는다.**
활성화는 별도 게이트다.

**b. `V24` 가 스스로 스텁이라고 적고 있다.**
> `V24__stub_features_tables.sql:1-2` — "V24: Stub Features Tables / 52개 스텁 엔드포인트를 실제 구현하기 위한 테이블 생성"

**테이블 존재는 구현 완료의 근거가 아니다.** 스텁을 구현하려고 만든 테이블이다.
이 감사에서 "마이그레이션이 있으니 완성됐다"는 추론을 쓰지 않은 이유다.

**c. `@Profile("wip")` 은 장식이 아니라 하중을 받는 구조물이다.**
`SubtitleEditorRepository` 는 도메인 인터페이스만 있고 infrastructure 구현체가 0건이다.
프로필을 떼면 404 가 아니라 **스프링 컨텍스트 기동이 실패한다.**
(대조군: `PipelineRunRepository`→`ShortsRunJooqRepository` 는 어댑터가 있다)
미완성 기능이 앱을 못 뜨게 하는 것을 프로필이 막고 있었다.

**d. 컨트롤러가 막혀도 스케줄러는 돌 수 있다 — 실제로 하나 있었다.**
`ABTestController`/`ABTestUseCase` 는 wip 이지만 `ABTestEvaluator` 는 `@Component` +
`@Scheduled`(매시간)에 `@Profile` 이 없었다. 의존 빈도 전부 게이트가 없어 기본 프로필에서
실제로 돌았다. 트랜잭션 항목별 격리 결함이 있었고 `372ee1f` 에서 수정했다.
55개 패키지 전수 조사 결과 이런 케이스는 **이 하나뿐**이며 `@EventListener` 는 0개다.

## 3. keep-enable 후보 — ABTest 하나

두 관점이 모두 준비됐다고 본 유일한 기능이다.
- 백엔드: `V4` 테이블 + ABTest/variant/Video jOOQ 어댑터 확인, 외부 AI credential 의존 없음
- 프론트: `api/stores/abtest.ts`, `AbTestView.vue`, `components/abtest/*` 완비.
  **라우트·nav 만 없다** (kimi F2)

**활성화 전 조건**
1. `ABTestUseCase` / `ABTestController` 테스트 (현재 0건. Evaluator 테스트 4개만 있음)
2. 기본 프로필 컨텍스트 기동 확인
3. 프론트 라우트 1개 추가
4. `ABTestCompletedEvent` 소비자 부재 확인 — 발행만 하고 받는 곳이 없다.
   의도된 것인지 판단 필요

`ContentCalendarAi` 와 `ChannelHealth` 는 백엔드 어댑터가 있으나
전자는 모달이 `/calendar` 리다이렉트 뒤에 고아로 남아 있고(kimi F2), 후자는 프론트 근거가 약하다.
2순위로 둔다.

## 4. remove 를 확정하지 않은 이유

kimi F6 3개(MusicRecommender, CreatorAcademy, FanPoll)는 프론트 흔적이 전무하고
infrastructure 어댑터도 외부 호출자도 0이다. 그러나 **전용 테이블이 있다.**

| 기능 | 전용 테이블 | 어댑터 | 외부 호출자 | 프론트 |
|---|---|---|---|---|
| MusicRecommender | `music_recommendations` (V26) | 없음 | 없음 | 없음 |
| CreatorAcademy | `academy_courses/lessons/enrollments` (V33) | 없음 | 없음 | 없음 |
| FanPoll | `fan_polls` (V29) | 없음 | 없음 | 없음 |

삭제하려면 테이블 폐기까지 함께 결정해야 하고, 그건 마이그레이션 되돌리기라
되돌리기 어려운 작업이다. 지금 이 셋은 프로필로 막혀 있어 **아무 해도 끼치지 않는다.**
급하지 않은 일에 되돌리기 어려운 변경을 하지 않는다.

→ **remove 확정 0개.** 위 표를 근거로 남기고 판단은 유보한다.

## 5. 별도로 다룰 것 (이번 범위 밖)

Codex 조사에서 나온 관찰이다. WIP 와 무관하게 **이미 상시 실행되는** 경로다.
- `AnalyticsSyncScheduler.kt:33-101` — 채널 분석 상시 갱신. `ChannelHealthUseCase` 가 그 결과를 쓴다
- `CompetitorSyncScheduler.kt:19-56` — 경쟁자 데이터 상시 갱신. `CompetitorAnalysis` WIP 와 도메인 계약이 겹친다

backend 전체에 `@Scheduled` 20개 메서드 / `@EventListener` 2개가 있고 **전부 `@Profile` 이 없다.**
`CreditScheduler`·`ABTestEvaluator` 에서 나온 트랜잭션 항목별 격리 결함이
나머지에도 있는지는 **미확인**이다. '상시 스케줄러 감사'로 별도로 다룬다.
