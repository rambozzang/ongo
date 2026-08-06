-- 계정 삭제를 비동기 요청으로 바꾸기 위한 스키마.
--
-- 설계는 hybrid 다.
--   account_deletion_jobs  durable workflow/status, 재시도, idempotency key 를 소유
--   users.deletion_state   단일 authoritative gate. 인증 직후 공통 경계에서 빠르게 차단
--
-- jobs 테이블만 두면 모든 쓰기 경로가 매번 조인해야 하고 누락된 경로가 생긴다.
-- users 컬럼만 두면 durable 단계·재시도·외부 정리 상태를 담을 곳이 없다.
--
-- 근거: docs/plans/account-deletion-policy-table.md §6

-- ---------------------------------------------------------------------------
-- 1. users — authoritative gate
-- ---------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS deletion_state        VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS deletion_requested_at TIMESTAMP;

-- 오타나 새 상태가 조용히 들어오는 것을 막는다. 게이트 값이 틀리면 동결이 새어버린다.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_users_deletion_state' AND conrelid = 'public.users'::regclass
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT ck_users_deletion_state
            CHECK (deletion_state IN ('ACTIVE', 'DELETION_REQUESTED', 'DELETED'));
    END IF;
END $$;

-- 동결된 계정만 훑는 경로(관리·모니터링)를 위해 부분 인덱스를 둔다.
-- 대다수 행이 ACTIVE 라 전체 인덱스는 낭비다.
CREATE INDEX IF NOT EXISTS idx_users_deletion_state_pending
    ON users (deletion_state) WHERE deletion_state <> 'ACTIVE';

COMMENT ON COLUMN users.deletion_state IS
    '계정 삭제 게이트. ACTIVE 가 아니면 쓰기를 막는다. 예외는 삭제 요청·토큰 갱신·로그아웃·상태 조회뿐';

-- ---------------------------------------------------------------------------
-- 2. account_deletion_jobs — durable workflow
-- ---------------------------------------------------------------------------
--
-- **users 를 외래키로 참조하지 않는다.** 의도한 것이다.
--
-- 이 테이블은 삭제가 끝난 뒤에도 남아야 하는 감사 기록이다. 외래키를 걸면
--   - CASCADE 면 사용자를 지우는 순간 삭제 기록 자체가 사라진다
--   - NO ACTION 이면 job 행이 사용자 삭제를 영원히 막는다
-- 둘 다 틀렸다. 그래서 user_id 를 그냥 BIGINT 로 둔다.
--
-- 부수 효과로 이 테이블은 계정 삭제 정책 레지스트리 대상이 아니다
-- (users 참조 외래키가 없으므로 AccountDeletionPolicyGuardIT 가 요구하지 않는다).
CREATE TABLE IF NOT EXISTS account_deletion_jobs (
    id                BIGSERIAL PRIMARY KEY,

    -- 외래키를 일부러 걸지 않는다. 위 주석 참조.
    user_id           BIGINT NOT NULL,

    status            VARCHAR(40) NOT NULL DEFAULT 'REQUESTED',

    -- 같은 요청이 두 번 들어와도 job 이 하나만 생기게 한다.
    idempotency_key   VARCHAR(100) NOT NULL,

    -- 왜 막혔는지. 진단·지원용이며 **개인정보 본문을 담지 않는다.**
    -- 제약 이름 같은 내부 식별자만 넣는다.
    support_reference TEXT,

    attempt_count     INTEGER NOT NULL DEFAULT 0,
    last_error_code   VARCHAR(80),

    requested_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),

    -- DB 삭제 트랜잭션이 커밋된 시점. 이 값이 있으면 DB 단계를 다시 하지 않는다.
    db_committed_at   TIMESTAMP,
    completed_at      TIMESTAMP,

    CONSTRAINT uq_account_deletion_jobs_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_account_deletion_jobs_status CHECK (
        status IN (
            'REQUESTED',
            'IN_PROGRESS',
            'DB_COMMITTED',
            'EXTERNAL_CLEANUP_PENDING',
            'COMPLETED',
            'BLOCKED_POLICY',
            'FAILED'
        )
    )
);

-- **동시 요청 중 하나만 성공해야 한다.**
-- 진행 중인 job 이 있으면 같은 사용자에 대해 새 job 을 만들 수 없다.
-- 종료 상태(COMPLETED/BLOCKED_POLICY/FAILED)는 제외해서 재요청이 가능하다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_account_deletion_jobs_active_user
    ON account_deletion_jobs (user_id)
    WHERE status IN ('REQUESTED', 'IN_PROGRESS', 'DB_COMMITTED', 'EXTERNAL_CLEANUP_PENDING');

-- 재시도 대상(EXTERNAL_CLEANUP_PENDING)과 상태 조회를 위한 인덱스.
CREATE INDEX IF NOT EXISTS idx_account_deletion_jobs_status_updated
    ON account_deletion_jobs (status, updated_at);

CREATE INDEX IF NOT EXISTS idx_account_deletion_jobs_user
    ON account_deletion_jobs (user_id, requested_at DESC);

COMMENT ON TABLE account_deletion_jobs IS
    '계정 삭제 작업의 durable 상태. 삭제 후에도 남는 감사 기록이라 users 를 외래키로 참조하지 않는다';
COMMENT ON COLUMN account_deletion_jobs.support_reference IS
    '진단·지원용 내부 식별자. 개인정보 본문을 담지 않는다';
