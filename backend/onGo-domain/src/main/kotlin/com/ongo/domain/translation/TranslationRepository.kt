package com.ongo.domain.translation

interface TranslationRepository {
    fun findByVideoId(videoId: Long): List<VideoTranslation>
    fun findById(id: Long): VideoTranslation?
    fun findByVideoIdAndLanguage(videoId: Long, language: String): VideoTranslation?
    fun save(translation: VideoTranslation): VideoTranslation
    fun update(id: Long, title: String?, description: String?, tags: String?, subtitleContent: String?, status: String?)
    fun delete(id: Long)

    /**
     * **아직 아무도 잡지 않았거나 죽은 워커가 잡고 있던** TRANSLATING 행 하나를
     * 원자적으로 선점한다.
     *
     * 조건을 WHERE 에 두는 것이 핵심이다. 읽고-판단하고-쓰면 복구 tick 두 개가 같은 행을
     * 동시에 통과해 LLM 을 두 번 태운다. 여기서는 DB 가 승자를 정한다.
     *
     * `attempts` 를 함께 올린다. 시도 기록이 없으면 죽는 입력 하나가 무한히 재실행된다.
     *
     * @param staleBefore 이 시각보다 오래된 `claimed_at` 은 죽은 워커로 본다.
     * @return 선점했으면 갱신된 행. 이미 다른 워커가 잡았거나 상태가 바뀌었으면 `null`.
     */
    fun claimForTranslation(id: Long, now: java.time.LocalDateTime, staleBefore: java.time.LocalDateTime): VideoTranslation?

    /** 멈춘 것으로 보이는 TRANSLATING 행. 복구 스캐너가 쓴다. */
    fun findStalled(staleBefore: java.time.LocalDateTime, limit: Int): List<VideoTranslation>

    /**
     * **아직 TRANSLATING 인 행만** 종료 상태로 바꾼다.
     *
     * 환불의 멱등 판정을 DB 가 하게 만든다. 인메모리 영수증 카운터는 재시작을 견디지
     * 못하므로, 재시작 뒤 복구와 사용자 재시도가 같은 행을 두 번 환불할 수 있다.
     * 이 조건부 갱신에서 **이긴 쪽만** 크레딧을 돌려준다.
     *
     * @return 이번 호출이 종료를 확정했으면 true. 이미 끝난 행이면 false.
     */
    fun settleFailure(id: Long, status: String): Boolean

    /**
     * 차감 출처 분해를 갈아끼운다. 재시도 요청이 **새 차감**을 했을 때만 부른다.
     *
     * 이전 시도의 분해는 이미 정산됐으므로 남겨 두면 같은 몫을 두 번 환불한다.
     * `null` 을 넣으면 지운다 — 새 차감이 없었다는 뜻이다.
     */
    fun replaceCreditAllocation(id: Long, allocation: TranslationCreditAllocation?)
}
