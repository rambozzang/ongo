-- V18: Storage Quota & Admin Management
-- subscriptions 테이블에 관리자 오버라이드용 스토리지 한도 컬럼 추가
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS storage_quota_limit_bytes BIGINT NULL;
COMMENT ON COLUMN subscriptions.storage_quota_limit_bytes IS 'Admin override storage quota in bytes. NULL = use plan default.';

-- users.role 컬럼에 인덱스 추가 (관리자 조회 최적화)
CREATE INDEX IF NOT EXISTS idx_users_role ON users (role);
