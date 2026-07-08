-- 누락되어 있던 revenue_alert_configs / revenue_insights 테이블 정의 + 원래 V38이 의도한 변경 적용
-- (코드(RevenueAlertConfigJooqRepository, RevenueInsightJooqRepository)가 이미 사용 중이지만
--  CREATE TABLE 마이그레이션이 누락되어 V38 ALTER 가 실패하던 문제 정정)

-- revenue_alert_configs : 사용자별 매출 알림 설정
CREATE TABLE IF NOT EXISTS revenue_alert_configs (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    alert_type      VARCHAR(50)  NOT NULL,
    threshold_value NUMERIC(18, 2),
    schedule_time   TIME,
    is_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_revenue_alert_configs_user_id
    ON revenue_alert_configs (user_id);

-- revenue_insights : AI 매출 인사이트
CREATE TABLE IF NOT EXISTS revenue_insights (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    insight_type VARCHAR(50)  NOT NULL,
    content      JSONB        NOT NULL,
    platform     VARCHAR(50),
    confidence   NUMERIC(5, 2),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_revenue_insights_user_id_created_at
    ON revenue_insights (user_id, created_at DESC);

-- (user_id, alert_type) UNIQUE — 동일 사용자가 같은 알림 타입을 중복 등록하는 것을 방지
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_revenue_alert_configs_user_type'
    ) THEN
        ALTER TABLE revenue_alert_configs
            ADD CONSTRAINT uq_revenue_alert_configs_user_type UNIQUE (user_id, alert_type);
    END IF;
END $$;

-- confidence 컬럼 정밀도 NUMERIC(?,?) → NUMERIC(5,2) (이미 (5,2)인 새 환경에서도 멱등)
ALTER TABLE revenue_insights
    ALTER COLUMN confidence TYPE NUMERIC(5, 2);
