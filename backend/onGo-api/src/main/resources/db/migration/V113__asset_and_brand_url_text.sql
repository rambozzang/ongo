-- presigned URL 을 담는 컬럼을 TEXT 로 넓힌다.
--
-- 운영(S3/R2)에서 다운로드 URL 은 SigV4 서명이 붙어 400~530 자에 이른다. 서명 쿼리만
-- `X-Amz-Algorithm`·`Credential`·`Date`·`Expires`·`SignedHeaders`·`Signature` 로 ~314 자이고,
-- 호스트·버킷·`assets/{userId}/`·UUID 가 ~127 자다. 여기까지 이미 ~441 자다.
--
-- 남은 자리는 파일명 몫인데, 오브젝트 키의 한글은 퍼센트 인코딩되어 **글자당 9 자**가 된다.
-- `여름 브이로그.mp4` 한 개면 79 자라 합계가 500 을 넘긴다 — 한국 크리에이터 대상 제품에서
-- 한글 파일명은 예외가 아니라 기본값이다. 그때 INSERT 는 `22001 value too long` 으로
-- 실패하고, 사용자에게는 원인 없는 오류만 남는다.
--
-- `videos.file_url`(V1)과 `content_images.image_url`(V13)은 처음부터 TEXT 였다. 에셋과
-- 브랜드킷만 VARCHAR(500) 으로 남아 있었다.
--
-- Postgres 에서 varchar → text 는 길이 제약만 떼는 변환이라 테이블 재작성 없이 끝난다
-- (같은 스토리지 표현). 값 손실도, 되돌릴 수 없는 변경도 없다.

ALTER TABLE assets ALTER COLUMN file_url TYPE TEXT;

-- 브랜드킷은 에셋의 URL 을 문자열로 복사해 저장한다(AssetGrid → BrandKitUseCase).
-- 같은 산술이 그대로 적용되므로 네 컬럼을 함께 넓힌다.
--
-- 주의: 이 마이그레이션은 **저장 실패만** 없앤다. 복사된 URL 이 7 일 뒤 만료되는 문제는
-- 남아 있고, assetId 참조로 바꾸는 별도 작업이 필요하다.
ALTER TABLE brand_kits ALTER COLUMN logo_url TYPE TEXT;
ALTER TABLE brand_kits ALTER COLUMN intro_template_url TYPE TEXT;
ALTER TABLE brand_kits ALTER COLUMN outro_template_url TYPE TEXT;
ALTER TABLE brand_kits ALTER COLUMN watermark_url TYPE TEXT;
