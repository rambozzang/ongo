package com.ongo.domain.accountdeletion

/**
 * 사용자 범위 쓰기가 허용되는지 확인하는 단일 관문.
 *
 * **아직 어디에도 적용하지 않았다.** 계약만 고정한 상태다.
 *
 * 적용을 조사 뒤로 미룬 이유가 있다. 조사 없이 먼저 붙이면 **붙이지 않은 경로를
 * "보호됐다"고 오판**하게 된다. 어떤 쓰기 경로가 존재하는지 먼저 전수 조사하고,
 * 그 목록을 기준으로 적용 대상과 누락 탐지 테스트를 함께 만든다.
 *
 * HTTP 필터 하나로는 부족하다. `@Scheduled` 배치와 내부 서비스 호출은 필터를 지나지
 * 않는다. 그래서 도메인 쪽에 관문을 둬서 진입 경로와 무관하게 검사할 수 있게 한다.
 *
 * ## 공유 데이터도 호출자 기준으로 막는다
 *
 * 워크스페이스처럼 여러 사람이 쓰는 데이터라도 **호출자의 게이트**를 본다.
 * 동결된 멤버는 공유 워크스페이스에도 쓰지 못하고, 다른 활성 멤버는 자기 게이트로
 * 평소처럼 일한다. "삭제 시 그 행을 어떻게 할 것인가"(정책 표의 `REVIEW_BLOCK`)와
 * "동결된 사용자가 계속 써도 되는가"는 **다른 질문**이다. 전자가 미정이라고 후자를
 * 허용할 근거가 되지 않는다.
 */
interface UserWriteGuard {

    /**
     * 이 사용자의 쓰기가 허용되지 않으면 예외를 던진다.
     *
     * **fail-closed** 다. 상태를 읽지 못하면 허용하지 않는다. 조회 실패를 "아마 괜찮겠지"로
     * 넘기면 삭제 진행 중인 계정에 데이터가 들어온다.
     *
     * @param origin 이 쓰기가 누구의 것인가. 기본은 사용자 쓰기다
     * @param systemPath [WriteOrigin.SYSTEM_RECONCILIATION] 일 때 필수.
     *   [SystemWritePathRegistry] 에 등록된 경로여야 한다
     *
     * @throws com.ongo.common.exception.AccountFrozenException 동결됐거나 상태를 확인할 수 없을 때
     * @throws IllegalArgumentException 등록되지 않은 경로가 시스템 우회를 시도할 때
     */
    fun requireWritable(
        userId: Long,
        origin: WriteOrigin = WriteOrigin.USER_AUTHORED,
        systemPath: String? = null,
    )
}

/**
 * 쓰기 가능 여부를 boolean 으로 돌려준다. 배치가 항목을 건너뛸 때 쓴다.
 *
 * **어떤 예외가 나든 `false` 다.** `AccountFrozenException` 만 잡으면, 가드 구현이 바뀌어
 * 다른 예외를 던지는 순간 그게 위로 전파되고 호출자가 어떻게 다루느냐에 따라 쓰기가
 * 허용될 수 있다. 예상치 못한 예외를 "쓰기 허용"으로 해석하는 경로를 아예 없앤다.
 *
 * 배치에서만 쓴다. HTTP 경로는 예외를 그대로 올려 409 로 번역해야 한다.
 *
 * @param onBlocked 막힌 이유를 로그로 남기고 싶을 때. 기본은 아무것도 하지 않는다
 */
inline fun UserWriteGuard.canWrite(
    userId: Long,
    onBlocked: (Exception) -> Unit = {},
): Boolean =
    try {
        requireWritable(userId)
        true
    } catch (e: Exception) {
        onBlocked(e)
        false
    }
