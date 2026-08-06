package com.ongo.domain.contentsource

/**
 * 영상이 어떤 경로로 들어왔는지.
 *
 * DB 의 `video_source` enum 과 이름이 1:1 대응한다. 값을 추가하면 마이그레이션도 함께 필요하다.
 * (`V42` 최초 정의, `V60` 에서 [URL_IMPORT] 추가)
 */
enum class VideoSource {
    /** 사용자가 PC 에서 직접 업로드 */
    UPLOAD_PC,

    /** 구글 드라이브에서 가져옴 */
    GOOGLE_DRIVE,

    /** YouTube/TikTok/Instagram 등 외부 URL 에서 가져옴 */
    URL_IMPORT,
}
