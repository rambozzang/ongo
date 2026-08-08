-- 외부 플랫폼 호출이 타임아웃된 경우 실제 게시 여부를 알 수 없으므로
-- FAILED와 분리한다. 여러 플랫폼 중 일부만 성공한 영상도 별도 표시한다.
ALTER TYPE upload_status ADD VALUE IF NOT EXISTS 'UNCONFIRMED';
ALTER TYPE upload_status ADD VALUE IF NOT EXISTS 'PARTIALLY_PUBLISHED';
