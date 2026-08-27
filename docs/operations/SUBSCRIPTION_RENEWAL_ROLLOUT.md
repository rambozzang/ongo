# 구독 자동 갱신 롤아웃 절차

`SUBSCRIPTION_RENEWAL_ENABLED=true` 를 켜기 전에 **반드시** 이 문서를 끝까지 읽으세요.

이 기능은 매일 02시에 고객 카드로 **실제 청구**를 하고, 실패한 구독을 `PAST_DUE` 로
내려 7일 뒤 Free 로 강등합니다. 되돌리기 가장 비싼 동작이라 기본값이 꺼짐입니다.

---

## 0. 지금 상태 (2026-08-27 읽기 전용 확인)

| 항목 | 값 |
|---|---|
| 운영 Flyway 최신 | **V93** |
| `subscriptions.billing_key_encrypted` | **없음** |
| `subscription_renewal_attempts` | **없음** |
| `subscriptions` 행 수 | 4 |
| `payments` 행 수 | 0 |
| ACTIVE BUSINESS 구독 | 1건 — `price = 0`, 기간 컬럼 NULL |

**이 상태에서 지금 코드를 배포하면 결제·구독 화면이 죽습니다.** 아래 1단계를 먼저 하세요.

---

## 1. 배포 전 — 마이그레이션이 먼저다 (토글과 무관)

새 코드는 `subscriptions.billing_key_encrypted` 를 `SELECT` 하고 `INSERT`/`UPDATE` 합니다
(`SubscriptionJooqRepository` 77·104·215행). 컬럼이 없으면 **구독을 읽는 모든 경로가
실패**합니다.

> `SUBSCRIPTION_RENEWAL_ENABLED=false` 로도 막을 수 없습니다.
> 토글은 정기 청구 **실행**만 끄고, 컬럼 접근은 구독 조회 전체에 있습니다.

`deploy/preflight-schema.sh` 가 배포 시 이걸 읽기 전용으로 확인하고, 없으면
서비스를 멈추기 전에 배포를 중단합니다.

**fail-closed 입니다.** 다음은 전부 배포를 막습니다(기존 서비스는 계속 실행됩니다).

| 상황 | 코드 |
|---|---|
| `flyway_schema_history` 에 성공한 `version='103'` 이 없음 | 1 |
| `subscriptions.billing_key_encrypted` 또는 `subscription_renewal_attempts` 없음 | 1 |
| `psql` 없음 / `DB_PASSWORD` 없음 / 접속 실패 | 2 |

점검하지 못한 상태(2)도 중단합니다 — 스키마를 확인하지 못한 채 새 코드를 올리는 것은
스키마가 없는 것과 같은 위험이고, 배포 시점에는 구분할 수 없습니다.

`DB_URL` 을 생략하면 `application.yml` 과 **같은 기본값**
(`jdbc:postgresql://localhost:54332/ongo?stringtype=unspecified`)을 씁니다.
여기서만 다른 기본을 쓰면 점검한 DB 와 애플리케이션이 붙는 DB 가 갈라집니다.

스크립트 자체 테스트: `bash deploy/preflight-schema.test.sh` (mock psql 사용, DB 접속 없음)

**할 일:** V94~V103 을 순서대로 적용합니다. 운영은 V93 이므로 **10개가 밀려 있습니다**
— V102/V103 만이 아닙니다.

적용 전 반드시:
- [ ] `deploy/backup_local_db.sh` 등으로 백업 확보
- [ ] 로컬/스테이징에서 V93 스냅샷에 V94~V103 을 순서대로 적용해 성공 확인
- [ ] 특히 **V102**(자기 참조 FK + 부분 유니크 인덱스)와 **V103**(FK + 유니크 + CHECK)은
      실 PostgreSQL 에서 실행된 적이 없습니다

---

## 2. 토글을 켜기 전 전제

### 2-1. V103 적용 확인

```
SELECT column_name FROM information_schema.columns
 WHERE table_name = 'subscriptions' AND column_name = 'billing_key_encrypted';
SELECT to_regclass('subscription_renewal_attempts');
```
둘 다 결과가 나와야 합니다.

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

### 2-4. 기존 유료 고객 — **가장 중요합니다**

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
| `BILLING_KEY_MISSING` | 결제수단 미등록 | 2-4 를 건너뛴 것. 고객 안내 필요 |
| `NEEDS_REVIEW` | 금액·통화 불일치 | **사람이 확인.** 돈은 이미 움직였고 구독 상태는 바뀌지 않음 |
| `ATTEMPTED` (10분 이상 남음) | 결과 확정 실패 | 다음 실행이 재조회로 결말. 반복되면 PortOne 상태 확인 |

`ATTEMPTED` 가 오래 남아 있어도 **재청구는 하지 않습니다.** 같은 `paymentId` 로
재조회해 결말을 짓습니다(`sub-{id}-renew-{yyyy-MM-dd}`).

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
| 스키마 | `V103__subscription_renewal_attempts.sql` |
| 배포 점검 | `deploy/preflight-schema.sh` |
