-- 예약 취소는 Schedule 행뿐 아니라 durable video_uploads 큐에도 전파되어야
-- 디스패처가 취소된 콘텐츠를 외부 플랫폼에 게시하지 않는다.
ALTER TYPE upload_status ADD VALUE IF NOT EXISTS 'CANCELLED';
