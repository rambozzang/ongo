package com.ongo.domain.contentsource

/**
 * 영상이 어떤 경로로 들어왔는지.
 *
 * DB 의 `video_source` enum 과 이름이 1:1 대응한다. 값을 추가하면 마이그레이션도 함께 필요하다.
 * (`V42` 최초 정의, `V60` 에서 [URL_IMPORT], `V85` 에서 [GENERATED], `V116` 에서 [DERIVED] 추가)
 */
enum class VideoSource {
    /** 사용자가 PC 에서 직접 업로드 */
    UPLOAD_PC,

    /** 구글 드라이브에서 가져옴 */
    GOOGLE_DRIVE,

    /** YouTube/TikTok/Instagram 등 외부 URL 에서 가져옴 */
    URL_IMPORT,

    /** 서버가 생성함 — 작성 화면의 생성 영상, 쇼츠 파이프라인 결과 클립. 크레딧으로 과금된다. */
    GENERATED,

    /**
     * 이미 있는 콘텐츠의 **사본** — 재활용 재게시, 반복 예약의 회차 영상.
     * 원본은 들어올 때 이미 월 업로드로 세었으므로 다시 세지 않는다(V116).
     */
    DERIVED,
}
