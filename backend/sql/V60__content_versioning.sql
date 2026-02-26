-- Wave 16: 콘텐츠 버전 관리
CREATE TABLE content_version_groups (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspaces(id),
    content_id      BIGINT NOT NULL,
    content_title   VARCHAR(300) NOT NULL,
    platform        VARCHAR(30) NOT NULL,
    total_versions  INT NOT NULL DEFAULT 0,
    latest_version  INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE content_versions (
    id                  BIGSERIAL PRIMARY KEY,
    group_id            BIGINT NOT NULL REFERENCES content_version_groups(id) ON DELETE CASCADE,
    version_number      INT NOT NULL,
    change_type         VARCHAR(20) NOT NULL,
    change_description  TEXT NOT NULL,
    previous_value      TEXT,
    new_value           TEXT,
    perf_before_views   BIGINT,
    perf_before_likes   INT,
    perf_before_engagement NUMERIC(5,2),
    perf_before_ctr     NUMERIC(5,2),
    perf_after_views    BIGINT,
    perf_after_likes    INT,
    perf_after_engagement NUMERIC(5,2),
    perf_after_ctr      NUMERIC(5,2),
    created_by          VARCHAR(100) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_content_version_groups_workspace ON content_version_groups(workspace_id);
CREATE INDEX idx_content_versions_group ON content_versions(group_id);
CREATE INDEX idx_content_versions_order ON content_versions(group_id, version_number);
