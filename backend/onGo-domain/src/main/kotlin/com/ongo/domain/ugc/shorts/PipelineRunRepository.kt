package com.ongo.domain.ugc.shorts

interface PipelineRunRepository {
    data class SaveResult(val run: PipelineRun, val created: Boolean)

    fun save(run: PipelineRun): PipelineRun

    /** 멱등 키가 있으면 동시 요청 중 하나만 새 실행을 만든다. */
    fun saveIdempotently(run: PipelineRun): SaveResult = SaveResult(save(run), created = true)

    fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): PipelineRun? = null

    fun update(run: PipelineRun): PipelineRun

    /**
     * **이 실행을 지금 돌릴 권리를 원자적으로 확보한다.** 확보했으면 true.
     *
     * ## 확보 조건은 `PENDING` 하나다
     *
     * 실행 이벤트를 내는 다섯 곳이 **모두 발행 직전에 `PENDING` 으로 전환한다.**
     *  - 생성: `PipelineRun(status = PENDING)` 을 저장한 뒤 발행
     *  - 단계 재실행: `update(status = PENDING)` 뒤 발행
     *  - 후킹 확정: `update(status = PENDING, errorMessage = null)` 뒤 발행
     *  - 예약 확정: `AWAITING_SCHEDULE` 확인 후 `PENDING` 으로 바꾸고 발행
     *  - 자동 예약 워커: `AWAITING_SCHEDULE` 를 `PENDING` 으로 선점한 뒤 발행
     *
     * 그러므로 **`PENDING` 이 아닌 상태로 들어온 실행 요청은 중복이다.** `FAILED` 를 그대로
     * 발행하는 경로는 없다 — 실패한 실행의 재시도도 재실행 API 를 지나며 거기서 `PENDING`
     * 으로 무장된다(워커의 `fail()` 은 `FAILED` 로 바꾸기만 하고 발행하지 않는다).
     *
     * 조건을 "RUNNING·CANCELLED 만 제외" 로 두면 겹쳐 도착한 중복만 막힌다. 첫 실행이
     * 게이트(`AWAITING_HOOK_SELECTION`)나 완료(`COMPLETED`)에 도달한 **뒤에** 도착한 중복은
     * 그대로 통과해 파이프라인이 처음부터 다시 돌고 모든 AI 단계가 두 번 청구된다.
     *
     * ## 왜 읽고-쓰기로는 안 되는가
     *
     * 파이프라인 실행은 `@Async @TransactionalEventListener(AFTER_COMMIT)` 로 시작되고
     * 그 이벤트를 내는 곳이 다섯 군데다. 재실행 API 의 상태 가드는 잠금 없이 읽고 판단하므로
     * 버튼을 두 번 누르거나 커밋과 비동기 리스너 사이의 틈에 두 번째 요청이 들어오면 두
     * 이벤트가 모두 통과한다. 그 뒤 오케스트레이터마저 조건 없이 `RUNNING` 을 쓰면 두 실행이
     * 나란히 진행되고, **모든 AI 단계가 두 번 청구된다.** 전사는 원본 길이에 비례해 매기므로
     * 긴 영상일수록 이중 차감액이 커진다.
     *
     * 그래서 조건을 SQL 에 둔다. 같은 관용구를 [markStartedIfAbsent]·[markDeliveredIfAbsent]
     * 가 이미 쓴다.
     *
     * ## 확보는 진척으로 기록된다
     *
     * 같은 문장에서 `version` 을 올리고 `updatedAt` 을 갱신한다. 그러지 않으면 확보 직후
     * 오케스트레이터가 생존 등록을 마치기 전 창에서, [failStale] 이 **확보 이전에 읽은
     * `version` 그대로** CAS 에 성공해 방금 시작한 실행을 `FAILED` 로 바꾼다. 그 상태에서
     * 사용자가 재실행을 누르면 같은 작업이 두 번 청구된다.
     *
     * 이 갱신이 있으면 복구기의 낡은 관측은 조건 자체로 빗나가므로, 안전이 생존 레지스트리의
     * **등록 타이밍에 의존하지 않는다.**
     *
     * 실행 중 프로세스가 죽으면 상태가 `RUNNING` 으로 남아 다음 확보가 막힌다. 그 상황은
     * 이 메서드가 생기기 전에도 재실행 API 가 `RUNNING` 을 거절해 똑같이 막혀 있었다 —
     * 회수는 별도 운영 절차의 몫이다.
     */
    fun claimRunning(id: Long): Boolean

    /**
     * 크래시로 `RUNNING` 에 고착된 실행을 **`FAILED` 로만** 되돌린다. 되돌렸으면 true.
     *
     * ## 왜 PENDING 이 아니라 FAILED 인가
     *
     * `FAILED` 는 [claimRunning] 의 조건(`PENDING`)을 만족하지 않는다. 그래서 이 복구는
     * **어떤 작업도 다시 실행하지 않고 어떤 크레딧도 청구하지 않는다.** 되돌려주는 것은
     * "사용자가 재실행을 누를 수 있는 상태" 뿐이다 — 재실행 API 는 `RUNNING` 만 거절하므로
     * `FAILED` 가 되면 기존 경로가 그대로 열린다.
     *
     * `PENDING` 으로 되돌리면 다음 이벤트가 곧바로 확보해 파이프라인이 자동으로 다시 돌고,
     * 만약 원래 작업이 살아 있었다면 같은 단계가 두 번 청구된다. 자동 복구가 사고의 원인이
     * 되는 방향이라 택하지 않는다.
     *
     * ## 원자적 조건
     *
     * 관측한 [expectedVersion] 과 `RUNNING` 을 **함께** 조건에 둔다. 호출자가 상태를 읽은
     * 뒤 살아 있는 작업이 단계를 하나라도 넘겼다면 `version` 이 올라가 이 갱신은 0행이 되고
     * 복구는 일어나지 않는다. 읽고-비교하고-쓰면 그 사이의 진행을 놓친다.
     *
     * 시간 기준만으로는 부족하다. 진척 신호(`updated_at`·`version`)는 **단계 경계에서만**
     * 갱신되므로 오래 걸리는 단계 하나가 죽은 것처럼 보인다. 그래서 호출자는 이 조건에 더해
     * "이 프로세스가 들고 있지 않다"는 확인까지 통과한 뒤에만 불러야 한다.
     */
    fun failStale(id: Long, expectedVersion: Long, reason: String): Boolean

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

    /**
     * **미정산 단계가 남은 `FAILED` 실행만** 골라 온다. 정산 재시도 훑기가 쓴다.
     *
     * ## 왜 `findByStatus(FAILED, …)` 로는 안 되는가
     *
     * `FAILED` 실행은 지워지지 않고 영구히 쌓인다. 상태만으로 앞에서 N 건을 끊으면 오래전에
     * 정산이 끝난 실행들이 그 N 자리를 영원히 차지하고, **정작 환불이 밀린 새 실행에는 영영
     * 도달하지 못한다.** 훑기가 조용히 무력해지는 것이라 더 나쁘다 — 로그도 남지 않는다.
     *
     * 그래서 조건을 질의에 넣어 **처리할 것이 있는 행만** 후보가 되게 한다. 정산에 성공한
     * 실행은 다음 tick 의 후보에서 스스로 빠지므로 목록이 계속 줄어든다.
     *
     * 최신순으로 준다. 자동 정산이 불가능한 행(분해를 모르는 과거 데이터)은 계속 남아 후보에
     * 머무는데, 오래된 순으로 주면 그 행들이 다시 앞자리를 막는다. 사람이 처리해야 할 그
     * 행들은 뒤로 두고 방금 밀린 환불부터 집는다.
     */
    fun findFailedWithUnsettledStages(limit: Int): List<PipelineRun>
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
