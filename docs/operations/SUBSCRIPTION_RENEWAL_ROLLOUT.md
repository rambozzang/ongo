# 구독 자동 갱신 롤아웃 절차

`SUBSCRIPTION_RENEWAL_ENABLED=true` 를 켜기 전에 **반드시** 이 문서를 끝까지 읽으세요.

이 기능은 매일 02시에 고객 카드로 **실제 청구**를 하고, 실패한 구독을 `PAST_DUE` 로
내려 7일 뒤 Free 로 강등합니다. 되돌리기 가장 비싼 동작이라 기본값이 꺼짐입니다.

---

## 0. 지금 상태 (2026-08-27 읽기 전용 확인)

| 항목 | 값 |
|---|---|
| 운영 Flyway 최신 | **V93** |
| `subscriptions.billing_key_encrypted` / `pending_billing_cycle` | **없음** |
| `subscription_renewal_attempts` | **없음** |
| `ai_pipeline_jobs.refunded_credits` | **없음** (V106 미적용) |
| `analytics_daily.revenue_status` / `revenue_currency` | **없음** (V107 미적용) |
| `subscriptions` 행 수 | 4 |
| `payments` 행 수 | 0 |
| ACTIVE BUSINESS 구독 | 1건 — `price = 0`, 기간 컬럼 NULL |

**이 상태에서 지금 코드를 배포하면 결제·구독 화면이 죽습니다.** 아래 1단계를 먼저 하세요.

---

## 1. 배포 전 — 마이그레이션이 먼저다 (토글과 무관)

새 코드는 `subscriptions.billing_key_encrypted` 와 `subscriptions.pending_billing_cycle` 를
`SELECT` 하고 `INSERT`/`UPDATE` 합니다
(`SubscriptionJooqRepository` 77·104·215행). 컬럼이 없으면 **구독을 읽는 모든 경로가
실패**합니다.

`ai_pipeline_jobs.refunded_credits`(V106)도 같습니다. `AiPipelineJooqRepository` 가 조회
시 이 컬럼을 읽고 `settleRefund` 가 조건부로 갱신하므로, 없으면 AI 파이프라인 조회·정산이
전부 실패합니다.

> `SUBSCRIPTION_RENEWAL_ENABLED=false` 로도 막을 수 없습니다.
> 토글은 정기 청구 **실행**만 끄고, 컬럼 접근은 구독 조회 전체에 있습니다.

`deploy/preflight-schema.sh` 가 배포 시 이걸 읽기 전용으로 확인하고, 없으면
서비스를 멈추기 전에 배포를 중단합니다.

### 마이그레이션은 언제 적용되나 — `deploy/migrate-schema.sh`

마이그레이션 실행기는 **애플리케이션 기동 시 Spring Flyway 하나뿐**입니다(Flyway Gradle
플러그인도, 별도 CLI 도 없습니다). 그런데 preflight 는 새 스키마가 **이미 있어야만**
서비스를 재기동시킵니다. 그대로 두면 교착입니다:

```
운영이 V93 → preflight 가 배포를 막음 → 앱이 뜨지 않음 → Flyway 가 안 돎 → 영원히 V93
```

`deploy/migrate-schema.sh` 가 이 고리를 끊습니다. `deploy.sh` 는 다음 순서로 진행합니다.

```
JAR 복사 → 환경변수 검증 → migrate-schema.sh → preflight-schema.sh → stop.sh → start.sh
```

`migrate-schema.sh` 는 **새 JAR 을 마이그레이션 전용으로 한 번 띄워** Flyway 만 적용하고
곧바로 내립니다. 운영 서비스가 아직 살아 있는 상태에서 두 번째 인스턴스를 띄우는 것이므로
다음을 끕니다.

| 설정 | 이유 |
|---|---|
| `ongo.scheduling.enabled=false` | `@Scheduled` 전부 정지. 켜두면 결제 청구·웹훅 재시도·게시 워커가 살아 있는 서비스와 **동시에** 돕니다 |
| `spring.main.web-application-type=none` | 웹 서버를 띄우지 않습니다 |
| `server.port=0` | 혹시 웹 스택이 살아나도 운영 포트를 빼앗지 않습니다 |

