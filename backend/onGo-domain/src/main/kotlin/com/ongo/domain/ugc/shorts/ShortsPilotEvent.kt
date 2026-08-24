package com.ongo.domain.ugc.shorts

import java.time.Instant

/**
 * 쇼츠 파일럿 측정 이벤트.
 *
 * ## 왜 콘텐츠를 담지 않는가
 *
 * 이 행은 "무엇을 만들었나"가 아니라 "무슨 일이 언제 일어났나"만 남긴다. 자막·후킹
 * 문구·원본 URL 을 넣으면 저작물과 발화 내용이 측정 테이블로 새고, 계정 삭제 시 지울
 * 대상만 늘어난다. 판단에 필요한 것은 회차와 시각뿐이다.
 *
 * ## 왜 clipId 가 없는가
 *
 * 재실행은 클립을 통째로 지운다. 클립을 참조하면 그 순간 이벤트도 함께 사라져
 * "재실행이 있었다"는 증거가 정확히 필요한 시점에 없어진다. 측정 단위는 run 이다.
 */
data class ShortsPilotEvent(
    val id: Long = 0,
    val runId: Long,
    val eventType: ShortsPilotEventType,
    val actorType: ShortsPilotActorType,
    /** 내부 users.id 만. SYSTEM 이벤트는 null 이다. */
    val actorId: Long? = null,
    /** [ShortsPilotEventType.RENDER_ATTEMPT_FAILED] 의 시도 회차. 그 외에는 null. */
    val attemptNo: Int? = null,
    /**
     * [ShortsPilotEventType.OPERATOR_TIME_LOGGED] 의 분 단위 투입 시간. 그 외에는 null.
     *
     * 운영자가 직접 입력한 값이며 추정하지 않는다. 다른 이벤트에 값이 붙으면 합계가
     * 조용히 부풀어 오르므로 DB CHECK 도 같은 제약을 건다.
     */
    val operatorMinutes: Int? = null,
    /**
     * [ShortsPilotEventType.OPERATOR_REVENUE_LOGGED] / [ShortsPilotEventType.OPERATOR_EXTERNAL_COST_LOGGED]
     * 의 원 단위 금액. 그 외에는 null.
     *
     * 운영자가 손으로 적은 값이다. 결제·청구 시스템과 연동돼 있지 않다. 다른 이벤트에
     * 값이 붙으면 합계가 조용히 부풀어 오르므로 DB CHECK 도 같은 제약을 건다.
     */
    val amountKrw: Long? = null,
    val createdAt: Instant = Instant.now(),
)

enum class ShortsPilotEventType {
    /** 운영자가 이 실행을 유료 파일럿 코호트에 넣었다. run 당 한 번만 가능하다. */
    PILOT_ENROLLED,

    /** 고객이 단계를 재실행했다. 스테이지 행이 삭제되기 **전에** 기록된다. */
    STAGE_RERUN,

    /** 렌더 시도가 실패했다. 재시도마다 새 행이 쌓인다. */
    RENDER_ATTEMPT_FAILED,

    /**
     * 운영자가 이 실행에 쓴 시간을 직접 입력했다. 한 실행에 여러 번 쌓일 수 있고,
     * 보고에서는 합산한다 — 하루를 넘겨 쓴 작업은 나눠 기록하는 편이 정확하다.
     */
    OPERATOR_TIME_LOGGED,

    /**
     * 운영자가 **확인한** 이 실행의 매출(원).
     *
     * 결제 시스템과 연동된 값이 아니다. `payments` 와 파이프라인 실행 사이에는 연결이 없고,
     * 5~10명 파일럿에서는 운영자가 청구서를 보고 적는 편이 정확하다. 한 실행에 여러 번
     * 쌓일 수 있고(분할 청구 등) 리포트는 합산한다.
     */
    OPERATOR_REVENUE_LOGGED,

    /**
     * 운영자가 **확인한** 이 실행의 외부 인프라 원가(원).
     *
     * AI 제공자·저장소 청구서를 사람이 보고 적은 값이다. 실시간 사용량 계측이 아니다.
     * 사람 인건비는 여기 넣지 않는다 — [OPERATOR_TIME_LOGGED] 로 분 단위로 따로 남기고,
     * 리포트에서도 기여이익과 시간을 분리해 보여준다.
     */
    OPERATOR_EXTERNAL_COST_LOGGED,
}

enum class ShortsPilotActorType {
    ADMIN,
    CUSTOMER,
    SYSTEM,
}

/**
 * append-only 저장소. 수정·삭제 메서드를 두지 않는다 —
 * 측정 이력을 고칠 수 있으면 그 이력으로 사업 판단을 할 수 없다.
 */
interface ShortsPilotEventRepository {
    /**
     * 이벤트를 그대로 append 한다. [ShortsPilotEventType.STAGE_RERUN] 과
     * [ShortsPilotEventType.RENDER_ATTEMPT_FAILED] 처럼 **여러 번 쌓이는 것이 정상**인
     * 이벤트가 이 경로를 쓴다.
     */
    fun save(event: ShortsPilotEvent): ShortsPilotEvent

    /**
     * 코호트 등록을 **단일 SQL 로** 시도한다.
     *
     * 읽고-확인하고-저장하면 동시 요청 둘이 모두 "아직 없다"를 보고 통과하고, 두 번째
     * INSERT 가 부분 유니크 인덱스에 걸려 500 으로 새어 나간다. DB 는 중복 행을 막지만
     * 그건 정합성이지 API 계약이 아니다. 그래서 검사와 삽입을 한 문장에 둔다.
     *
     * 등록은 run 당 한 번뿐이라는 점에서 [save] 와 다르다 — 그래서 메서드를 나눈다.
     * 같은 메서드에 조건을 얹으면 누적이 정상인 이벤트까지 조용히 삼켜진다.
     *
     * @return 이번 호출이 행을 만들었으면 true. 이미 등록돼 있어 아무것도 하지 않았으면
     *   false. **충돌은 예외가 아니다** — 호출자는 false 를 정상 응답으로 다룬다.
     */
    fun insertEnrollmentIfAbsent(event: ShortsPilotEvent): Boolean

    fun findByRunId(runId: Long): List<ShortsPilotEvent>

    /**
     * 파일럿에 등록된 실행 ID 집합.
     *
     * 등록 여부의 근거는 [insertEnrollmentIfAbsent] 가 만든 `PILOT_ENROLLED` 행 하나뿐이다.
     * 다른 곳에 코호트 목록을 따로 두면 두 진실이 갈라진다.
     */
    fun findEnrolledRunIds(): List<Long>

    /**
     * 여러 실행의 이벤트를 **한 번의 조회로** 가져온다.
     *
     * 보고는 실행마다 재실행·렌더실패·투입시간을 세야 한다. 실행별로 [findByRunId] 를
     * 돌리면 5~10건이라도 조회가 그만큼 나가고, 그 패턴은 파일럿이 끝난 뒤에도 남는다.
     */
    fun findByRunIds(runIds: List<Long>): List<ShortsPilotEvent>
}
