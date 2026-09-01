package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.ugc.shorts.PipelineRun
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.PipelineRunStatus
import com.ongo.domain.ugc.shorts.PipelineStage
import com.ongo.infrastructure.persistence.jooq.Fields.CLIP_COUNT
import com.ongo.infrastructure.persistence.jooq.Fields.CREATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.CURRENT_STAGE
import com.ongo.infrastructure.persistence.jooq.Fields.ERROR_MESSAGE
import com.ongo.infrastructure.persistence.jooq.Fields.ID
import com.ongo.infrastructure.persistence.jooq.Fields.IDEMPOTENCY_KEY
import com.ongo.infrastructure.persistence.jooq.Fields.SOURCE_DURATION_MS
import com.ongo.infrastructure.persistence.jooq.Fields.SOURCE_VIDEO_ID
import com.ongo.infrastructure.persistence.jooq.Fields.STATUS
import com.ongo.infrastructure.persistence.jooq.Fields.TEMPLATE_ID
import com.ongo.infrastructure.persistence.jooq.Fields.CROP_JSON
import com.ongo.infrastructure.persistence.jooq.Fields.DELIVERED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.STARTED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.AUTO_SCHEDULE
import com.ongo.infrastructure.persistence.jooq.Fields.AUTO_SCHEDULE_INTERVAL_HOURS
import com.ongo.infrastructure.persistence.jooq.Fields.AUTO_SCHEDULE_PLATFORMS
import com.ongo.infrastructure.persistence.jooq.Fields.AUTO_SCHEDULE_START_AT
import com.ongo.infrastructure.persistence.jooq.Fields.TRANSCRIPT_TEXT
import com.ongo.infrastructure.persistence.jooq.Fields.UPDATED_AT
import com.ongo.infrastructure.persistence.jooq.Fields.USER_ID
import com.ongo.infrastructure.persistence.jooq.Fields.VERSION
import com.ongo.infrastructure.persistence.jooq.Fields.WORKSPACE_ID
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SHORTS_PIPELINE_RUNS
import com.ongo.infrastructure.persistence.jooq.Tables.UGC_SHORTS_RUN_STAGES
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.ZoneOffset

/** 단계 테이블 컬럼. 바깥 실행 테이블과 이름이 겹치므로 반드시 한정한다. */
private val STAGE_RUN_ID = DSL.field(DSL.name("ugc_shorts_run_stages", "run_id"), Long::class.java)
private val STAGE_STATUS = DSL.field(DSL.name("ugc_shorts_run_stages", "status"), String::class.java)
private val STAGE_REFUNDED_CREDITS = DSL.field(DSL.name("ugc_shorts_run_stages", "refunded_credits"), Int::class.java)
private val STAGE_CREDIT_COST = DSL.field(DSL.name("ugc_shorts_run_stages", "credit_cost"), Int::class.java)