**성공 판정은 Spring 기동 완료 문구 확인 하나뿐입니다.** 종료 코드 0 만으로는 컨텍스트가
뜨기도 전에 조용히 끝난 경우와 구분되지 않습니다.

| 결과 | 코드 |
|---|---|
| 적용 완료(기동 완료 문구 확인) | 0 |
| 실패 / 조기 종료 / java·JAR·환경변수 없음 | 1 |
| 제한 시간 초과(기본 600초, `MIGRATE_TIMEOUT_SECONDS`) | 2 |

**어느 실패든 서비스는 멈추지 않은 상태입니다** — `stop.sh` 앞에서 끝나기 때문입니다.

수동 실행(배포와 별개로 마이그레이션만 적용할 때):

```bash
bash deploy/migrate-schema.sh                      # 기본 600초 제한
MIGRATE_TIMEOUT_SECONDS=1800 bash deploy/migrate-schema.sh   # 긴 마이그레이션
```

로그는 `/data/ongo/log/migrate.log` 이며 서비스 로그(`backend.log`)와 분리돼 있습니다.

스크립트 자체 테스트: `bash deploy/migrate-schema.test.sh` (fake java 사용, DB·JVM 접속 없음)

**fail-closed 입니다.** 다음은 전부 배포를 막습니다(기존 서비스는 계속 실행됩니다).

| 상황 | 코드 |
|---|---|
| `flyway_schema_history` 에 성공한 `version='107'` 이 없음 | 1 |
| `flyway_schema_history` 에 **실패한(`success = false`) 마이그레이션**이 남아 있음 | 1 |
| `subscriptions.billing_key_encrypted`·`pending_billing_cycle`, `subscription_renewal_attempts.payment_id`, `ai_pipeline_jobs.refunded_credits`, `analytics_daily.revenue_status`·`revenue_currency` 중 하나라도 없음 | 1 |
| `credit_tx_type` enum 에 `REVOKE` 값이 없음(V100 미적용) | 1 |
| `psql` 없음 / `DB_PASSWORD` 없음 / 접속 실패 | 2 |

점검하지 못한 상태(2)도 중단합니다 — 스키마를 확인하지 못한 채 새 코드를 올리는 것은
스키마가 없는 것과 같은 위험이고, 배포 시점에는 구분할 수 없습니다.

`DB_URL` 을 생략하면 `application.yml` 과 **같은 기본값**
(`jdbc:postgresql://localhost:54332/ongo?stringtype=unspecified`)을 씁니다.
여기서만 다른 기본을 쓰면 점검한 DB 와 애플리케이션이 붙는 DB 가 갈라집니다.

스크립트 자체 테스트: `bash deploy/preflight-schema.test.sh` (mock psql 사용, DB 접속 없음)

**할 일:** V94~V107 을 순서대로 적용합니다. 운영은 V93 이므로 **14개가 밀려 있습니다**
— V102/V103/V104/V105/V106/V107 만이 아닙니다.

**적용 방법은 `deploy/migrate-schema.sh` 입니다.** `deploy.sh` 가 preflight 앞에서 자동으로
부르므로 평소에는 따로 실행할 필요가 없습니다. 이번처럼 밀린 마이그레이션이 많고 시간이
오래 걸릴 때는 `MIGRATE_TIMEOUT_SECONDS` 를 넉넉히 주고 **먼저 수동으로** 적용한 뒤
배포하는 편이 안전합니다(배포 창을 마이그레이션 시간만큼 잡아먹지 않습니다).

```bash
MIGRATE_TIMEOUT_SECONDS=1800 bash deploy/migrate-schema.sh
```

적용 전 반드시:
- [ ] `deploy/backup_local_db.sh` 등으로 백업 확보
- [ ] 로컬/스테이징에서 V93 스냅샷에 V94~V107 을 순서대로 적용해 성공 확인
- [ ] 특히 **V102**(자기 참조 FK + 부분 유니크 인덱스), **V103**(FK + 유니크 + CHECK),
      **V104**(자동 갱신 내부 결제 원장 FK + 부분 유니크 인덱스), **V105**(하향 예약
      결제 주기), **V106**(파이프라인 환불 표식 + CHECK), **V107**(광고 수익 상태·통화)은
      실 PostgreSQL 에서 실행된 적이 없습니다
