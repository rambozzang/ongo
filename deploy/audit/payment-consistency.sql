-- 결제·크레딧·구독 정합성 감사 — **읽기 전용**.
--
-- ## 왜 필요한가
--
-- 2026-08-06 까지 jOOQ 가 스프링 트랜잭션에 참여하지 않아(149ecf4 에서 수정) `@Transactional`
-- 코드가 전부 문장 단위 auto-commit 이었다. 롤백도 `FOR UPDATE` 도 무효였으므로, 그 이전에
-- 실패한 결제·지급·환불은 **절반만 기록된 채** 남아 있을 수 있다. 이 쿼리는 그 흔적을 찾는다.
-- 고치지 않는다. 무엇을 어떻게 복구할지는 사람이 결과를 보고 정한다.
--
-- ## 실행
--
--   psql "$DATABASE_URL" -X -A -F $'\t' -f deploy/audit/payment-consistency.sql
--
-- 결과가 0 행이면 깨끗하다. 행마다 severity · check · user_id · ref_id · detail 이다.
--   CRITICAL  돈이 걸린 불일치 — 결제했는데 못 받았거나, 안 냈는데 받았거나, 환불했는데 남았다
--   WARN      확인이 필요한 상태 — 멈춘 결제·웹훅, 기간이 지난 활성 구독, 요금제 불일치
--   INFO      표시값 불일치 — 차감 판정에는 영향이 없다
--
-- 규칙의 근거(코드 위치)는 각 항목 위 주석에 있다. 코드의 불변식이 바뀌면 여기도 바꿔야 한다 —
-- 그 일치는 `PaymentConsistencyAuditIT` 가 실 PostgreSQL 에서 항목마다 고정한다.

SET default_transaction_read_only = on;
BEGIN READ ONLY;

