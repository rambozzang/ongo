-- Flyway Migration: V105__pending_billing_cycle.sql
--
-- 다운그레이드와 결제 주기 변경을 기간 종료 시 함께 적용하기 위한 예약 컬럼이다.
-- 기존 구독은 NULL이면 현재 billing_cycle을 그대로 사용한다.

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS pending_billing_cycle billing_cycle;

COMMENT ON COLUMN subscriptions.pending_billing_cycle IS
    '기간 종료 후 pending_plan_type과 함께 적용할 결제 주기';
