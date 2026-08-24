package com.ongo.application.ugc.shorts

import com.ongo.application.ugc.shorts.dto.ShortsPilotLeadTimeSummary
import com.ongo.application.ugc.shorts.dto.ShortsPilotLimitation
import com.ongo.application.ugc.shorts.dto.ShortsPilotReport
import com.ongo.application.ugc.shorts.dto.ShortsPilotReportState
import com.ongo.application.ugc.shorts.dto.ShortsPilotReportSummary
import com.ongo.application.ugc.shorts.dto.ShortsPilotRunRow
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.ShortsPilotEvent
import com.ongo.domain.ugc.shorts.ShortsPilotEventRepository
import com.ongo.domain.ugc.shorts.ShortsPilotEventType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 유료 파일럿 측정 보고.
 *
 * ## 무엇을 답하는가
 *
 * 첫 실행~첫 납품 리드타임, 재실행·렌더 실패 횟수, 운영자가 직접 입력한 투입 시간.
 * 전부 이미 수집된 사실이다.
 *
 * ## 무엇을 답하지 않는가
 *
 * 수익·전환율·건당 원가는 계산하지 않는다. 결제와 실행 사이에 연결이 없고 인프라 실측도
 * 없어서, 계산하면 그건 추정이지 측정이 아니다. 모른다는 사실은
 * [ShortsPilotLimitation] 으로 코드만 내보내고 숫자를 붙이지 않는다.
 *
 * ## 조회 횟수
 *
 * 등록 ID 조회 1회 + 이벤트 일괄 조회 1회 + 실행 일괄 조회 1회, 총 3회로 고정이다.
 * 실행 수에 비례해 늘지 않는다.
 */
