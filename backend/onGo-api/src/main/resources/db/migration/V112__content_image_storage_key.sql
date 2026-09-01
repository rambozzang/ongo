-- 게시 이미지의 **실제 저장 키**를 남긴다.
--
-- 이미지는 `content/{videoId}/images/...` 에 저장되는데 그 키가 어디에도 없었다. 영상 삭제는
-- videoId 로 접두사를 지울 수 있지만, 탈퇴 정리는 사용자별 접두사가 없어 키를 하나씩 알아야
-- 한다. `image_url` 에서 되짚는 방법은 쓰지 않는다 — 서명이 붙은 presigned URL 이고 경로
-- 형식도 어댑터마다 달라서, 추측이 빗나가면 남의 파일을 지운다. 되돌릴 수 없는 작업이다.
--
-- NULL 은 **V112 이전에 만들어진 행**이라는 뜻이다. 자동 삭제 대상이 아니라 수기 확인
-- 대상으로 세어 올린다(UserObjectSnapshotAdapter.countUnresolved). videos/assets 가
-- V96 에서 같은 규칙을 이미 쓰고 있다.
ALTER TABLE content_images ADD COLUMN IF NOT EXISTS storage_object_key TEXT;

-- 탈퇴 정리는 "다른 사용자가 같은 키를 가리키는가" 를 키 목록으로 되묻는다. 그 조회를 위한
-- 인덱스다. 키가 있는 행만 대상이라 부분 인덱스로 둔다.
CREATE INDEX IF NOT EXISTS idx_content_images_storage_object_key
    ON content_images (storage_object_key)
    WHERE storage_object_key IS NOT NULL;

-- 사용량 합계가 이미지를 소유자별로 더하려면 video_id 로 videos 를 타야 한다.
-- V13 의 idx_content_images_video_id 가 이미 그 조인을 받쳐 준다.
