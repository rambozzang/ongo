package com.ongo.domain.ugc.shorts

interface ClipPublicationRepository {
    fun findByClipIdAndPlatform(clipId: Long, platform: String): ClipPublication?

    /**
     * 여러 클립의 게시 결과를 **한 번에** 읽는다.
     *
     * 상세 화면은 클립마다 대상별 결과를 보여줘야 하는데, 클립마다 따로 조회하면 클립 수에
     * 비례해 질의가 는다(N+1). 실행 하나에 클립이 여러 개인 것이 정상이라 이 경로는 항상
     * 여러 건이다.
     *
     * 빈 목록을 받으면 질의하지 않고 빈 결과를 돌려준다 — `IN ()` 를 만들지 않기 위해서다.
     */
    fun findByClipIds(clipIds: List<Long>): List<ClipPublication>

    fun findByVideoUploadId(videoUploadId: Long): List<ClipPublication>
    fun save(publication: ClipPublication): ClipPublication
    fun update(publication: ClipPublication): ClipPublication
}
