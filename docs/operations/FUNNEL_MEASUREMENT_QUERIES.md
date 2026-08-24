# 쇼츠 전환 퍼널 조회 (운영자용)

크레딧 부족으로 막힌 시도 → 트라이얼 시작 → 실행 생성. 세 단계는 모두
`activity_logs` 한 테이블에 남는다. 아래 쿼리는 **읽기 전용**이며, 스키마를
바꾸지 않는다.

## 세는 사건

| `action` | 의미 | 기록 방식 |
| --- | --- | --- |
| `SHORTS_RUN_BLOCKED_INSUFFICIENT_CREDIT` | 크레딧이 모자라 실행 생성이 거절됨 | **독립 트랜잭션**(`REQUIRES_NEW`). 거절로 바깥 트랜잭션이 롤백돼도 남는다 |
| `SUBSCRIPTION_TRIAL_STARTED` | STARTER 트라이얼 시작 성공 | 업무 트랜잭션 결속. 시작이 실패하면 흔적도 없다 |
| `SHORTS_RUN_CREATED` | 실행 행이 실제로 새로 만들어짐 | 업무 트랜잭션 결속. `entity_id` = `pipeline_runs.id` |
| `SHORTS_CLIP_AVAILABLE` | 클립에 **접근 가능한 완성 영상이 연결됨** | 연결 커밋 **뒤** 별도 트랜잭션. `entity_id` = `pipeline_runs.id` |
| `PAYMENT_SUBSCRIPTION_COMPLETED` | 구독 결제 확정 | 결제 커밋 **뒤** 별도 트랜잭션. `entity_id` = `payments.id` |
| `PAYMENT_CREDIT_COMPLETED` | 크레딧 구매 확정 | 동일. 아래 퍼널의 마지막 칸에는 **넣지 않는다** |

두 결제 사건을 나눠 두는 이유: 크레딧 구매는 이미 쓰고 있는 사용자의 추가 구매이고,
구독 결제는 유료 전환 그 자체다. 한 이름으로 합치면 재구매가 전환율을 부풀린다.
아래 진행 쿼리가 `PAYMENT_SUBSCRIPTION_COMPLETED` 만 쓰는 것도 그래서다.

결제 사건은 포트원 재조회로 상태·금액·통화를 검증하고 크레딧/구독 권한까지 반영된
**뒤에만** 남는다. 재호출·중복 웹훅은 이미 완료된 결제를 조기 반환하므로 추가 행을
만들지 않는다. 기록은 결제 커밋 뒤 별도 트랜잭션이라, 기록이 실패해도 결제는 그대로
확정돼 있다 — 즉 **이 로그는 하한이다**(6절 참조).

`SHORTS_RUN_CREATED`는 **행이 새로 생겼을 때만** 남는다. 멱등 키 재사용으로
기존 실행을 돌려준 요청(조기 반환·동시 저장 경쟁에서 진 쪽 모두)은 세지 않는다.
따라서 이 사건 수는 요청 수가 아니라 실행 수다.

`SHORTS_CLIP_AVAILABLE`은 **연결이 성립한 클립마다** 남는다. 경로가 둘이며 **둘 다**
같은 이름으로 남는다.

- 서버 렌더: 영상 저장 → 클립 `RENDERED` 연결 → job `COMPLETED` 가 모두 성공한 뒤
- 외부 완성본 연결(서버 렌더를 쓸 수 없는 환경의 보완 경로): 권한·상태 검증을 통과해
  연결이 성립한 뒤

서버 렌더만 세면 후자로 결과를 받은 고객이 통째로 빠져 지표가 과소집계되므로,
사건의 정의를 "서버가 렌더했다"가 아니라 **"접근 가능한 완성 영상이 연결됐다"**에 둔다.