- [ ] **V106 의 CHECK** 는 `refunded_credits <= total_credits_charged` 를 요구합니다.
      기존 행은 기본값 0 이라 통과하지만, 적용 전에 `ai_pipeline_jobs` 행 수를 확인해
      실패 시 영향 범위를 알 수 있게 하십시오
- [ ] **V107 은 `analytics_daily` 파티션 부모 테이블을 변경합니다.** 월별 파티션이
      많으면 `ALTER TABLE` 이 전 파티션에 ACCESS EXCLUSIVE 락을 잡습니다. 트래픽이
      적은 시간대에 적용하고, 적용 전 `analytics_daily` 행 수와 파티션 수를 확인하십시오:
      `SELECT count(*) FROM pg_inherits WHERE inhparent = 'analytics_daily'::regclass;`
- [ ] **V107 의 CHECK 두 개**는 `revenue_status` 허용값과 "MEASURED 만 통화를 가진다"를
      요구합니다. 기존 행은 기본값 `UNSUPPORTED` + 통화 NULL 이라 통과합니다 —
      기존 `revenue_micro` 값은 어떤 수익도 조회한 적이 없으므로 그대로 두되
      **측정값으로 취급되지 않습니다**(조회 SQL 이 `MEASURED` 만 합산)

> **광고 수익 수집(V107) 관련 주의**
>
> V107 이 적용되기 전까지 `AnalyticsSyncScheduler` 의 수익 동기화와 수익 조회 SQL 은
> 존재하지 않는 컬럼을 참조해 실패합니다. 그래서 `deploy/preflight-schema.sh` 가
> `REQUIRED_FLYWAY_VERSION=107` 과 두 컬럼을 검사해 **미적용 상태의 배포를 차단합니다.**
> 이 게이트를 우회하지 마십시오.
>
> 또한 새 OAuth scope(`yt-analytics-monetary.readonly`)는 **이미 연결된 채널에
> 소급 적용되지 않습니다.** 기존 사용자는 채널을 다시 연동해야 수익이 수집되며,
> 그 전까지 해당 행은 `PERMISSION_REQUIRED` 로 저장됩니다. 일반 분석 지표는
> 영향을 받지 않습니다(별도 질의).

---

## 2. 토글을 켜기 전 전제

### 2-1. V107 적용 확인

```
SELECT column_name FROM information_schema.columns
 WHERE table_name = 'analytics_daily' AND column_name IN ('revenue_status', 'revenue_currency');
SELECT column_name FROM information_schema.columns
 WHERE table_name = 'subscriptions' AND column_name = 'billing_key_encrypted';
SELECT column_name FROM information_schema.columns
 WHERE table_name = 'subscriptions' AND column_name = 'pending_billing_cycle';
SELECT to_regclass('subscription_renewal_attempts');
SELECT column_name FROM information_schema.columns
 WHERE table_name = 'subscription_renewal_attempts' AND column_name = 'payment_id';
SELECT column_name FROM information_schema.columns
 WHERE table_name = 'ai_pipeline_jobs' AND column_name = 'refunded_credits';
```
다섯 쿼리 모두 결과가 나와야 합니다.

`ai_pipeline_jobs.refunded_credits` 는 파이프라인 정산의 멱등 표식입니다. 없으면 조회부터
실패해 AI 파이프라인이 뜨지 않고, 억지로 우회하면 같은 환불을 두 번 내보냅니다.

### 2-2. PortOne 콘솔 — 빌링키 권한

- [ ] 상점 계정에 **빌링키(정기결제) 사용 권한**이 열려 있을 것.
      일반 결제와 계약이 다릅니다. 닫혀 있으면 발급이 실패하고, 설계상 **구독 결제가
      시작되지 않습니다**(고객은 오류를 봅니다 — 조용히 넘어가지 않습니다).
