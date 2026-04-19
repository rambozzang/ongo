-- revenue_alert_configs: (user_id, alert_type) UNIQUE 제약조건 추가
-- 동일 사용자가 같은 알림 타입을 중복 등록하는 것을 방지
ALTER TABLE revenue_alert_configs
    ADD CONSTRAINT uq_revenue_alert_configs_user_type UNIQUE (user_id, alert_type);

-- revenue_insights: confidence 컬럼 정밀도 확대 NUMERIC(3,2) → NUMERIC(5,2)
-- AI 신뢰도 값이 소수점 포함 최대 999.99까지 저장 가능하도록 수정
ALTER TABLE revenue_insights
    ALTER COLUMN confidence TYPE NUMERIC(5, 2);
