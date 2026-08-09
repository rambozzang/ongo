-- 작성 화면에서 서버가 생성한 영상을 업로드/URL 임포트와 구분한다.
ALTER TYPE video_source ADD VALUE IF NOT EXISTS 'GENERATED';
