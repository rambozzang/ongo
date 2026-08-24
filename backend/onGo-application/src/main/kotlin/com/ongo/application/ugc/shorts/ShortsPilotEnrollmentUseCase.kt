package com.ongo.application.ugc.shorts

import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.shorts.PipelineRunRepository
import com.ongo.domain.ugc.shorts.ShortsPilotActorType
import com.ongo.domain.ugc.shorts.ShortsPilotEvent
import com.ongo.domain.ugc.shorts.ShortsPilotEventRepository
import com.ongo.domain.ugc.shorts.ShortsPilotEventType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 유료 파일럿 코호트 등록.
 *
 * ## 왜 운영자가 지정하는가
 *
 * "돈을 받고 만든 실행"과 "무료로 돌려본 실행"은 데이터로 구분되지 않는다. 결제 테이블과
 * 파이프라인 실행 사이에 연결이 없기 때문이다. 5~10명 파일럿에서는 그 연결을 자동화하는
 * 것보다 운영자가 명시적으로 표시하는 편이 정확하고, 틀렸을 때 원인도 분명하다.
 *
 * ## 무엇을 저장하지 않는가
 *
 * 이메일·영상·결제 정보를 받지도 돌려주지도 않는다. 남는 것은 runId, 운영자 userId,
 * 시각뿐이다.
 */
@Service
class ShortsPilotEnrollmentUseCase(
    private val pipelineRunRepository: PipelineRunRepository,
    private val pilotEventRepository: ShortsPilotEventRepository,
) {

    /** 등록 결과. 이미 등록돼 있었는지를 호출자가 응답에 반영한다. */
    data class EnrollmentResult(val runId: Long, val alreadyEnrolled: Boolean)

    /**
     * 실행을 파일럿 코호트에 넣는다.
     *
     * 이미 등록된 실행은 **성공으로 처리한다.** 운영자가 목록을 훑으며 다시 누르는 일은
     * 흔하고, 그때 오류를 띄우면 "내가 뭘 잘못했나"를 확인하느라 시간을 쓴다. 코호트에
     * 들어 있다는 결과는 어느 쪽이든 같다.
     *
     * ## 왜 조회로 먼저 확인하지 않는가
     *
     * 조회 후 삽입은 동시 요청 둘이 모두 "아직 없다"를 보고 통과한다. 그러면 두 번째
     * INSERT 가 부분 유니크 인덱스에 걸려 500 으로 새어 나간다 — DB 는 중복 행을 막았지만
     * 운영자는 원인을 알 수 없는 서버 오류를 본다. 검사와 삽입을 한 문장에 두어 두 번째
     * 요청도 정상 응답을 받게 한다.
     */
    @Transactional
    fun enroll(actorUserId: Long, runId: Long): EnrollmentResult {
        // 없는 실행을 코호트에 넣으면 파일럿 인원이 실제보다 많아 보인다.
        pipelineRunRepository.findById(runId) ?: throw NotFoundException("파이프라인 실행", runId)

        val inserted = pilotEventRepository.insertEnrollmentIfAbsent(
            ShortsPilotEvent(
                runId = runId,
                eventType = ShortsPilotEventType.PILOT_ENROLLED,
                actorType = ShortsPilotActorType.ADMIN,
                actorId = actorUserId,
            ),
        )
        // 충돌은 실패가 아니다. 코호트에 들어 있다는 결과는 어느 쪽이든 같다.
        return EnrollmentResult(runId = runId, alreadyEnrolled = !inserted)
    }
}
