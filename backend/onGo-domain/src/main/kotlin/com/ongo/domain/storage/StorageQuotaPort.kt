package com.ongo.domain.storage

interface StorageQuotaPort {
    /**
     * 사용량 판정과 예약 사이를 직렬화하기 위한 per-user 잠금.
     *
     * 검사와 예약 저장 사이에 잠금이 없으면 동시에 들어온 두 요청이 같은 사용량을 읽고 둘 다
     * 한도를 통과한다(TOCTOU). 애플리케이션 레벨 락은 인스턴스가 늘면 무너지므로, 사용자 행을
     * DB 에서 잠가 여러 인스턴스에서도 성립하게 한다. 호출한 트랜잭션이 끝날 때 함께 풀린다.
     */
    fun lockUserForQuota(userId: Long)

    /**
     * 사용자가 실제로 차지하고 있는 바이트.
     *
     * @param excludeVideoId 자기 자신의 예약을 두 번 세지 않도록 제외할 영상. 확정 단계에서
     *        "내 예약을 뺀 사용량 + 내 실제 크기"로 판단하기 위해 쓴다.
     */
    fun calculateUserStorageBytes(userId: Long, excludeVideoId: Long? = null): Long
}