WITH findings (severity, check_name, user_id, ref_id, detail) AS (

    -- 1) 결제는 완료됐는데 크레딧 지급 기록이 없다.
    --    지급은 `CreditService.addPurchasedCredits` 가 CHARGE 거래를 reference_id = payments.id 로 남긴다.
    SELECT 'CRITICAL', 'PAID_NOT_GRANTED', p.user_id, p.id,
           format('크레딧 결제 %s원 완료, CHARGE 없음 (%s)', p.amount, p.created_at)
      FROM payments p
     WHERE p.type::text = 'CREDIT'
       AND p.status::text = 'COMPLETED'
       AND NOT EXISTS (SELECT 1 FROM ai_credit_transactions t
                        WHERE t.type::text = 'CHARGE' AND t.reference_id = p.id)

    UNION ALL
    -- 2) 결제 하나에 지급이 두 번 이상 — 동시 웹훅 중복 지급(FOR UPDATE 무효 시기).
    SELECT 'CRITICAL', 'DOUBLE_GRANT', min(t.user_id), t.reference_id,
           format('CHARGE %s건, 합계 %s크레딧', count(*), sum(t.amount))
      FROM ai_credit_transactions t
     WHERE t.type::text = 'CHARGE' AND t.reference_id IS NOT NULL
     GROUP BY t.reference_id
    HAVING count(*) > 1

    UNION ALL
    -- 3) 지급은 있는데 근거 결제가 없거나 완료·환불 상태가 아니다.
    SELECT 'CRITICAL', 'GRANT_WITHOUT_PAYMENT', t.user_id, t.reference_id,
           format('CHARGE %s크레딧, 결제 상태 %s', t.amount, coalesce(p.status::text, '없음'))
      FROM ai_credit_transactions t
      LEFT JOIN payments p ON p.id = t.reference_id
     WHERE t.type::text = 'CHARGE'
       AND (p.id IS NULL OR p.status::text NOT IN ('COMPLETED', 'REFUNDED'))

    UNION ALL
    -- 4) 크레딧 결제가 환불됐는데 그 뒤로 회수(REVOKE)가 없다.
    --    회수 기록은 결제 id 가 아니라 PG 식별자를 사유로 남기므로(`PORTONE_CANCEL_<id>`,
    --    `REFUND_<id>`) 사용자·시각으로 대조한다. 걸린 행은 사람이 확인한다.
    SELECT 'CRITICAL', 'REFUNDED_NOT_REVOKED', p.user_id, p.id,
           format('크레딧 결제 %s원 환불, 이후 REVOKE 없음', p.amount)
      FROM payments p
     WHERE p.type::text = 'CREDIT'
       AND p.status::text = 'REFUNDED'
       AND NOT EXISTS (SELECT 1 FROM ai_credit_transactions t
                        WHERE t.user_id = p.user_id AND t.type::text = 'REVOKE'
                          AND t.created_at >= p.created_at)

    UNION ALL
    -- 5) 구매 크레딧 잔여가 지급량보다 많다 — 환불 복원이 두 번 적용된 흔적.
    SELECT 'CRITICAL', 'REMAINING_OVER_TOTAL', c.user_id, c.id,
           format('%s: 잔여 %s > 지급 %s', c.package_name, c.remaining, c.total_credits)
      FROM ai_purchased_credits c
     WHERE c.remaining > c.total_credits

    UNION ALL
    -- 6) 구독 결제는 완료됐는데 사용자 요금제가 FREE 다.
    --    한도 판정은 전부 users.plan_type 을 본다(채널·예약·월 업로드). 여기가 FREE 면
    --    돈을 내고도 유료 기능을 못 쓴다. 기간이 끝난 결제는 제외한다.
    SELECT 'CRITICAL', 'PAID_PLAN_NOT_APPLIED', p.user_id, p.id,
           format('구독 결제 %s원 완료, 사용자 요금제 FREE, 구독 기간 끝 %s', p.amount, s.current_period_end)
      FROM payments p
      JOIN users u ON u.id = p.user_id
      LEFT JOIN subscriptions s ON s.user_id = p.user_id
     WHERE p.type::text = 'SUBSCRIPTION'
       AND p.status::text = 'COMPLETED'
       AND u.plan_type::text = 'FREE'
       AND (s.current_period_end IS NULL OR s.current_period_end > now())

    UNION ALL
    -- 7) 구독 행의 요금제와 사용자 요금제가 다르다. 한도는 users 쪽을 따른다.
    SELECT 'WARN', 'PLAN_MISMATCH', s.user_id, s.id,
           format('subscriptions=%s(%s) users=%s', s.plan_type, s.status, u.plan_type)
      FROM subscriptions s
      JOIN users u ON u.id = s.user_id
     WHERE s.status::text IN ('ACTIVE', 'CANCELLED', 'PAST_DUE')
       AND s.plan_type::text <> u.plan_type::text

    UNION ALL
    -- 8) 유료 ACTIVE 인데 기간 값이 전혀 없다. 결제 경로(`completeSubscription`)는 반드시
    --    current_period_end 를 채우므로 결제로 만들어진 행이 아니다. 자동 강등 대상에서
    --    빠지므로(`findActiveExpiredWithoutRenewal`) **사람이 정하지 않으면 무기한 유료다.**
    SELECT 'CRITICAL', 'PAID_WINDOW_MISSING', s.user_id, s.id,
           format('%s ACTIVE, current_period_end·next_billing_date 모두 NULL', s.plan_type)
      FROM subscriptions s
     WHERE s.status::text = 'ACTIVE'
       AND s.plan_type::text <> 'FREE'
       AND s.paddle_subscription_id IS NULL
       AND s.current_period_end IS NULL
       AND s.next_billing_date IS NULL

    UNION ALL
    -- 9) 활성 구독인데 결제 기간이 사흘 넘게 지났다 — 만료 전환이 멈췄다.
    --    자동 갱신이 꺼져 있으면 `BillingScheduler` 가 매일 새벽 FREE 로 내린다.
    SELECT 'WARN', 'ACTIVE_PAST_PERIOD', s.user_id, s.id,
           format('%s ACTIVE, 기간 끝 %s', s.plan_type, s.current_period_end)
      FROM subscriptions s
     WHERE s.status::text = 'ACTIVE'
       AND s.current_period_end < now() - interval '3 days'

    UNION ALL
    -- 10) 하루 넘게 PENDING 인 결제 — 웹훅도 사용자 복귀도 오지 않았다.
    SELECT 'WARN', 'STALE_PENDING_PAYMENT', p.user_id, p.id,
           format('%s %s원 PENDING since %s', p.type, p.amount, p.created_at)
      FROM payments p
     WHERE p.status::text = 'PENDING'
       AND p.created_at < now() - interval '1 day'

    UNION ALL
    -- 11) 재시도를 다 쓰고 실패한 웹훅, 또는 한 시간 넘게 처리되지 않은 웹훅.
    SELECT 'WARN', 'WEBHOOK_UNPROCESSED', NULL::bigint, w.id,
           format('%s %s (재시도 %s/%s) since %s', w.event_type, w.status, w.retry_count, w.max_retries, w.created_at)
      FROM webhook_events w
     WHERE (w.status = 'FAILED' AND w.retry_count >= w.max_retries)
        OR (w.status = 'PENDING' AND w.created_at < now() - interval '1 hour')

    UNION ALL
    -- 12) 표시 잔액이 정의와 다르다. 정의: free_remaining + 유효한 구매분 remaining 합계
    --     (`CreditService.applyPlanEntitlement`). 차감은 구매분 테이블을 직접 보므로 돈은
    --     새지 않지만, 화면 숫자가 틀린다. 만료로 인한 차이도 여기 잡힌다.
    SELECT 'INFO', 'BALANCE_DRIFT', a.user_id, a.id,
           format('balance=%s, 정의상=%s', a.balance, a.free_remaining + coalesce(pc.active_remaining, 0))
      FROM ai_credits a
      LEFT JOIN (SELECT user_id, sum(remaining) AS active_remaining
                   FROM ai_purchased_credits
                  WHERE status::text = 'ACTIVE' AND expires_at > now()
                  GROUP BY user_id) pc ON pc.user_id = a.user_id
     WHERE a.balance <> a.free_remaining + coalesce(pc.active_remaining, 0)
)
SELECT severity, check_name, user_id, ref_id, detail
  FROM findings
 ORDER BY CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'WARN' THEN 1 ELSE 2 END, check_name, user_id, ref_id;

ROLLBACK;
