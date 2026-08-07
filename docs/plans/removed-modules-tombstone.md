# 삭제한 모듈 기록 (tombstone)

목적: 지운 것을 **왜** 지웠는지, **무엇을 남겼는지** 남긴다.
나중에 "이 기능 어디 갔나"를 물을 때 여기서 답이 나와야 한다.

전제 문서: `wip-final-judgment.md`, `wip-frontend-refs.md`

## 원칙

1. **코드만 지운다. 테이블과 마이그레이션은 건드리지 않는다.**
   빈 테이블은 해를 끼치지 않고, 마이그레이션 되돌리기는 되돌리기 어렵다.
   `DROP TABLE` 은 별도 승인 사항이다.
2. **한 번에 하나씩** 지우고 매번 전체 테스트를 돌린다.
3. 지우기 전에 **패키지가 정의한 모든 타입의 외부 참조가 0** 임을 확인한다.
4. 테스트 통과를 런타임 호환성의 증거로 삼지 않는다.
   (이번 머지에서 `springdoc`/Boot 조합 문제가 컴파일·테스트를 모두 통과하고
   런타임에서만 터지는 것을 확인했다)

## 1차 — F6 (프론트 흔적 전무) 3개

`wip-frontend-refs.md` 의 F6 분류다. 프론트에 뷰·api·i18n 키가 **하나도** 없다.

### 공통 근거 (실측)

| 항목 | 결과 |
|---|---|
| 패키지 정의 타입의 외부 참조 | **0건** (컨트롤러·유스케이스·도메인·DTO 전부) |
| 인프라 어댑터 | **없음** — 도메인 인터페이스만 있고 구현체 0개 |
| 프론트 흔적 | **없음** — 뷰·api 클라이언트·i18n 키 전부 0 |
| 컨트롤러·유스케이스 프로필 | 둘 다 `@Profile("wip")` |
| 테스트 | 0건 |

마지막 항목이 중요하다. 컨트롤러와 유스케이스가 **둘 다** wip 이라 기본 프로필에서는
빈이 아예 생성되지 않는다. 즉 이 코드는 **한 번도 실행된 적이 없다.**
지워서 잃는 것이 없고, 되살리려면 어차피 다시 써야 한다.

### 대상

| 모듈 | 삭제 커밋 | 파일 | 남겨두는 테이블 (마이그레이션) |
|---|---|---:|---|
| `musicrecommender` | `12b3361` | 8 | `music_recommendations` (V26) |
| `creatoracademy` | `a272278` | 8 | `academy_courses`, `academy_lessons`, `academy_enrollments` (V33) |
| `fanpoll` | `fa4b635` | 7 | `fan_polls`, `poll_options` (V29) |

합계 22파일 678줄. 각 커밋마다 컴파일 + 전체 541 테스트를 돌렸다.
`wip` 컨트롤러 55 → 52.

**보존한 것**
- 위 테이블과 해당 Flyway 마이그레이션 — 변경하지 않았다. `DROP TABLE` 은 별도 승인 사항이다
- `Tables.kt` 의 테이블 상수 — 되살릴 때 다시 써야 하므로 남긴다.
  (`SchemaDriftGuardIT` 는 선언→실존 한 방향만 보므로 상수가 남아도 실패하지 않는다)
- 프론트: 지운 것 없음. 애초에 이 셋은 프론트 흔적이 0이었다(F6)

## 2차 — A 그룹 21개 (외부 소비자 0)

`wip` 52개를 전수 스캔해 각 패키지가 정의한 **모든 타입**의 패키지 밖 참조를 셌다.

| 배치 | 커밋 | 모듈 | 파일 |
|---|---|---|---:|
| 1/3 | `dc28773` | brandvoice, collaborationboard, commentsummary, competitoranalysis, contentlibrary, contentrewriter, contentrights | 43 |
| 2/3 | `86238e6` | contentseries, contentversioning, copyrightcheck, creatorbenchmark, fancommunity, faninsights, hashtaganalytics | 49 |
| 3/3 | `17f208c` | livestream, moodboard, platformhealth, sociallistening, sponsorship, subtitletranslation, trendpredictor | 51 |