@Service
class ShortsPilotReportUseCase(
    private val pipelineRunRepository: PipelineRunRepository,
    private val pilotEventRepository: ShortsPilotEventRepository,
) {

    @Transactional(readOnly = true)
    fun report(): ShortsPilotReport {
        val enrolledRunIds = pilotEventRepository.findEnrolledRunIds()
        if (enrolledRunIds.isEmpty()) {
            /*
             * 등록이 없으면 집계가 존재하지 않는다. 0 으로 채운 summary 를 돌려주면
             * "실패율 0%", "재실행 0건"으로 읽혀 아직 시작도 안 한 파일럿이 완벽해 보인다.
             */
            return ShortsPilotReport(
                state = ShortsPilotReportState.NO_DATA,
                summary = null,
                runs = emptyList(),
                limitations = LIMITATIONS,
            )
        }

        val eventsByRun = pilotEventRepository.findByRunIds(enrolledRunIds).groupBy { it.runId }
        val runsById = pipelineRunRepository.findByIds(enrolledRunIds).associateBy { it.id }

        val rows = enrolledRunIds.mapNotNull { runId ->
            // 등록 이벤트는 있는데 실행 행이 사라진 경우(삭제 등). 없는 것을 지어내지 않는다.
            val run = runsById[runId] ?: return@mapNotNull null
            val events = eventsByRun[runId].orEmpty()

            ShortsPilotRunRow(
                runId = runId,
                createdAt = run.createdAt,
                startedAt = run.startedAt,
                deliveredAt = run.deliveredAt,
                // 한쪽이라도 없으면 리드타임은 "모른다"다. 0 이 아니다.
                leadTimeMs = leadTimeMs(run.startedAt, run.deliveredAt),
                stageRerunCount = events.count { it.eventType == ShortsPilotEventType.STAGE_RERUN },
                renderAttemptFailureCount =
                    events.count { it.eventType == ShortsPilotEventType.RENDER_ATTEMPT_FAILED },
                operatorMinutes = operatorMinutes(events),
                operatorReportedRevenueKrw = amountSum(events, ShortsPilotEventType.OPERATOR_REVENUE_LOGGED),
                operatorReportedExternalCostKrw =
                    amountSum(events, ShortsPilotEventType.OPERATOR_EXTERNAL_COST_LOGGED),
                contributionExcludingExternalCostKrw = contribution(events),
                contributionPerOperatorHourKrw = contributionPerHour(events),
            )
        }

        return ShortsPilotReport(
            state = ShortsPilotReportState.OK,
            summary = summarize(rows),
            runs = rows,
            limitations = LIMITATIONS,
        )
    }

    /**
     * 시작·납품이 모두 기록됐을 때만 값이 있다.
     *
     * 시작만 있는 실행을 "아직 0ms"로 세면 평균이 실제보다 짧아져 납기가 좋아 보인다.
     */
    private fun leadTimeMs(startedAt: java.time.Instant?, deliveredAt: java.time.Instant?): Long? {
        if (startedAt == null || deliveredAt == null) return null
        val elapsed = java.time.Duration.between(startedAt, deliveredAt).toMillis()
        // 시계 역전 같은 이상값은 음수를 만든다. 그런 값을 평균에 넣느니 모른다고 한다.
        return elapsed.takeIf { it >= 0 }
    }

    /** 입력이 하나도 없으면 null. 0 분과 미입력은 다르다. */
    private fun operatorMinutes(events: List<ShortsPilotEvent>): Int? {
        val logged = events.filter { it.eventType == ShortsPilotEventType.OPERATOR_TIME_LOGGED }
        if (logged.isEmpty()) return null
        return logged.sumOf { it.operatorMinutes ?: 0 }
    }

    /** 해당 유형 이벤트 금액의 합. 기록이 없으면 null 이다 — 0 원과 미기록은 다르다. */
    private fun amountSum(events: List<ShortsPilotEvent>, type: ShortsPilotEventType): Long? {
        val logged = events.filter { it.eventType == type }
        if (logged.isEmpty()) return null
        return logged.sumOf { it.amountKrw ?: 0L }
    }

    /**
     * 매출 − 외부원가. **둘 다 기록됐을 때만** 값이 있다.
     *
     * 한쪽만으로 계산하면 매출만 적힌 실행이 이익률 100% 로 보인다. 파일럿에서 가장
     * 위험한 오독이 그것이라, 모르면 모른다고 두는 편이 낫다.
     */
    private fun contribution(events: List<ShortsPilotEvent>): Long? {
        val revenue = amountSum(events, ShortsPilotEventType.OPERATOR_REVENUE_LOGGED) ?: return null
        val cost = amountSum(events, ShortsPilotEventType.OPERATOR_EXTERNAL_COST_LOGGED) ?: return null
        return revenue - cost
    }

    /**
     * 기여이익 ÷ 투입 시간(시간).
     *
     * 사람 시간을 모르는 채 시간당 수치를 내면 계산이 아니라 창작이다. 분 기록이 없거나
     * 기여이익이 없으면 null 이다.
     */
    private fun contributionPerHour(events: List<ShortsPilotEvent>): Long? {
        val contribution = contribution(events) ?: return null
        val minutes = operatorMinutes(events) ?: return null
        if (minutes <= 0) return null
        return contribution * 60 / minutes
    }

    private fun summarize(rows: List<ShortsPilotRunRow>): ShortsPilotReportSummary {
        val observedLeadTimes = rows.mapNotNull { it.leadTimeMs }
        val loggedMinutes = rows.mapNotNull { it.operatorMinutes }
        val observedContributions = rows.mapNotNull { it.contributionExcludingExternalCostKrw }

        return ShortsPilotReportSummary(
            enrolledRunCount = rows.size,
            startedRunCount = rows.count { it.startedAt != null },
            deliveredRunCount = rows.count { it.deliveredAt != null },
            totalStageReruns = rows.sumOf { it.stageRerunCount },
            totalRenderAttemptFailures = rows.sumOf { it.renderAttemptFailureCount },
            // 아무도 입력하지 않았으면 "0분 썼다"가 아니라 "모른다"다.
            totalOperatorMinutes = loggedMinutes.takeIf { it.isNotEmpty() }?.sum(),
            totalOperatorReportedRevenueKrw = rows.mapNotNull { it.operatorReportedRevenueKrw }
                .takeIf { it.isNotEmpty() }?.sum(),
            totalOperatorReportedExternalCostKrw = rows.mapNotNull { it.operatorReportedExternalCostKrw }
                .takeIf { it.isNotEmpty() }?.sum(),
            // 매출·외부원가가 둘 다 있는 실행만 더한다. 한쪽만 적힌 실행을 섞으면 모수가 어긋난다.
            totalContributionExcludingExternalCostKrw = observedContributions
                .takeIf { it.isNotEmpty() }?.sum(),
            contributionObservedRunCount = observedContributions.size,
            leadTime = observedLeadTimes.takeIf { it.isNotEmpty() }?.let { observed ->
                ShortsPilotLeadTimeSummary(
                    observedRunCount = observed.size,
                    minMs = observed.min(),
                    maxMs = observed.max(),
                    // 관측된 것들만의 평균. 미관측 실행을 0 으로 끼워 넣지 않는다.
                    averageMs = observed.sum() / observed.size,
                )
            },
        )
    }

    private companion object {
        /** 등록이 있든 없든 같은 목록을 낸다 — 모르는 것은 데이터 양과 무관하다. */
        val LIMITATIONS = listOf(
            ShortsPilotLimitation.PAYMENT_NOT_ATTRIBUTED,
            ShortsPilotLimitation.REPEAT_PURCHASE_NOT_MEASURED,
            ShortsPilotLimitation.ACTUAL_INFRASTRUCTURE_COST_NOT_AVAILABLE,
            ShortsPilotLimitation.REVENUE_AND_COST_ARE_OPERATOR_REPORTED,
            ShortsPilotLimitation.LABOR_COST_NOT_INCLUDED_IN_CONTRIBUTION,
        )
    }
}