- [ ] 빌링키 발급 채널이 결제 채널과 같은지 확인.
      현재 코드는 `PORTONE_CHANNEL_KEY` 하나를 양쪽에 씁니다. 별도 채널이 필요하면
      설정 추가가 선행돼야 합니다.
- [ ] `PORTONE_API_SECRET` 으로 `GET /billing-keys/{key}` 와
      `POST /payments/{id}/billing-key` 를 호출할 수 있을 것.

### 2-3. 웹훅

- [ ] `PORTONE_WEBHOOK_SECRET` 설정 및 웹훅 URL 등록 확인.
      정기 청구는 웹훅 없이도 동작하지만(청구 후 재조회로 확정), 첫 결제 확정은
      웹훅 경로를 씁니다.

### 2-4. 결제창 이탈·실패 원장

체크아웃 intent를 만들 때 `payments`에 `PENDING` 원장이 먼저 생깁니다. 브라우저가 결제창을
닫거나 실패를 받으면 프런트가 `POST /api/v1/portone/payments/{paymentId}/reconcile`를 호출해
PortOne을 재조회합니다. PortOne에 결제가 없거나 `FAILED`/`CANCELLED`일 때만 `FAILED`로 닫고,
실제 `PAID`면 일반 완료 경로로 정산합니다.

- [ ] 결제 내역에서 `PENDING`을 매출로 집계하지 않는다. 매출은 `COMPLETED`만 집계한다.
- [ ] 브라우저가 강제 종료됐거나 PortOne 재조회가 실패한 `PENDING`은 자동으로 실패 단정하지
      않는다. 웹훅 또는 PortOne 콘솔 재조회로 확인한 뒤 운영자가 처리한다.
- [ ] `CANCELLED` 웹훅이 와도 `COMPLETED`가 아닌 원장은 환불(`REFUNDED`)이나 크레딧 회수로
      처리하지 않는다.

### 2-5. 기존 유료 고객 — **가장 중요합니다**

현재 아무도 빌링키를 등록한 적이 없습니다. 토글을 켜면 기존 ACTIVE 구독은 전부
`BILLING_KEY_MISSING` → `PAST_DUE` → 7일 뒤 Free 로 떨어집니다.

- [ ] 대상 구독 확인:
      ```
      SELECT id, user_id, plan_type, price, current_period_end, next_billing_date
        FROM subscriptions
       WHERE status = 'ACTIVE' AND billing_key_encrypted IS NULL;
      ```
- [ ] 각 고객에게 결제수단 재등록 안내(구독 화면에서 다시 결제하면 등록됩니다)
- [ ] 안내 후 유예 기간을 두고, 그 기간이 지난 뒤에 켜기

**데이터 이상 주의:** ACTIVE BUSINESS 1건이 `price = 0` 이고 기간이 NULL 입니다.
`price = 0` 으로 청구하면 PortOne 이 거절하고, `next_billing_date` 가 NULL 이면
`findDueForBilling` 이 애초에 이 구독을 찾지 못합니다. **켜기 전에 이 행이 무엇인지
확인하세요** — 테스트 데이터라면 갱신 대상에서 빼야 합니다.

### 2-6. 레거시 Paddle 구독은 대상이 아닙니다 (코드로 제외됨)

자동 갱신은 **PortOne 빌링키로만** 청구합니다. Paddle 로 결제한 레거시 구독도
`status = 'ACTIVE'` 이고 `next_billing_date` 가 채워져 있어(Paddle 의 `next_billed_at` 을
그대로 저장합니다) 조건상으로는 갱신 대상처럼 보입니다. 그대로 두면 어느 쪽으로 가든
고객이 손해를 봅니다.

- 빌링키가 없으면 → `BILLING_KEY_MISSING` → `PAST_DUE` → 7일 뒤 Free.
  **Paddle 에서는 정상 결제 중인데 우리 쪽에서만 권한을 뺏습니다.**
- 빌링키가 있으면 → Paddle 과 PortOne 이 같은 주기를 각각 청구합니다. **이중 청구**입니다.

그래서 **코드가 두 겹으로 제외합니다.** 운영자가 따로 할 일은 없지만, 이 전제가 깨지면
위 사고가 그대로 일어나므로 켜기 전에 확인하세요.

