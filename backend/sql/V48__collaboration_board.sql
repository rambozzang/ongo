-- 협업 보드
CREATE TABLE board_tasks (
    id              BIGSERIAL       PRIMARY KEY,
    workspace_id    BIGINT          NOT NULL REFERENCES workspaces(id),
    title           VARCHAR(300)    NOT NULL,
    description     TEXT,
    column_type     VARCHAR(20)     NOT NULL DEFAULT 'IDEA',
    priority        VARCHAR(10)     NOT NULL DEFAULT 'MEDIUM',
    status          VARCHAR(20)     NOT NULL DEFAULT 'TODO',
    assignee_id     BIGINT          REFERENCES users(id),
    due_date        DATE,
    tags            TEXT[]          DEFAULT '{}',
    attachments     INT             NOT NULL DEFAULT 0,
    video_id        VARCHAR(100),
    order_index     INT             NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE board_comments (
    id              BIGSERIAL       PRIMARY KEY,
    task_id         BIGINT          NOT NULL REFERENCES board_tasks(id) ON DELETE CASCADE,
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    content         TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE board_activities (
    id              BIGSERIAL       PRIMARY KEY,
    workspace_id    BIGINT          NOT NULL REFERENCES workspaces(id),
    user_id         BIGINT          NOT NULL REFERENCES users(id),
    action          VARCHAR(20)     NOT NULL,
    task_id         BIGINT          REFERENCES board_tasks(id) ON DELETE SET NULL,
    task_title      VARCHAR(300),
    from_column     VARCHAR(20),
    to_column       VARCHAR(20),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_board_tasks_workspace ON board_tasks(workspace_id);
CREATE INDEX idx_board_tasks_column ON board_tasks(column_type);
CREATE INDEX idx_board_tasks_assignee ON board_tasks(assignee_id);
CREATE INDEX idx_board_activities_workspace ON board_activities(workspace_id);
