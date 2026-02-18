-- V16: content_hash 유니크 제약 추가 (동일 사용자 중복 업로드 방지)
-- 기존 일반 인덱스를 유니크 인덱스로 교체

-- 기존 일반 인덱스 삭제
DROP INDEX IF EXISTS idx_videos_content_hash;

-- 사용자별 content_hash 유니크 제약 (NULL은 허용 — 해시 미계산 상태)
CREATE UNIQUE INDEX idx_videos_user_content_hash
    ON videos(user_id, content_hash)
    WHERE content_hash IS NOT NULL;