**공통 근거 (실측)**: 타입 외부 참조 0 / 컨트롤러·유스케이스·서비스 전부 `@Profile("wip")` /
`@Scheduled` 0개.

**배치마다 검사 4종**: 컴파일+전체 543 테스트 / 기본 프로필 smoke(context refresh + MVC
wiring) / 잔여 참조 0 / `Tables.kt`·Flyway 미변경.

### 스캔이 걸러낸 것 — 지웠으면 깨졌을 것들

27개가 "소비자 있음"으로 제외됐다. 그중에는 **비-WIP 상시 경로**가 쓰는 것이 있었다.

| 패키지 | 소비자 |
|---|---|
| `sentimentanalyzer` | `CommentUseCase` (비-WIP) |
| `scriptwriter` | `ShortsRenderSpecBuilder` (UGC 파이프라인, 비-WIP) |
| `mediakit` | `BrandDealController` / `BrandDealRepository` |
| `audiencesegment` | `AudienceController` / `AudienceRepository` |
| `calendarinsights` | `AnalyticsUseCase` |

"wip 이니까 안 쓰인다"로 판단했으면 전부 깨졌다.

### 남은 관찰 — 짝을 이루는 중복 구현

서로를 참조해 둘 다 "소비자 있음"으로 잡히는 쌍이 여럿이다.
`portfolio` ↔ `portfoliobuilder`, `influencermatch` ↔ `creatornetwork`,
`scriptwriter` ↔ `videoscriptassistant`, `contentcalendarai` ↔ `aicalendar`.

같은 기능을 두 번 만든 것으로 보인다. 정리하려면 **어느 쪽이 정본인지** 판단이 필요해
이번 범위 밖으로 뒀다.

## 누계

F6 3개 + A 21개 = **24개 모듈, 165파일**. `wip` 컨트롤러 **55 → 31**.
테이블·마이그레이션은 전부 보존했다.

## 되살리는 방법

`git log --all --oneline -- backend/**/<패키지명>/` 으로 삭제 커밋을 찾아
`git show <커밋>` 하면 전체 내용이 나온다. 테이블이 남아 있으므로 코드만 복원하면 된다.

## 남은 분류 (이번에 지우지 않음)

| 분류 | 개수 | 이유 |
|---|---:|---|
| B — 어댑터 있고 UI 없음 | 7 | 어댑터는 실행 투자와 스키마 계약이다. UI 부재만으로는 근거가 부족하다(codex). 기능별 소유자·매출 가치 판단 후 별도 결정 |
| F4 — 죽은 i18n 키만 | 28 | 백엔드 코드 삭제와 **분리**한다. ko/en 양쪽에서 정적·동적 참조 0을 확인한 뒤 별도 커밋 |
| F5 — 키가 다른 기능 소유 | 18 | 활성 기능이 그 키를 쓴다. **유지** |
| C — F1/F2/F3 | 6 | UI 가 실재하거나 라우트만 달면 도달 가능 |

### A 그룹 42개를 일괄 삭제하지 않는 이유 (codex 게이트)

`ChannelHealth` / `Analytics` 처럼 **WIP 컨트롤러와 무관하게 상시 스케줄러가 쓰는**
기능이 있다. 실제로 이번 세션에서 `ABTestEvaluator` 가 그런 경우였다 —
컨트롤러는 wip 인데 스케줄러는 기본 프로필에서 매시간 돌고 있었다.

따라서 모듈마다 import·빈 주입·스케줄러·이벤트·공유 타입 소비자를 전수 확인하고,
상시 실행·비-WIP API·공용 저장소 참조가 **하나라도** 있으면 `keep-disabled` 로 승격한다.
