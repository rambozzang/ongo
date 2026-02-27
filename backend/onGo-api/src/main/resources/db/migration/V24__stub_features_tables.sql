-- V24: Stub Features Tables
-- 52개 스텁 엔드포인트를 실제 구현하기 위한 테이블 생성

-- =====================================================
-- 1. Creator Portfolios
-- =====================================================
CREATE TABLE creator_portfolios (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    display_name VARCHAR(200),
    bio TEXT,
    category VARCHAR(50),
    profile_image_url VARCHAR(500),
    theme VARCHAR(30) DEFAULT 'default',
    public_slug VARCHAR(100) UNIQUE,
    showcase_videos JSONB DEFAULT '[]',
    brand_history JSONB DEFAULT '[]',
    is_public BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_creator_portfolios_user_id ON creator_portfolios(user_id);
CREATE UNIQUE INDEX idx_creator_portfolios_slug ON creator_portfolios(public_slug);

-- =====================================================
-- 2. Performance Predictions
-- =====================================================
CREATE TABLE performance_predictions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    video_id BIGINT REFERENCES videos(id),
    predicted_views BIGINT,
    predicted_likes BIGINT,
    predicted_engagement_rate DECIMAL(5,4),
    confidence_score DECIMAL(5,4),
    optimal_upload_time TIMESTAMP,
    prediction_data JSONB,
    actual_views BIGINT,
    actual_likes BIGINT,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_performance_predictions_user_id ON performance_predictions(user_id);
CREATE INDEX idx_performance_predictions_video_id ON performance_predictions(video_id);

-- =====================================================
-- 3. Visual Workflows (별도 테이블, automation_workflows와 다른 구조)
-- =====================================================
CREATE TABLE visual_workflows (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    workflow_data JSONB NOT NULL DEFAULT '{}',
    is_active BOOLEAN DEFAULT FALSE,
    last_triggered_at TIMESTAMP,
    trigger_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_visual_workflows_user_id ON visual_workflows(user_id);

-- =====================================================
-- 4. Brand Voice Profiles
-- =====================================================
CREATE TABLE brand_voice_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    tone VARCHAR(50) NOT NULL,
    train_status VARCHAR(20) DEFAULT 'PENDING',
    sample_count INT DEFAULT 0,
    vocabulary JSONB DEFAULT '[]',
    avoid_words JSONB DEFAULT '[]',
    emoji_usage VARCHAR(20) DEFAULT 'NONE',
    avg_sentence_length INT DEFAULT 0,
    signature_phrase VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_brand_voice_profiles_user_id ON brand_voice_profiles(user_id);

-- =====================================================
-- 5. Creator Profiles (Creator Network)
-- =====================================================
CREATE TABLE creator_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    avatar_url VARCHAR(500),
    platform VARCHAR(30) NOT NULL,
    subscribers BIGINT DEFAULT 0,
    category VARCHAR(50),
    match_score INT DEFAULT 0,
    is_connected BOOLEAN DEFAULT FALSE,
    bio TEXT,
    joined_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_creator_profiles_user_id ON creator_profiles(user_id);
CREATE INDEX idx_creator_profiles_platform ON creator_profiles(platform);

-- =====================================================
-- 6. Collaboration Requests
-- =====================================================
CREATE TABLE collaboration_requests (
    id BIGSERIAL PRIMARY KEY,
    from_creator_id BIGINT NOT NULL REFERENCES creator_profiles(id),
    to_creator_id BIGINT NOT NULL REFERENCES creator_profiles(id),
    message TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    proposed_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_collaboration_requests_from ON collaboration_requests(from_creator_id);
CREATE INDEX idx_collaboration_requests_to ON collaboration_requests(to_creator_id);

-- =====================================================
-- 7. Sentiment Results
-- =====================================================
CREATE TABLE sentiment_results (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES workspaces(id),
    content_title VARCHAR(500) NOT NULL,
    platform VARCHAR(30) NOT NULL,
    total_comments INT DEFAULT 0,
    positive_count INT DEFAULT 0,
    neutral_count INT DEFAULT 0,
    negative_count INT DEFAULT 0,
    positive_rate DECIMAL(5,2) DEFAULT 0,
    avg_sentiment_score INT DEFAULT 0,
    top_positive_keywords JSONB DEFAULT '[]',
    top_negative_keywords JSONB DEFAULT '[]',
    analyzed_at TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_sentiment_results_workspace_id ON sentiment_results(workspace_id);

-- =====================================================
-- 8. Comment Sentiments
-- =====================================================
CREATE TABLE comment_sentiments (
    id BIGSERIAL PRIMARY KEY,
    result_id BIGINT NOT NULL REFERENCES sentiment_results(id) ON DELETE CASCADE,
    comment_text TEXT NOT NULL,
    author_name VARCHAR(200) NOT NULL,
    sentiment VARCHAR(20) DEFAULT 'NEUTRAL',
    score INT DEFAULT 50,
    keywords JSONB DEFAULT '[]',
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_comment_sentiments_result_id ON comment_sentiments(result_id);

-- =====================================================
-- 9. Thumbnail A/B Tests
-- =====================================================
CREATE TABLE thumbnail_ab_tests (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES workspaces(id),
    video_title VARCHAR(500) NOT NULL,
    platform VARCHAR(30) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    variant_a_image_url VARCHAR(500),
    variant_a_ctr DECIMAL(7,4) DEFAULT 0,
    variant_a_impressions BIGINT DEFAULT 0,
    variant_a_clicks BIGINT DEFAULT 0,
    variant_b_image_url VARCHAR(500),
    variant_b_ctr DECIMAL(7,4) DEFAULT 0,
    variant_b_impressions BIGINT DEFAULT 0,
    variant_b_clicks BIGINT DEFAULT 0,
    winner VARCHAR(10),
    started_at TIMESTAMP DEFAULT NOW(),
    ended_at TIMESTAMP
);
CREATE INDEX idx_thumbnail_ab_tests_workspace_id ON thumbnail_ab_tests(workspace_id);

-- =====================================================
-- 10. Funnel Stages
-- =====================================================
CREATE TABLE funnel_stages (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    video_id BIGINT NOT NULL REFERENCES videos(id),
    video_title VARCHAR(500) NOT NULL,
    stage VARCHAR(50) NOT NULL,
    count BIGINT DEFAULT 0,
    rate DECIMAL(7,4) DEFAULT 0,
    drop_off DECIMAL(7,4) DEFAULT 0,
    measured_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_funnel_stages_user_id ON funnel_stages(user_id);
CREATE INDEX idx_funnel_stages_video_id ON funnel_stages(video_id);

-- =====================================================
-- 11. Funnel Comparisons
-- =====================================================
CREATE TABLE funnel_comparisons (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    video_id_a BIGINT NOT NULL REFERENCES videos(id),
    video_title_a VARCHAR(500) NOT NULL,
    video_id_b BIGINT NOT NULL REFERENCES videos(id),
    video_title_b VARCHAR(500) NOT NULL,
    winner VARCHAR(10),
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_funnel_comparisons_user_id ON funnel_comparisons(user_id);

-- =====================================================
-- 12. Cross Platform Reports
-- =====================================================
CREATE TABLE cross_platform_reports (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    period VARCHAR(30) NOT NULL,
    contents JSONB DEFAULT '[]',
    platform_summaries JSONB DEFAULT '[]',
    audience_overlap JSONB DEFAULT '{}',
    recommendations JSONB DEFAULT '[]',
    generated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_cross_platform_reports_user_id ON cross_platform_reports(user_id);

-- =====================================================
-- 13. Library Items
-- =====================================================
CREATE TABLE library_items (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(500) NOT NULL,
    type VARCHAR(30) NOT NULL,
    platform VARCHAR(30),
    thumbnail_url VARCHAR(500),
    file_size BIGINT DEFAULT 0,
    tags JSONB DEFAULT '[]',
    folder_id BIGINT,
    uploaded_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_library_items_user_id ON library_items(user_id);
CREATE INDEX idx_library_items_folder_id ON library_items(folder_id);

-- =====================================================
-- 14. Library Folders
-- =====================================================
CREATE TABLE library_folders (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    parent_id BIGINT REFERENCES library_folders(id),
    color VARCHAR(20) DEFAULT '#3B82F6',
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_library_folders_user_id ON library_folders(user_id);
ALTER TABLE library_items ADD CONSTRAINT fk_library_items_folder FOREIGN KEY (folder_id) REFERENCES library_folders(id);

-- =====================================================
-- 15. Revenue Goals
-- =====================================================
CREATE TABLE revenue_goals (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES workspaces(id),
    name VARCHAR(200) NOT NULL,
    target_amount BIGINT NOT NULL,
    current_amount BIGINT DEFAULT 0,
    currency VARCHAR(10) DEFAULT 'KRW',
    period VARCHAR(30) NOT NULL,
    platform VARCHAR(30) DEFAULT 'ALL',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    progress INT DEFAULT 0,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_revenue_goals_workspace_id ON revenue_goals(workspace_id);

-- =====================================================
-- 16. Revenue Goal Milestones
-- =====================================================
CREATE TABLE revenue_goal_milestones (
    id BIGSERIAL PRIMARY KEY,
    goal_id BIGINT NOT NULL REFERENCES revenue_goals(id) ON DELETE CASCADE,
    label VARCHAR(200) NOT NULL,
    target_amount BIGINT NOT NULL,
    reached BOOLEAN DEFAULT FALSE,
    reached_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_revenue_goal_milestones_goal_id ON revenue_goal_milestones(goal_id);

-- =====================================================
-- 17. Live Streams
-- =====================================================
CREATE TABLE live_streams (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(500) NOT NULL,
    description TEXT,
    platform VARCHAR(30) NOT NULL,
    status VARCHAR(20) DEFAULT 'SCHEDULED',
    scheduled_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    viewer_count INT DEFAULT 0,
    peak_viewers INT DEFAULT 0,
    chat_messages INT DEFAULT 0,
    stream_url VARCHAR(500),
    thumbnail_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_live_streams_user_id ON live_streams(user_id);
CREATE INDEX idx_live_streams_status ON live_streams(user_id, status);

-- =====================================================
-- 18. Stream Chats
-- =====================================================
CREATE TABLE stream_chats (
    id BIGSERIAL PRIMARY KEY,
    stream_id BIGINT NOT NULL REFERENCES live_streams(id) ON DELETE CASCADE,
    username VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT NOW(),
    is_highlighted BOOLEAN DEFAULT FALSE,
    is_moderator BOOLEAN DEFAULT FALSE
);
CREATE INDEX idx_stream_chats_stream_id ON stream_chats(stream_id);

-- =====================================================
-- 19. Content A/B Tests
-- =====================================================
CREATE TABLE content_ab_tests (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(500) NOT NULL,
    status VARCHAR(20) DEFAULT 'RUNNING',
    winner VARCHAR(10),
    confidence DECIMAL(5,4) DEFAULT 0,
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_content_ab_tests_user_id ON content_ab_tests(user_id);

-- =====================================================
-- 20. Content Variants
-- =====================================================
CREATE TABLE content_variants (
    id BIGSERIAL PRIMARY KEY,
    test_id BIGINT NOT NULL REFERENCES content_ab_tests(id) ON DELETE CASCADE,
    label VARCHAR(50) NOT NULL,
    video_id BIGINT NOT NULL REFERENCES videos(id),
    video_title VARCHAR(500) NOT NULL,
    views BIGINT DEFAULT 0,
    likes BIGINT DEFAULT 0,
    comments BIGINT DEFAULT 0,
    ctr DECIMAL(7,4) DEFAULT 0,
    avg_watch_time INT DEFAULT 0
);
CREATE INDEX idx_content_variants_test_id ON content_variants(test_id);

-- =====================================================
-- 21. Fan Campaigns
-- =====================================================
CREATE TABLE fan_campaigns (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    segment_id BIGINT NOT NULL,
    segment_name VARCHAR(200) NOT NULL,
    campaign_type VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    target_count INT DEFAULT 0,
    sent_count INT DEFAULT 0,
    open_rate DECIMAL(5,4),
    click_rate DECIMAL(5,4),
    status VARCHAR(20) DEFAULT 'DRAFT',
    scheduled_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_fan_campaigns_user_id ON fan_campaigns(user_id);

-- =====================================================
-- 22. Campaign Segments
-- =====================================================
CREATE TABLE campaign_segments (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    name VARCHAR(200) NOT NULL,
    criteria TEXT NOT NULL,
    fan_count INT DEFAULT 0,
    avg_engagement DECIMAL(7,4) DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_campaign_segments_user_id ON campaign_segments(user_id);

-- =====================================================
-- 23. Content Rights
-- =====================================================
CREATE TABLE content_rights (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    video_id VARCHAR(100),
    video_title VARCHAR(500),
    asset_name VARCHAR(500) NOT NULL,
    asset_type VARCHAR(30) DEFAULT 'MUSIC',
    license_type VARCHAR(30) DEFAULT 'FREE',
    license_status VARCHAR(30) DEFAULT 'ACTIVE',
    source VARCHAR(500),
    license_url VARCHAR(500),
    expires_at TIMESTAMP,
    purchased_at TIMESTAMP,
    cost BIGINT DEFAULT 0,
    currency VARCHAR(10) DEFAULT 'KRW',
    notes TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_content_rights_user_id ON content_rights(user_id);

-- =====================================================
-- 24. Rights Alerts
-- =====================================================
CREATE TABLE rights_alerts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    content_right_id BIGINT NOT NULL REFERENCES content_rights(id) ON DELETE CASCADE,
    asset_name VARCHAR(500) NOT NULL,
    asset_type VARCHAR(30) NOT NULL,
    message TEXT NOT NULL,
    severity VARCHAR(20) DEFAULT 'INFO',
    days_until_expiry INT DEFAULT 0,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_rights_alerts_user_id ON rights_alerts(user_id);
CREATE INDEX idx_rights_alerts_content_right_id ON rights_alerts(content_right_id);

-- =====================================================
-- 25. Content Clips
-- =====================================================
CREATE TABLE content_clips (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    source_video_id BIGINT REFERENCES videos(id),
    title VARCHAR(500),
    start_time_ms BIGINT NOT NULL,
    end_time_ms BIGINT NOT NULL,
    aspect_ratio VARCHAR(10) DEFAULT '9:16',
    status VARCHAR(20) DEFAULT 'PENDING',
    output_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_content_clips_user_id ON content_clips(user_id);
CREATE INDEX idx_content_clips_source_video_id ON content_clips(source_video_id);

-- =====================================================
-- 26. Video Captions (contentstudio 추가 엔티티)
-- =====================================================
CREATE TABLE video_captions (
    id BIGSERIAL PRIMARY KEY,
    video_id BIGINT NOT NULL REFERENCES videos(id),
    language VARCHAR(10) DEFAULT 'ko',
    caption_data JSONB NOT NULL,
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_video_captions_video_id ON video_captions(video_id);

-- =====================================================
-- 27. AI Thumbnails (contentstudio 추가 엔티티)
-- =====================================================
CREATE TABLE ai_thumbnails (
    id BIGSERIAL PRIMARY KEY,
    video_id BIGINT NOT NULL REFERENCES videos(id),
    style VARCHAR(50),
    text_overlay VARCHAR(500),
    image_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_ai_thumbnails_video_id ON ai_thumbnails(video_id);
