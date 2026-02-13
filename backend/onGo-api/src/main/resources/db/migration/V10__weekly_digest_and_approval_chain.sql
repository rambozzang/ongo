-- ============================================================================
-- V10: Weekly Digest + Approval Chain tables
-- ============================================================================

-- --------------------------------------------------------------------------
-- weekly_digests: AI 주간 다이제스트
-- --------------------------------------------------------------------------
CREATE TABLE weekly_digests (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    week_start_date     DATE NOT NULL,
    week_end_date       DATE NOT NULL,
    summary             TEXT NOT NULL,
    top_videos          TEXT NOT NULL DEFAULT '',
    anomalies           TEXT NOT NULL DEFAULT '',
    action_items        TEXT NOT NULL DEFAULT '',
    generated_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_weekly_digests_user_id ON weekly_digests(user_id, created_at DESC);
CREATE UNIQUE INDEX idx_weekly_digests_user_week ON weekly_digests(user_id, week_start_date);

CREATE TRIGGER trg_weekly_digests_updated_at
    BEFORE UPDATE ON weekly_digests FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

COMMENT ON TABLE weekly_digests IS 'AI 주간 다이제스트 (매주 월요일 자동 생성, Pro/Business 전용)';

-- --------------------------------------------------------------------------
-- approvals: 승인 테이블 (InMemory → DB 영속화)
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS approvals (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    video_id            BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    video_title         VARCHAR(200) NOT NULL,
    platforms           VARCHAR(200) NOT NULL,
    scheduled_at        TIMESTAMP,
    requester_id        BIGINT NOT NULL REFERENCES users(id),
    requester_name      VARCHAR(100) NOT NULL DEFAULT '',
    reviewer_id         BIGINT REFERENCES users(id),
    reviewer_name       VARCHAR(100),
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    comment             TEXT,
    revision_note       TEXT,
    requested_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    decided_at          TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_approvals_user_id ON approvals(user_id);
CREATE INDEX idx_approvals_reviewer_id ON approvals(reviewer_id);
CREATE INDEX idx_approvals_status ON approvals(status);

CREATE TRIGGER trg_approvals_updated_at
    BEFORE UPDATE ON approvals FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

COMMENT ON TABLE approvals IS '콘텐츠 승인 요청';

-- --------------------------------------------------------------------------
-- approval_comments: 승인 코멘트
-- --------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS approval_comments (
    id                  BIGSERIAL PRIMARY KEY,
    approval_id         BIGINT NOT NULL REFERENCES approvals(id) ON DELETE CASCADE,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    user_name           VARCHAR(100) NOT NULL,
    content             TEXT NOT NULL,
    field               VARCHAR(50),
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_approval_comments_approval_id ON approval_comments(approval_id);

COMMENT ON TABLE approval_comments IS '승인 요청 코멘트';

-- --------------------------------------------------------------------------
-- approval_chains: 멀티스텝 승인 체인
-- --------------------------------------------------------------------------
CREATE TABLE approval_chains (
    id                  BIGSERIAL PRIMARY KEY,
    approval_id         BIGINT NOT NULL REFERENCES approvals(id) ON DELETE CASCADE,
    step_order          INTEGER NOT NULL,
    approver_id         BIGINT NOT NULL REFERENCES users(id),
    approver_name       VARCHAR(100) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    deadline_at         TIMESTAMP,
    approved_at         TIMESTAMP,
    comment             TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_approval_chains_approval_id ON approval_chains(approval_id, step_order);
CREATE INDEX idx_approval_chains_approver_id ON approval_chains(approver_id);
CREATE INDEX idx_approval_chains_deadline ON approval_chains(deadline_at)
    WHERE status = 'PENDING';

CREATE TRIGGER trg_approval_chains_updated_at
    BEFORE UPDATE ON approval_chains FOR EACH ROW EXECUTE FUNCTION fn_update_timestamp();

COMMENT ON TABLE approval_chains IS '멀티스텝 승인 체인 (단계별 승인자)';
