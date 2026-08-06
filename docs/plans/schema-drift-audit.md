# 코드-스키마 드리프트 전수 감사

조사일: 2026-08-07
발단: codex 가 `competitor_analytics_daily` 누락을 보고했다. "이 경로에 테스트가 없어 CI 가
못 잡았다"면 같은 종류가 더 있으리라 보고 전수 조사했다.

## 0. 방법

추정하지 않고 **실제 PostgreSQL 16** 과 대조했다.

1. 신선한 `postgres:16` 컨테이너에 `backend/onGo-api/.../db/migration` 의 마이그레이션을
   Flyway 버전 순서대로 전량 적용한다. (`V10_2` = 10.2 이므로 단순 문자열 정렬은 틀린다.
   버전을 파싱해 `sort -V` 로 정렬한다)
2. `information_schema` 에서 실제 테이블·컬럼을 덤프한다.
3. `Tables.kt` 의 `DSL.table(...)` / `DSL.field(...)` 선언과 대조한다.

regex 로만 마이그레이션을 훑으면 오탐이 난다. 실제 적용 결과를 본 이유다.

## 1. 결과 요약

| 항목 | 값 |
|---|---:|
| `Tables.kt` 선언 테이블 | 127 |
| 마이그레이션 전량 적용 후 실존 테이블 | 241 |
| **누락 테이블** | **7** (competitor 건 포함) |
| **컬럼 드리프트 테이블** | **1** (`media_kits`) |

## 2. 누락 테이블 7개와 판정

| 테이블 | 도달 경로 | `@Profile("wip")` | 판정 |
|---|---|---|---|
| `competitor_analytics_daily` | `CompetitorSyncScheduler` (상시) | 없음 | **V61 생성** |
| `brand_deals` | `RevenueController`, `BrandDealController` | 없음 | **V62 생성** |
| `trends` | `TrendController` | 없음 | **V62 생성** |
| `trend_alerts` | `TrendController` | 없음 | **V62 생성** |
| `video_translations` | `TranslationController` | 없음 | **V62 생성** |
| `ab_test_results` | 없음 | — | 생성 안 함 |
| `test_variants` | 없음 | — | 생성 안 함 |

### 2.1 생성하지 않은 2개

`ABTestResultRepository` / `TestVariantRepository` 를 `onGo-application`·`onGo-api` 에서
주입받는 곳이 **0건**이다(단어 경계 검색, import 제외). jOOQ 저장소만 있고 소비자가 없다.
죽은 코드를 위해 테이블을 만들지 않는다.

혼동 주의: `TestVariantRepository` 는 `ABTestVariantRepository` 와 **다른 인터페이스**다.
후자는 매시간 도는 `ABTestEvaluator` 가 쓰지만 `AB_TESTS` / `AB_TEST_VARIANTS` 만
참조하고 둘 다 실존한다. 상시 스케줄러에는 문제가 없다.

### 2.2 competitor 건과 나머지 4건의 결정적 차이

`CompetitorSyncScheduler` 는 `upsertAnalytics` 예외를 `:52-54` 에서 삼킨다. 그래서
`relation does not exist` 가 조용히 묻히고 분석만 영구 누락됐다.

**나머지 4개는 예외를 삼키는 곳이 없다.** 컨트롤러까지 그대로 올라가 사용자에게 500 이 나간다.
프론트가 실제로 호출하는 것도 확인했다 — `api/revenue.ts:58`, `api/branddeal.ts:8,14,20`,
`api/video.ts:86,92,98`.

## 3. `media_kits` — 테이블은 있는데 컬럼이 다르다

누락 테이블만 찾으면 놓치는 종류다.

`V31__collab_agency_tables.sql:154` 가 만든 `media_kits` 는
`title / template_style / platforms / demographics / top_content / campaign_results /
rate_cards / published_url` 형태다.

그런데 유일한 소비자인 `BrandDealJooqRepository.saveMediaKit` 은 전혀 다른 컬럼을 쓴다.
실제 DB 대조 결과 아래 7개가 **전부 없다.** `ALTER TABLE media_kits` 도 0건이다.

```
display_name  categories  social_links  stats_snapshot  rate_card  is_public  slug
```

추가로, `saveMediaKit` 은 `title` 을 채우지 않는다. `title VARCHAR(300) NOT NULL` 이 남아 있으면
컬럼을 추가해도 insert 가 그대로 실패한다.

**판정**: V31 쪽 형태를 쓰는 코드는 0건이라 충돌 소비자가 없다. 살아 있는 코드를 기준으로
스키마를 맞춘다(V62). 기존 컬럼은 데이터 유실을 피하려 남긴다.

## 4. 쓰기 경로 컬럼 드리프트 전수 확인

