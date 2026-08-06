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

#### 해법 — codex 검토 반영

세 선택지 (a) 수동 정리 (b) CASCADE 전환 (c) soft delete 를 놓고 codex 검토를 받았다.
결론은 **어느 하나를 통째로 고르는 게 아니다.**

- **(a) 수동 정리**: 응급 데이터 복구용이지 반복 가능한 self-service 탈퇴 경로가 아니다.
- **(c) soft delete**: delete 의미 자체를 바꾼다. 모든 조회에 필터, PII 익명화, 보존기간
  정책이 따라붙는데 정작 FK 실패는 그대로 남는다.
- **(b) 전면 CASCADE**: 위험하다. `NO ACTION` 105건을 일괄로 바꾸면 audit 성격이거나
  공유 참조인 테이블까지 연쇄 삭제된다.

**권장안**: "명시적 자식 삭제 + 사용자 삭제"를 한 트랜잭션으로 묶고, 소유가 확실한
`comments.user_id` / `competitors.user_id` 부터 순서를 고정한다. 굳이 FK 를 바꾼다면
이 **두 개에만 표적 CASCADE** 를 검토하되 ownership 과 FK 전체 영향 확인,
Testcontainers DELETE 재현을 선행한다.

`activity_logs` 처럼 보존이 필요할 수 있는 `NO ACTION` 은 **무조건 CASCADE 하지 않는다.**
제품 차원의 보존/익명화 결정을 먼저 받아야 한다.

대상을 고를 때 "`NO ACTION` **이면서** 살아 있는 쓰기 경로가 있는 테이블" 조건으로 추리면
105개에서 몇 개로 줄어든다.

#### 범위 주의

사용자 삭제는 DB 행만으로 끝나지 않는다. 영상·외부 스토리지, 플랫폼 토큰·연동,
결제 데이터의 삭제/익명화는 별도 체크리스트로 분리해야 한다. 법적 의무는 여기서 단정하지
않고 제품·법무 확인 항목으로 남긴다.

최소 검증 기준: 댓글 보유 / 경쟁 채널 보유 / 둘 다 보유 / `activity_logs` 보유 fixture 를
각각 `DELETE` 하고 성공·의도된 보존·롤백을 실제 PostgreSQL 로 단언한다.

### 5.2 `media_kits` FK 에 CASCADE 가 없다

V31 이 만든 `media_kits_user_id_fkey` 에 CASCADE 가 없다. 지금까지는 insert 가 항상
실패해 테이블이 비어 있었으므로 드러나지 않았다. V62 로 쓰기가 처음 가능해지면서
5.1 의 문제를 한 건 더 늘린다. 5.1 과 함께 결정해야 해서 이번에 건드리지 않았다.

### 5.3 `trends.keyword` 선행 와일드카드 검색

`searchByKeyword` 가 `ILIKE '%..%'` 라 btree 를 못 탄다. `pg_trgm` 확장이 필요한데
확장 도입은 별건이라 넣지 않았다. 데이터가 쌓여 느려지면 그때 다룬다.

## 6. 재발 방지 — `SchemaDriftGuardIT` (구현 완료)

`onGo-infrastructure/src/test/kotlin/.../SchemaDriftGuardIT.kt` 로 넣었다.
codex 검토를 반영해 **테이블 가드와 컬럼 가드를 분리**했고, 이번에는 테이블 가드만 넣는다.

**동작**: Testcontainers `postgres:16` 에 Flyway 가 실제 마이그레이션을 적용한 뒤,
`Tables` 오브젝트를 **리플렉션**으로 읽어 선언된 테이블이 `information_schema` 에
전부 있는지 대조한다. 소스를 파싱하지 않는다. 파싱은 포맷이 바뀌면 조용히 헛돌지만
리플렉션은 컴파일된 실제 선언을 본다.

**탐지력을 실증했다.** 예외 목록을 비우고 돌리면 정확히 `ab_test_results`,
`test_variants` 두 개를 지목하며 실패한다. 즉 이 가드가 있었다면
`competitor_analytics_daily` 를 포함한 7건을 전부 잡았다.

**설계 판단 두 가지**

- `BASE TABLE` 로 한정한다. 현재 뷰는 0건이고, `analytics_daily` 는 파티션 부모라
  `BASE TABLE` 로 잡혀 문제없다. 자식 파티션은 `Tables.kt` 에 없고 가드는
  선언 → 실존 **한 방향만** 보므로 영향이 없다.
- 선언을 100개 미만으로 읽으면 먼저 실패시킨다. 리플렉션이 깨져 선언 0건이 되면
  가드가 아무것도 검증하지 않은 채 통과하기 때문이다. 통과의 의미를 지키려는 장치다.

**예외 목록**은 §2.1 의 죽은 코드 2개뿐이다. 두 번째 테스트가 "예외에 넣어놨는데 실제로는
존재하는 테이블"을 잡아낸다. 나중에 테이블이 생기면 예외를 걷어내게 강제해서, 예외 목록이
가드의 영구 사각지대로 굳는 것을 막는다.

### 아직 안 한 것 (codex 권고 반영)

- **컬럼 가드는 넣지 않았다.** `Tables.kt` 의 컬럼을 정적으로 테이블에 귀속시키면
  조인·`DSL.field`·alias 에서 오탐과 누락이 생긴다(§4 의 한계와 같은 이유).
  쓰기 경로는 저장소 SQL contract IT, 읽기 경로는 실제 SELECT 재현 IT 로 보강하는 편이
  안전하다. 별건으로 남긴다.
- **운영 DB 의 기존 드리프트**는 이 가드가 보지 않는다. 신선한 DB 에 마이그레이션을
  적용한 결과만 본다. 운영 DB 는 별도 read-only 감사가 필요하다.
