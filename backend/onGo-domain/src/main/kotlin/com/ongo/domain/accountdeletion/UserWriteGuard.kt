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
 */
interface UserWriteGuard {

    /**
     * 이 사용자의 쓰기가 허용되지 않으면 예외를 던진다.
     *
     * **fail-closed** 다. 상태를 읽지 못하면 허용하지 않는다. 조회 실패를 "아마 괜찮겠지"로
     * 넘기면 삭제 진행 중인 계정에 데이터가 들어온다.
     *
     * @throws com.ongo.common.exception.AccountFrozenException 동결됐거나 상태를 확인할 수 없을 때
     */
    fun requireWritable(userId: Long)
}
