package com.ongo.application.ugc.shorts

import com.ongo.application.ugc.shorts.dto.ShortsPilotEntryListResponse
import com.ongo.application.ugc.shorts.dto.ShortsPilotEntryRow
import com.ongo.application.ugc.shorts.dto.ShortsPilotReversalResponse
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.shorts.REVERSIBLE_PILOT_EVENT_TYPES
import com.ongo.domain.ugc.shorts.ShortsPilotActorType
import com.ongo.domain.ugc.shorts.ShortsPilotEvent
import com.ongo.domain.ugc.shorts.ShortsPilotEventRepository
import com.ongo.domain.ugc.shorts.ShortsPilotEventType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 운영자 수기 기록의 열람과 역분개(취소).
 *
 * ## 왜 취소가 필요한가
 *
 * 매출·외부원가·투입시간은 사람이 손으로 적는다. 300,000 을 3,000,000 으로 잘못 치면
 * 지금까지는 고칠 방법이 전혀 없었다 — update/delete 경로가 없고, 음수로 상쇄하려 해도
 * 하한 1 이 막는다. 그래서 오입력이 영구히 "그럴듯한 숫자"로 합계에 참여했다. 틀린 값은
 * 미기록(null)보다 나쁘다.
 *
 * ## 왜 음수 입력이 아니라 취소 이벤트인가
 *
 * 음수를 일반 입력으로 열면 합계를 임의로 조작할 수 있고, 그런 원장으로는 단위경제를
 * 주장할 수 없다. 취소는 대상 원본을 명시적으로 가리키므로 "무엇을 왜 뺐는지"가 남는다.
 *
 * ## 원본은 지우지 않는다
 *
 * 취소해도 원본 행은 그대로 있다. 무엇을 잘못 적었었는지가 사라지면 감사가 불가능하다.
 * 보고서가 합계에서만 제외한다.
 *
 * ## 자동 이벤트는 취소 대상이 아니다
 *
 * 재실행·렌더 실패는 사람이 적은 값이 아니라 일어난 사실이다. 취소를 허용하면 불편한
 * 사실을 지우는 데 쓰이게 된다.
 */
@Service
class ShortsPilotEntryUseCase(
    private val pilotEventRepository: ShortsPilotEventRepository,
) {

    /**
     * 이 실행의 수기 기록 목록.
     *
     * 조회는 [ShortsPilotEventRepository.findByRunId] 한 번이다. 취소 여부는 같은 목록
     * 안의 취소 행으로 판정하므로 추가 질의가 없다.
     */
    @Transactional(readOnly = true)
    fun entries(runId: Long): ShortsPilotEntryListResponse {
        val events = pilotEventRepository.findByRunId(runId)
        val reversedIds = events.reversedTargetIds()

        return ShortsPilotEntryListResponse(
            entries = events
                .filter { it.eventType in REVERSIBLE_PILOT_EVENT_TYPES }
                .map { event ->
                    ShortsPilotEntryRow(
                        entryId = event.id,
                        type = event.eventType.name,
                        amountKrw = event.amountKrw,
                        operatorMinutes = event.operatorMinutes,
                        recordedAt = event.createdAt,
                        isReversed = event.id in reversedIds,
                    )
                },
        )
    }

    /**
     * 수기 기록 하나를 무효화한다.
     *
     * 이미 취소된 기록도 **성공으로 처리한다.** 운영자가 목록을 훑으며 다시 누르는 일은
     * 흔하고, 그때 오류를 띄우면 "내가 뭘 잘못했나"를 확인하느라 시간을 쓴다. 결과는
     * 어느 쪽이든 같다 — 그 기록은 합계에서 빠져 있다.
     */
    @Transactional
    fun reverse(actorUserId: Long, runId: Long, entryId: Long): ShortsPilotReversalResponse {
        val events = pilotEventRepository.findByRunId(runId)

        /*
         * 이 실행의 이벤트 목록 안에서 찾는다. 전역 조회로 찾은 뒤 runId 를 비교하면
         * 남의 실행 이벤트가 존재한다는 사실이 응답 차이로 새어 나간다.
         */
        val target = events.firstOrNull { it.id == entryId }
            ?: throw NotFoundException("파일럿 기록", entryId)

        if (target.eventType !in REVERSIBLE_PILOT_EVENT_TYPES) {
            /*
             * 취소 이벤트 자체와 자동 이벤트가 여기로 온다. 취소의 취소를 허용하면 합계가
             * 되살아나는데, 그러면 이 원장은 사실상 수정 가능한 장부가 된다.
             */
            throw BusinessException(
                "SHORTS_PILOT_ENTRY_NOT_REVERSIBLE",
                "취소할 수 있는 기록이 아닙니다. 매출·외부원가·투입시간 기록만 취소할 수 있습니다.",
            )
        }

        /*
         * 조회 후 삽입이 아니다. 동시 요청 둘이 모두 "아직 취소 안 됨"을 보고 통과하면
         * 같은 원본에 취소 행이 두 개 생긴다. 부분 유니크 인덱스를 판정자로 삼아
         * 검사와 삽입을 한 문장에 둔다 — 두 번째 요청은 예외 없이 false 를 받는다.
         */
        val inserted = pilotEventRepository.insertReversalIfAbsent(
            ShortsPilotEvent(
                runId = runId,
                eventType = ShortsPilotEventType.OPERATOR_ENTRY_REVERSED,
                actorType = ShortsPilotActorType.ADMIN,
                actorId = actorUserId,
                reversesEventId = entryId,
            ),
        )

        // 충돌은 실패가 아니다. 이미 빠져 있다는 결과는 어느 쪽이든 같다.
        return ShortsPilotReversalResponse(entryId = entryId, alreadyReversed = !inserted)
    }
}

/**
 * 이 이벤트 목록이 무효화하는 원본 id 집합.
 *
 * 보고서와 목록이 같은 규칙을 써야 화면의 "취소됨"과 합계에서 빠진 값이 어긋나지 않는다.
 */
fun List<ShortsPilotEvent>.reversedTargetIds(): Set<Long> =
    filter { it.eventType == ShortsPilotEventType.OPERATOR_ENTRY_REVERSED }
        .mapNotNull { it.reversesEventId }
        .toSet()

/**
 * 취소된 원본과 취소 행 자체를 뺀 목록. 보고서 합계는 이것으로 낸다.
 *
 * 원본을 **지우는 게 아니라** 집계에서만 제외한다. 저장소의 행은 그대로 남는다.
 * 취소 행도 함께 빼는 이유: 금액·시간이 없어 합계를 바꾸지는 않지만, 남겨 두면
 * "기록이 있다/없다" 판정에 섞여 미기록을 0 으로 뒤집을 여지가 생긴다.
 */
fun List<ShortsPilotEvent>.withoutReversedEntries(): List<ShortsPilotEvent> {
    val reversedIds = reversedTargetIds()
    if (reversedIds.isEmpty()) {
        return filterNot { it.eventType == ShortsPilotEventType.OPERATOR_ENTRY_REVERSED }
    }
    return filterNot {
        it.eventType == ShortsPilotEventType.OPERATOR_ENTRY_REVERSED || it.id in reversedIds
    }
}