> **열람·다운로드가 아니다.** 이 사건은 서버가 고객이 받아갈 수 있는 결과를 연결했다는
> 뜻이지, 고객이 그것을 열어봤다는 뜻이 아니다. 열람·다운로드 계측은 아직 없다.

같은 클립을 재렌더하거나 다시 연결하면 이 사건이 **둘 이상** 생긴다. 중복을 스키마나
애플리케이션에서 억지로 제거하지 않는다 — 재시도는 실제로 일어난 사실이고, 지우면
"몇 번 만에 성공했나"를 알 수 없게 된다. 대신 **집계에서** 접는다(2번 쿼리 참조).

## 1. 기간별 단계 카운트 (고유 사용자 기준)

```sql
SELECT
    action,
    COUNT(*)                AS events,
    COUNT(DISTINCT user_id) AS users
FROM activity_logs
WHERE created_at >= :start_at        -- 예: '2026-08-01 00:00:00'
  AND created_at <  :end_at          -- 상한은 미포함
  AND action IN (
      'SHORTS_RUN_BLOCKED_INSUFFICIENT_CREDIT',
      'SUBSCRIPTION_TRIAL_STARTED',
      'SHORTS_RUN_CREATED',
      'SHORTS_CLIP_AVAILABLE',
      'PAYMENT_SUBSCRIPTION_COMPLETED',
      'PAYMENT_CREDIT_COMPLETED'
  )
GROUP BY action
ORDER BY action;
```

`events`는 사건 수, `users`는 그 사건을 한 번이라도 낸 사람 수다. 한 사람이
여러 번 막히면 `events`만 늘어난다. **전환율의 분모로는 `users`를 써야 한다.**

## 2. 순서를 지킨 진행 (막힘 → 트라이얼 → 실행 생성 → 실결제)

단계별 `users`를 그냥 나누면 순서가 무시된다 — 막히기 *전에* 트라이얼을
시작했던 사람까지 전환으로 세어진다. 사람마다 첫 사건 시각을 잡고 순서를
강제한다.

마지막 칸은 **구독 결제**만 센다. 크레딧 구매는 유료 전환이 아니라 사용량 추가라,
넣으면 "체험한 사람이 유료로 넘어간 비율"이 재구매로 부풀려진다.

