package com.ongo.domain.accountdeletion

/**
 * 쓰기가 누구의 것인가.
 *
 * 동결은 **사용자가 만든 쓰기**를 막는 장치다. 시스템 정합성 작업까지 막으면 다른 것이
 * 깨진다. 결제 웹훅 재처리를 멈추면 결제 상태·환불·크레딧 원장이 어긋나고,
 * 무료 크레딧 리셋을 일괄로 건너뛰면 동결이 길어졌다 풀렸을 때 권리가 누락된다.
 *
 * 그래서 둘을 타입으로 나눈다. bypass 가 암묵적으로 새지 않게 하려는 것이다.
 */
enum class WriteOrigin {
    /** 사용자가 요청한 쓰기. 동결 중 막는다. */
    USER_AUTHORED,

    /**
     * 시스템 정합성 작업. 동결 중에도 진행한다.
     *
     * 아무나 쓸 수 없다. [SystemWritePathRegistry] 에 근거와 함께 등록된 경로만
     * 이 값을 쓸 수 있고, 사용할 때마다 로그를 남긴다.
     */
    SYSTEM_RECONCILIATION,
}
