-- V37: 서버 영상 처리 기능 제거에 따른 불필요 테이블/컬럼 삭제
-- video_media_info (FFprobe), video_processing_progress (진행률), video_subtitles (Whisper STT)
-- video_resizes (리사이즈), processing_stage ENUM, videos 컬럼 2개

-- 테이블 삭제
DROP TABLE IF EXISTS video_media_info CASCADE;
DROP TABLE IF EXISTS video_processing_progress CASCADE;
DROP TABLE IF EXISTS video_subtitles CASCADE;
DROP TABLE IF EXISTS video_resizes CASCADE;

-- videos 테이블에서 불필요 컬럼 제거
ALTER TABLE videos DROP COLUMN IF EXISTS auto_thumbnails;
ALTER TABLE videos DROP COLUMN IF EXISTS selected_thumbnail_index;

-- ENUM 타입 삭제
DROP TYPE IF EXISTS processing_stage;