```sql
-- 1단계: 실행에 매이지 않는 사용자 단위 사건. 아래 실행 필터의 기준선이 된다.
--
-- PAYMENT_* 의 entity_id 는 payments.id 라 runId 와 조인할 수 없다 — 사용자 단위
-- 시각으로만 결합한다.
WITH user_events AS (
    SELECT
        user_id,
        MIN(created_at) FILTER (WHERE action = 'SHORTS_RUN_BLOCKED_INSUFFICIENT_CREDIT') AS blocked_at,
        MIN(created_at) FILTER (WHERE action = 'SUBSCRIPTION_TRIAL_STARTED')             AS trial_at,
        MIN(created_at) FILTER (WHERE action = 'PAYMENT_SUBSCRIPTION_COMPLETED')         AS paid_at
    FROM activity_logs
    WHERE created_at >= :start_at
      AND created_at <  :end_at
      AND action IN (
          'SHORTS_RUN_BLOCKED_INSUFFICIENT_CREDIT',
          'SUBSCRIPTION_TRIAL_STARTED',
          'PAYMENT_SUBSCRIPTION_COMPLETED'
      )
    GROUP BY user_id
),
-- 2단계: **같은 실행 안에서만** 생성/가용 시각을 짝짓는다.
--
-- 사용자 단위로 바로 MIN 을 하면 A 실행의 생성 시각과 B 실행의 가용 시각이 섞여,
-- 결과물을 낸 적 없는 실행이 결과를 낸 것처럼 보인다. 두 사건 모두 entity_id 가
-- runId 이고 entity_type 이 'shorts_run' 이라 실행 단위로 묶을 수 있다.
run_scoped AS (
    SELECT
        user_id,
        entity_id AS run_id,
        MIN(created_at) FILTER (WHERE action = 'SHORTS_RUN_CREATED')    AS run_at,
        -- 클립마다·재시도마다 남으므로 MIN 으로 그 실행의 첫 가용만 남긴다.
        MIN(created_at) FILTER (WHERE action = 'SHORTS_CLIP_AVAILABLE') AS available_at
    FROM activity_logs
    WHERE created_at >= :start_at
      AND created_at <  :end_at
      AND entity_type = 'shorts_run'
      AND action IN (
          'SHORTS_RUN_CREATED',
          'SHORTS_CLIP_AVAILABLE'
      )
    GROUP BY user_id, entity_id
),
-- 3단계: **체험 이후에 만들어진 실행만** 남기고 사용자 단위로 접는다.
--
-- 필터를 여기(실행 단위)에 둬야 한다. 사용자 단위로 MIN(run_at) 을 먼저 구하고 나중에
-- 체험 시각과 비교하면, 체험 **전에** 만들어 실패한 실행이 최솟값을 차지해 체험 후의
-- 실제 성공이 통째로 누락된다.
runs_after_trial AS (
    SELECT
        r.user_id,
        MIN(r.run_at) AS first_run_after_trial,
        -- **같은 실행 안에서** 생성 뒤에 가용이 온 경우만 인정한다.
        MIN(r.available_at) FILTER (WHERE r.available_at > r.run_at) AS first_available_after_trial
    FROM run_scoped r
    JOIN user_events e ON e.user_id = r.user_id
    WHERE r.run_at > e.trial_at
    GROUP BY r.user_id
)
SELECT
    COUNT(*) FILTER (WHERE e.blocked_at IS NOT NULL)                          AS blocked_users,
    COUNT(*) FILTER (WHERE e.trial_at > e.blocked_at)                         AS trial_after_block,
    COUNT(*) FILTER (
        WHERE e.trial_at > e.blocked_at
          AND t.first_run_after_trial IS NOT NULL
    )                                                                         AS run_after_trial,
    COUNT(*) FILTER (
        WHERE e.trial_at > e.blocked_at
          AND t.first_available_after_trial IS NOT NULL
    )                                                                         AS available_after_run,
    COUNT(*) FILTER (
        WHERE e.trial_at > e.blocked_at
          AND t.first_available_after_trial IS NOT NULL
          AND e.paid_at > t.first_available_after_trial
    )                                                                         AS paid_after_available
FROM user_events e
LEFT JOIN runs_after_trial t ON t.user_id = e.user_id;
```

`trial_at > blocked_at`은 둘 중 하나라도 NULL이면 NULL → `FILTER`에서 제외된다.
즉 "막힌 적 없음"과 "막혔지만 트라이얼 안 함"이 모두 분자에서 빠진다 — 의도한
동작이다. `first_available_after_trial IS NOT NULL`,
`paid_at > first_available_after_trial`도 같은 규칙이라, 앞 단계를 지나지 않은 사람은
뒤 칸에서 빠진다.

### 이 쿼리가 세는 것의 정확한 정의

사용자가 실행을 여러 번 했을 때 각 칸의 의미는 이렇다.

- `first_run_after_trial` = **체험 시작 이후에 만들어진** 실행들 중 가장 이른 생성 시각.
  체험 전에 만든 실행은 애초에 대상에서 빠진다.
- `first_available_after_trial` = 그 **체험 이후 실행들** 중, **자기 실행 안에서** 생성
  뒤에 가용이 온 실행들의 가장 이른 가용 시각. 그런 실행이 하나도 없으면 `NULL`.
- 따라서 `run_after_trial`은 **"체험 이후 실행을 최소 하나 만든 사용자 수"**,
  `available_after_run`은 **"체험 이후 실행 중 결과물을 낸 것이 최소 하나 있는 사용자
  수"**다. 실행 3개 중 1개만 성공해도 이 사용자는 1로 센다.
