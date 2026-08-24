-- 계정 탈퇴 시 외부 스토리지 객체를 확실히 회수하기 위한 스키마.
--
-- 배경: 탈퇴는 DB row 만 지우고 R2/S3 객체는 남겨 왔다. 비용이 계속 나가고, 무엇보다
-- 탈퇴한 사용자의 영상이 버킷에 그대로 남는 개인정보 문제다. 그렇다고 DB 삭제 전에 객체를
-- 지우면 그 트랜잭션이 롤백됐을 때 **살아있는 계정의 파일을 잃는다.** 되돌릴 수 없다.
--
-- 그래서 두 단계로 나눈다.
--   1) DB 삭제 트랜잭션 안에서 지울 객체 키를 원장에 적고 함께 커밋한다
--   2) 커밋된 뒤에야 워커가 그 키를 실제로 지운다
-- 원장이 커밋됐다는 것은 "DB 삭제가 확정됐다"는 뜻이므로, 그 이후의 객체 삭제는 안전하다.

-- 1. 객체 키를 행에 직접 보관한다.
--
-- 지금까지는 file_url(7일 presigned URL)에서 키를 되짚어야 했는데, 서명 쿼리가 붙어 있고
-- shorts/run-* 같은 경로는 기존 파서가 인정하지 않아 실패한다. 삭제 대상을 URL 추측으로
-- 정하는 것은 남의 파일을 지울 위험이 있어 허용할 수 없다.
--
-- nullable 로만 추가한다. NOT NULL 이나 backfill 은 수백만 행에서 테이블 재작성/장시간
-- 잠금을 유발한다. 기존 행은 key 가 NULL 로 남고, 탈퇴 시 그런 행은 삭제하지 않고
-- 검토 대상으로 남긴다(추측 삭제 금지).
ALTER TABLE videos ADD COLUMN IF NOT EXISTS storage_object_key VARCHAR(1024);
ALTER TABLE assets ADD COLUMN IF NOT EXISTS storage_object_key VARCHAR(1024);

-- 2. 삭제할 객체의 원장(outbox).
--
-- job 당 여러 건이 생기고 각 건이 독립적으로 성공/실패한다. 하나가 실패해도 나머지는
-- 진행하고, 실패한 건만 다음 tick 이 다시 집는다. 전부 성공해야 job 을 COMPLETED 로 올린다.
CREATE TABLE IF NOT EXISTS account_deletion_object_tasks (
    id                BIGSERIAL PRIMARY KEY,
    job_id            BIGINT NOT NULL REFERENCES account_deletion_jobs (id) ON DELETE CASCADE,
    -- 버킷 상대 키. 절대 URL 이 아니다 — 외부 URL 은 애초에 원장에 들어오지 않는다.
    object_key        VARCHAR(1024) NOT NULL,
    -- PENDING → DONE, 실패 시 PENDING 유지하며 attempt_count 증가.
    -- BLOCKED 는 사람이 봐야 하는 상태이며 job 을 COMPLETED 로 올리지 못하게 막는다.
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count     INT NOT NULL DEFAULT 0,
    last_error_code   VARCHAR(100),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at      TIMESTAMP,
    -- 같은 job 에 같은 키를 두 번 넣지 않는다. 스냅샷 재실행이나 재시도가 겹쳐도
    -- 삭제가 중복 시도되지 않게 하는 멱등성의 근거다.
    CONSTRAINT uq_account_deletion_object_tasks_job_key UNIQUE (job_id, object_key)
);

-- 3. 키를 확정할 수 없는 행 수를 job 에 영속화한다.
--
-- 메모리에만 들고 있으면 DB 커밋 직후 프로세스가 죽었을 때 그 사실이 사라진다. 다음 tick 은
-- "원장에 남은 일이 없다"만 보고 COMPLETED 로 올리는데, 실제로는 버킷에 파일이 남아 있다.
-- 지우지 않은 것을 지웠다고 기록하는 것이 이 기능에서 가장 나쁜 실패다.
ALTER TABLE account_deletion_jobs
    ADD COLUMN IF NOT EXISTS unresolved_object_rows INT NOT NULL DEFAULT 0;

-- 4. 외부 정리 재시도 시각.
--
-- 외부 정리는 DB 가 이미 커밋된 뒤라 다시 시도해도 안전하다. 그래서 실패했다고 30분씩
-- 묵히지 않고 **다음 tick 에 바로** 집을 수 있어야 한다(NULL = 즉시 가능).
--
-- 다만 영구 실패하는 job 하나가 매 tick 을 독차지하면 다른 사용자의 탈퇴가 밀린다.
-- claimNext 는 한 번에 job 하나만 집기 때문이다. 그래서 실패할 때만 지수 backoff 로
-- 다음 시각을 밀어 둔다 — 성공 경로에는 지연이 없고, 실패 경로만 간격이 벌어진다.
ALTER TABLE account_deletion_jobs
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP;

-- 워커가 매 tick 마다 "아직 남은 일"을 찾는 질의. job 별 미완 건수 확인에도 쓴다.
CREATE INDEX IF NOT EXISTS idx_account_deletion_object_tasks_status
    ON account_deletion_object_tasks (status, job_id);
