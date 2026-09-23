package com.ongo.domain.video

import com.ongo.domain.contentsource.VideoSource

/**
 * 요금제의 "월 업로드" 한도가 **무엇을 세는가**.
 *
 * ## 규칙: 사용자가 그 달에 새로 들여온 원본 콘텐츠
 *
 * 센다 — PC 업로드, 구글 드라이브 가져오기, URL 가져오기, 그리고 그 경로로 만든 초안
 * (글·이미지 게시물, CSV 가져오기, 에셋을 영상으로 전환한 것).
 *
 * 세지 않는다 —
 *  - [VideoSource.GENERATED]: 서버가 만든 결과물(쇼츠 클립, 생성 영상). 크레딧으로 이미 과금된다.
 *    이것을 세면 쇼츠를 한 번 돌린 무료 사용자가 자기 영상을 올리지 못한다.
 *  - [VideoSource.DERIVED]: 재활용·반복 예약 사본. 원본을 들어올 때 이미 셌다.
 *
 * ## 이 목록을 바꿀 때
 *
 * 셈에 들어가는 행을 만드는 **모든** 경로가 `MonthlyUploadQuotaUseCase` 검사를 거쳐야 한다.
 * 세면서 검사하지 않는 경로가 하나라도 있으면, 사용자는 그 경로로 한도를 채운 뒤 영문도 모른 채
 * 다른 경로에서 막힌다. 반대로 검사하면서 세지 않으면 한도가 새는 구멍이 된다.
 */
object MonthlyUploadPolicy {
    val COUNTED_SOURCES: Set<VideoSource> = setOf(
        VideoSource.UPLOAD_PC,
        VideoSource.GOOGLE_DRIVE,
        VideoSource.URL_IMPORT,
    )

    fun counts(source: VideoSource): Boolean = source in COUNTED_SOURCES
}