- 실행별 성공률이 필요하면 이 쿼리가 아니라 `run_scoped` CTE를 직접 집계해야 한다.

> **필터는 실행 단위에 있어야 한다.**
> 사용자 단위로 `MIN(run_at)`을 먼저 구하고 나중에 체험 시각과 비교하면, 체험 **전에**
> 만들어 실패한 실행이 최솟값을 차지해 `first_run_at > trial_at`이 거짓이 되고, 체험
> 후에 성공한 실행이 통째로 누락된다. 그래서 `runs_after_trial`이 `user_events`와
> 결합해 `r.run_at > e.trial_at`을 **집계 전에** 건다.

**중복은 집계에서 접는다.** `SHORTS_CLIP_AVAILABLE`은 클립마다, 재렌더·재연결마다
남는다. 2단계의 `MIN`이 실행 단위로, 3단계의 `MIN`이 사용자 단위로 접는다. 클립 수를
세고 싶다면 이 쿼리가 아니라 `ugc_shorts_clips`를 봐야 한다.

**결제는 실행과 조인하지 않는다.** `PAYMENT_SUBSCRIPTION_COMPLETED`의 `entity_id`는
`payments.id`라 `runId`와 의미가 다르다. 사용자 단위 시각으로만 결합하며, 그래서
"이 실행 때문에 결제했다"가 아니라 "결과물이 준비된 뒤에 결제했다"만 말할 수 있다.

다섯 숫자는 왼쪽부터 좁아지는 깔때기다.

| 비율 | 계산 | 뜻 |
| --- | --- | --- |
| 막힘 → 트라이얼 | `trial_after_block / blocked_users` | 크레딧 벽에서 체험으로 넘어간 비율 |
| 트라이얼 → 실행 생성 | `run_after_trial / trial_after_block` | 체험 **이후** 실행을 만들기까지 간 비율 |
| 실행 생성 → 첫 클립 가용 | `available_after_run / run_after_trial` | 체험 이후 실행이 실제 결과물로 이어진 비율 |
| 첫 클립 가용 → 실결제 | `paid_after_available / available_after_run` | 결과물이 준비된 뒤 구독 결제까지 간 비율 |

세 번째 비율이 파이프라인의 실효 성공률이고, 네 번째가 이 문서의 존재 이유다.

> **`SHORTS_RUN_CREATED` 는 결과물이 나왔다는 뜻이 아니다.**
> 이 사건은 `ugc_shorts_pipeline_runs` 행이 만들어진 시점에 남는다. 그 뒤 전사·세그먼트·
> 후킹·렌더가 남아 있고, 어느 단계에서든 실패하거나 사용자가 중간에 떠날 수 있다.
> "실행 생성 → 첫 클립 가용" 비율이 그 구간의 손실을 보여준다.
>
> **`SHORTS_CLIP_AVAILABLE` 도 "고객이 열람했다"는 뜻이 아니다.**
> 서버가 접근 가능한 완성 영상을 클립에 연결했다는 뜻이며, 고객이 그것을 재생하거나
> 내려받았는지는 **측정하지 않는다.** 열람·다운로드 계측은 아직 없다. 따라서 마지막
> 비율을 "결과를 **확인한** 뒤의 전환율"로 읽으면 안 된다 — "결과가 **준비된** 뒤"다.
>
> 검수 완료를 나타내는 사건도 없다. 렌더 실패 상세는 `shorts_render_jobs`, 파일럿
> 코호트의 리드타임은 `ugc_shorts_pipeline_runs.delivered_at` 과
> `ShortsPilotReportUseCase` 가 따로 다룬다.

### 결제만 따로 보고 싶을 때

퍼널 순서와 무관하게 기간 내 실결제를 세려면 1번 쿼리의 `users` 열을 쓴다.
크레딧 구매까지 포함한 총 결제 사용자는 두 action 을 함께 세면 된다.

