package com.ongo.application.video

/**
 * 멀티파트 업로드를 몇 조각으로, 조각당 몇 바이트로 나눌지.
 *
 * ## 왜 서버가 정하는가
 *
 * 조각 크기를 클라이언트가 정하게 두면, 조각 URL 을 발급할 때 "이 조각은 몇 바이트여야
 * 하는가" 를 서버가 알 수 없다. 서버가 계획을 세우고 **조각마다 크기를 서명에 넣으면**,
 * 신고와 다른 크기의 조각은 스토리지가 그 자리에서 거부한다. 단일 PUT 이 신고 크기를
 * 서명에 묶어 용량 우회를 막던 것과 같은 방어다.
 *
 * ## 제약 (S3 규격, R2 동일)
 *
 * - 마지막을 제외한 조각은 **5 MiB 이상**
 * - 조각 번호는 1..10,000
 *
 * 기본 16 MiB 로 자른다. 2 GB 면 128 조각이다. 조각이 작을수록 끊겼을 때 다시 보내는 양이
 * 줄지만 요청 수가 늘고, 클수록 그 반대다. 16 MiB 는 느린 회선(수 Mbps)에서도 조각 하나가
 * 1분 안쪽에 끝나는 크기다. 파일이 커서 10,000 조각을 넘기면 조각을 키운다.
 */
data class MultipartUploadPlan(
    val totalSize: Long,
    val partSize: Long,
) {
    init {
        require(totalSize > 0) { "업로드 크기가 올바르지 않습니다." }
        require(partSize >= MIN_PART_SIZE) { "조각 크기는 ${MIN_PART_SIZE}바이트 이상이어야 합니다." }
        require(partCount <= MAX_PARTS) { "조각 수가 ${MAX_PARTS}개를 넘습니다." }
    }

    val partCount: Int get() = ((totalSize + partSize - 1) / partSize).toInt()

    /** [partNumber] 번 조각이 가져야 할 정확한 바이트 수. 마지막 조각만 짧을 수 있다. */
    fun sizeOf(partNumber: Int): Long {
        require(partNumber in 1..partCount) { "조각 번호 $partNumber 은 1..$partCount 범위 밖입니다." }
        return if (partNumber < partCount) partSize else totalSize - partSize * (partCount - 1)
    }

    companion object {
        const val MIN_PART_SIZE: Long = 5L * 1024 * 1024
        const val DEFAULT_PART_SIZE: Long = 16L * 1024 * 1024
        const val MAX_PARTS: Int = 10_000

        fun forSize(totalSize: Long): MultipartUploadPlan {
            require(totalSize > 0) { "업로드 크기가 올바르지 않습니다." }
            // 올림 나눗셈으로 10,000 조각 안에 들어가는 최소 조각 크기를 구한다.
            val needed = (totalSize + MAX_PARTS - 1) / MAX_PARTS
            return MultipartUploadPlan(totalSize, maxOf(DEFAULT_PART_SIZE, needed))
        }
    }
}