`insertInto(T)` / `update(T)` 는 대상 테이블이 구문에 명시돼 정적으로 정확히 귀속된다.
`*JooqRepository.kt` 72개 전부를 이 기준으로 실제 스키마와 대조했다.

**결과: `media_kits` 외에는 없다.** V62 적용 후 재실행하면 0건이다.

한계 두 가지를 명시한다.

- `select` 는 제외했다. `.from(T)` 뒤 where 절 필드가 조인·서브쿼리로 섞일 수 있어 정적
  귀속이 불확실하다. 실제로 이 한계 때문에 `brand_deals.platform` 은 자동 감사로 못 잡았고,
  `RevenueJooqRepository.getBrandDealRevenue` 의 select 목록을 손으로 읽어서 찾았다.
  V62 에 포함돼 있다.
- 한 파일이 여러 테이블을 `update` 하면 파일 단위 폴백이 필드를 오귀속할 수 있다.
  `media_kits` 의 확정 7개는 자동 감사가 아니라 DB 직접 조회로 확인한 값이다.

## 5. 이번 범위 밖 — 별건 (중요)

감사 중 발견했으나 고치지 않았다. 마이그레이션 범위를 넘어서는 설계 결정이다.

### 5.1 계정 삭제가 일부 사용자에게 실패한다

`AuthController:178` → `AuthUseCase.deleteAccount:218` → `UserJooqRepository.delete` 는
refresh token 만 정리하고 `DELETE FROM users` 를 그대로 실행한다.
`users(id)` 참조 FK 의 삭제 규칙은 **`NO ACTION` 105건 / `CASCADE` 17건**이다.

처음에는 이걸 보고 "모든 사용자의 탈퇴가 막힌다"고 판단했는데 **틀렸다.**
실제 DB 로 사용자 상태를 만들어 `DELETE` 를 재현해 확인한 결과는 다음과 같다.

| 사용자 상태 | 결과 |
|---|---|
| 가입 직후 (`ai_credits`, `user_settings`, `subscriptions`) | **성공** — 셋 다 CASCADE 다 |
| 영상·채널·결제·알림·스케줄 보유 | **성공** — 전부 CASCADE 다 |
| `activity_logs` 보유 | 실패하지만 **해당 없음** (아래) |
| **댓글 보유** (`comments_user_id_fkey`) | **실패** |
| **경쟁 채널 등록** (`competitors_user_id_fkey`) | **실패** |

`activity_logs` 는 `NO ACTION` 이라 있으면 막지만, `ActivityLogUseCase` 의 저장 메서드를
호출하는 곳이 0건이라(컨트롤러도 `@GetMapping` 조회 하나뿐) 실제로 행이 쌓이지 않는다.
현재로선 문제를 일으키지 않는다.

`comments` 와 `competitors` 는 다르다. 둘 다 `@Profile("wip")` 없는 컨트롤러의
살아 있는 쓰기 경로다(`FanCommunityUseCase:65`, `CompetitorUseCase:81`).
**즉 경쟁 채널을 한 번이라도 등록했거나 댓글이 동기화된 사용자는 탈퇴가 FK 위반으로 실패한다.**

선택지는 (a) 삭제 전 수동 정리, (b) FK 를 CASCADE 로 전환, (c) soft delete 다.
어느 쪽이든 되돌리기 어렵고 개인정보 삭제 요구와도 얽힌다. **별도 판단이 필요하다.**
정리 대상을 고를 때 위 표처럼 "`NO ACTION` 이면서 살아 있는 쓰기 경로가 있는 테이블"만
추리면 범위가 크게 줄어든다.

### 5.2 `media_kits` FK 에 CASCADE 가 없다

V31 이 만든 `media_kits_user_id_fkey` 에 CASCADE 가 없다. 지금까지는 insert 가 항상
실패해 테이블이 비어 있었으므로 드러나지 않았다. V62 로 쓰기가 처음 가능해지면서
5.1 의 문제를 한 건 더 늘린다. 5.1 과 함께 결정해야 해서 이번에 건드리지 않았다.

### 5.3 `trends.keyword` 선행 와일드카드 검색

`searchByKeyword` 가 `ILIKE '%..%'` 라 btree 를 못 탄다. `pg_trgm` 확장이 필요한데
확장 도입은 별건이라 넣지 않았다. 데이터가 쌓여 느려지면 그때 다룬다.

## 6. 재발 방지

이 감사 로직을 CI 가드로 넣을 것을 제안한다. 두 가지를 본다.

1. `Tables.kt` 의 `DSL.table` 선언이 마이그레이션 적용 결과에 전부 존재하는가
2. `insertInto` / `update` 가 쓰는 컬럼이 해당 테이블에 존재하는가

기존 Testcontainers IT 와 같은 방식으로 붙일 수 있다. 죽은 코드 2개는 명시적 예외 목록으로 둔다.
