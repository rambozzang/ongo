-- 콘텐츠 성과 보고서
CREATE TABLE performance_reports (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id),
    title               VARCHAR(300) NOT NULL,
    period              VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    start_date          DATE NOT NULL,
    end_date            DATE NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_views         BIGINT NOT NULL DEFAULT 0,
    total_engagement    BIGINT NOT NULL DEFAULT 0,
    top_video_id        BIGINT,
    top_video_title     VARCHAR(500),
    report_url          VARCHAR(1000),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE report_sections (
    id              BIGSERIAL PRIMARY KEY,
    report_id       BIGINT NOT NULL REFERENCES performance_reports(id) ON DELETE CASCADE,
    section_type    VARCHAR(50) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    content         TEXT NOT NULL,
    chart_data      JSONB,
    sort_order      INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_perf_reports_user ON performance_reports(user_id);
CREATE INDEX idx_perf_reports_status ON performance_reports(status);
CREATE INDEX idx_perf_reports_period ON performance_reports(period);
CREATE INDEX idx_report_sections_report ON report_sections(report_id);