- `SubscriptionJooqRepository.findDueForBilling` — `paddle_subscription_id IS NULL` 조건
- `SubscriptionRenewalService.renew` — 진입부에서 Paddle 구독을 건너뜀(상태 변경·PG 호출 없음)

- [ ] 제외 대상 규모 확인(읽기 전용):
      ```
      SELECT count(*)
        FROM subscriptions
       WHERE status = 'ACTIVE' AND paddle_subscription_id IS NOT NULL;
      ```
- [ ] 위 건수가 0이 아니면, **그 고객들의 갱신은 Paddle 이 계속 맡는다**는 사실을 확인하세요.
      이 문서의 범위는 PortOne 갱신뿐이며, Paddle 갱신을 여기서 대신 처리하지 않습니다.
- [ ] 로그에 `Paddle 구독은 PortOne 자동 갱신 대상이 아니라 건너뛴다` 가 반복되면 정상입니다
      (실패가 아니라 제외입니다).

---

## 3. 켜기

```
SUBSCRIPTION_RENEWAL_ENABLED=true
```

재기동 후 첫 02시 실행 로그를 확인합니다.

- 꺼져 있을 때: `구독 자동 갱신이 꺼져 있어 건너뛴다. subscription.renewal.enabled=false`
- 켜져 있을 때: `구독 갱신 대상 N건`

---

## 4. 켠 뒤 확인할 것

```
SELECT outcome, count(*) FROM subscription_renewal_attempts GROUP BY outcome;
```

| outcome | 뜻 | 조치 |
|---|---|---|
| `CHARGED` | 청구 성공, 기간 연장됨 | 없음 |
| `CHARGE_FAILED` | PG 가 거절 | 고객에게 카드 확인 안내(자동 알림 발송됨) |
| `BILLING_KEY_MISSING` | 결제수단 미등록 | 2-5 를 건너뛴 것. 고객 안내 필요 |
| `NEEDS_REVIEW` | 금액·통화 불일치 | **사람이 확인.** 돈은 이미 움직였고 구독 상태는 바뀌지 않음 → 4-1 절차 |
| `ATTEMPTED` (10분 이상 남음) | 결과 확정 실패 | 다음 실행이 재조회로 결말. 반복되면 PortOne 상태 확인 |

`ATTEMPTED` 가 오래 남아 있어도 **재청구는 하지 않습니다.** 같은 `paymentId` 로
재조회해 결말을 짓습니다(`sub-{id}-renew-{yyyy-MM-dd}`).

---

## 4-1. `NEEDS_REVIEW` 처리 절차

`NEEDS_REVIEW` 는 **돈이 이미 움직였을 수 있는데 코드가 결말을 정할 수 없는** 주기입니다.
그래서 이 상태의 결제 원장은 `PENDING` 으로 남고, 구독 상태도 바뀌지 않습니다.
브라우저의 `POST /portone/payments/{id}/reconcile` 은 자동 갱신 결제를 **의도적으로 제외**하므로
고객이 스스로 해소할 수 없습니다. 아래 관리자 경로가 유일한 처리 수단입니다.

### 원칙 — 반드시 지킬 것

- **자동 청구 금지.** 재조회 경로는 PortOne 에 `GET /payments/{id}` 만 보냅니다.
  확인 대상은 이미 승인됐을 수 있어 재청구는 이중 결제가 됩니다.
- **임의 성공 처리 금지.** API 는 결과를 요청 본문으로 받지 않습니다. 확정은 PG 응답이 정합니다.
  DB 에서 `outcome` 이나 `payments.status` 를 손으로 바꾸지 마십시오.
- **바꾸지 않는 것이 기본값.** 금액·통화가 다르거나 PG 가 중간 상태면 아무것도 바뀌지 않습니다.
  그건 실패가 아니라 "확정할 근거가 없다" 는 정상 결과입니다.

### 목록 조회

```bash
curl -sS -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$API_BASE/api/v1/admin/subscriptions/renewal-reviews?page=0&size=20"
```

응답의 `cause` 로 처리 방법이 갈립니다.

