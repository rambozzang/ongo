package com.ongo.domain.ugc.shorts

interface PipelineRunRepository {
    data class SaveResult(val run: PipelineRun, val created: Boolean)

    fun save(run: PipelineRun): PipelineRun

    /** 멱등 키가 있으면 동시 요청 중 하나만 새 실행을 만든다. */
    fun saveIdempotently(run: PipelineRun): SaveResult = SaveResult(save(run), created = true)

    fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): PipelineRun? = null

    fun update(run: PipelineRun): PipelineRun

    /**
     * 최초 실행 시각을 **한 번만** 기록한다.
     *
     * 재실행·재개도 같은 진입점을 지나므로, 읽고-비교하고-쓰면 두 번째 실행이 첫 시작
     * 시간을 덮어써 리드타임이 실제보다 짧아진다. 구현은 `WHERE started_at IS NULL` 로
     * 조건을 SQL 에 두어 경쟁 자체를 없앤다.
     *
     * @return 이번 호출이 값을 기록했으면 true. 이미 있었으면 false.
     */
    fun markStartedIfAbsent(id: Long, startedAt: java.time.Instant): Boolean = false

    /**
     * 첫 납품 시각을 **한 번만** 기록한다.
     *
     * 클립 여러 개가 병렬로 렌더를 끝내면 완료 콜백이 동시에 들어온다. 읽고-쓰기로
     * 구현하면 둘 다 "아직 비었다"를 보고 나중 시각으로 덮어쓴다 — 첫 납품이 아니라
     * 마지막 납품이 기록된다.
     *
     * @return 이번 호출이 값을 기록했으면 true. 이미 있었으면 false.
     */
    fun markDeliveredIfAbsent(id: Long, deliveredAt: java.time.Instant): Boolean = false

    fun findById(id: Long): PipelineRun?

    /**
     * 여러 실행을 한 번에 읽는다. 파일럿 보고가 등록된 실행의 시각을 모을 때 쓴다.
     *
     * 기본 구현은 [findById] 를 반복하므로 조회가 건수만큼 나간다 — 테스트 페이크가
     * 그대로 쓰기 위한 것이고, 운영 어댑터는 단일 IN 질의로 재정의한다.
     */
    fun findByIds(ids: List<Long>): List<PipelineRun> = ids.mapNotNull(::findById)
    fun findByStatus(status: PipelineRunStatus, limit: Int): List<PipelineRun>
    fun findByWorkspace(workspaceId: Long, offset: Int, limit: Int): List<PipelineRun>
    fun countByWorkspace(workspaceId: Long): Long

    /**
     * 최근 실행을 워크스페이스 구분 없이 훑되, [excludedIds] 는 뺀다.
     *
     * 파일럿 코호트에 넣을 후보를 고르는 **운영자 전용** 경로다. 제외는 SQL 에서 한다 —
     * 페이지만큼 읽어 온 뒤 애플리케이션에서 거르면 페이지마다 남는 개수가 달라지고,
     * [countRecentExcluding] 이 낸 총수와도 어긋나 페이지 이동이 깨진다.
     *
     * 기본 구현은 빈 목록이다. 테스트 페이크가 컴파일되게 두기 위한 것이고, 운영
     * 어댑터가 단일 질의로 재정의한다 — [findByIds] 와 같은 이유다.
     */
    fun findRecentExcluding(excludedIds: Collection<Long>, offset: Int, limit: Int): List<PipelineRun> =
        emptyList()

    /** [findRecentExcluding] 과 **같은 조건**의 총수. 조건이 갈라지면 페이지 수가 틀어진다. */
    fun countRecentExcluding(excludedIds: Collection<Long>): Long = 0

    fun delete(id: Long): Boolean
}
