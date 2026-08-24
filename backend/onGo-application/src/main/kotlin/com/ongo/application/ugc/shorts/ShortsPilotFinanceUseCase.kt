package com.ongo.application.ugc.shorts

import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.ShortsPilotActorType
import com.ongo.domain.ugc.shorts.ShortsPilotEvent
import com.ongo.domain.ugc.shorts.ShortsPilotEventRepository
import com.ongo.domain.ugc.shorts.ShortsPilotEventType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 운영자가 **확인한** 실행별 매출과 외부 인프라 원가를 기록한다.
 *
 * ## 자동 연동이 아니다
 *
 * PortOne 결제 내역·AI 제공자 청구서·R2 사용량과 연결돼 있지 않다. `payments` 와
 * 파이프라인 실행 사이에는 FK 가 없고 인프라 사용량 계측도 없다. 여기 들어오는 숫자는
 * 사람이 청구서를 보고 적은 값이며, 그 사실이 필드 이름과 응답에 그대로 드러나야 한다 —
 * 한 번 "실제 원가"로 읽히기 시작하면 그 숫자로 가격을 정하게 된다.
 *
 * ## 왜 append-only 인가
 *
 * 수정·삭제 경로를 만들지 않는다. 분할 청구나 추가 비용은 **행을 더 쌓아** 표현하고
 * 리포트가 합산한다. 고칠 수 있는 원장으로는 단위경제를 주장할 수 없다.
 *
 * ## 인건비는 여기 없다
 *
 * 사람 투입은 [ShortsPilotOperatorTimeUseCase] 가 분 단위로 따로 남긴다. 금액으로 환산해
 * 섞으면 시급 가정이 원장에 박혀서 나중에 그 가정을 바꿀 수 없다.
 */
@Service
class ShortsPilotFinanceUseCase(
    private val pipelineRunRepository: PipelineRunRepository,
    private val pilotEventRepository: ShortsPilotEventRepository,
) {

    /** 운영자가 확인한 이 실행의 매출(원). 청구 시스템에서 가져온 값이 아니다. */
    @Transactional
    fun logRevenue(actorUserId: Long, runId: Long, amountKrw: Long) =
        log(actorUserId, runId, amountKrw, ShortsPilotEventType.OPERATOR_REVENUE_LOGGED)

    /** 운영자가 확인한 이 실행의 외부 인프라 원가(원). 사용량 계측에서 가져온 값이 아니다. */
    @Transactional
    fun logExternalCost(actorUserId: Long, runId: Long, amountKrw: Long) =
        log(actorUserId, runId, amountKrw, ShortsPilotEventType.OPERATOR_EXTERNAL_COST_LOGGED)

    private fun log(actorUserId: Long, runId: Long, amountKrw: Long, type: ShortsPilotEventType) {
        /*
         * 하한 1원: 0 은 "무상 제공"과 "기록을 깜빡했다"를 구분하지 못한다. 무상 건은 아예
         * 적지 않고 리포트에서 미기록으로 남는 편이 정직하다.
         * 상한 1억원: 자릿수 오타 방어. append-only 라 잘못 들어간 값을 지울 수 없다.
         */
        if (amountKrw !in MIN_AMOUNT_KRW..MAX_AMOUNT_KRW) {
            throw BusinessException(
                "SHORTS_PILOT_INVALID_AMOUNT",
                "금액은 ${MIN_AMOUNT_KRW}원 이상 ${MAX_AMOUNT_KRW}원 이하로 입력해 주세요. " +
                    "무상 건이라면 기록하지 않는 편이 정확합니다.",
            )
        }

        pipelineRunRepository.findById(runId) ?: throw NotFoundException("파이프라인 실행", runId)

        /*
         * 리포트는 파일럿에 등록된 실행만 집계한다. 미등록 실행에 기록을 허용하면 어디에도
         * 나타나지 않는 행이 쌓이고, 운영자는 입력이 반영된 줄 안다.
         */
        if (runId !in pilotEventRepository.findEnrolledRunIds()) {
            throw BusinessException(
                "SHORTS_PILOT_RUN_NOT_ENROLLED",
                "파일럿에 등록되지 않은 실행입니다. 먼저 파일럿 코호트에 등록해 주세요.",
            )
        }

        pilotEventRepository.save(
            ShortsPilotEvent(
                runId = runId,
                eventType = type,
                actorType = ShortsPilotActorType.ADMIN,
                actorId = actorUserId,
                amountKrw = amountKrw,
            ),
        )
    }

    private companion object {
        const val MIN_AMOUNT_KRW = 1L
        const val MAX_AMOUNT_KRW = 100_000_000L
    }
}