| `cause` | 뜻 | 할 일 |
|---|---|---|
| `APPROVAL_MISMATCH` | 내부 결제 원장이 연결돼 있음 | 아래 재조회를 실행 |
| `LEGACY_NO_INTERNAL_LEDGER` | V103 이전 주기라 내부 원장 없음 | **재조회로 해결 불가.** 수기 대사 |

### 재조회

```bash
curl -sS -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$API_BASE/api/v1/admin/subscriptions/renewal-reviews/{attemptId}/recheck"
```

| `decision` | 뜻 | 다음 행동 |
|---|---|---|
| `RESOLVED` | PG 응답으로 확정됨(`outcome` 확인) | 없음. 정산·알림까지 끝났습니다 |
| `STILL_UNDER_REVIEW` | 금액·통화 불일치이거나 PG 가 중간 상태 | 상태 그대로. PortOne 콘솔에서 승인 내역 확인 후 수기 판단 |
| `LOOKUP_FAILED` | PG 조회 실패 | 상태 그대로. 잠시 후 재시도 |
| `ALREADY_RESOLVED` | 다른 운영자가 먼저 처리 | 목록 새로고침 |
| `MANUAL_ONLY` | 내부 원장 없음 | 수기 대사 |
| `NOT_UNDER_REVIEW` | 이미 다른 결과 | 없음 |

`RESOLVED` 는 두 경우뿐입니다.
- PG 가 `PAID` 이고 **금액·통화가 내부 결제와 정확히 일치** → `CHARGED`, 기존 완료 경로로 정산
- PG 에 결제가 없거나 `FAILED`/`CANCELLED` → `CHARGE_FAILED`, 결제 `PENDING → FAILED`, 구독 `PAST_DUE` + 고객 알림

### 수기 대사가 필요한 경우 (`STILL_UNDER_REVIEW`, `MANUAL_ONLY`)

금액이 다른 건은 **차액 정산·부분환불·재청구 중 무엇을 할지가 사업 판단**입니다.
코드로 정하지 않으며 자동화하지 않습니다. PortOne 콘솔에서 실제 승인 내역을 확인한 뒤
결정하고, 결정 내용과 근거를 남기십시오. 필요하면 환불은 PortOne 콘솔에서 직접 처리합니다.

### 현황 확인 쿼리 (읽기 전용)

```sql
SELECT outcome, count(*) FROM subscription_renewal_attempts GROUP BY 1;

SELECT a.id, a.subscription_id, a.period_start, a.payment_id,
       p.status, p.amount, p.currency
  FROM subscription_renewal_attempts a
  LEFT JOIN payments p ON p.id = a.payment_id
 WHERE a.outcome = 'NEEDS_REVIEW'
 ORDER BY a.created_at;
```

> 자동 갱신이 꺼져 있는 동안에는 `NEEDS_REVIEW` 가 생기지 않습니다. 토글을 켜기 전에
> 위 첫 번째 쿼리로 0 건임을 확인하십시오.

---

## 5. 끄기

```
SUBSCRIPTION_RENEWAL_ENABLED=false
```

재기동하면 다음 실행부터 갱신이 멈춥니다. **이미 청구된 건은 되돌아가지 않습니다** —
환불은 PortOne 콘솔에서 별도로 처리해야 합니다.

끈다고 `PAST_DUE` 가 원복되지도 않습니다. 필요하면 해당 구독을 직접 확인하세요.

---

## 관련 코드

| 대상 | 위치 |
|---|---|
| 토글 | `BillingScheduler` 생성자 `subscription.renewal.enabled` |
| 갱신 로직 | `SubscriptionRenewalService` |
| 빌링키 등록 | `SubscriptionBillingKeyUseCase`, `POST /api/v1/portone/billing-key` |
| 확인 대상 운영 | `AdminSubscriptionReviewController`, `AdminSubscriptionReviewUseCase`, `SubscriptionRenewalService.recheckReview` |
| 스키마 | `V103__subscription_renewal_attempts.sql`, `V104__renewal_attempt_payment.sql`, `V105__pending_billing_cycle.sql`, `V106__ai_pipeline_refunded_credits.sql` |
| 배포 점검 | `deploy/preflight-schema.sh` |
