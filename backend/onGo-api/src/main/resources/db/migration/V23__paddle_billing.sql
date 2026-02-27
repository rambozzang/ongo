-- V23: Paddle Billing 연동을 위한 컬럼 추가

-- subscriptions: Paddle 구독/고객 ID
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS paddle_subscription_id VARCHAR(100);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS paddle_customer_id VARCHAR(100);
CREATE UNIQUE INDEX IF NOT EXISTS idx_sub_paddle_sub_id ON subscriptions(paddle_subscription_id) WHERE paddle_subscription_id IS NOT NULL;

-- subscriptions: pending_plan_type (다운그레이드 예약용 - 코드에 있지만 DB에 누락)
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS pending_plan_type VARCHAR(50);

-- payments: Paddle 트랜잭션 ID, 인보이스
ALTER TABLE payments ADD COLUMN IF NOT EXISTS paddle_transaction_id VARCHAR(100);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS paddle_invoice_url TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS idx_pay_paddle_txn_id ON payments(paddle_transaction_id) WHERE paddle_transaction_id IS NOT NULL;

-- users: Paddle 고객 ID
ALTER TABLE users ADD COLUMN IF NOT EXISTS paddle_customer_id VARCHAR(100);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_paddle_cid ON users(paddle_customer_id) WHERE paddle_customer_id IS NOT NULL;
