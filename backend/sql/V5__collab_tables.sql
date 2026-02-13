-- Team Members
CREATE TABLE team_members (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    member_email VARCHAR(255) NOT NULL,
    member_name VARCHAR(100),
    role VARCHAR(20) DEFAULT 'VIEWER',
    status VARCHAR(20) DEFAULT 'INVITED',
    permissions JSONB DEFAULT '{}',
    invited_at TIMESTAMP DEFAULT NOW(),
    joined_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_team_members_user_id ON team_members(user_id);
CREATE UNIQUE INDEX idx_team_members_user_email ON team_members(user_id, member_email);

-- Inbox Messages
CREATE TABLE inbox_messages (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    platform VARCHAR(20),
    sender_name VARCHAR(100) NOT NULL,
    sender_avatar_url VARCHAR(500),
    message_type VARCHAR(20) DEFAULT 'COMMENT',
    content TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    is_starred BOOLEAN DEFAULT FALSE,
    platform_message_id VARCHAR(255),
    video_id BIGINT REFERENCES videos(id),
    received_at TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_inbox_messages_user_id ON inbox_messages(user_id);
CREATE INDEX idx_inbox_messages_is_read ON inbox_messages(user_id, is_read);

-- Activity Logs
CREATE TABLE activity_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50),
    entity_id BIGINT,
    details JSONB DEFAULT '{}',
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_activity_logs_user_id ON activity_logs(user_id);
CREATE INDEX idx_activity_logs_created_at ON activity_logs(created_at);