## 읽을 때 주의할 것

- **배포 경계를 넘어 비교하지 말 것.** 각 사건은 해당 변경이 배포된 시점부터
  쌓인다. 그 이전 구간은 "0건"이 아니라 "측정 없음"이다.
- **두 결제 사건은 앞 세 사건보다 늦게 도입됐다.** 즉 결제 계측 배포 이전 구간은
  앞 세 칸만 값이 있고 `paid_after_run` 은 구조적으로 0 이다. 그 구간을 "아무도
  결제하지 않았다"로 읽으면 안 된다. 결제 배포일 이후로 기간을 잘라서 보라
  (실제 결제 여부는 `payments` 테이블이 별도 근거다).
- **`created_at`은 시간대 정보가 없다.** 컬럼이 `TIMESTAMP`이고 값은 DB의
  `DEFAULT NOW()`가 채우므로, DB 서버의 `TimeZone` 설정 기준 로컬 시각이다.
  KST로 읽으려면 서버 설정을 먼저 확인하라.
- **`action`에는 인덱스가 없다.** 있는 인덱스는 `user_id`와 `created_at`뿐이다.
  기간 조건을 반드시 함께 걸어 스캔 범위를 좁혀라.
- **막힘 사건은 독립 기록이라 기록 실패를 삼킨다.** 실패 시 경고 로그만 남고
  카운트는 조용히 빠진다. 즉 `blocked_users`는 하한이다.
- **트라이얼 시작은 STARTER 플랜만 남는다.** 다른 플랜 요청이나 이미 트라이얼을
  쓴 사용자의 재요청은 거절되므로 사건이 없다.
- **결제 사건도 하한이다.** 결제 커밋 뒤 별도 트랜잭션으로 기록하고 실패를 삼키므로,
  기록이 실패한 결제는 여기서 빠진다. 이 설계는 의도적이다 — 이미 승인된 결제를
  측정 실패로 되돌리지 않기 위해서다. 매출 대사는 반드시 `payments` 로 하고, 이
  로그는 **퍼널 순서 분석에만** 쓴다.
- **결제 사건의 `entity_id` 는 내부 `payments.id` 다.** 포트원 식별자가 아니므로
  PG 콘솔과 대조할 때는 `payments` 를 조인해 `pg_transaction_id` 를 읽어야 한다.
  로그에는 금액·영수증 URL·PG 원문을 남기지 않는다.
- **`SHORTS_CLIP_AVAILABLE` 은 중복이 정상이다.** 클립마다, 재렌더·재연결마다 남는다.
  퍼널에서는 반드시 사용자별 `MIN(created_at)` 으로 접어야 하고, 단순 `COUNT(*)` 는
  "결과를 받은 사람 수"가 아니라 "연결이 성립한 횟수"다.
- **이 사건도 하한이다.** 연결 커밋 뒤 별도 트랜잭션으로 기록하고 실패를 삼키므로,
  기록이 실패한 연결은 여기서 빠진다. 결제 사건과 같은 이유로 의도적이다 — 이미 만든
  결과물을 측정 실패로 버리지 않기 위해서다. 실제 결과물 유무는 `ugc_shorts_clips`
  (`status`, `rendered_video_id`)가 단일 진실이다.
- **열람·다운로드 계측은 없다.** 이 문서의 어떤 숫자도 "고객이 결과를 봤다"를 증명하지
  않는다. 필요하면 별도 계측을 먼저 만들어야 한다.
- **이 문서의 SQL 은 자동 가드가 있다.** `FunnelMeasurementDocTest` 가 action 목록·집계
  단위·체험 기준선을 구조적으로 검사한다. 다만 Gradle 이 이 마크다운을 테스트 입력으로
  추적하지 않으므로, **문서만 고친 뒤에는 `--rerun-tasks` 없이 돌리면 스킵된다.**
