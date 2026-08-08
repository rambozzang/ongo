-- 예약 자체도 플랫폼별 최종 결과를 잃지 않도록 terminal outcome을 구분한다.
ALTER TYPE schedule_status ADD VALUE IF NOT EXISTS 'PARTIALLY_PUBLISHED';
ALTER TYPE schedule_status ADD VALUE IF NOT EXISTS 'UNCONFIRMED';
