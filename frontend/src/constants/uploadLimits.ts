/**
 * 업로드 크기 상한. **서버 값과 같아야 한다** — `FileValidationUtil.VIDEO_DIRECT_UPLOAD_MAX_BYTES`(10GiB)·
 * `FileValidationUtil.IMAGE_MAX_FILE_SIZE`(50MiB). 화면이 더 넉넉하면 사용자는 올린 뒤에야 거절당하고,
 * 더 좁으면 서버가 받는 파일을 화면이 막는다.
 *
 * 실제로 올릴 수 있는 양은 요금제 저장공간이 따로 정한다(Free 1GB, Starter 10GB …). 상한 안이라도 공간이
 * 모자라면 서버가 `STORAGE_QUOTA_EXCEEDED` 로 거절하고, 업로드 목록이 업그레이드 링크를 띄운다.
 */
export const VIDEO_MAX_UPLOAD_BYTES = 10 * 1024 * 1024 * 1024
export const VIDEO_MAX_UPLOAD_LABEL = '10GB'

export const IMAGE_MAX_UPLOAD_BYTES = 50 * 1024 * 1024
export const IMAGE_MAX_UPLOAD_LABEL = '50MB'