@Repository
class ShortsRunJooqRepository(
    private val dsl: DSLContext,
) : PipelineRunRepository {

    override fun save(run: PipelineRun): PipelineRun {
        val id = dsl.insertInto(UGC_SHORTS_PIPELINE_RUNS)
            .set(WORKSPACE_ID, run.workspaceId)
            .set(USER_ID, run.userId)
            .set(SOURCE_VIDEO_ID, run.sourceVideoId)
            .set(TEMPLATE_ID, run.templateId)
            .set(AUTO_SCHEDULE, run.autoSchedule)
            .set(AUTO_SCHEDULE_START_AT, run.autoScheduleStartAt?.let(::localDateTime))
            .set(AUTO_SCHEDULE_INTERVAL_HOURS, run.autoScheduleIntervalHours)
            .set(AUTO_SCHEDULE_PLATFORMS, run.autoSchedulePlatforms.takeIf { it.isNotEmpty() }?.joinToString("\n"))
            .set(IDEMPOTENCY_KEY, run.idempotencyKey)
            .set(REQUEST_HASH, run.requestHash)
            // 견적은 삽입 시점에 한 번만 쓴다. update 에는 넣지 않는다 — 아래 update 주석 참조.
            .set(SOURCE_DURATION_MS, run.sourceDurationMs)
            .set(STATUS, run.status.name)
            .set(CURRENT_STAGE, run.currentStage?.name)
            .set(TRANSCRIPT_TEXT, run.transcriptText)
            .set(CROP_JSON, run.cropJson)
            .set(CLIP_COUNT, run.clipCount)
            .set(ERROR_MESSAGE, run.errorMessage)
            .returningResult(ID)
            .fetchOne()!!
            .get(ID)

        return findById(id)!!
    }

    override fun saveIdempotently(run: PipelineRun): PipelineRunRepository.SaveResult {
        val key = run.idempotencyKey
            ?: return PipelineRunRepository.SaveResult(save(run), created = true)
        val insertedId = dsl.insertInto(UGC_SHORTS_PIPELINE_RUNS)
            .set(WORKSPACE_ID, run.workspaceId)
            .set(USER_ID, run.userId)
            .set(SOURCE_VIDEO_ID, run.sourceVideoId)
            .set(TEMPLATE_ID, run.templateId)
            .set(AUTO_SCHEDULE, run.autoSchedule)
            .set(AUTO_SCHEDULE_START_AT, run.autoScheduleStartAt?.let(::localDateTime))
            .set(AUTO_SCHEDULE_INTERVAL_HOURS, run.autoScheduleIntervalHours)
            .set(AUTO_SCHEDULE_PLATFORMS, run.autoSchedulePlatforms.takeIf { it.isNotEmpty() }?.joinToString("\n"))
            .set(IDEMPOTENCY_KEY, key)
            .set(REQUEST_HASH, run.requestHash)
            .set(SOURCE_DURATION_MS, run.sourceDurationMs)
            .set(STATUS, run.status.name)
            .set(CURRENT_STAGE, run.currentStage?.name)
            .set(TRANSCRIPT_TEXT, run.transcriptText)
            .set(CROP_JSON, run.cropJson)
            .set(CLIP_COUNT, run.clipCount)
            .set(ERROR_MESSAGE, run.errorMessage)
            .onConflict(USER_ID, IDEMPOTENCY_KEY)
            .doNothing()
            .returningResult(ID)
            .fetchOne()
            ?.get(ID)
        val id = insertedId ?: dsl.select(ID)
            .from(UGC_SHORTS_PIPELINE_RUNS)
            .where(USER_ID.eq(run.userId))
            .and(IDEMPOTENCY_KEY.eq(key))
            .fetchOne(ID)
            ?: error("쇼츠 멱등 키 작업을 저장한 뒤 조회할 수 없습니다")
        return PipelineRunRepository.SaveResult(
            run = findById(id) ?: error("쇼츠 파이프라인 실행을 찾을 수 없습니다: $id"),
            created = insertedId != null,
        )
    }

    override fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): PipelineRun? =
        dsl.select()
            .from(UGC_SHORTS_PIPELINE_RUNS)
            .where(USER_ID.eq(userId))
            .and(IDEMPOTENCY_KEY.eq(idempotencyKey))
            .fetchOne()
            ?.toPipelineRun()

    /*
     * SOURCE_DURATION_MS 는 의도적으로 갱신하지 않는다.
     *
     * 이 값은 실행이 만들어질 때 인용된 청구 근거다. update 가 건드릴 수 있으면 재실행마다
     * 금액이 달라질 수 있고, 그건 사용자가 동의한 적 없는 청구다. 여기서 `.set` 하지
     * 않으므로 기존 값이 그대로 보존된다.
     */
    override fun update(run: PipelineRun): PipelineRun {
        // 낙관적 락: 로드 시점 version과 일치할 때만 갱신하고 version을 증가시킨다.
        val affected = dsl.update(UGC_SHORTS_PIPELINE_RUNS)
            .set(TEMPLATE_ID, run.templateId)
            .set(AUTO_SCHEDULE, run.autoSchedule)
            .set(AUTO_SCHEDULE_START_AT, run.autoScheduleStartAt?.let(::localDateTime))
            .set(AUTO_SCHEDULE_INTERVAL_HOURS, run.autoScheduleIntervalHours)
            .set(AUTO_SCHEDULE_PLATFORMS, run.autoSchedulePlatforms.takeIf { it.isNotEmpty() }?.joinToString("\n"))
            .set(STATUS, run.status.name)
            .set(CURRENT_STAGE, run.currentStage?.name)
            .set(TRANSCRIPT_TEXT, run.transcriptText)
            .set(CROP_JSON, run.cropJson)
            .set(CLIP_COUNT, run.clipCount)
            .set(ERROR_MESSAGE, run.errorMessage)
            .set(VERSION, run.version + 1)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(run.id))
            .and(VERSION.eq(run.version))
            .execute()

        if (affected == 0) {
            throw IllegalStateException("실행이 다른 곳에서 수정되었습니다. 새로고침 후 다시 시도해 주세요")
        }
        return findById(run.id)!!
    }

    /*
     * 조건을 SQL 에 둔다. 읽고-비교하고-쓰면 재개된 실행이 첫 시작 시각을 덮어써
     * 리드타임이 실제보다 짧아진다. version 을 건드리지 않는 이유는 이 값이 낙관적 락의
     * 대상인 업무 상태가 아니라 관측 기록이기 때문이다 — 진행 중인 update 를 깨뜨리면
     * 측정 때문에 파이프라인이 실패한다.
     */
    /*
     * 조건을 SQL 에 둔다. 읽고-비교하고-쓰면 동시에 도착한 두 실행 이벤트가 모두 통과해
     * 같은 실행이 두 번 돌고, 모든 AI 단계가 두 번 청구된다. 계약은 인터페이스에 있다.
     *
     * `error_message` 도 함께 비운다 — 종전 `update(status = RUNNING, errorMessage = null)`
     * 이 하던 일과 같아야 재실행이 지난 실패 사유를 남기지 않는다.
     */
    override fun claimRunning(id: Long): Boolean =
        dsl.update(UGC_SHORTS_PIPELINE_RUNS)
            .set(STATUS, PipelineRunStatus.RUNNING.name)
            .set(ERROR_MESSAGE, null as String?)
            /*
             * **확보도 진척이다.** 같은 문장 안에서 version 을 올리고 updated_at 을 갱신한다.
             *
             * 이 두 값이 그대로면, 확보 직후 오케스트레이터가 `activeRunIds` 에 등록하기 전
             * 창에서 고착 복구기가 **확보 이전에 읽은 version 그대로** CAS 에 성공한다.
             * 방금 시작한 실행이 FAILED 로 바뀌는데도 단계는 계속 돌고, 그 사이 사용자가
             * 재실행을 누르면 같은 작업이 두 번 청구된다.
             *
             * `version + 1` 을 SQL 에서 계산한다. 읽어서 더하면 그 사이의 갱신을 덮어쓴다.
             * updated_at 도 함께 옮겨야 stale 판정이 "확보 시점" 부터 세어진다 — 그러지 않으면
             * 무장 시각 기준으로 남아, 무장과 확보 사이가 길었던 실행이 시작하자마자 고착으로
             * 오인된다.
             */
            .set(VERSION, VERSION.plus(1L))
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id))
            // 정상 이벤트는 발행 직전에 반드시 PENDING 으로 무장된다. 계약은 인터페이스에 있다.
            .and(STATUS.eq(PipelineRunStatus.PENDING.name))
            .execute() > 0

    /*
     * 관측한 version 과 RUNNING 을 함께 조건에 둔다. 살아 있는 작업이 단계를 하나라도
     * 넘겼다면 version 이 올라가 0행이 되고 복구는 일어나지 않는다. 계약은 인터페이스에 있다.
     *
     * PENDING 이 아니라 FAILED 로 되돌린다 — FAILED 는 claimRunning 의 조건이 아니므로
     * 이 복구가 어떤 작업도 다시 실행시키지 않는다.
     */
    override fun failStale(id: Long, expectedVersion: Long, reason: String): Boolean =
        dsl.update(UGC_SHORTS_PIPELINE_RUNS)
            .set(STATUS, PipelineRunStatus.FAILED.name)
            .set(ERROR_MESSAGE, reason)
            .set(VERSION, expectedVersion + 1)
            .set(UPDATED_AT, LocalDateTime.now())
            .where(ID.eq(id))
            .and(STATUS.eq(PipelineRunStatus.RUNNING.name))
            .and(VERSION.eq(expectedVersion))
            .execute() > 0

    override fun markStartedIfAbsent(id: Long, startedAt: java.time.Instant): Boolean =
        dsl.update(UGC_SHORTS_PIPELINE_RUNS)
            .set(STARTED_AT, localDateTime(startedAt))
            .where(ID.eq(id))
            .and(STARTED_AT.isNull)
            .execute() > 0

    /*
     * 클립 여러 개가 병렬로 렌더를 끝내면 완료 콜백이 동시에 들어온다. `IS NULL` 조건이
     * 없으면 나중 시각이 첫 납품 시각을 덮어써 리드타임이 실제보다 길어진다.
     */
    override fun markDeliveredIfAbsent(id: Long, deliveredAt: java.time.Instant): Boolean =
        dsl.update(UGC_SHORTS_PIPELINE_RUNS)
            .set(DELIVERED_AT, localDateTime(deliveredAt))
            .where(ID.eq(id))
            .and(DELIVERED_AT.isNull)
            .execute() > 0

    override fun findById(id: Long): PipelineRun? =
        dsl.select()
            .from(UGC_SHORTS_PIPELINE_RUNS)
            .where(ID.eq(id))
            .fetchOne()
            ?.toPipelineRun()

    /** 기본 구현의 반복 조회 대신 단일 IN 질의로 읽는다. */
    override fun findByIds(ids: List<Long>): List<PipelineRun> {
        if (ids.isEmpty()) return emptyList()
        return dsl.select()
            .from(UGC_SHORTS_PIPELINE_RUNS)
            .where(ID.`in`(ids))
            .fetch()
            .map { it.toPipelineRun() }
    }

    override fun findByStatus(status: PipelineRunStatus, limit: Int): List<PipelineRun> =
        dsl.select()
            .from(UGC_SHORTS_PIPELINE_RUNS)
            .where(STATUS.eq(status.name))
            .orderBy(UPDATED_AT.asc())
            .limit(limit.coerceIn(1, 200))
            .fetch()
            .map { it.toPipelineRun() }

    /**
     * 미정산 단계가 **실제로 남아 있는** 실패 실행만 고른다. 조건을 애플리케이션이 아니라
     * 질의에 두어야 후보 목록이 처리량에 따라 줄어든다 — 자세한 이유는 인터페이스 문서 참고.
     *
     * 상관 서브질의의 컬럼은 전부 테이블명으로 한정한다. 두 테이블 모두 `status` 를 갖고 있어
     * 한정하지 않으면 바깥 테이블의 `status` 로 해석돼 조건이 조용히 뒤집힌다.
     */
    override fun findFailedWithUnsettledStages(limit: Int): List<PipelineRun> =
        dsl.select()
            .from(UGC_SHORTS_PIPELINE_RUNS)
            .where(STATUS.eq(PipelineRunStatus.FAILED.name))
            .and(
                DSL.exists(
                    DSL.selectOne()
                        .from(UGC_SHORTS_RUN_STAGES)
                        .where(STAGE_RUN_ID.eq(DSL.field(DSL.name("ugc_shorts_pipeline_runs", "id"), Long::class.java)))
                        .and(STAGE_STATUS.eq("RUNNING"))
                        .and(STAGE_REFUNDED_CREDITS.eq(0))
                        .and(STAGE_CREDIT_COST.gt(0)),
                ),
            )
            .orderBy(UPDATED_AT.desc())
            .limit(limit.coerceIn(1, 200))
            .fetch()
            .map { it.toPipelineRun() }

    override fun findByWorkspace(workspaceId: Long, offset: Int, limit: Int): List<PipelineRun> =
        dsl.select()
            .from(UGC_SHORTS_PIPELINE_RUNS)
            .where(WORKSPACE_ID.eq(workspaceId))
            .orderBy(CREATED_AT.desc())
            .offset(offset)
            .limit(limit)
            .fetch()
            .map { it.toPipelineRun() }

    override fun countByWorkspace(workspaceId: Long): Long =
        dsl.fetchCount(UGC_SHORTS_PIPELINE_RUNS, WORKSPACE_ID.eq(workspaceId)).toLong()

    /*
     * 제외 조건을 목록·총수 두 질의가 **같은 함수로** 만든다. 한쪽만 고치면 총수와 실제
     * 행 수가 어긋나 마지막 페이지가 비어 보인다.
     *
     * 빈 목록일 때 `ID.notIn(emptyList())` 를 쓰지 않는 이유: jOOQ 가 `id not in ()` 를
     * 만들면 SQL 에서 참이 되지 않아 전 행이 사라지는 방언이 있다. 조건 자체를 빼는 편이
     * 의도("제외할 것이 없다")와 정확히 같다.
     */
    private fun excludingCondition(excludedIds: Collection<Long>): Condition =
        if (excludedIds.isEmpty()) DSL.noCondition() else ID.notIn(excludedIds)

    override fun findRecentExcluding(
        excludedIds: Collection<Long>,
        offset: Int,
        limit: Int,
    ): List<PipelineRun> =
        dsl.select()
            .from(UGC_SHORTS_PIPELINE_RUNS)
            .where(excludingCondition(excludedIds))
            // 정렬에 ID 를 덧붙인다. created_at 이 같은 행이 있으면 순서가 페이지마다
            // 달라져 같은 실행이 두 페이지에 보이거나 아예 빠진다.
            .orderBy(CREATED_AT.desc(), ID.desc())
            .offset(maxOf(offset, 0))
            .limit(limit.coerceIn(1, 200))
            .fetch()
            .map { it.toPipelineRun() }

    override fun countRecentExcluding(excludedIds: Collection<Long>): Long =
        dsl.fetchCount(UGC_SHORTS_PIPELINE_RUNS, excludingCondition(excludedIds)).toLong()

    override fun delete(id: Long): Boolean =
        dsl.deleteFrom(UGC_SHORTS_PIPELINE_RUNS)
            .where(ID.eq(id))
            .execute() > 0

    private fun Record.toPipelineRun(): PipelineRun = PipelineRun(
        id = get(ID),
        workspaceId = get(WORKSPACE_ID),
        userId = get(USER_ID),
        sourceVideoId = get(SOURCE_VIDEO_ID),
        templateId = get(TEMPLATE_ID),
        autoSchedule = get(AUTO_SCHEDULE) == true,
        autoScheduleStartAt = localDateTime(AUTO_SCHEDULE_START_AT)?.atZone(ZoneOffset.UTC)?.toInstant(),
        autoScheduleIntervalHours = get(AUTO_SCHEDULE_INTERVAL_HOURS),
        autoSchedulePlatforms = get(AUTO_SCHEDULE_PLATFORMS)
            ?.toString()
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty(),
        idempotencyKey = get(IDEMPOTENCY_KEY),
        requestHash = get(REQUEST_HASH),
        // 이 컬럼 도입 이전 행은 NULL 이다. 그대로 NULL 로 읽어 정액 경로를 타게 둔다.
        sourceDurationMs = get(SOURCE_DURATION_MS),
        status = PipelineRunStatus.valueOf(get(STATUS)),
        currentStage = get(CURRENT_STAGE)?.let { PipelineStage.valueOf(it) },
        transcriptText = get(TRANSCRIPT_TEXT),
        cropJson = get(CROP_JSON),
        clipCount = get(CLIP_COUNT),
        errorMessage = get(ERROR_MESSAGE),
        createdAt = localDateTime(CREATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
        updatedAt = localDateTime(UPDATED_AT)!!.atZone(ZoneOffset.UTC).toInstant(),
        // 측정 시작 전에 만들어진 행은 NULL 이다. 소급 추정하지 않는다.
        startedAt = localDateTime(STARTED_AT)?.atZone(ZoneOffset.UTC)?.toInstant(),
        deliveredAt = localDateTime(DELIVERED_AT)?.atZone(ZoneOffset.UTC)?.toInstant(),
        version = get(VERSION),
    )

    private fun localDateTime(value: java.time.Instant): LocalDateTime =
        LocalDateTime.ofInstant(value, ZoneOffset.UTC)

    private companion object {
        val REQUEST_HASH = DSL.field("request_hash", String::class.java)
    }
}
