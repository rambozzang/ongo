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
 * 운영자가 이 실행에 쓴 시간을 기록한다.
 *
 * ## 왜 손으로 입력하는가
 *
 * 사람이 몇 분을 썼는지는 시스템에 신호가 없다. 로그인 시간이나 화면 체류로 추정하면
 * 회의·검수·재작업이 빠지거나 겹쳐서, 그 숫자로 건당 원가를 말할 수 없게 된다.
 * 5~10명 파일럿에서는 운영자가 직접 적는 편이 정확하고 틀렸을 때 고치기도 쉽다.
 *
 * ## 왜 누적인가
 *
 * 한 실행에 여러 번 쌓인다. 하루를 넘겨 쓴 작업은 나눠 적는 편이 정확하고, 그래야
 * "언제 얼마나" 썼는지도 남는다. 보고는 합산한다.
 */
@Service
class ShortsPilotOperatorTimeUseCase(
    private val pipelineRunRepository: PipelineRunRepository,
    private val pilotEventRepository: ShortsPilotEventRepository,
) {

    @Transactional
    fun log(actorUserId: Long, runId: Long, minutes: Int) {
        /*
         * 상한 1440(24시간)은 오타 방어다. 하한 1 은 0 분 기록을 막는다 — 0 은 "안 썼다"와
         * "기록을 깜빡했다"를 구분하지 못해 합계를 오염시킨다. DB CHECK 도 같은 범위를 건다.
         */
        if (minutes !in MIN_MINUTES..MAX_MINUTES) {
            throw BusinessException(
                "SHORTS_PILOT_INVALID_OPERATOR_MINUTES",
                "투입 시간은 ${MIN_MINUTES}분 이상 ${MAX_MINUTES}분 이하로 입력해 주세요. " +
                    "하루를 넘겼다면 나눠서 기록해 주세요.",
            )
        }

        pipelineRunRepository.findById(runId) ?: throw NotFoundException("파이프라인 실행", runId)

        /*
         * 파일럿에 등록되지 않은 실행에는 기록하지 않는다. 보고는 등록된 실행만 집계하므로
         * 여기서 통과시키면 어디에도 나타나지 않는 행이 쌓이고, 운영자는 입력이 반영된 줄
         * 안다. 조용히 사라지느니 지금 거절하는 편이 낫다.
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
                eventType = ShortsPilotEventType.OPERATOR_TIME_LOGGED,
                actorType = ShortsPilotActorType.ADMIN,
                actorId = actorUserId,
                operatorMinutes = minutes,
            ),
        )
    }

    private companion object {
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 1440
    }
}
